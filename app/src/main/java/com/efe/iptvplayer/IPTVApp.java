package com.efe.iptvplayer;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import com.efe.iptvplayer.model.Category;

public class IPTVApp extends Application {

    private static IPTVApp instance;

    // Yüklenen playlist RAM'de tutulur; her ekran geçişinde tekrar indirmeyi
    // önler (pil ve veri tasarrufu).
    private List<Category> liveCategories = new ArrayList<>();
    private List<Category> vodCategories = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static IPTVApp get() { return instance; }

    public SharedPreferences prefs() {
        return getSharedPreferences("iptv_prefs", MODE_PRIVATE);
    }

    public List<Category> getLiveCategories() { return liveCategories; }
    public void setLiveCategories(List<Category> c) { this.liveCategories = c; }

    public List<Category> getVodCategories() { return vodCategories; }
    public void setVodCategories(List<Category> c) { this.vodCategories = c; }
}
