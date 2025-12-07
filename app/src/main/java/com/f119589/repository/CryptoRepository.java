package com.f119589.repository;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.f119589.data.client.KrakenClient;
import com.f119589.data.db.AppDb;
import com.f119589.data.db.FavouritePairDao;
import com.f119589.data.entity.FavouritePair;
import com.f119589.dto.AssetPairDto;
import com.f119589.dto.TickEvent;
import com.f119589.service.KrakenWebSocketService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CryptoRepository {

    private static final String TAG = "CryptoRepository";

    private static volatile CryptoRepository INSTANCE;
    private final KrakenClient api;
    private final FavouritePairDao favoriteDao;
    private final ExecutorService networkIo = Executors.newFixedThreadPool(4);
    private final ExecutorService dbIo = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    private final MutableLiveData<List<AssetPairDto>> marketsLive = new MutableLiveData<>();

    private final MutableLiveData<TickEvent> tickEventsLive = new MutableLiveData<>();

    // Map wsName -> altName (needed because REST uses altName, while WS uses wsName)
    private final Map<String, String> wsToAltMap = new ConcurrentHashMap<>();

    private CryptoRepository(Context context) {
        Context appContext = context.getApplicationContext();
        OkHttpClient ok = new OkHttpClient.Builder().build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.kraken.com")
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.api = retrofit.create(KrakenClient.class);
        this.favoriteDao = AppDb.get(appContext).favoritePairDao();
    }

    public static CryptoRepository get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (CryptoRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CryptoRepository(ctx.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<AssetPairDto>> markets() {
        return marketsLive;
    }

    public void refreshAssetPairs() {
        networkIo.submit(() -> {
            try {
                Response<JsonObject> response = api.getAssetPairs().execute();
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "Failed fetching asset pairs from Kraken");
                    return;
                }
                JsonObject body = response.body();
                JsonObject result = body.getAsJsonObject("result");
                if (result == null) {
                    Log.w(TAG, "Missing result data in asset pairs response");
                    return;
                }

                wsToAltMap.clear();
                List<AssetPairDto> assetPairs = result.entrySet()
                        .stream()
                        .map(Map.Entry::getValue)
                        .map(JsonElement::getAsJsonObject)
                        .map(obj -> {
                            String altName = optString(obj, "altname", null); //"XBTUSD"
                            String wsName = optString(obj, "wsname", null); //"XBT/USD"
                            if (altName == null || wsName == null) return null;

                            String base = trimPrefix(optString(obj, "base", "")); //"XXBT" -> "XBT"
                            String quote = trimPrefix(optString(obj, "quote", "")); //"ZUSD" -> "USD"
                            String display = prettifyDisplay(base, quote);
                            return new AssetPairDto(wsName, altName, display, base, quote);
                        })
                        .filter(Objects::nonNull)
                        .peek(dto -> wsToAltMap.put(dto.wsName(), dto.altName()))
                        .sorted(Comparator
                                .comparing(AssetPairDto::quote)
                                .thenComparing(AssetPairDto::base))
                        .collect(Collectors.toList());

                marketsLive.postValue(assetPairs);
            } catch (Exception ex) {
                Log.e(TAG, "refreshAssetPairs error", ex);
            }
        });
    }

    public LiveData<List<FavouritePair>> favorites() {
        return favoriteDao.observeAll();
    }

    public void addFavorite(Context context, AssetPairDto pair) {
        Context appContext = context.getApplicationContext();
        dbIo.submit(() -> {
            FavouritePair e = favoriteDao.findOneSync(pair.dbSymbol());
            if (e == null) {
                e = new FavouritePair();
            }
            e.setSymbol(pair.dbSymbol());
            e.setDisplayName(pair.display());
            favoriteDao.upsert(e);
            notifyWsSubscriptionsChanged(appContext);
        });
    }

    public void removeFavorite(Context context, String wsSymbol) {
        Context appContext = context.getApplicationContext();
        dbIo.submit(() -> {
            FavouritePair e = favoriteDao.findOneSync(wsSymbol);
            if (e != null) {
                favoriteDao.delete(e);
                notifyWsSubscriptionsChanged(appContext);
            }
        });
    }

    /**
     * Fetches approx. 24h worth of OHLC candles (using 5-min interval),
     * compacts to [ [tsSec, close], ... ] JSON array, and stores to DB.
     */
    public void fetchAndCacheOhlc24h(String wsSymbol) {
        networkIo.submit(() -> {
            try {
                FavouritePair favourite = favoriteDao.findOneSync(wsSymbol);
                if (favourite == null) {
                    // No persisted favorite, skip caching to avoid phantom rows.
                    return;
                }

                String alt = resolveAltSymbol(wsSymbol);
                if (alt == null) {
                    Log.w(TAG, "fetchAndCacheOhlc24h: missing alt name for " + wsSymbol);
                    return;
                }

                // 5-min bars over ~24h => ~288 points; use 'since' = now - 24h
                long nowSec = System.currentTimeMillis() / 1000L;
                long since = nowSec - 24 * 60 * 60;

                Response<JsonObject> r = api.getOhlc(alt, 5, since).execute();
                if (!r.isSuccessful() || r.body() == null) return;

                JsonObject result = r.body().getAsJsonObject("result");
                if (result == null) return;

                // In result, the pair key is the alt name (e.g., "XBTUSD")
                JsonArray arr = result.getAsJsonArray(alt);
                if (arr == null) return;

                // Compact to [[t, close], ...] and track first/last closes for 24h change.
                double firstClose = Double.NaN;
                double lastClose = Double.NaN;
                JsonArray compact = new JsonArray();
                for (JsonElement el : arr) {
                    JsonArray row = el.getAsJsonArray();
                    long t = row.get(0).getAsLong();
                    double close = row.get(4).getAsDouble();
                    if (Double.isNaN(firstClose)) firstClose = close;
                    lastClose = close;
                    JsonArray pt = new JsonArray();
                    pt.add(t);
                    pt.add(close);
                    compact.add(pt);
                }

                Double change24hPercent = null;
                if (!Double.isNaN(firstClose) && !Double.isNaN(lastClose) && firstClose != 0d) {
                    change24hPercent = ((lastClose - firstClose) / firstClose) * 100.0;
                }

                String json = gson.toJson(compact);
                Double firstCloseValue = Double.isNaN(firstClose) ? null : firstClose;
                favoriteDao.updateOhlcCache(wsSymbol, json, System.currentTimeMillis(), change24hPercent, firstCloseValue);

            } catch (Exception ex) {
                Log.e(TAG, "fetchAndCacheOhlc24h error for " + wsSymbol, ex);
            }
        });
    }

    /**
     * Update last price & timestamp (called from WebSocket Service later).
     */
    public void updateLivePrice(String wsSymbol, double price) {
        dbIo.submit(() -> {
            try {
                FavouritePair favourite = favoriteDao.findOneSync(wsSymbol);
                if (favourite == null) return;

                Double baseline = favourite.getOhlc24hFirstClose();
                Double change = favourite.getChange24hPercent();
                if (baseline != null && baseline != 0d) {
                    change = ((price - baseline) / baseline) * 100.0;
                }

                favoriteDao.updatePriceAndChange(wsSymbol, price, System.currentTimeMillis(), change);
            } catch (Exception ex) {
                Log.e(TAG, "updateLivePrice error", ex);
            }
        });
    }

    /**
     * Post a tick event from WebSocket service (for immediate UI updates).
     */
    public void postTickEvent(String wsSymbol, double price) {
        tickEventsLive.postValue(new TickEvent(wsSymbol, price));
    }

    /**
     * Observe tick events from WebSocket service.
     */
    public LiveData<TickEvent> tickEvents() {
        return tickEventsLive;
    }

    // --------------------------- Optional helpers ---------------------------

    /**
     * Quickly fetch a one-shot ticker snapshot (useful before WS connects).
     */
    public void refreshTickerSnapshot(String wsSymbol) {
        networkIo.submit(() -> {
            try {
                String alt = resolveAltSymbol(wsSymbol);
                if (alt == null) {
                    Log.w(TAG, "refreshTickerSnapshot: missing alt name for " + wsSymbol);
                    return;
                }
                Response<JsonObject> r = api.getTicker(alt).execute();
                if (!r.isSuccessful() || r.body() == null) return;

                JsonObject result = r.body().getAsJsonObject("result");
                if (result == null) return;

                // Ticker result key is alt name. Inside, "c" is last trade price [<price>, <lot volume>].
                JsonObject pairObj = result.getAsJsonObject(alt);
                if (pairObj == null) return;

                JsonArray cArr = pairObj.getAsJsonArray("c");
                if (cArr == null || cArr.isEmpty()) return;

                double last = Double.parseDouble(cArr.get(0).getAsString());
                updateLivePrice(wsSymbol, last);

            } catch (Exception ex) {
                Log.e(TAG, "refreshTickerSnapshot error", ex);
            }
        });
    }

    // --------------------------- Private utils ---------------------------

    private static String optString(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    /**
     * Kraken bases/quotes sometimes include prefixes like 'X'/'Z' (e.g., "XXBT","ZUSD"). Strip leading non-letters.
     */
    private static String trimPrefix(String v) {
        if (v == null) return null;
        // normalize to letters/digits
        return v.replaceAll("^[^A-Z]*", "");
    }

    /**
     * Prefer wsName for display; optionally map XBT->BTC if desired.
     */
    private static String prettifyDisplay(String base, String quote) {
        // Example: map XBT -> BTC for friendlier display (optional).
        String baseUi = mapTickerUi(base);
        String quoteUi = mapTickerUi(quote);
        return String.format(Locale.US, "%s/%s", baseUi, quoteUi);
    }

    private static String mapTickerUi(String a) {
        if (a == null) return "";
        if (a.equalsIgnoreCase("XBT")) return "BTC";
        if (a.equalsIgnoreCase("XDG")) return "DOGE";
        if (a.equalsIgnoreCase("XETH")) return "ETH";
        // Add more aliases if you wish; by default return original.
        return a.toUpperCase(Locale.US);
    }

    private String resolveAltSymbol(String wsSymbol) {
        if (wsSymbol == null) return null;
        String alt = wsToAltMap.get(wsSymbol);
        if (alt != null) return alt;

        try {
            Response<JsonObject> response = api.getAssetPairs().execute();
            if (!response.isSuccessful() || response.body() == null) return null;
            JsonObject result = response.body().getAsJsonObject("result");
            if (result == null) return null;

            for (Map.Entry<String, JsonElement> e : result.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                String altName = optString(obj, "altname", null);
                String wsName = optString(obj, "wsname", null);
                if (altName == null || wsName == null) continue;
                wsToAltMap.put(wsName, altName);
                if (wsSymbol.equals(wsName)) {
                    alt = altName;
                }
            }
            return alt;
        } catch (Exception ex) {
            Log.w(TAG, "resolveAltSymbol error for " + wsSymbol, ex);
            return null;
        }
    }

    private void notifyWsSubscriptionsChanged(Context context) {
        Intent intent = new Intent(KrakenWebSocketService.ACTION_REFRESH_SUBSCRIPTIONS);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
