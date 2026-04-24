package com.jox3.tv.ui.series;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

public class SeriesActivity extends AppCompatActivity {

    private MediaItem item;
    private JsonObject seriesInfo;
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
            Glide.with(this).load(item.cover).into(ivCover);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rvEpisodes.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadSeriesInfo() {
        exec.execute(() -> {
            try {
                seriesInfo = AppState.get().api.getSeriesInfo(item.id);
                mainHandler.post(() -> populateInfo());
            } catch (Exception e) {
                mainHandler.post(() ->
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void populateInfo() {
        if (seriesInfo == null) return;

        // Info básica
        JsonObject info = seriesInfo.has("info") ?
            seriesInfo.getAsJsonObject("info") : null;
        if (info != null) {
            String plot   = info.has("plot")   ? info.get("plot").getAsString()   : "";
            String rating = info.has("rating") ? info.get("rating").getAsString() : "";
            String year   = info.has("releaseDate") ? info.get("releaseDate").getAsString() : "";
            String cover  = info.has("cover")  ? info.get("cover").getAsString()  : item.cover;

            tvPlot.setText(plot);
            tvRating.setText(rating.isEmpty() ? "" : "★ " + rating);
            tvYear.setText(year);
            if (cover != null && !cover.isEmpty())
                Glide.with(this).load(cover).into(ivCover);
        }

        // Temporadas
        if (!seriesInfo.has("episodes")) return;
        JsonObject episodes = seriesInfo.getAsJsonObject("episodes");
        List<String> seasons = new ArrayList<>(episodes.keySet());
        seasons.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
            catch (Exception e) { return a.compareTo(b); }
        });

        if (seasons.isEmpty()) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            seasons.stream().map(s -> "Temporada " + s)
                .collect(java.util.stream.Collectors.toList()));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSeasons.setAdapter(adapter);

        spinnerSeasons.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                loadEpisodes(episodes, seasons.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Cargar primera temporada
        loadEpisodes(episodes, seasons.get(0));
    }

    private void loadEpisodes(JsonObject episodes, String season) {
        JsonArray eps = episodes.getAsJsonArray(season);
        if (eps == null) return;

        List<EpisodeItem> list = new ArrayList<>();
        for (int i = 0; i < eps.size(); i++) {
            JsonObject ep = eps.get(i).getAsJsonObject();
            String epId    = ep.has("id")    ? ep.get("id").getAsString()    : "";
            String title   = ep.has("title") ? ep.get("title").getAsString() : "Episodio " + (i+1);
            String ext     = ep.has("container_extension") ?
                ep.get("container_extension").getAsString() : "mp4";
            String plot    = ep.has("info") && !ep.get("info").isJsonNull() ?
                ep.getAsJsonObject("info").has("plot") ?
                    ep.getAsJsonObject("info").get("plot").getAsString() : "" : "";

            String url = AppState.get().account.host + "/series/" +
                AppState.get().account.user + "/" +
                AppState.get().account.pass + "/" + epId + "." + ext;

            list.add(new EpisodeItem(epId, "Ep " + (i+1) + " - " + title, url, plot));
        }

        EpisodeAdapter adapter = new EpisodeAdapter(list, ep -> {
            MediaItem epItem = new MediaItem(ep.id, item.name + " - " + ep.title,
                item.cover, ep.url, "", MediaItem.VOD);
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("item", epItem);
            startActivity(intent);
        });
        rvEpisodes.setAdapter(adapter);
    }
}
