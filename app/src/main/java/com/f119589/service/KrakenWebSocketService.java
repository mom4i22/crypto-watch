package com.f119589.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.f119589.data.db.AppDb;
import com.f119589.data.entity.FavouritePair;
import com.f119589.repository.CryptoRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class KrakenWebSocketService extends Service {

    public static final String TAG = "PriceWsService";

    /**
     * Send this (regular) broadcast to force the service to resubscribe to current favorites.
     */
    public static final String ACTION_REFRESH_SUBSCRIPTIONS = "ws_refresh_subs";

    private OkHttpClient client;
    private WebSocket socket;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_REFRESH_SUBSCRIPTIONS.equals(intent.getAction())) {
                io.execute(() -> refreshSubscriptions());
            }
        }
    };

    private final Set<String> desiredSubscriptions = new HashSet<>();
    private final Set<String> pendingSubscriptions = new HashSet<>();
    private final Set<String> activeSubscriptions = new HashSet<>();

    // Reconnection control
    private boolean intentionalClose = false;
    private int reconnectAttempts = 0;

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.ensureChannel(this);
        startForeground(Notifications.NOTIF_ID_FOREGROUND, Notifications.foreground(this));

        client = new OkHttpClient.Builder().build();
        ContextCompat.registerReceiver(
                this,
                refreshReceiver,
                new IntentFilter(ACTION_REFRESH_SUBSCRIPTIONS),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        connect();
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(refreshReceiver);
        } catch (Exception ignored) {
        }
        intentionalClose = true;
        if (socket != null) socket.close(1000, "app stop");
        if (client != null) client.dispatcher().executorService().shutdown();
        io.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------------
    // Connection & subscription
    // ---------------------------------------------------------------------

    private void connect() {
        Request req = new Request.Builder()
                .url("wss://ws.kraken.com")
                .build();

        socket = client.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.i(TAG, "WebSocket opened");
                reconnectAttempts = 0;
                io.execute(() -> subscribeToFavorites(webSocket));
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
                Log.w(TAG, "WebSocket failure: " + t.getMessage());
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.i(TAG, "WebSocket closed: " + code + " " + reason);
                if (!intentionalClose) scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (intentionalClose) return;
        reconnectAttempts++;
        long delayMs = Math.min(30_000, 1_000L * (long) Math.pow(2, Math.min(5, reconnectAttempts)));
        io.schedule(() -> {
            Log.i(TAG, "Reconnecting...");
            connect();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void runSafely(String label, ThrowingRunnable task) {
        runSafely(label, null, task);
    }

    private void runSafely(String label, String payload, ThrowingRunnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            if (payload == null) {
                Log.e(TAG, label + " error", ex);
            } else {
                Log.e(TAG, label + " error: " + payload, ex);
            }
        }
    }

    private void subscribeToFavorites(WebSocket ws) {
        runSafely("subscribeToFavorites", () -> {
            List<FavouritePair> favs = AppDb.get(this).favoritePairDao().getAllSync();
            Set<String> pairs = extractSymbols(favs);
            if (pairs.isEmpty()) {
                Log.i(TAG, "No favorites; nothing to subscribe.");
                return;
            }
            desiredSubscriptions.clear();
            desiredSubscriptions.addAll(pairs);
            pendingSubscriptions.clear();
            activeSubscriptions.clear();

            requestSubscriptions(ws, pairs);
        });
    }

    /**
     * Call after favorites change to resubscribe.
     */
    private void refreshSubscriptions() {
        runSafely("refreshSubscriptions", () -> {
            if (socket == null) return;
            List<FavouritePair> favs = AppDb.get(this).favoritePairDao().getAllSync();

            // Determine additions/removals
            Set<String> desired = extractSymbols(favs);

            desiredSubscriptions.clear();
            desiredSubscriptions.addAll(desired);

            Set<String> toRemove = new HashSet<>(activeSubscriptions);
            toRemove.removeAll(desired);

            if (!toRemove.isEmpty()) {
                socket.send(buildUnsubscribePayload(new ArrayList<>(toRemove)));
                activeSubscriptions.removeAll(toRemove);
                pendingSubscriptions.removeAll(toRemove);
                Log.i(TAG, "Unsubscribed: " + toRemove);
            }

            Set<String> toAdd = new HashSet<>(desired);
            toAdd.removeAll(activeSubscriptions);
            toAdd.removeAll(pendingSubscriptions);

            if (!toAdd.isEmpty()) {
                requestSubscriptions(socket, toAdd);
            }
        });
    }

    private void handleMessage(String text) {
        runSafely("handleMessage", text, () -> handleMessageInternal(text));
    }

    private void handleMessageInternal(String text) {
        JsonElement root = JsonParser.parseString(text);

        // 1) Event objects (including subscriptionStatus & heartbeat) — log for debugging
        JsonObject evt = asObject(root);
        if (evt != null) {
            handleEvent(evt);
            return;
        }

        // 2) Data arrays: [ chanId, payloadObj, "ticker", "PAIR" ]
        JsonArray arr = asArray(root);
        if (arr == null || arr.size() < 2) return;

        JsonObject obj = asObject(arr.get(1));
        if (obj == null) return;

        String wsSymbol = extractWsSymbol(arr, obj);
        if (wsSymbol == null) return; // can't route without a symbol

        Double last = extractLastPrice(obj);
        if (last == null) return;

        // Update DB and post tick event to LiveData
        CryptoRepository repo = CryptoRepository.get(getApplicationContext());
        repo.updateLivePrice(wsSymbol, last);
        repo.postTickEvent(wsSymbol, last);
    }

    // ---------------------------------------------------------------------
    // Payload builders
    // ---------------------------------------------------------------------

    private void handleSubscriptionStatus(JsonObject evt) {
        if (!evt.has("status") || !evt.has("pair")) return;
        String status = evt.get("status").getAsString();
        String pair = evt.get("pair").getAsString();

        switch (status) {
            case "subscribed":
                pendingSubscriptions.remove(pair);
                activeSubscriptions.add(pair);
                Log.i(TAG, "Subscription confirmed: " + pair);
                break;
            case "unsubscribed":
                pendingSubscriptions.remove(pair);
                activeSubscriptions.remove(pair);
                Log.i(TAG, "Unsubscribed confirmed: " + pair);
                break;
            case "error":
                pendingSubscriptions.remove(pair);
                activeSubscriptions.remove(pair);
                Log.w(TAG, "Subscription error: " + evt);
                if (desiredSubscriptions.contains(pair)) {
                    io.schedule(() -> requestSubscriptions(socket, Collections.singleton(pair)), 2, TimeUnit.SECONDS);
                }
                break;
            default:
                break;
        }
    }

    private void handleEvent(JsonObject evt) {
        if (!evt.has("event")) return;
        String ev = evt.get("event").getAsString();
        if ("subscriptionStatus".equals(ev)) {
            io.execute(() -> handleSubscriptionStatus(evt));
            return;
        }
        Log.d(TAG, "WS event: " + evt);
    }

    private static Set<String> extractSymbols(List<FavouritePair> favs) {
        if (favs == null) return Collections.emptySet();
        return favs.stream()
                .filter(Objects::nonNull)
                .map(FavouritePair::getSymbol)
                .filter(symbol -> symbol != null && !symbol.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Nullable
    private static JsonObject asObject(JsonElement el) {
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonArray asArray(JsonElement el) {
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
    }

    @Nullable
    private static String extractWsSymbol(JsonArray arr, JsonObject obj) {
        if (obj.has("pair")) {
            return obj.get("pair").getAsString();
        }
        if (arr.size() < 4) return null;
        JsonElement channelEl = arr.get(2);
        JsonElement pairEl = arr.get(3);
        if (channelEl != null && channelEl.isJsonPrimitive()
                && "ticker".equals(channelEl.getAsString())
                && pairEl != null && pairEl.isJsonPrimitive()) {
            return pairEl.getAsString();
        }
        return null;
    }

    @Nullable
    private static Double extractLastPrice(JsonObject obj) {
        JsonElement cEl = obj.get("c");
        if (cEl == null || !cEl.isJsonArray()) return null;
        JsonArray c = cEl.getAsJsonArray();
        if (c.isEmpty() || !c.get(0).isJsonPrimitive()) return null;
        return Double.parseDouble(c.get(0).getAsString());
    }

    private void requestSubscriptions(WebSocket ws, Set<String> pairs) {
        if (ws == null || pairs.isEmpty()) return;
        ws.send(buildSubscribePayload(pairs));
        pendingSubscriptions.addAll(pairs);
        Log.i(TAG, "Subscribe request: " + pairs);
    }

    private static String buildSubscribePayload(Set<String> pairs) {
        // {"event":"subscribe","pair":["XBT/USD","ETH/USD"],"subscription":{"name":"ticker"}}
        JsonArray pairArr = new JsonArray();
        for (String p : pairs) pairArr.add(p);

        return "{\"event\":\"subscribe\",\"pair\":" + pairArr +
                ",\"subscription\":{\"name\":\"ticker\"}}";
    }

    private static String buildUnsubscribePayload(List<String> pairs) {
        JsonArray pairArr = new JsonArray();
        for (String p : pairs) pairArr.add(p);

        return "{\"event\":\"unsubscribe\",\"pair\":" + pairArr +
                ",\"subscription\":{\"name\":\"ticker\"}}";
    }
}
