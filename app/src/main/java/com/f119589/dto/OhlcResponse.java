package com.f119589.dto;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public record OhlcResponse(
        @SerializedName("result") JsonObject result
) {
}
