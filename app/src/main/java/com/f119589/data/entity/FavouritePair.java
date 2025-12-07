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
    private String symbol; // Kraken-style pair for WS subscription, e.g., "XBT/USD"

    private String displayName;

    private double lastPrice;

    private long lastUpdated;

    /**
     * Cached 24h OHLC data for the sparkline, stored as a compact JSON string.
     */
    private String ohlc24hJson;

    private long ohlc24hUpdatedAt;

    private Double change24hPercent;

    private Double ohlc24hFirstClose;
}
