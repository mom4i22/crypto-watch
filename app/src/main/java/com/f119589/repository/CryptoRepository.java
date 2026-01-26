package com.f119589.repository;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.f119589.data.client.CoinGeckoClient;
import com.f119589.data.client.KrakenClient;
import com.f119589.data.db.AppDb;
import com.f119589.data.db.FavouritePairDao;
import com.f119589.data.entity.FavouritePair;
import com.f119589.dto.AssetPairDto;
import com.f119589.dto.AssetPairsResponse;
import com.f119589.dto.MarketSnapshotDto;
import com.f119589.dto.MarketSnapshotResponse;
import com.f119589.dto.OhlcResponse;
import com.f119589.dto.TickEvent;
import com.f119589.dto.TickerResponse;
import com.f119589.service.KrakenWebSocketService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
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
    private static final String USD_COINGECKO_NAME = "usd";
    private static final String BTC_COINGECKO_NAME = "btc";

    private static volatile CryptoRepository INSTANCE;
    private final KrakenClient api;
    private final CoinGeckoClient geckoApi;
    private final FavouritePairDao favoriteDao;
    private final ExecutorService networkIo = Executors.newFixedThreadPool(4);
    private final ExecutorService dbIo = Executors.newSingleThreadExecutor();
    private final Gson gson;

    private final MutableLiveData<List<AssetPairDto>> marketsLive = new MutableLiveData<>();

    private final MutableLiveData<TickEvent> tickEventsLive = new MutableLiveData<>();
    private final MutableLiveData<MarketSnapshotDto> marketSnapshotLive = new MutableLiveData<>();

    // Map wsName -> altName (needed because REST uses altName, while WS uses wsName)
    private final Map<String, String> wsToAltMap = new ConcurrentHashMap<>();

    private CryptoRepository(Context context) {
        Context appContext = context.getApplicationContext();
        OkHttpClient ok = new OkHttpClient.Builder().build();
        this.gson = new Gson();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.kraken.com")
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        Retrofit geckoRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.coingecko.com/api/v3/")
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        this.api = retrofit.create(KrakenClient.class);
        this.geckoApi = geckoRetrofit.create(CoinGeckoClient.class);
        this.favoriteDao = AppDb.get(appContext).favoritePairDao();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void runNetwork(String label, ThrowingRunnable task) {
        networkIo.submit(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                Log.e(TAG, label + " error", ex);
            }
        });
    }

    private void runDb(String label, ThrowingRunnable task) {
        dbIo.submit(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                Log.e(TAG, label + " error", ex);
            }
        });
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

    public LiveData<MarketSnapshotDto> marketSnapshot() {
        return marketSnapshotLive;
    }

    public void refreshAssetPairs() {
        runNetwork("refreshAssetPairs", () -> {
            Response<AssetPairsResponse> response = api.getAssetPairs().execute();
            AssetPairsResponse body = response.body();
            if (!response.isSuccessful() || body == null || body.result() == null) {
                Log.w(TAG, "Failed fetching asset pairs from Kraken");
                return;
            }
            wsToAltMap.clear();
            List<AssetPairDto> assetPairs = body.result()
                    .values()
                    .stream()
                    .map(info -> {
                        if (info == null) return null;
                        String altName = info.altName();
                        String wsName = info.wsName();
                        if (altName == null || wsName == null) return null;
                        String base = trimPrefix(info.base());
                        String quote = trimPrefix(info.quote());
                        String display = prettifyDisplay(base, quote);
                        wsToAltMap.put(wsName, altName);
                        return new AssetPairDto(wsName, altName, display, base, quote);
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .comparing(AssetPairDto::quote)
                            .thenComparing(AssetPairDto::base))
                    .collect(Collectors.toList());

            marketsLive.postValue(assetPairs);
        });
    }

    public void refreshMarketSnapshot() {
        runNetwork("refreshMarketSnapshot", () -> {
            Response<MarketSnapshotResponse> response = geckoApi.getGlobal().execute();
            MarketSnapshotResponse body = response.body();
            if (!response.isSuccessful() || body == null) {
                Log.w(TAG, "Failed fetching market snapshot from CoinGecko");
                return;
            }
            MarketSnapshotResponse.MarketSnapshotData data = body.data();
            double marketCap = data.totalMarketCap().get(USD_COINGECKO_NAME);
            double volume = data.totalVolume().get(USD_COINGECKO_NAME);
            double btcDominancePercentage = data.marketCapPercentage().get(BTC_COINGECKO_NAME);

            marketSnapshotLive.postValue(new MarketSnapshotDto(
                    marketCap, volume, btcDominancePercentage
            ));
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
        runNetwork("fetchAndCacheOhlc24h for " + wsSymbol, () -> {
            FavouritePair favourite = favoriteDao.findOneSync(wsSymbol);
            if (favourite == null) {
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

            Response<OhlcResponse> r = api.getOhlc(alt, 5, since).execute();
            OhlcResponse body = r.body();
            if (!r.isSuccessful() || body == null) return;

            JsonObject result = body.result();
            JsonElement pairEl = result.get(alt);

            double firstClose = Double.NaN;
            double lastClose = Double.NaN;
            JsonArray compact = new JsonArray();
            for (JsonElement rowEl : pairEl.getAsJsonArray()) {
                if (!rowEl.isJsonArray()) continue;
                JsonArray row = rowEl.getAsJsonArray();
                if (row.size() < 5) continue;
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
        });
    }

    public void updateLivePrice(String wsSymbol, double price) {
        runDb("updateLivePrice for " + wsSymbol, () -> {
            FavouritePair favourite = favoriteDao.findOneSync(wsSymbol);
            if (favourite == null) return;

            Double baseline = favourite.getOhlc24hFirstClose();
            Double change = favourite.getChange24hPercent();
            if (baseline != null && baseline != 0d) {
                change = ((price - baseline) / baseline) * 100.0;
            }

            favoriteDao.updatePriceAndChange(wsSymbol, price, System.currentTimeMillis(), change);
        });
    }

    public void postTickEvent(String wsSymbol, double price) {
        tickEventsLive.postValue(new TickEvent(wsSymbol, price));
    }

    public LiveData<TickEvent> tickEvents() {
        return tickEventsLive;
    }

    public void refreshTickerSnapshot(String wsSymbol) {
        runNetwork("refreshTickerSnapshot for " + wsSymbol, () -> {
            String alt = resolveAltSymbol(wsSymbol);
            if (alt == null) {
                Log.w(TAG, "refreshTickerSnapshot: missing alt name for " + wsSymbol);
                return;
            }
            Response<TickerResponse> r = api.getTicker(alt).execute();
            TickerResponse body = r.body();
            if (!r.isSuccessful() || body == null) return;

            Map<String, TickerResponse.TickerInfo> result = body.result();
            // Ticker result key is alt name. Inside, "c" is last trade price [<price>, <lot volume>].
            TickerResponse.TickerInfo pairObj = result.get(alt);
            if (pairObj == null || pairObj.lastTradeClose() == null || pairObj.lastTradeClose().isEmpty()) return;

            double last = Double.parseDouble(pairObj.lastTradeClose().getFirst());
            updateLivePrice(wsSymbol, last);
        });
    }

    /**
     * Kraken bases/quotes sometimes include prefixes like 'X'/'Z' (e.g., "XXBT","ZUSD"). Strip leading non-letters.
     */
    private static String trimPrefix(String v) {
        if (v == null) return null;
        return v.replaceAll("^[^A-Z]*", "");
    }

    /**
     * Prefer wsName for display; optionally map XBT->BTC if desired.
     */
    private static String prettifyDisplay(String base, String quote) {
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

    private String resolveAltSymbol(String wsSymbol) throws IOException {
        if (wsSymbol == null) return null;
        String alt = wsToAltMap.get(wsSymbol);
        if (alt != null) return alt;

        Response<AssetPairsResponse> response = api.getAssetPairs().execute();
        AssetPairsResponse body = response.body();
        if (!response.isSuccessful() || body == null || body.result() == null) return null;

        for (AssetPairsResponse.AssetPairInfo info : body.result().values()) {
            if (info == null) continue;
            String altName = info.altName();
            String wsName = info.wsName();
            if (altName == null || wsName == null) continue;
            wsToAltMap.put(wsName, altName);
            if (wsSymbol.equals(wsName)) {
                alt = altName;
            }
        }
        return alt;
    }

    private void notifyWsSubscriptionsChanged(Context context) {
        Intent intent = new Intent(KrakenWebSocketService.ACTION_REFRESH_SUBSCRIPTIONS);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
