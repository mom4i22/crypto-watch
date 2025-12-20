package com.f119589.data.client;

import com.f119589.dto.AssetPairsResponse;
import com.f119589.dto.OhlcResponse;
import com.f119589.dto.TickerResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface KrakenClient {

    @GET("/0/public/AssetPairs")
    Call<AssetPairsResponse> getAssetPairs();

    @GET("/0/public/OHLC")
    Call<OhlcResponse> getOhlc(
            @Query("pair") String pairAltName,
            @Query("interval") int interval,
            @Query("since") Long since
    );

    @GET("/0/public/Ticker")
    Call<TickerResponse> getTicker(@Query("pair") String pairAltName);
}
