package com.efe.iptvplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.efe.iptvplayer.R;
import com.efe.iptvplayer.data.AppDatabase;
import com.efe.iptvplayer.data.WatchProgress;
import com.efe.iptvplayer.model.MediaItem;

import java.util.List;

public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.VH> {

    public interface OnItemClick { void onClick(MediaItem item); }

    private final List<MediaItem> items;
    private final OnItemClick listener;

    public PosterAdapter(List<MediaItem> items, OnItemClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MediaItem item = items.get(position);
        holder.title.setText(item.getName());
        Glide.with(holder.itemView.getContext())
                .load(item.getPosterUrl())
                .placeholder(R.drawable.poster_placeholder)
                .error(R.drawable.poster_placeholder)
                .centerCrop()
                .into(holder.poster);

        // Kaldığın yerden devam et ilerleme çubuğu — arka planda DB'den okunur
        holder.progress.setVisibility(View.GONE);
        new Thread(() -> {
            WatchProgress wp = AppDatabase.getInstance(holder.itemView.getContext())
                    .watchProgressDao().getByKey(item.getStreamUrl());
            if (wp != null && wp.durationMs > 0) {
                int pct = (int) (wp.progressFraction() * 100);
                holder.itemView.post(() -> {
                    holder.progress.setVisibility(View.VISIBLE);
                    holder.progress.setProgress(pct);
                });
            }
        }).start();

        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title;
        ProgressBar progress;
        VH(View v) {
            super(v);
            poster = v.findViewById(R.id.ivPoster);
            title = v.findViewById(R.id.tvTitle);
            progress = v.findViewById(R.id.progressResume);
        }
    }
}
