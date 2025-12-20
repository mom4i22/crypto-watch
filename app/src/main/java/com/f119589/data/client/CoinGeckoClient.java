package com.f119589.data.client;

import com.f119589.dto.MarketSnapshotResponse;

import retrofit2.Call;
import retrofit2.http.GET;

public interface CoinGeckoClient {

    @GET("global")
    Call<MarketSnapshotResponse> getGlobal();
}
