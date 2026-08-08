package com.efe.iptvplayer.model;

/**
 * Canlı yayın, film veya dizi bölümünü temsil eden ortak model.
 * Hem M3U hem Xtream Codes kaynaklarından bu modele dönüştürülür.
 */
public class MediaItem {

    public enum Type { LIVE, MOVIE, SERIES_EPISODE }

    private String id;              // Xtream stream_id ya da M3U için hash
    private String name;
    private String posterUrl;       // tvg-logo / stream_icon / cover
    private String streamUrl;       // oynatılacak asıl URL
    private String categoryId;
    private String categoryName;
    private Type type;
    private long addedAtEpochMs;    // "Yeni Eklenenler" sıralaması için
    private String seriesId;        // sadece dizi bölümleri için
    private int season;
    private int episode;

    public MediaItem() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public long getAddedAtEpochMs() { return addedAtEpochMs; }
    public void setAddedAtEpochMs(long addedAtEpochMs) { this.addedAtEpochMs = addedAtEpochMs; }

    public String getSeriesId() { return seriesId; }
    public void setSeriesId(String seriesId) { this.seriesId = seriesId; }

    public int getSeason() { return season; }
    public void setSeason(int season) { this.season = season; }

    public int getEpisode() { return episode; }
    public void setEpisode(int episode) { this.episode = episode; }
}
