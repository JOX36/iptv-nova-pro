package com.jox3.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.util.AppPrefs;

import java.util.ArrayList;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onClick(MediaItem item);
        void onFav(MediaItem item, boolean isFav);
    }

    // Tipos de view
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM   = 1;

    // Item interno que puede ser header o MediaItem
    private static class Row {
        boolean isHeader;
        String  header;
        MediaItem item;
        Row(String h)    { isHeader = true;  header = h; }
        Row(MediaItem m) { isHeader = false; item = m;   }
    }

    private final List<Row> rows = new ArrayList<>();
    private List<MediaItem> rawItems = new ArrayList<>();
    private final String type;
    private final AppPrefs prefs;
    private final Listener listener;
    private boolean searchMode = false;

    public MediaAdapter(String type, AppPrefs prefs, Listener l) {
        this.type = type; this.prefs = prefs; this.listener = l;
    }

    /** Modo normal — lista plana sin headers */
    public void setItems(List<MediaItem> list) {
        searchMode = false;
        rawItems = new ArrayList<>(list);
        rows.clear();
        for (MediaItem m : list) rows.add(new Row(m));
        notifyDataSetChanged();
    }

    /** Modo búsqueda — agrupa por tipo con headers */
    public void setSearchResults(List<MediaItem> live, List<MediaItem> vod, List<MediaItem> series) {
        searchMode = true;
        rows.clear();
        if (!live.isEmpty()) {
            rows.add(new Row("📺  EN VIVO  —  " + live.size() + " resultado" + (live.size() > 1 ? "s" : "")));
            for (MediaItem m : live) rows.add(new Row(m));
        }
        if (!vod.isEmpty()) {
            rows.add(new Row("🎬  PELÍCULAS  —  " + vod.size() + " resultado" + (vod.size() > 1 ? "s" : "")));
            for (MediaItem m : vod) rows.add(new Row(m));
        }
        if (!series.isEmpty()) {
            rows.add(new Row("🎭  SERIES  —  " + series.size() + " resultado" + (series.size() > 1 ? "s" : "")));
            for (MediaItem m : series) rows.add(new Row(m));
        }
        if (rows.isEmpty()) {
            // Sin resultados — lista vacía
        }
        notifyDataSetChanged();
    }

    public List<MediaItem> getItems() { return rawItems; }

    @Override public int getItemViewType(int pos) {
        return rows.get(pos).isHeader ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        if (t == TYPE_HEADER) {
            View v = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_search_header, p, false);
            return new HeaderVH(v);
        }
        int layout = type.equals(MediaItem.LIVE) && !searchMode ?
            R.layout.item_channel : R.layout.item_vod;
        // En modo búsqueda siempre usar item_vod para todo
        if (searchMode) layout = R.layout.item_vod;
        View v = LayoutInflater.from(p.getContext()).inflate(layout, p, false);
        return new ItemVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        Row row = rows.get(pos);
        if (row.isHeader) {
            ((HeaderVH) holder).tvHeader.setText(row.header);
            return;
        }
        MediaItem item = row.item;
        ItemVH h = (ItemVH) holder;

        if (h.tvName != null) h.tvName.setText(item.name);

        // Imagen
        boolean isLive = MediaItem.LIVE.equals(item.type) || (!searchMode && type.equals(MediaItem.LIVE));
        String imgUrl = isLive ? item.logo : item.thumb();
        if (h.ivImage != null) {
            if (imgUrl != null && !imgUrl.isEmpty()) {
                h.ivImage.setVisibility(View.VISIBLE);
                if (h.tvPh != null) h.tvPh.setVisibility(View.GONE);
                Glide.with(h.ivImage.getContext().getApplicationContext())
                    .load(imgUrl).centerCrop()
                    .placeholder(android.R.color.darker_gray)
                    .into(h.ivImage);
            } else {
                h.ivImage.setVisibility(View.GONE);
                if (h.tvPh != null) h.tvPh.setVisibility(View.VISIBLE);
            }
        }

        // Rating
        if (h.tvRating != null) {
            if (item.rating != null && !item.rating.isEmpty() && !item.rating.equals("0")) {
                h.tvRating.setText("★ " + item.rating);
                h.tvRating.setVisibility(View.VISIBLE);
            } else {
                h.tvRating.setVisibility(View.GONE);
            }
        }

        // Progreso VOD
        if (h.progressBar != null && !isLive) {
            int pct = prefs.progressPct(item.id);
            if (pct > 2 && pct < 95) {
                h.progressBar.setVisibility(View.VISIBLE);
                h.progressBar.post(() -> {
                    View parent = (View) h.progressBar.getParent();
                    if (parent != null) {
                        ViewGroup.LayoutParams lp = h.progressBar.getLayoutParams();
                        lp.width = (int)(parent.getWidth() * pct / 100f);
                        h.progressBar.setLayoutParams(lp);
                    }
                });
            } else {
                h.progressBar.setVisibility(View.GONE);
            }
        }

        // Badge temporadas para Series
        if (h.tvQuality != null) {
            if (MediaItem.SERIES.equals(item.type)) {
                // Mostrar indicador de serie
                h.tvQuality.setText("SERIE");
                h.tvQuality.setBackgroundColor(0xFF8B5CF6); // púrpura
                h.tvQuality.setVisibility(View.VISIBLE);
            } else if (item.quality() != null && !item.quality().isEmpty()) {
                h.tvQuality.setText(item.quality());
                h.tvQuality.setBackgroundColor(0xFF00D4FF);
                h.tvQuality.setVisibility(View.VISIBLE);
            } else {
                h.tvQuality.setVisibility(View.GONE);
            }
        }

        // Favorito
        if (h.ivFav != null) {
            boolean fav = prefs.isFav(item.favKey());
            h.ivFav.setImageResource(fav ?
                android.R.drawable.btn_star_big_on :
                android.R.drawable.btn_star_big_off);
            h.ivFav.setOnClickListener(v -> {
                boolean newFav = prefs.toggleFav(item.favKey());
                h.ivFav.setImageResource(newFav ?
                    android.R.drawable.btn_star_big_on :
                    android.R.drawable.btn_star_big_off);
                if (listener != null) listener.onFav(item, newFav);
            });
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(rows.get(h.getAdapterPosition()).item);
        });
    }

    @Override public int getItemCount() { return rows.size(); }

    // ViewHolder para headers de búsqueda
    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderVH(View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tv_header);
        }
    }

    // ViewHolder para items
    static class ItemVH extends RecyclerView.ViewHolder {
        ImageView ivImage, ivFav;
        TextView tvName, tvQuality, tvRating, tvPh;
        View progressBar;

        ItemVH(View v) {
            super(v);
            ivImage     = v.findViewById(R.id.iv_cover) != null ?
                          v.findViewById(R.id.iv_cover) :
                          v.findViewById(R.id.iv_logo);
            ivFav       = v.findViewById(R.id.iv_fav);
            tvName      = v.findViewById(R.id.tv_name);
            tvQuality   = v.findViewById(R.id.tv_quality);
            tvRating    = v.findViewById(R.id.tv_rating);
            tvPh        = v.findViewById(R.id.tv_cover_ph) != null ?
                          v.findViewById(R.id.tv_cover_ph) :
                          v.findViewById(R.id.tv_logo_ph);
            progressBar = v.findViewById(R.id.progress_bar);
        }
    }
}
