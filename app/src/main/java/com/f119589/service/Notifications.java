package com.f119589.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.f119589.R;
import com.f119589.ui.MainActivity;

public class Notifications {

    public static final String CHANNEL_ID = "price_ws_channel";
    public static final int NOTIF_ID_FOREGROUND = 1001;

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Prices",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("Kraken WebSocket live price updates");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    public static Notification foreground(Context ctx) {
        Intent open = new Intent(ctx, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_price)
                .setContentTitle("Crypto Watchlist")
                .setContentText("Streaming live prices…")
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }
}
