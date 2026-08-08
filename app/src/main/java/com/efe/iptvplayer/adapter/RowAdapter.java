package com.efe.iptvplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.efe.iptvplayer.R;
import com.efe.iptvplayer.model.HomeRow;
import com.efe.iptvplayer.model.MediaItem;

import java.util.List;

public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    private final List<HomeRow> rows;
    private final PosterAdapter.OnItemClick listener;

    public RowAdapter(List<HomeRow> rows, PosterAdapter.OnItemClick listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        HomeRow row = rows.get(position);
        holder.title.setText(row.title);
        holder.recyclerView.setLayoutManager(
                new LinearLayoutManager(holder.itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
        holder.recyclerView.setAdapter(new PosterAdapter(row.items, listener));
        // Yatay içerideki view'lar geri dönüşümde yeniden ölçülmesin diye:
        holder.recyclerView.setHasFixedSize(false);
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        RecyclerView recyclerView;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.tvRowTitle);
            recyclerView = v.findViewById(R.id.rvRowItems);
        }
    }
}
