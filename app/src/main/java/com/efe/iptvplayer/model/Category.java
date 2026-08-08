package com.efe.iptvplayer.model;

import java.util.ArrayList;
import java.util.List;

public class Category {
    private String id;
    private String name;
    private List<MediaItem> items = new ArrayList<>();

    public Category(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<MediaItem> getItems() { return items; }
    public void addItem(MediaItem item) { items.add(item); }
}
