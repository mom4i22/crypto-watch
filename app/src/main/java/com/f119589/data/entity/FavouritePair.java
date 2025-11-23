package com.f119589.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(
        tableName = "favorites",
        indices = {
                @Index(value = {"displayName"}),
                @Index(value = {"lastUpdated"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavouritePair {

    @PrimaryKey
    @NonNull
    private String symbol;        // Kraken-style pair for WS subscription, e.g., "XBT/USD"

    private String displayName;   // UI-friendly, e.g., "BTC/USD" (optional mapping)

    private double lastPrice;     // latest known price from WS/REST

    private long lastUpdated;     // epoch millis of lastPrice update

    /**
     * Cached 24h OHLC data for the sparkline, stored as a compact JSON string.
     */
    private String ohlc24hJson;

    /**
     * Epoch millis when ohlc24hJson was last refreshed.
     */
    private long ohlc24hUpdatedAt;

    /**
     * Percentage change between the first and last close in the cached OHLC window.
     */
    private Double change24hPercent;

    /**
     * First close value from the cached 24h OHLC window (baseline for live updates).
     */
    private Double ohlc24hFirstClose;
}
