package com.jox3.tv.ui.vod;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;
import com.jox3.tv.model.Category;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class VodDetailActivity extends AppCompatActivity {

    private MediaItem item;
    private AppPrefs prefs;
    private AppState state;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_detail);
        prefs = new AppPrefs(this);
        state = AppState.get();
        item = (MediaItem) getIntent().getSerializableExtra("item");
        if (item == null) { finish(); return; }
        initViews();
        loadDetails();
    }

    private void initViews() {
        ((TextView) findViewById(R.id.tv_title)).setText(item.name);
        ImageView ivCover = findViewById(R.id.iv_cover);
        if (item.thumb() != null && !item.thumb().isEmpty())
            Glide.with(this).load(item.thumb()).centerCrop().into(ivCover);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_play).setOnClickListener(v -> playVod());
    }

    private void loadDetails() {
        updateUI(item);
        exec.execute(() -> {
            try {
                // 1. Obtener info completa con género
                MediaItem info = state.api.getVodInfo(item.id);
                if (info.plot     != null && !info.plot.isEmpty())     item.plot     = info.plot;
                if (info.genre    != null && !info.genre.isEmpty())    item.genre    = info.genre;
                if (info.cast     != null && !info.cast.isEmpty())     item.cast     = info.cast;
                if (info.year     != null && !info.year.isEmpty())     item.year     = info.year;
                if (info.duration != null && !info.duration.isEmpty()) item.duration = info.duration;
                if (info.rating   != null && !info.rating.isEmpty())   item.rating   = info.rating;
                if (info.cover    != null && !info.cover.isEmpty())    item.cover    = info.cover;

                // 2. Cargar categorías VOD si no existen
                if (state.vodCats.isEmpty())
                    state.vodCats.addAll(state.api.getVodCats());

                // 3. Cargar categorías hasta tener suficientes items para recomendar
                //    Máximo 5 categorías para no tardar demasiado
                int loaded = 0;
                for (Category cat : state.vodCats) {
                    if (loaded >= 5) break;
                    if (!cat.loaded) {
                        try {
                            cat.items = state.api.getVodStreams(cat.id);
                            cat.loaded = true;
                        } catch (Exception ignored) {}
                    }
                    loaded++;
                }

                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    updateUI(item);
                    loadRecommendations();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    updateUI(item);
                    loadRecommendations();
                });
            }
        });
    }

    private void updateUI(MediaItem m) {
        if (isDestroyed() || isFinishing()) return;
        TextView tvPlot     = findViewById(R.id.tv_plot);
        TextView tvGenre    = findViewById(R.id.tv_genre);
        TextView tvCast     = findViewById(R.id.tv_cast);
        TextView tvYear     = findViewById(R.id.tv_year);
        TextView tvDuration = findViewById(R.id.tv_duration);
        TextView tvRating   = findViewById(R.id.tv_rating);
        TextView tvProgress = findViewById(R.id.tv_progress);
        ImageView ivCover   = findViewById(R.id.iv_cover);

        tvPlot.setText(m.plot != null && !m.plot.isEmpty() ? m.plot : "Sin sinopsis disponible");
        tvGenre.setText(m.genre != null && !m.genre.isEmpty() ? m.genre : "");
        tvCast.setText(m.cast != null && !m.cast.isEmpty() ? "Reparto: " + m.cast : "");
        tvYear.setText(m.year != null ? m.year : "");
        tvDuration.setText(m.duration != null ? m.duration : "");
        tvRating.setText(m.rating != null && !m.rating.isEmpty() ? "★ " + m.rating : "");
        tvGenre.setVisibility(m.genre != null && !m.genre.isEmpty() ? View.VISIBLE : View.GONE);
        tvCast.setVisibility(m.cast != null && !m.cast.isEmpty() ? View.VISIBLE : View.GONE);
        tvRating.setVisibility(m.rating != null && !m.rating.isEmpty() ? View.VISIBLE : View.GONE);
        if (m.cover != null && !m.cover.isEmpty() && !isDestroyed())
            Glide.with(this).load(m.cover).centerCrop().into(ivCover);

        int pct = prefs.progressPct(m.id);
        if (pct > 0 && pct < 95) {
            tvProgress.setText("Continuar desde " + pct + "%");
            tvProgress.setVisibility(View.VISIBLE);
        } else {
            tvProgress.setVisibility(View.GONE);
        }
    }

    private void loadRecommendations() {
        if (isDestroyed() || isFinishing()) return;

        // Juntar todos los items disponibles
        List<MediaItem> all = new ArrayList<>();
        for (Category c : state.vodCats)
            if (c.loaded) all.addAll(c.items);

        if (all.isEmpty()) return;

        List<MediaItem> recs = new ArrayList<>();
        String recTitle = "TE PUEDE INTERESAR";

        // Prioridad 1: mismo género exacto
        if (item.genre != null && !item.genre.isEmpty()) {
            // Obtener géneros de la película actual (puede ser "Terror, Acción")
            String[] genreArr = item.genre.toLowerCase().split(",");

            // Buscar películas que compartan al menos un género
            recs = all.stream()
                .filter(m -> !m.id.equals(item.id))
                .filter(m -> m.genre != null && !m.genre.isEmpty())
                .filter(m -> {
                    String mg = m.genre.toLowerCase();
                    for (String g : genreArr) {
                        if (mg.contains(g.trim())) return true;
                    }
                    return false;
                })
                .sorted((a, b) -> {
                    // Priorizar las que comparten más géneros
                    int aScore = 0, bScore = 0;
                    for (String g : genreArr) {
                        String gt = g.trim();
                        if (a.genre != null && a.genre.toLowerCase().contains(gt)) aScore++;
                        if (b.genre != null && b.genre.toLowerCase().contains(gt)) bScore++;
                    }
                    return Integer.compare(bScore, aScore);
                })
                .limit(12)
                .collect(Collectors.toList());

            // título siempre "TE PUEDE INTERESAR"
        }

        // Prioridad 2: misma categoría
        if (recs.isEmpty()) {
            recs = all.stream()
                .filter(m -> !m.id.equals(item.id))
                .filter(m -> item.group != null && item.group.equals(m.group))
                .limit(12).collect(Collectors.toList());

        }

        // Prioridad 3: cualquier película
        if (recs.isEmpty()) {
            recs = all.stream()
                .filter(m -> !m.id.equals(item.id))
                .limit(12).collect(Collectors.toList());
        }

        if (recs.isEmpty()) return;

        TextView tvRecTitle = findViewById(R.id.tv_rec_title);
        RecyclerView rvRecs = findViewById(R.id.rv_recommendations);
        if (tvRecTitle == null || rvRecs == null) return;

        tvRecTitle.setText(recTitle);
        tvRecTitle.setVisibility(View.VISIBLE);
        rvRecs.setVisibility(View.VISIBLE);
        rvRecs.setLayoutManager(new GridLayoutManager(this, 3));
        rvRecs.setAdapter(new RecommendationAdapter(recs, recItem ->
            startActivity(new Intent(this, VodDetailActivity.class).putExtra("item", recItem))));
    }

    private void playVod() {
        PlayerActivity.requestClose = true;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    static class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.VH> {
        interface OnClick { void onClick(MediaItem item); }
        private final List<MediaItem> items;
        private final OnClick listener;
        RecommendationAdapter(List<MediaItem> items, OnClick l) { this.items = items; listener = l; }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_recommendation, p, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            MediaItem m = items.get(pos);
            h.tvName.setText(m.name);
            if (m.thumb() != null && !m.thumb().isEmpty())
                Glide.with(h.itemView.getContext().getApplicationContext())
                    .load(m.thumb()).centerCrop().into(h.ivCover);
            h.itemView.setOnClickListener(v -> listener.onClick(m));
        }
        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivCover; TextView tvName;
            VH(View v) { super(v); ivCover=v.findViewById(R.id.iv_cover); tvName=v.findViewById(R.id.tv_name); }
        }
    }
}
