package com.jox3.tv.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;
import com.jox3.tv.model.EpgProgram;
import com.jox3.tv.util.AppState;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.util.AppPrefs;

import java.util.ArrayList;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onClick(MediaItem item);
        void onFav(MediaItem item, boolean isFav);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM   = 1;

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
    private final ExecutorService epgExec = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MediaAdapter(String type, AppPrefs prefs, Listener l) {
        this.type = type; this.prefs = prefs; this.listener = l;
    }

    public void setItems(List<MediaItem> list) {
        searchMode = false;
        rawItems = new ArrayList<>(list);
        rows.clear();
        for (MediaItem m : list) rows.add(new Row(m));
        notifyDataSetChanged();
    }

    public void setSearchResults(List<MediaItem> live, List<MediaItem> vod, List<MediaItem> series) {
        searchMode = true;
        rows.clear();
        if (!live.isEmpty()) {
            rows.add(new Row("\uD83D\uDCFA  EN VIVO  —  " + live.size() + " resultado" + (live.size()>1?"s":"")));
            for (MediaItem m : live) rows.add(new Row(m));
        }
        if (!vod.isEmpty()) {
            rows.add(new Row("\uD83C\uDFAC  PELÍCULAS  —  " + vod.size() + " resultado" + (vod.size()>1?"s":"")));
            for (MediaItem m : vod) rows.add(new Row(m));
        }
        if (!series.isEmpty()) {
            rows.add(new Row("\uD83C\uDFAD  SERIES  —  " + series.size() + " resultado" + (series.size()>1?"s":"")));
            for (MediaItem m : series) rows.add(new Row(m));
        }
        notifyDataSetChanged();
    }

    /** Configurar SpanSizeLookup para que headers ocupen todo el ancho */
    public void attachToGrid(GridLayoutManager mgr, int spanCount) {
        mgr.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int pos) {
                return (pos < rows.size() && rows.get(pos).isHeader) ? spanCount : 1;
            }
        });
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
        boolean isLive = MediaItem.LIVE.equals(type) && !searchMode;
        int layout = isLive ? R.layout.item_channel : R.layout.item_vod;
        return new ItemVH(LayoutInflater.from(p.getContext()).inflate(layout, p, false));
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

        boolean isLive = MediaItem.LIVE.equals(item.type) ||
            (MediaItem.LIVE.equals(type) && !searchMode);
        String imgUrl = isLive ? item.logo : item.thumb();

        // EPG se carga bajo demanda — no aquí para no saturar la red

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

        if (h.tvRating != null) {
            if (item.rating != null && !item.rating.isEmpty() && !item.rating.equals("0")) {
                h.tvRating.setText("\u2605 " + item.rating);
                h.tvRating.setVisibility(View.VISIBLE);
            } else h.tvRating.setVisibility(View.GONE);
        }

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
            } else h.progressBar.setVisibility(View.GONE);
        }

        // Badge temporadas para Series — muestra "▼ N temp"
        if (h.tvSeasonsBadge != null) {
            if (MediaItem.SERIES.equals(item.type) && item.seasons > 1) {
                h.tvSeasonsBadge.setText("\u25BC " + item.seasons + " temp");
                h.tvSeasonsBadge.setVisibility(View.VISIBLE);
            } else if (MediaItem.SERIES.equals(item.type)) {
                h.tvSeasonsBadge.setText("SERIE");
                h.tvSeasonsBadge.setVisibility(View.VISIBLE);
            } else {
                h.tvSeasonsBadge.setVisibility(View.GONE);
            }
        }

        if (h.tvQuality != null) {
            String q = item.quality();
            if (q != null && !q.isEmpty() && !MediaItem.SERIES.equals(item.type)) {
                h.tvQuality.setText(q);
                h.tvQuality.setVisibility(View.VISIBLE);
            } else h.tvQuality.setVisibility(View.GONE);
        }

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
            int p = h.getAdapterPosition();
            if (p >= 0 && p < rows.size() && !rows.get(p).isHeader && listener != null)
                listener.onClick(rows.get(p).item);
        });
    }

    @Override public int getItemCount() { return rows.size(); }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderVH(View v) { super(v); tvHeader = v.findViewById(R.id.tv_header); }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        ImageView ivImage, ivFav;
        TextView tvName, tvQuality, tvRating, tvPh, tvSeasonsBadge;
        View progressBar;

        ItemVH(View v) {
            super(v);
            ivImage       = v.findViewById(R.id.iv_cover) != null ?
                            v.findViewById(R.id.iv_cover) : v.findViewById(R.id.iv_logo);
            ivFav         = v.findViewById(R.id.iv_fav);
            tvName        = v.findViewById(R.id.tv_name);
            tvQuality     = v.findViewById(R.id.tv_quality);
            tvRating      = v.findViewById(R.id.tv_rating);
            tvSeasonsBadge= v.findViewById(R.id.tv_seasons_badge);
            tvPh          = v.findViewById(R.id.tv_cover_ph) != null ?
                            v.findViewById(R.id.tv_cover_ph) : v.findViewById(R.id.tv_logo_ph);
            progressBar   = v.findViewById(R.id.progress_bar);
        }
    }

    private void loadEpgForChannel(com.jox3.tv.model.MediaItem item, ItemVH h) {
        android.widget.TextView tvEpgTitle = h.itemView.findViewById(R.id.tv_epg_title);
        android.widget.TextView tvEpgNext  = h.itemView.findViewById(R.id.tv_epg_next);
        View layoutEpgProgress = h.itemView.findViewById(R.id.layout_epg_progress);
        View epgProgressBar    = h.itemView.findViewById(R.id.epg_progress_bar);

        if (tvEpgTitle == null) return;

        epgExec.execute(() -> {
            try {
                List<EpgProgram> programs = AppState.get().api.getShortEpg(item.id);
                mainHandler.post(() -> {
                    if (programs.isEmpty()) return;
                    EpgProgram current = null;
                    EpgProgram next    = null;
                    for (int i = 0; i < programs.size(); i++) {
                        if (programs.get(i).isNow()) {
                            current = programs.get(i);
                            if (i + 1 < programs.size()) next = programs.get(i + 1);
                            break;
                        }
                    }
                    if (current == null && !programs.isEmpty()) current = programs.get(0);

                    if (current != null) {
                        tvEpgTitle.setText(current.title);
                        tvEpgTitle.setVisibility(View.VISIBLE);

                        if (layoutEpgProgress != null && epgProgressBar != null) {
                            layoutEpgProgress.setVisibility(View.VISIBLE);
                            int pct = current.progressPct();
                            epgProgressBar.post(() -> {
                                ViewGroup.LayoutParams lp = epgProgressBar.getLayoutParams();
                                lp.width = (int)(layoutEpgProgress.getWidth() * pct / 100f);
                                epgProgressBar.setLayoutParams(lp);
                            });
                        }
                    }
                    if (next != null && tvEpgNext != null) {
                        tvEpgNext.setText("▶ " + next.timeRange() + " " + next.title);
                        tvEpgNext.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception ignored) {}
        });
    }
}
