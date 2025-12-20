package com.f119589.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public record AssetPairsResponse(
        @SerializedName("result") Map<String, AssetPairInfo> result
) {
    public record AssetPairInfo(
            @SerializedName("altname") String altName,
            @SerializedName("wsname") String wsName,
            @SerializedName("base") String base,
            @SerializedName("quote") String quote
    ) {
    }
}
