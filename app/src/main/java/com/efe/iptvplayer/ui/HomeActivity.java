package com.efe.iptvplayer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.efe.iptvplayer.IPTVApp;
import com.efe.iptvplayer.R;
import com.efe.iptvplayer.adapter.RowAdapter;
import com.efe.iptvplayer.data.AppDatabase;
import com.efe.iptvplayer.data.WatchProgress;
import com.efe.iptvplayer.model.Category;
import com.efe.iptvplayer.model.HomeRow;
import com.efe.iptvplayer.model.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private RecyclerView rvRows;
    private ProgressBar homeProgress;
    private EditText etSearch;
    private RowAdapter rowAdapter;
    private final List<HomeRow> allRows = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        rvRows = findViewById(R.id.rvRows);
        homeProgress = findViewById(R.id.homeProgress);
        etSearch = findViewById(R.id.etSearch);

        rvRows.setLayoutManager(new LinearLayoutManager(this));

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                filterRows(etSearch.getText().toString());
                return true;
            }
            return false;
        });

        buildRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ekrana her dönüşte "Kaldığın Yerden Devam Et" satırını güncelle
        // (kullanıcı bir video izleyip geri gelmiş olabilir).
        refreshContinueWatchingRow();
    }

    private void buildRows() {
        homeProgress.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            List<HomeRow> rows = new ArrayList<>();

            // 1) Kaldığın yerden devam et
            rows.add(new HomeRow("Kaldığın Yerden Devam Et", buildContinueWatchingItems()));

            // 2) Yeni eklenenler (tüm kaynaklardan en yeni 20 öğe)
            List<MediaItem> newest = collectAllItems();
            Collections.sort(newest, (a, b) -> Long.compare(b.getAddedAtEpochMs(), a.getAddedAtEpochMs()));
            rows.add(new HomeRow("Yeni Eklenenler", newest.subList(0, Math.min(20, newest.size()))));

            // 3) Kategori satırları (canlı yayın kategorileri)
            for (Category cat : IPTVApp.get().getLiveCategories()) {
                if (!cat.getItems().isEmpty()) {
                    rows.add(new HomeRow(cat.getName(), cat.getItems()));
                }
            }
            // 4) VOD kategorileri (varsa, Xtream girişinde)
            for (Category cat : IPTVApp.get().getVodCategories()) {
                if (!cat.getItems().isEmpty()) {
                    rows.add(new HomeRow("🎬 " + cat.getName(), cat.getItems()));
                }
            }

            runOnUiThread(() -> {
                allRows.clear();
                allRows.addAll(rows);
                rowAdapter = new RowAdapter(rows, this::openPlayer);
                rvRows.setAdapter(rowAdapter);
                homeProgress.setVisibility(View.GONE);
            });
        });
    }

    private List<MediaItem> buildContinueWatchingItems() {
        List<WatchProgress> progresses = AppDatabase.getInstance(this).watchProgressDao().getContinueWatching();
        List<MediaItem> items = new ArrayList<>();
        for (WatchProgress wp : progresses) {
            MediaItem item = new MediaItem();
            item.setName(wp.name);
            item.setPosterUrl(wp.posterUrl);
            item.setStreamUrl(wp.streamUrl);
            item.setType(MediaItem.Type.MOVIE);
            items.add(item);
        }
        return items;
    }

    private void refreshContinueWatchingRow() {
        if (rowAdapter == null || allRows.isEmpty()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<MediaItem> updated = buildContinueWatchingItems();
            runOnUiThread(() -> {
                allRows.get(0).items = updated;
                rowAdapter.notifyItemChanged(0);
            });
        });
    }

    private List<MediaItem> collectAllItems() {
        List<MediaItem> all = new ArrayList<>();
        for (Category c : IPTVApp.get().getLiveCategories()) all.addAll(c.getItems());
        for (Category c : IPTVApp.get().getVodCategories()) all.addAll(c.getItems());
        return all;
    }

    private void filterRows(String query) {
        if (query == null || query.trim().isEmpty()) {
            rowAdapter = new RowAdapter(allRows, this::openPlayer);
            rvRows.setAdapter(rowAdapter);
            return;
        }
        String q = query.toLowerCase();
        List<MediaItem> matches = new ArrayList<>();
        for (MediaItem item : collectAllItems()) {
            if (item.getName() != null && item.getName().toLowerCase().contains(q)) {
                matches.add(item);
            }
        }
        List<HomeRow> searchRows = new ArrayList<>();
        searchRows.add(new HomeRow("Arama Sonuçları (" + matches.size() + ")", matches));
        rvRows.setAdapter(new RowAdapter(searchRows, this::openPlayer));
    }

    private void openPlayer(MediaItem item) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_NAME, item.getName());
        intent.putExtra(PlayerActivity.EXTRA_URL, item.getStreamUrl());
        intent.putExtra(PlayerActivity.EXTRA_POSTER, item.getPosterUrl());
        startActivity(intent);
    }
}
