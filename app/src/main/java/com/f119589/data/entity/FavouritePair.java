package com.f119589.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "favorites",
        indices = {
                @Index(value = {"displayName"}),
                @Index(value = {"lastUpdated"})
        }
)
public class FavouritePair {

    @PrimaryKey
    @NonNull
    public String symbol;        // Kraken-style pair for WS subscription, e.g., "XBT/USD"

    public String displayName;   // UI-friendly, e.g., "BTC/USD" (optional mapping)

    public double lastPrice;     // latest known price from WS/REST

    public long lastUpdated;     // epoch millis of lastPrice update

    /**
     * Cached 24h OHLC data for the sparkline, stored as a compact JSON string.
     */
    public String ohlc24hJson;

    public FavouritePair(@NonNull String symbol,
                         String displayName,
                         double lastPrice,
                         long lastUpdated,
                         String ohlc24hJson) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.lastPrice = lastPrice;
        this.lastUpdated = lastUpdated;
        this.ohlc24hJson = ohlc24hJson;
    }

    public FavouritePair() {
    }
}
