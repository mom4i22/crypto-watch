package com.f119589.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.f119589.data.client.KrakenClient;
import com.f119589.data.db.AppDb;
import com.f119589.data.db.FavouritePairDao;
import com.f119589.data.entity.FavouritePair;
import com.f119589.dto.AssetPairDto;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CryptoRepository {

    private static final String TAG = "CryptoRepository";

    private static volatile CryptoRepository INSTANCE;

    private final KrakenClient api;
    private final FavouritePairDao favoriteDao;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Gson gson = new Gson();

    // Live markets list (from /AssetPairs)
    private final MutableLiveData<List<AssetPairDto>> marketsLive = new MutableLiveData<>();

    // Map wsName -> altName (needed because REST uses altName, while WS uses wsName)
    private final Map<String, String> wsToAltMap = new ConcurrentHashMap<>();

    private CryptoRepository(Context appCtx) {
        OkHttpClient ok = new OkHttpClient.Builder().build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.kraken.com")
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.api = retrofit.create(KrakenClient.class);
        this.favoriteDao = AppDb.get(appCtx).favoritePairDao();
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

    // --------------------------- Markets (REST) ---------------------------

    /**
     * Expose markets list to UI (observe in MarketsFragment).
     */
    public LiveData<List<AssetPairDto>> markets() {
        return marketsLive;
    }

    /**
     * Pull /AssetPairs, parse into AssetPair list, publish to LiveData.
     */
    public void refreshAssetPairs() {
        io.submit(() -> {
            try {
                Response<JsonObject> r = api.getAssetPairs().execute();
                if (!r.isSuccessful() || r.body() == null) {
                    Log.w(TAG, "AssetPairs failed: " + (r.errorBody() != null ? r.errorBody().string() : "unknown"));
                    return;
                }
                JsonObject body = r.body();
                JsonObject result = body.getAsJsonObject("result");
                if (result == null) {
                    Log.w(TAG, "AssetPairs: missing result");
                    return;
                }

                List<AssetPairDto> list = new ArrayList<>();
                wsToAltMap.clear();

                for (Map.Entry<String, JsonElement> e : result.entrySet()) {
                    JsonObject obj = e.getValue().getAsJsonObject();

                    // Kraken fields of interest
                    String altName = optString(obj, "altname", null);    // e.g., "XBTUSD"
                    String wsName = optString(obj, "wsname", null);     // e.g., "XBT/USD"
                    String base = trimPrefix(optString(obj, "base", ""));  // e.g., "XXBT" -> "XBT"
                    String quote = trimPrefix(optString(obj, "quote", "")); // e.g., "ZUSD" -> "USD"

                    if (altName == null || wsName == null) continue;

                    String display = prettifyDisplay(wsName, base, quote);
                    list.add(new AssetPairDto(wsName, altName, display, base, quote));
                    wsToAltMap.put(wsName, altName);
                }

                // Sort nicely (quote, then base)
                list.sort(Comparator
                        .comparing((AssetPairDto p) -> p.quote)
                        .thenComparing(p -> p.base));

                marketsLive.postValue(list);
            } catch (Exception ex) {
                Log.e(TAG, "refreshAssetPairs error", ex);
            }
        });
    }

    // --------------------------- Favorites (Room) ---------------------------

    /**
     * Observe favorites list from Room.
     */
    public LiveData<List<FavouritePair>> favorites() {
        return favoriteDao.observeAll();
    }

    /**
     * Add/update a favorite (store symbol as wsName, and a friendly display).
     */
    public void addFavorite(AssetPairDto pair) {
        io.submit(() -> {
            FavouritePair e = favoriteDao.findOneSync(pair.dbSymbol());
            if (e == null) e = new FavouritePair();
            e.symbol = pair.dbSymbol();          // e.g., "XBT/USD"
            e.displayName = pair.display;        // e.g., "BTC/USD" (if you choose to map XBT->BTC)
            favoriteDao.upsert(e);
        });
    }

    public void removeFavorite(String wsSymbol) {
        io.submit(() -> {
            FavouritePair e = favoriteDao.findOneSync(wsSymbol);
            if (e != null) favoriteDao.delete(e);
        });
    }

    // --------------------------- OHLC cache (sparkline) ---------------------------

    /**
     * Fetches approx. 24h worth of OHLC candles (using 5-min interval),
     * compacts to [ [tsSec, close], ... ] JSON array, and stores to DB.
     */
    public void fetchAndCacheOhlc24h(String wsSymbol) {
        io.submit(() -> {
            try {
                String alt = wsToAltMap.get(wsSymbol);
                if (alt == null) {
                    // If markets not yet loaded, try a best-effort conversion: remove slash.
                    alt = wsSymbol.replace("/", "");
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

                // Compact to [[t, close], ...]
                JsonArray compact = new JsonArray();
                for (JsonElement el : arr) {
                    JsonArray row = el.getAsJsonArray();
                    long t = row.get(0).getAsLong();
                    double close = row.get(4).getAsDouble();
                    JsonArray pt = new JsonArray();
                    pt.add(t);
                    pt.add(close);
                    compact.add(pt);
                }

                String json = gson.toJson(compact);

                FavouritePair e = favoriteDao.findOneSync(wsSymbol);
                if (e == null) {
                    // If user hasn’t favorited yet, create a minimal entry so sparkline can render after they do.
                    e = new FavouritePair();
                    e.symbol = wsSymbol;
                }
                e.ohlc24hJson = json;
                favoriteDao.upsert(e);

            } catch (Exception ex) {
                Log.e(TAG, "fetchAndCacheOhlc24h error for " + wsSymbol, ex);
            }
        });
    }

    /**
     * Update last price & timestamp (called from WebSocket Service later).
     */
    public void updateLivePrice(String wsSymbol, double price) {
        io.submit(() -> {
            try {
                favoriteDao.updatePrice(wsSymbol, price, System.currentTimeMillis());
            } catch (Exception ex) {
                Log.e(TAG, "updateLivePrice error", ex);
            }
        });
    }

    // --------------------------- Optional helpers ---------------------------

    /**
     * Quickly fetch a one-shot ticker snapshot (useful before WS connects).
     */
    public void refreshTickerSnapshot(String wsSymbol) {
        io.submit(() -> {
            try {
                String alt = wsToAltMap.getOrDefault(wsSymbol, wsSymbol.replace("/", ""));
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
    private static String prettifyDisplay(String wsName, String base, String quote) {
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
}
