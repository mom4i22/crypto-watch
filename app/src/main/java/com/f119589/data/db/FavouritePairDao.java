package com.f119589.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.f119589.data.entity.FavouritePair;

import java.util.List;

@Dao
public interface FavouritePairDao {

    @Query("SELECT * FROM favorites ORDER BY displayName ASC")
    LiveData<List<FavouritePair>> observeAll();

    @Query("SELECT * FROM favorites ORDER BY displayName ASC")
    List<FavouritePair> getAllSync();

    @Query("SELECT * FROM favorites WHERE symbol = :symbol LIMIT 1")
    FavouritePair findOneSync(String symbol);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FavouritePair entity);

    @Delete
    void delete(FavouritePair entity);

    @Query("UPDATE favorites SET lastPrice = :price, lastUpdated = :updatedAt, change24hPercent = :changePercent WHERE symbol = :symbol")
    void updatePriceAndChange(String symbol, double price, long updatedAt, Double changePercent);

    @Query("UPDATE favorites SET ohlc24hJson = :json, ohlc24hUpdatedAt = :updatedAt, change24hPercent = :changePercent, ohlc24hFirstClose = :firstClose WHERE symbol = :symbol")
    void updateOhlcCache(String symbol, String json, long updatedAt, Double changePercent, Double firstClose);
}
