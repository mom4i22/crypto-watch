package com.f119589.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public record TickerResponse(
        @SerializedName("result") Map<String, TickerInfo> result
) {
    public record TickerInfo(
            @SerializedName("c") List<String> lastTradeClose
    ) {
    }
}
