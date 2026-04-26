package com.jox3.tv.ui.series;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;

import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {

    public interface OnClick { void onClick(EpisodeItem ep); }

    private final List<EpisodeItem> items;
    private final OnClick listener;

    public EpisodeAdapter(List<EpisodeItem> items, OnClick l) {
        this.items = items;
        listener = l;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_episode, p, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        EpisodeItem ep = items.get(pos);
        h.tvTitle.setText(ep.title);

        if (ep.plot != null && !ep.plot.isEmpty()) {
            h.tvPlot.setText(ep.plot);
            h.tvPlot.setVisibility(View.VISIBLE);
        } else {
            h.tvPlot.setVisibility(View.GONE);
        }

        // Miniatura
        if (ep.thumb != null && !ep.thumb.isEmpty()) {
            h.ivThumb.setVisibility(View.VISIBLE);
            h.tvThumbPh.setVisibility(View.GONE);
            Glide.with(h.itemView.getContext().getApplicationContext())
                .load(ep.thumb)
                .centerCrop()
                .placeholder(android.R.color.darker_gray)
                .into(h.ivThumb);
        } else {
            h.ivThumb.setVisibility(View.GONE);
            h.tvThumbPh.setVisibility(View.VISIBLE);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(ep));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvTitle, tvPlot, tvThumbPh;

        VH(View v) {
            super(v);
            ivThumb   = v.findViewById(R.id.iv_thumb);
            tvThumbPh = v.findViewById(R.id.tv_thumb_ph);
            tvTitle   = v.findViewById(R.id.tv_title);
            tvPlot    = v.findViewById(R.id.tv_plot);
        }
    }
}
