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

    @Query("SELECT COUNT(*) FROM favorites WHERE symbol = :symbol")
    LiveData<Integer> observeIsFavorite(String symbol);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FavouritePair entity);

    @Delete
    void delete(FavouritePair entity);

    @Query("DELETE FROM favorites WHERE symbol = :symbol")
    void deleteBySymbol(String symbol);

    @Query("UPDATE favorites SET lastPrice = :price, lastUpdated = :updatedAt WHERE symbol = :symbol")
    void updatePrice(String symbol, double price, long updatedAt);

    @Query("UPDATE favorites SET ohlc24hJson = :json WHERE symbol = :symbol")
    void updateOhlcCache(String symbol, String json);

    @Query("UPDATE favorites SET displayName = :displayName WHERE symbol = :symbol")
    void updateDisplayName(String symbol, String displayName);
}
