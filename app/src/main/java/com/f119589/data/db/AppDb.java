package com.f119589.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.f119589.data.entity.FavouritePair;

@Database(
        entities = {
                FavouritePair.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDb extends RoomDatabase {

    private static volatile AppDb INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE favorites ADD COLUMN ohlc24hUpdatedAt INTEGER NOT NULL DEFAULT 0");
        }
    };

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
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

}
