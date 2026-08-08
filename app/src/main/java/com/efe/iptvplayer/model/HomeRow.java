package com.efe.iptvplayer.model;

import java.util.List;

/** Anasayfadaki tek bir yatay satır: "Kaldığın Yerden Devam Et", "Yeni Eklenenler", ya da bir kategori. */
public class HomeRow {
    public String title;
    public List<MediaItem> items;

    public HomeRow(String title, List<MediaItem> items) {
        this.title = title;
        this.items = items;
    }
}
