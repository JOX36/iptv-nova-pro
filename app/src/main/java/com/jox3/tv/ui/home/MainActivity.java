package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.jox3.tv.R;
import com.jox3.tv.adapter.CategoryAdapter;
import com.jox3.tv.adapter.MediaAdapter;
import com.jox3.tv.model.Category;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.ui.setup.LoginActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    // Estado
    private String currentType = MediaItem.LIVE;
    private String currentSideTab = "cats";
    private Category selectedCat;

    // UI
    private CategoryAdapter catAdapter;
    private MediaAdapter mediaAdapter;
    private RecyclerView rvCats, rvContent;
    private LinearProgressIndicator progressTop;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearch;
    private TextView tvAccount;

    // Datos
    private AppPrefs prefs;
    private AppState state;
    private ExecutorService exec = Executors.newFixedThreadPool(3);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new AppPrefs(this);
        state = AppState.get();

        if (state.account == null) {
            goLogin(); return;
        }

        initViews();
        loadCats(currentType);
    }

    private void initViews() {
        progressTop  = findViewById(R.id.progress_top);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        etSearch     = findViewById(R.id.et_search);
        tvAccount    = findViewById(R.id.tv_account);
        rvCats       = findViewById(R.id.rv_categories);
        rvContent    = findViewById(R.id.rv_content);

        tvAccount.setText("👤 " + state.account.displayHost());
        tvAccount.setOnClickListener(v -> goLogin());

        // Sidebar tabs
        setupSideTabs();

        // Category list
        catAdapter = new CategoryAdapter(cat -> loadItems(cat));
        rvCats.setLayoutManager(new LinearLayoutManager(this));
        rvCats.setAdapter(catAdapter);

        // Content grid
        mediaAdapter = new MediaAdapter(currentType, prefs, new MediaAdapter.Listener() {
            @Override public void onClick(MediaItem item) { openItem(item); }
            @Override public void onFav(MediaItem item, boolean isFav) {
                Toast.makeText(MainActivity.this,
                    isFav ? "⭐ Agregado a favoritos" : "Eliminado de favoritos",
                    Toast.LENGTH_SHORT).show();
            }
        });
        rvContent.setAdapter(mediaAdapter);
        updateGrid();

        // Bottom nav
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_live)   switchType(MediaItem.LIVE);
            else if (id == R.id.nav_vod)    switchType(MediaItem.VOD);
            else if (id == R.id.nav_series) switchType(MediaItem.SERIES);
            else if (id == R.id.nav_search) etSearch.requestFocus();
            return true;
        });

        // Búsqueda
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { doSearch(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        // Pull to refresh
        swipeRefresh.setColorSchemeColors(getColor(R.color.accent));
        swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.bg2));
        swipeRefresh.setOnRefreshListener(() -> {
            if (selectedCat != null) {
                selectedCat.loaded = false;
                loadItems(selectedCat);
            }
            swipeRefresh.setRefreshing(false);
        });
    }

    private void setupSideTabs() {
        TextView tabCats = findViewById(R.id.stab_cats);
        TextView tabFavs = findViewById(R.id.stab_favs);
        TextView tabHist = findViewById(R.id.stab_hist);
        tabCats.setOnClickListener(v -> { currentSideTab = "cats"; showCats();   highlightTab(tabCats, tabFavs, tabHist); });
        tabFavs.setOnClickListener(v -> { currentSideTab = "favs"; showFavs();   highlightTab(tabFavs, tabCats, tabHist); });
        tabHist.setOnClickListener(v -> { currentSideTab = "hist"; showHistory(); highlightTab(tabHist, tabCats, tabFavs); });
    }

    private void highlightTab(TextView active, TextView... others) {
        active.setTextColor(getColor(R.color.accent));
        for (TextView t : others) t.setTextColor(getColor(R.color.muted));
    }

    private void switchType(String type) {
        currentType = type;
        selectedCat = null;
        mediaAdapter = new MediaAdapter(type, prefs, new MediaAdapter.Listener() {
            @Override public void onClick(MediaItem item) { openItem(item); }
            @Override public void onFav(MediaItem item, boolean isFav) {}
        });
        rvContent.setAdapter(mediaAdapter);
        updateGrid();
        loadCats(type);
    }

    private void updateGrid() {
        int cols = currentType.equals(MediaItem.LIVE) ? 2 : 2;
        rvContent.setLayoutManager(new GridLayoutManager(this, cols));
    }

    // ── Cargar categorías ──
    private void loadCats(String type) {
        List<Category> cached = state.cats(type);
        if (!cached.isEmpty()) {
            catAdapter.setItems(cached);
            if (!cached.isEmpty()) loadItems(cached.get(0));
            return;
        }
        showProgress(true);
        exec.execute(() -> {
            try {
                List<Category> cats;
                if      (type.equals(MediaItem.LIVE))   cats = state.api.getLiveCats();
                else if (type.equals(MediaItem.VOD))    cats = state.api.getVodCats();
                else                                    cats = state.api.getSeriesCats();
                state.cats(type).addAll(cats);
                mainHandler.post(() -> {
                    showProgress(false);
                    catAdapter.setItems(cats);
                    if (!cats.isEmpty()) loadItems(cats.get(0));
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showProgress(false);
                    toast("Error: " + e.getMessage());
                });
            }
        });
    }

    // ── Cargar items de categoría ──
    private void loadItems(Category cat) {
        selectedCat = cat;
        // Seleccionar en sidebar
        List<Category> cats = state.cats(currentType);
        catAdapter.setSelected(cats.indexOf(cat));

        if (cat.loaded) {
            mediaAdapter.setItems(cat.items);
            return;
        }
        showProgress(true);
        exec.execute(() -> {
            try {
                List<MediaItem> items;
                if      (currentType.equals(MediaItem.LIVE))   items = state.api.getLiveStreams(cat.id);
                else if (currentType.equals(MediaItem.VOD))    items = state.api.getVodStreams(cat.id);
                else                                            items = state.api.getSeries(cat.id);
                cat.items = items;
                cat.loaded = true;
                mainHandler.post(() -> {
                    showProgress(false);
                    catAdapter.setSelected(state.cats(currentType).indexOf(cat));
                    mediaAdapter.setItems(items);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showProgress(false);
                    toast("Error: " + e.getMessage());
                });
            }
        });
    }

    // ── Búsqueda ──
    private void doSearch(String q) {
        if (q.trim().isEmpty()) {
            if (selectedCat != null && selectedCat.loaded)
                mediaAdapter.setItems(selectedCat.items);
            return;
        }
        String ql = q.toLowerCase();
        // Buscar en todos los items cargados
        List<MediaItem> all = new ArrayList<>();
        for (Category c : state.cats(currentType))
            if (c.loaded) all.addAll(c.items);
        List<MediaItem> results = all.stream()
            .filter(i -> i.name != null && i.name.toLowerCase().contains(ql))
            .collect(Collectors.toList());
        mediaAdapter.setItems(results);
    }

    // ── Sidebar: Favs / Historial ──
    private void showCats() {
        catAdapter.setItems(state.cats(currentType));
    }

    private void showFavs() {
        List<MediaItem> all = new ArrayList<>();
        for (Category c : state.cats(currentType))
            if (c.loaded) all.addAll(c.items);
        List<MediaItem> favs = all.stream()
            .filter(i -> prefs.isFav(i.favKey()))
            .collect(Collectors.toList());
        // Mostrar favs en sidebar como lista simple
        mediaAdapter.setItems(favs);
    }

    private void showHistory() {
        List<MediaItem> hist = prefs.history().stream()
            .filter(i -> i.type != null && i.type.equals(currentType))
            .collect(Collectors.toList());
        mediaAdapter.setItems(hist);
    }

    // ── Abrir item ──
    private void openItem(MediaItem item) {
        prefs.addHistory(item);
        state.current = item;

        if (item.type.equals(MediaItem.SERIES)) {
            // Abrir detalle de series
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("item", item);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }

        if (item.type.equals(MediaItem.LIVE)) {
            // Pasar lista de canales para navegación
            List<MediaItem> channelList = selectedCat != null ? selectedCat.items : new ArrayList<>();
            state.channelList = channelList;
            state.channelIdx  = channelList.indexOf(item);
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showProgress(boolean show) {
        progressTop.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) progressTop.setIndeterminate(true);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void goLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
