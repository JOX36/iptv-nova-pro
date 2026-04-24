package com.jox3.tv.adapter;

import android.graphics.Color;
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

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.VH> {

    public interface Listener {
        void onClick(MediaItem item);
        void onFav(MediaItem item, boolean isFav);
    }

    private List<MediaItem> items = new ArrayList<>();
    private final String type;
    private AppPrefs prefs;
    private Listener listener;

    public MediaAdapter(String type, AppPrefs prefs, Listener l) {
        this.type = type; this.prefs = prefs; this.listener = l;
    }

    public void setItems(List<MediaItem> list) {
        items = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    public List<MediaItem> getItems() { return items; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        int layout = type.equals(MediaItem.LIVE) ? R.layout.item_channel : R.layout.item_vod;
        View v = LayoutInflater.from(p.getContext()).inflate(layout, p, false);
        return new VH(v, type);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        MediaItem item = items.get(pos);
        boolean isLive = type.equals(MediaItem.LIVE);

        // Nombre
        h.tvName.setText(item.name);

        // Imagen
        String imgUrl = isLive ? item.logo : item.thumb();
        if (imgUrl != null && !imgUrl.isEmpty()) {
            Glide.with(h.itemView)
                .load(imgUrl)
                .centerCrop()
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .into(h.ivImage);
            if (h.tvPh != null) h.tvPh.setVisibility(View.GONE);
            h.ivImage.setVisibility(View.VISIBLE);
        } else {
            h.ivImage.setVisibility(View.GONE);
            if (h.tvPh != null) {
                h.tvPh.setVisibility(View.VISIBLE);
                h.tvPh.setText(type.equals(MediaItem.SERIES) ? "🎭" : isLive ? "📺" : "🎬");
            }
        }

        // Badge calidad
        String q = item.quality();
        if (h.tvQuality != null && !q.isEmpty()) {
            h.tvQuality.setText(q);
            h.tvQuality.setVisibility(View.VISIBLE);
            int color = Color.parseColor(
                q.equals("4K")  ? "#FFC107" :
                q.equals("FHD") ? "#00FF88" :
                q.equals("HD")  ? "#00D4FF" : "#888888");
            h.tvQuality.setBackgroundColor(color);
            h.tvQuality.setTextColor(Color.parseColor("#0a0e14"));
        } else if (h.tvQuality != null) {
            h.tvQuality.setVisibility(View.GONE);
        }

        // Rating VOD
        if (h.tvRating != null) {
            if (item.rating != null && !item.rating.isEmpty() && !item.rating.equals("0")) {
                h.tvRating.setText("⭐ " + item.rating);
                h.tvRating.setVisibility(View.VISIBLE);
            } else {
                h.tvRating.setVisibility(View.GONE);
            }
        }

        // EPG Live
        if (h.tvEpg != null) {
            h.tvEpg.setVisibility(View.GONE); // se actualiza externamente
        }

        // Progreso VOD
        if (h.progressBar != null && !isLive) {
            int pct = prefs.progressPct(item.id);
            if (pct > 0 && pct < 95) {
                h.progressBar.setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams lp = h.progressBar.getLayoutParams();
                // ancho relativo al parent — se hace en post
                h.progressBar.post(() -> {
                    int w = ((View)h.progressBar.getParent()).getWidth();
                    lp.width = (int)(w * pct / 100f);
                    h.progressBar.setLayoutParams(lp);
                });
            } else {
                h.progressBar.setVisibility(View.GONE);
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

        // Click
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(items.get(h.getAdapterPosition()));
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivImage, ivFav;
        TextView tvName, tvQuality, tvRating, tvEpg, tvPh;
        View progressBar;

        VH(View v, String type) {
            super(v);
            ivImage     = v.findViewById(R.id.iv_logo) != null ?
                          v.findViewById(R.id.iv_logo) : v.findViewById(R.id.iv_cover);
            ivFav       = v.findViewById(R.id.iv_fav);
            tvName      = v.findViewById(R.id.tv_name);
            tvQuality   = v.findViewById(R.id.tv_quality);
            tvRating    = v.findViewById(R.id.tv_rating);
            tvEpg       = v.findViewById(R.id.tv_epg);
            tvPh        = v.findViewById(R.id.tv_logo_ph) != null ?
                          v.findViewById(R.id.tv_logo_ph) : v.findViewById(R.id.tv_cover_ph);
            progressBar = v.findViewById(R.id.progress_bar);
        }
    }
}
