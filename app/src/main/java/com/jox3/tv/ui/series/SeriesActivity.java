package com.jox3.tv.ui.series;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SeriesActivity extends AppCompatActivity {

    private MediaItem item;
    private JsonObject seriesData;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Spinner spinnerSeasons;
    private RecyclerView rvEpisodes;
    private TextView tvTitle, tvPlot, tvRating, tvYear;
    private ImageView ivCover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series);

        item = (MediaItem) getIntent().getSerializableExtra("item");
        if (item == null) { finish(); return; }

        initViews();
        loadSeriesInfo();
    }

    private void initViews() {
        tvTitle      = findViewById(R.id.tv_title);
        tvPlot       = findViewById(R.id.tv_plot);
        tvRating     = findViewById(R.id.tv_rating);
        tvYear       = findViewById(R.id.tv_year);
        ivCover      = findViewById(R.id.iv_cover);
        spinnerSeasons = findViewById(R.id.spinner_seasons);
        rvEpisodes   = findViewById(R.id.rv_episodes);

        tvTitle.setText(item.name);

        if (item.cover != null && !item.cover.isEmpty())
            Glide.with(this).load(item.cover).centerCrop().into(ivCover);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        rvEpisodes.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadSeriesInfo() {
        exec.execute(() -> {
            try {
                seriesData = AppState.get().api.getSeriesInfo(item.id);
                mainHandler.post(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    populateInfo();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isDestroyed() && !isFinishing())
                        Toast.makeText(this, "Error cargando serie", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void populateInfo() {
        if (seriesData == null) return;

        // Info básica
        JsonObject info = seriesData.has("info") ?
            seriesData.getAsJsonObject("info") : null;
        if (info != null) {
            String plot   = info.has("plot")        ? info.get("plot").getAsString()        : "";
            String rating = info.has("rating")      ? info.get("rating").getAsString()      : "";
            String year   = info.has("releaseDate") ? info.get("releaseDate").getAsString() : "";
            String cover  = info.has("cover")       ? info.get("cover").getAsString()       :
                            info.has("backdrop_path") ? info.get("backdrop_path").getAsString() : item.cover;

            if (tvPlot   != null) tvPlot.setText(plot.isEmpty() ? "Sin sinopsis" : plot);
            if (tvRating != null) tvRating.setText(rating.isEmpty() ? "" : "★ " + rating);
            if (tvYear   != null) tvYear.setText(year);

            if (cover != null && !cover.isEmpty() && ivCover != null)
                Glide.with(this).load(cover).centerCrop().into(ivCover);
        }

        // Temporadas — manejar múltiples formatos del API
        if (!seriesData.has("episodes")) return;
        JsonObject episodes = new JsonObject();
        try {
            com.google.gson.JsonElement epEl = seriesData.get("episodes");
            if (epEl.isJsonObject()) {
                // Formato normal: {"1": [...], "2": [...]}
                episodes = epEl.getAsJsonObject();
            } else if (epEl.isJsonArray()) {
                // Formato alternativo: array de episodios sin temporadas
                // Agrupamos todo en temporada "1"
                com.google.gson.JsonArray arr = epEl.getAsJsonArray();
                if (arr.size() > 0) {
                    episodes.add("1", arr);
                }
            } else {
                return;
            }
        } catch (Exception e) { return; }
        if (episodes == null || episodes.size() == 0) return;

        List<String> seasons = new ArrayList<>(episodes.keySet());
        seasons.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
            catch (Exception e) { return a.compareTo(b); }
        });

        if (seasons.isEmpty()) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            seasons.stream().map(s -> "Temporada " + s).collect(Collectors.toList()));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSeasons.setAdapter(adapter);

        final JsonObject finalEpisodes = episodes;
        final List<String> finalSeasons = seasons;
        spinnerSeasons.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                loadEpisodes(finalEpisodes, finalSeasons.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Cargar primera temporada
        loadEpisodes(finalEpisodes, finalSeasons.get(0));
    }

    // Método auxiliar para obtener array de episodios de una temporada
    private com.google.gson.JsonArray getSeasonArray(JsonObject episodes, String season) {
        try {
            com.google.gson.JsonElement el = episodes.get(season);
            if (el == null) return new com.google.gson.JsonArray();
            if (el.isJsonArray()) return el.getAsJsonArray();
            if (el.isJsonObject()) {
                // A veces viene como objeto con keys numéricos
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (String key : obj.keySet()) arr.add(obj.get(key));
                return arr;
            }
        } catch (Exception ignored) {}
        return new com.google.gson.JsonArray();
    }

    private void loadEpisodes(JsonObject episodes, String season) {
        JsonArray eps = getSeasonArray(episodes, season);
        if (eps == null || eps.size() == 0) return;

        List<EpisodeItem> list = new ArrayList<>();
        for (int i = 0; i < eps.size(); i++) {
            JsonObject ep = eps.get(i).getAsJsonObject();
            String epId  = ep.has("id")    ? ep.get("id").getAsString()    : "";
            String title = ep.has("title") ? ep.get("title").getAsString() : "Episodio " + (i + 1);
            String ext   = ep.has("container_extension") ?
                ep.get("container_extension").getAsString() : "mp4";

            // Sinopsis y miniatura del episodio
            String plot = "";
            String thumb = "";
            if (ep.has("info") && !ep.get("info").isJsonNull()) {
                JsonObject epInfo = ep.getAsJsonObject("info");
                if (epInfo.has("plot"))          plot  = epInfo.get("plot").getAsString();
                if (epInfo.has("movie_image"))   thumb = epInfo.get("movie_image").getAsString();
                if (thumb.isEmpty() && epInfo.has("episode_image"))
                    thumb = epInfo.get("episode_image").getAsString();
                if (thumb.isEmpty() && epInfo.has("backdrop_path"))
                    thumb = epInfo.get("backdrop_path").getAsString();
            }
            // Fallback: usar cover de la serie
            if (thumb.isEmpty() && item.cover != null) thumb = item.cover;

            String url = AppState.get().account.host + "/series/" +
                AppState.get().account.user + "/" +
                AppState.get().account.pass + "/" + epId + "." + ext;

            list.add(new EpisodeItem(epId, "Ep " + (i + 1) + " - " + title, url, plot, thumb));
        }

        rvEpisodes.setAdapter(new EpisodeAdapter(list, ep -> {
            MediaItem epItem = new MediaItem(ep.id,
                item.name + " - " + ep.title,
                item.cover, ep.url, "", MediaItem.VOD);
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("item", epItem);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }));
    }
}
