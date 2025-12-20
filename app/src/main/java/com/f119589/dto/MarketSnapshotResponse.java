package com.f119589.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public record MarketSnapshotResponse(@SerializedName("data") MarketSnapshotData data) {
    public record MarketSnapshotData(
            @SerializedName("total_market_cap") Map<String, Double> totalMarketCap,
            @SerializedName("total_volume") Map<String, Double> totalVolume,
            @SerializedName("market_cap_percentage") Map<String, Double> marketCapPercentage) {
    }
}
