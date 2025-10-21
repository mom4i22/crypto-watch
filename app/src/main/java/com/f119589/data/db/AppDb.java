package com.f119589.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.f119589.data.entity.FavouritePair;

@Database(
        entities = {
                FavouritePair.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDb extends RoomDatabase {

    private static volatile AppDb INSTANCE;

    public abstract FavouritePairDao favoritePairDao();

    public static AppDb get(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDb.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDb.class,
                                    "cryptowatch.db"
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }

}
