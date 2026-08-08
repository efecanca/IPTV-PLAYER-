package com.efe.iptvplayer.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Bir içeriğin kaldığı konumu (ms) saklar. mediaId + streamUrl birlikte
 * benzersiz kabul edilir çünkü M3U kaynaklarında stabil id olmayabilir.
 */
@Entity(tableName = "watch_progress")
public class WatchProgress {

    @PrimaryKey
    @NonNull
    public String key; // streamUrl'in hash'i ya da mediaId

    public String name;
    public String posterUrl;
    public String streamUrl;
    public long positionMs;
    public long durationMs;
    public long lastWatchedAt;

    public WatchProgress() {
        this.key = "";
    }

    public float progressFraction() {
        if (durationMs <= 0) return 0f;
        return Math.min(1f, (float) positionMs / durationMs);
    }
}
