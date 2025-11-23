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
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_REFRESH_SUBSCRIPTIONS.equals(intent.getAction())) {
                io.submit(() -> refreshSubscriptions());
            }
        }
    };

    // Keep a copy of what we're currently subscribed to (to avoid duplicate subs).
    private final Set<String> currentSubscribed = new HashSet<>();

    // Reconnection control
    private boolean intentionalClose = false;
    private int reconnectAttempts = 0;

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
                io.submit(() -> subscribeToFavorites(webSocket));
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
        io.submit(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            Log.i(TAG, "Reconnecting...");
            connect();
        });
    }

    private void subscribeToFavorites(WebSocket ws) {
        try {
            List<FavouritePair> favs = AppDb.get(this).favoritePairDao().getAllSync();

            Set<String> pairs = favs.stream()
                    .filter(Objects::nonNull)
                    .map(FavouritePair::getSymbol)
                    .filter(symbol -> !symbol.isEmpty())
                    .collect(Collectors.toCollection(HashSet::new));

            if (pairs.isEmpty()) {
                Log.i(TAG, "No favorites; nothing to subscribe.");
                return;
            }
            String payload = buildSubscribePayload(pairs);
            currentSubscribed.clear();
            currentSubscribed.addAll(pairs);
            ws.send(payload);
            Log.i(TAG, "Subscribed to: " + pairs);
        } catch (Exception ex) {
            Log.e(TAG, "subscribeToFavorites error", ex);
        }
    }

    /**
     * Call after favorites change to resubscribe.
     */
    private void refreshSubscriptions() {
        try {
            if (socket == null) return;
            List<FavouritePair> favs = AppDb.get(this).favoritePairDao().getAllSync();

            // Determine additions/removals
            Set<String> desired = favs.stream()
                    .filter(Objects::nonNull)
                    .map(FavouritePair::getSymbol)
                    .collect(Collectors.toCollection(HashSet::new));

            Set<String> toAdd = new HashSet<>(desired);
            toAdd.removeAll(currentSubscribed);

            Set<String> toRemove = new HashSet<>(currentSubscribed);
            toRemove.removeAll(desired);

            if (!toRemove.isEmpty()) {
                socket.send(buildUnsubscribePayload(new ArrayList<>(toRemove)));
                currentSubscribed.removeAll(toRemove);
                Log.i(TAG, "Unsubscribed: " + toRemove);
            }
            if (!toAdd.isEmpty()) {
                socket.send(buildSubscribePayload(toAdd));
                currentSubscribed.addAll(toAdd);
                Log.i(TAG, "Subscribed: " + toAdd);
            }
        } catch (Exception ex) {
            Log.e(TAG, "refreshSubscriptions error", ex);
        }
    }

    // ---------------------------------------------------------------------
    // Message handling
    // ---------------------------------------------------------------------

    private void handleMessage(String text) {
        try {
            JsonElement root = JsonParser.parseString(text);

            // 1) Event objects (including subscriptionStatus & heartbeat) — log for debugging
            if (root.isJsonObject()) {
                var evt = root.getAsJsonObject();
                if (evt.has("event")) {
                    String ev = evt.get("event").getAsString();
                    if ("subscriptionStatus".equals(ev)) {
                        // Helpful to verify you actually subscribed to your pairs
                        // e.g., evt: {"event":"subscriptionStatus","status":"subscribed","pair":"XBT/USD","subscription":{"name":"ticker"},...}
                        android.util.Log.i(TAG, "WS subscriptionStatus: " + evt);
                    } else if ("heartbeat".equals(ev)) {
                        // optional: ignore or very-verbose log
                        // android.util.Log.v(TAG, "WS heartbeat");
                    } else {
                        android.util.Log.d(TAG, "WS event: " + evt);
                    }
                }
                return;
            }

            // 2) Data arrays: [ chanId, payloadObj, "ticker", "PAIR" ]
            if (!root.isJsonArray()) return;
            JsonArray arr = root.getAsJsonArray();
            if (arr.size() < 2) return;

            JsonElement payloadEl = arr.get(1);
            if (!payloadEl.isJsonObject()) return;
            var obj = payloadEl.getAsJsonObject();

            // Determine the pair:
            String wsSymbol = null;
            if (obj.has("pair")) {
                wsSymbol = obj.get("pair").getAsString();
            } else if (arr.size() >= 4) {
                // Index 2 is channel name, index 3 is pair for Kraken public feeds
                String channelName = arr.get(2).isJsonPrimitive() ? arr.get(2).getAsString() : null;
                if ("ticker".equals(channelName) && arr.get(3).isJsonPrimitive()) {
                    wsSymbol = arr.get(3).getAsString();
                }
            }
            if (wsSymbol == null) return; // can't route without a symbol

            // Extract last trade price from "c": ["price","lot volume"]
            if (!obj.has("c")) return;
            JsonArray c = obj.getAsJsonArray("c");
            if (c.isEmpty()) return;

            double last = Double.parseDouble(c.get(0).getAsString());

            // Update DB and post tick event to LiveData
            CryptoRepository repo = CryptoRepository.get(getApplicationContext());
            repo.updateLivePrice(wsSymbol, last);
            repo.postTickEvent(wsSymbol, last);

        } catch (Exception ex) {
            android.util.Log.e(TAG, "handleMessage parse error: " + text, ex);
        }
    }


    // ---------------------------------------------------------------------
    // Payload builders
    // ---------------------------------------------------------------------

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
