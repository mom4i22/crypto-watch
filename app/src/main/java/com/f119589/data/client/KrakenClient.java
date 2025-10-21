package com.f119589.data.client;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface KrakenClient {

    @GET("/0/public/AssetPairs")
    Call<JsonObject> getAssetPairs();

    @GET("/0/public/OHLC")
    Call<JsonObject> getOhlc(
            @Query("pair") String pairAltName,
            @Query("interval") int interval,
            @Query("since") Long since
    );

    @GET("/0/public/Ticker")
    Call<JsonObject> getTicker(@Query("pair") String pairAltName);
}
