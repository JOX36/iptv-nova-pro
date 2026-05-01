package com.jox3.tv.ui.series;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import com.google.gson.JsonElement;
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
    private TextView tvSeasonSelected;
    private android.view.View btnSeasonSelector;
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
        tvTitle        = findViewById(R.id.tv_title);
        tvPlot         = findViewById(R.id.tv_plot);
        tvRating       = findViewById(R.id.tv_rating);
        tvYear         = findViewById(R.id.tv_year);
        ivCover        = findViewById(R.id.iv_cover);
        spinnerSeasons    = findViewById(R.id.spinner_seasons);
        tvSeasonSelected  = findViewById(R.id.tv_season_selected);
        btnSeasonSelector = findViewById(R.id.btn_season_selector);
        rvEpisodes     = findViewById(R.id.rv_episodes);

        tvTitle.setText(item.name);

        if (item.cover != null && !item.cover.isEmpty())
            Glide.with(this).load(item.cover).centerCrop().into(ivCover);
        else if (item.thumb() != null && !item.thumb().isEmpty())
            Glide.with(this).load(item.thumb()).centerCrop().into(ivCover);

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
                    if (isDestroyed() || isFinishing()) return;
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void populateInfo() {
        if (seriesData == null) return;

        // Info básica
        if (seriesData.has("info") && seriesData.get("info").isJsonObject()) {
            JsonObject info = seriesData.getAsJsonObject("info");
            String plot   = getStr(info, "plot", "description");
            String rating = getStr(info, "rating", "rating_5based");
            String year   = getStr(info, "releaseDate", "year", "release_date");
            String cover  = getStr(info, "cover", "backdrop_path", "movie_image");

            if (tvPlot   != null) tvPlot.setText(plot.isEmpty() ? "Sin sinopsis" : plot);
            if (tvRating != null) tvRating.setText(rating.isEmpty() ? "" : "★ " + rating);
            if (tvYear   != null) tvYear.setText(year);
            if (!cover.isEmpty() && ivCover != null && !isDestroyed())
                Glide.with(this).load(cover).centerCrop().into(ivCover);
        }

        // Episodios
        if (!seriesData.has("episodes")) { showNoEpisodes(); return; }
        JsonObject seasonsMap = buildSeasonsMap(seriesData.get("episodes"));
        if (seasonsMap == null || seasonsMap.size() == 0) { showNoEpisodes(); return; }

        List<String> seasons = new ArrayList<>(seasonsMap.keySet());
        seasons.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
            catch (Exception e) { return a.compareTo(b); }
        });

        int total = 0;
        for (String s : seasons) total += toArray(seasonsMap.get(s)).size();


        final JsonObject finalMap = seasonsMap;
        final List<String> finalSeasons = seasons;

        // Mostrar temporada 1 por defecto
        if (tvSeasonSelected != null)
            tvSeasonSelected.setText("Temporada " + seasons.get(0));

        // Botón ▼ abre diálogo con lista de temporadas
        if (btnSeasonSelector != null) {
            btnSeasonSelector.setOnClickListener(v -> {
                String[] options = finalSeasons.stream()
                    .map(s -> "Temporada " + s)
                    .toArray(String[]::new);

                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Seleccionar temporada")
                    .setItems(options, (d, which) -> {
                        String sel = finalSeasons.get(which);
                        if (tvSeasonSelected != null)
                            tvSeasonSelected.setText("Temporada " + sel);
                        loadEpisodes(finalMap, sel);
                    })
                    .show();
            });
        }

        loadEpisodes(finalMap, seasons.get(0));
    }

    private JsonObject buildSeasonsMap(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        try {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.size() == 0) return null;
                JsonObject result = new JsonObject();
                for (String key : obj.keySet()) {
                    JsonArray arr = toArray(obj.get(key));
                    if (arr.size() > 0) result.add(key, arr);
                }
                return result.size() > 0 ? result : null;
            }
            if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                if (arr.size() == 0) return null;
                JsonObject result = new JsonObject();
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) continue;
                    JsonObject ep = arr.get(i).getAsJsonObject();
                    String season = getStr(ep, "season", "season_num", "s");
                    if (season.isEmpty() || season.equals("0")) season = "1";
                    if (!result.has(season)) result.add(season, new JsonArray());
                    result.getAsJsonArray(season).add(ep);
                }
                return result.size() > 0 ? result : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private JsonArray toArray(JsonElement el) {
        if (el == null || el.isJsonNull()) return new JsonArray();
        if (el.isJsonArray()) return el.getAsJsonArray();
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            JsonArray arr = new JsonArray();
            for (String k : obj.keySet()) {
                JsonElement v = obj.get(k);
                if (v.isJsonObject()) arr.add(v);
                else if (v.isJsonArray()) {
                    JsonArray inner = v.getAsJsonArray();
                    for (int i = 0; i < inner.size(); i++)
                        if (inner.get(i).isJsonObject()) arr.add(inner.get(i));
                }
            }
            return arr;
        }
        return new JsonArray();
    }

    private void loadEpisodes(JsonObject seasonsMap, String season) {
        JsonArray eps = seasonsMap.has(season) ?
            toArray(seasonsMap.get(season)) : new JsonArray();

        if (eps.size() == 0) {
            Toast.makeText(this, "Sin episodios en esta temporada", Toast.LENGTH_SHORT).show();
            return;
        }

        List<EpisodeItem> list = new ArrayList<>();
        for (int i = 0; i < eps.size(); i++) {
            if (!eps.get(i).isJsonObject()) continue;
            JsonObject ep = eps.get(i).getAsJsonObject();

            String epId = getStr(ep, "id", "stream_id", "episode_id");
            if (epId.isEmpty()) continue;

            String title = getStr(ep, "title", "name", "episode_name");
            if (title.isEmpty()) title = "Episodio " + (i + 1);

            String ext = getStr(ep, "container_extension", "ext");
            if (ext.isEmpty()) ext = "mp4";

            String epNum = getStr(ep, "episode_num", "num");
            String plot  = "";
            String thumb = "";

            if (ep.has("info") && !ep.get("info").isJsonNull() && ep.get("info").isJsonObject()) {
                JsonObject info = ep.getAsJsonObject("info");
                plot  = getStr(info, "plot", "overview", "description");
                thumb = getStr(info, "movie_image", "episode_image", "backdrop_path", "still_path");
            }

            if (thumb.isEmpty() && item.cover != null) thumb = item.cover;

            String url = AppState.get().account.host + "/series/" +
                AppState.get().account.user + "/" +
                AppState.get().account.pass + "/" + epId + "." + ext;

            String label = epNum.isEmpty() ?
                "Ep " + (i + 1) + " — " + title :
                "Ep " + epNum + " — " + title;

            list.add(new EpisodeItem(epId, label, url, plot, thumb));
        }

        if (list.isEmpty()) {
            Toast.makeText(this, "No se pudieron cargar los episodios", Toast.LENGTH_SHORT).show();
            return;
        }

        rvEpisodes.setAdapter(new EpisodeAdapter(list, ep -> {
            PlayerActivity.requestClose = true;
            MediaItem epItem = new MediaItem(
                ep.id, item.name + " — " + ep.title,
                item.cover, ep.url, "", MediaItem.VOD);
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("item", epItem);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }));
    }

    private void showNoEpisodes() {
        Toast.makeText(this, "Sin episodios disponibles", Toast.LENGTH_SHORT).show();
    }

    private String getStr(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) continue;
            try {
                String val = obj.get(key).getAsString().trim();
                if (!val.isEmpty() && !val.equals("null") && !val.equals("0")) return val;
            } catch (Exception ignored) {}
        }
        return "";
    }
}
