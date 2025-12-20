package com.f119589.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public record OhlcResponse(
        @SerializedName("result") Map<String, List<List<Double>>> result
) {
}
