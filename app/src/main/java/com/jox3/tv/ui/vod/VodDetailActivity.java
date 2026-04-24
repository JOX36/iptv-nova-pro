package com.jox3.tv.ui.vod;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VodDetailActivity extends AppCompatActivity {

    private MediaItem item;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_detail);

        item = (MediaItem) getIntent().getSerializableExtra("item");
        if (item == null) { finish(); return; }

        initViews();
        loadDetails();
    }

    private void initViews() {
        TextView tvTitle  = findViewById(R.id.tv_title);
        ImageView ivCover = findViewById(R.id.iv_cover);

        tvTitle.setText(item.name);
        if (item.thumb() != null && !item.thumb().isEmpty())
            Glide.with(this).load(item.thumb()).into(ivCover);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_play).setOnClickListener(v -> playVod());
    }

    private void loadDetails() {
        // Mostrar lo que ya tenemos
        updateUI(item);

        // Cargar info adicional del servidor
        exec.execute(() -> {
            try {
                MediaItem info = AppState.get().api.getVodInfo(item.id);
                // Combinar con item existente
                if (info.plot  != null && !info.plot.isEmpty())  item.plot  = info.plot;
                if (info.genre != null && !info.genre.isEmpty()) item.genre = info.genre;
                if (info.cast  != null && !info.cast.isEmpty())  item.cast  = info.cast;
                if (info.year  != null && !info.year.isEmpty())  item.year  = info.year;
                if (info.duration != null && !info.duration.isEmpty()) item.duration = info.duration;
                if (info.rating   != null && !info.rating.isEmpty())   item.rating   = info.rating;
                if (info.cover    != null && !info.cover.isEmpty())    item.cover    = info.cover;
                mainHandler.post(() -> updateUI(item));
            } catch (Exception e) {
                mainHandler.post(() ->
                    Toast.makeText(this, "No se pudo cargar info", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateUI(MediaItem m) {
        TextView tvPlot     = findViewById(R.id.tv_plot);
        TextView tvGenre    = findViewById(R.id.tv_genre);
        TextView tvCast     = findViewById(R.id.tv_cast);
        TextView tvYear     = findViewById(R.id.tv_year);
        TextView tvDuration = findViewById(R.id.tv_duration);
        TextView tvRating   = findViewById(R.id.tv_rating);
        ImageView ivCover   = findViewById(R.id.iv_cover);

        tvPlot.setText(m.plot != null && !m.plot.isEmpty() ? m.plot : "Sin sinopsis disponible");
        tvGenre.setText(m.genre != null ? m.genre : "");
        tvCast.setText(m.cast != null && !m.cast.isEmpty() ? "Reparto: " + m.cast : "");
        tvYear.setText(m.year != null ? m.year : "");
        tvDuration.setText(m.duration != null ? m.duration : "");
        tvRating.setText(m.rating != null && !m.rating.isEmpty() ? "★ " + m.rating : "");

        tvGenre.setVisibility(m.genre != null && !m.genre.isEmpty() ? View.VISIBLE : View.GONE);
        tvCast.setVisibility(m.cast != null && !m.cast.isEmpty() ? View.VISIBLE : View.GONE);
        tvRating.setVisibility(m.rating != null && !m.rating.isEmpty() ? View.VISIBLE : View.GONE);

        if (m.cover != null && !m.cover.isEmpty())
            Glide.with(this).load(m.cover).into(ivCover);

        // Progreso
        AppPrefs prefs = new AppPrefs(this);
        int pct = prefs.progressPct(m.id);
        TextView tvProgress = findViewById(R.id.tv_progress);
        if (pct > 0 && pct < 95) {
            tvProgress.setText("Continuar desde " + pct + "%");
            tvProgress.setVisibility(View.VISIBLE);
        } else {
            tvProgress.setVisibility(View.GONE);
        }
    }

    private void playVod() {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
