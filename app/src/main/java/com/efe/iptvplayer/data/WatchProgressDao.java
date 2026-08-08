package com.efe.iptvplayer.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(WatchProgress progress);

    @Query("SELECT * FROM watch_progress WHERE key = :key LIMIT 1")
    WatchProgress getByKey(String key);

    // "Kaldığın Yerden Devam Et" satırı için: en son izlenenler, bitmemiş olanlar
    @Query("SELECT * FROM watch_progress WHERE (positionMs * 1.0 / durationMs) < 0.95 " +
           "ORDER BY lastWatchedAt DESC LIMIT 25")
    List<WatchProgress> getContinueWatching();

    @Query("DELETE FROM watch_progress WHERE key = :key")
    void delete(String key);
}
