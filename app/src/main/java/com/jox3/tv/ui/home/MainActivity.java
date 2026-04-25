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
import androidx.drawerlayout.widget.DrawerLayout;
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

    private String currentType = MediaItem.LIVE;
    private Category selectedCat;
    private boolean isSearchMode = false;

    private DrawerLayout drawerLayout;
    private CategoryAdapter catAdapter;
    private MediaAdapter mediaAdapter;
    private RecyclerView rvCats, rvContent;
    private LinearProgressIndicator progressTop;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearch;
    private TextView tvAccount;
    private TextView tvSearchResults;

    private AppPrefs prefs;
    private AppState state;
    private final ExecutorService exec = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new AppPrefs(this);
        state = AppState.get();

        if (state.account == null) { goLogin(); return; }

        initViews();
        loadCats(currentType);
    }

    private void initViews() {
        drawerLayout    = findViewById(R.id.drawer_layout);
        progressTop     = findViewById(R.id.progress_top);
        swipeRefresh    = findViewById(R.id.swipe_refresh);
        etSearch        = findViewById(R.id.et_search);
        tvAccount       = findViewById(R.id.tv_account);
        rvCats          = findViewById(R.id.rv_categories);
        rvContent       = findViewById(R.id.rv_content);
        tvSearchResults = findViewById(R.id.tv_search_results);

        tvAccount.setText(state.account.displayHost());
        tvAccount.setOnClickListener(v -> goLogin());

        // Hamburger menu
        View btnMenu = findViewById(R.id.btn_menu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                View sidebar = findViewById(R.id.sidebar);
                if (sidebar != null) {
                    if (drawerLayout.isDrawerOpen(sidebar)) drawerLayout.closeDrawers();
                    else drawerLayout.openDrawer(sidebar);
                }
            });
        }

        // Sidebar tabs
        TextView tabCats = findViewById(R.id.stab_cats);
        TextView tabFavs = findViewById(R.id.stab_favs);
        TextView tabHist = findViewById(R.id.stab_hist);
        if (tabCats != null) tabCats.setOnClickListener(v -> { showCats();    highlight(tabCats, tabFavs, tabHist); });
        if (tabFavs != null) tabFavs.setOnClickListener(v -> { showFavs();    highlight(tabFavs, tabCats, tabHist); });
        if (tabHist != null) tabHist.setOnClickListener(v -> { showHistory(); highlight(tabHist, tabCats, tabFavs); });

        // Categories
        catAdapter = new CategoryAdapter(cat -> {
            isSearchMode = false;
            if (tvSearchResults != null) tvSearchResults.setVisibility(View.GONE);
            loadItems(cat);
            drawerLayout.closeDrawers();
        });
        if (rvCats != null) {
            rvCats.setLayoutManager(new LinearLayoutManager(this));
            rvCats.setAdapter(catAdapter);
        }

        // Content
        mediaAdapter = new MediaAdapter(currentType, prefs, new MediaAdapter.Listener() {
            @Override public void onClick(MediaItem item) { openItem(item); }
            @Override public void onFav(MediaItem item, boolean isFav) {
                Toast.makeText(MainActivity.this,
                    isFav ? "Agregado a favoritos" : "Eliminado de favoritos",
                    Toast.LENGTH_SHORT).show();
            }
        });
        if (rvContent != null) {
            rvContent.setLayoutManager(new GridLayoutManager(this, 2));
            rvContent.setAdapter(mediaAdapter);
        }

        // Bottom nav
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        if (nav != null) {
            nav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    isSearchMode = false;
                    showHome();
                } else if (id == R.id.nav_live) {
                    isSearchMode = false;
                    switchType(MediaItem.LIVE);
                } else if (id == R.id.nav_vod) {
                    isSearchMode = false;
                    switchType(MediaItem.VOD);
                } else if (id == R.id.nav_series) {
                    isSearchMode = false;
                    switchType(MediaItem.SERIES);
                } else if (id == R.id.nav_search) {
                    isSearchMode = true;
                    if (etSearch != null) etSearch.requestFocus();
                }
                return true;
            });
        }

        // Búsqueda mejorada — debounce 400ms
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    searchHandler.removeCallbacks(searchRunnable);
                    String q = s.toString().trim();
                    if (q.isEmpty()) {
                        isSearchMode = false;
                        if (tvSearchResults != null) tvSearchResults.setVisibility(View.GONE);
                        if (selectedCat != null && selectedCat.loaded)
                            mediaAdapter.setItems(selectedCat.items);
                        return;
                    }
                    isSearchMode = true;
                    searchRunnable = () -> doSearch(q);
                    searchHandler.postDelayed(searchRunnable, 400);
                }
                public void afterTextChanged(Editable s) {}
            });
        }

        // Pull to refresh
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(getColor(R.color.accent));
            swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.bg2));
            swipeRefresh.setOnRefreshListener(() -> {
                if (!isSearchMode && selectedCat != null) {
                    selectedCat.loaded = false;
                    loadItems(selectedCat);
                }
                swipeRefresh.setRefreshing(false);
            });
        }
    }

    private void highlight(TextView active, TextView... others) {
        if (active == null) return;
        active.setTextColor(getColor(R.color.accent));
        for (TextView t : others) if (t != null) t.setTextColor(getColor(R.color.muted));
    }

    private void showHome() {
        List<MediaItem> hist = prefs.history();
        if (!hist.isEmpty()) {
            mediaAdapter.setItems(hist);
            if (tvSearchResults != null) {
                tvSearchResults.setText("Vistos recientemente — " + hist.size());
                tvSearchResults.setVisibility(View.VISIBLE);
            }
        }
    }

    private void switchType(String type) {
        currentType = type;
        selectedCat = null;
        if (tvSearchResults != null) tvSearchResults.setVisibility(View.GONE);
        mediaAdapter = new MediaAdapter(type, prefs, new MediaAdapter.Listener() {
            @Override public void onClick(MediaItem item) { openItem(item); }
            @Override public void onFav(MediaItem item, boolean isFav) {}
        });
        if (rvContent != null) rvContent.setAdapter(mediaAdapter);
        loadCats(type);
    }

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
                mainHandler.post(() -> { showProgress(false); toast("Error: " + e.getMessage()); });
            }
        });
    }

    private void loadItems(Category cat) {
        selectedCat = cat;
        catAdapter.setSelected(state.cats(currentType).indexOf(cat));
        if (cat.loaded) { mediaAdapter.setItems(cat.items); return; }
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
                mainHandler.post(() -> { showProgress(false); toast("Error: " + e.getMessage()); });
            }
        });
    }

    // Búsqueda mejorada — busca en todas las categorías cargadas de TODOS los tipos
    private void doSearch(String q) {
        String ql = q.toLowerCase();
        List<MediaItem> liveResults   = new ArrayList<>();
        List<MediaItem> vodResults    = new ArrayList<>();
        List<MediaItem> seriesResults = new ArrayList<>();

        for (Category c : state.liveCats)   if (c.loaded)
            liveResults.addAll(c.items.stream()
                .filter(i -> i.name != null && i.name.toLowerCase().contains(ql))
                .collect(Collectors.toList()));

        for (Category c : state.vodCats)    if (c.loaded)
            vodResults.addAll(c.items.stream()
                .filter(i -> i.name != null && i.name.toLowerCase().contains(ql))
                .collect(Collectors.toList()));

        for (Category c : state.seriesCats) if (c.loaded)
            seriesResults.addAll(c.items.stream()
                .filter(i -> i.name != null && i.name.toLowerCase().contains(ql))
                .collect(Collectors.toList()));

        // Combinar todos los resultados con separadores
        List<MediaItem> combined = new ArrayList<>();
        combined.addAll(liveResults);
        combined.addAll(vodResults);
        combined.addAll(seriesResults);

        int total = combined.size();

        // Mostrar resumen de resultados
        if (tvSearchResults != null) {
            if (total == 0) {
                tvSearchResults.setText("Sin resultados para \"" + q + "\"");
            } else {
                StringBuilder sb = new StringBuilder();
                if (!liveResults.isEmpty())   sb.append("Live: ").append(liveResults.size()).append("  ");
                if (!vodResults.isEmpty())    sb.append("Peliculas: ").append(vodResults.size()).append("  ");
                if (!seriesResults.isEmpty()) sb.append("Series: ").append(seriesResults.size());
                tvSearchResults.setText(sb.toString().trim());
            }
            tvSearchResults.setVisibility(View.VISIBLE);
        }

        mediaAdapter.setItems(combined);
    }

    private void showCats() { catAdapter.setItems(state.cats(currentType)); }

    private void showFavs() {
        List<MediaItem> all = new ArrayList<>();
        for (Category c : state.cats(currentType)) if (c.loaded) all.addAll(c.items);
        mediaAdapter.setItems(all.stream()
            .filter(i -> prefs.isFav(i.favKey()))
            .collect(Collectors.toList()));
    }

    private void showHistory() {
        mediaAdapter.setItems(prefs.history().stream()
            .filter(i -> i.type != null && i.type.equals(currentType))
            .collect(Collectors.toList()));
    }

    private void openItem(MediaItem item) {
        prefs.addHistory(item);
        state.current = item;
        Intent intent;
        if (item.type.equals(MediaItem.LIVE)) {
            state.channelList = selectedCat != null ? selectedCat.items : new ArrayList<>();
            state.channelIdx  = state.channelList.indexOf(item);
            intent = new Intent(this, PlayerActivity.class);
        } else if (item.type.equals(MediaItem.VOD)) {
            intent = new Intent(this, com.jox3.tv.ui.vod.VodDetailActivity.class);
        } else {
            intent = new Intent(this, com.jox3.tv.ui.series.SeriesActivity.class);
        }
        intent.putExtra("item", item);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showProgress(boolean show) {
        if (progressTop == null) return;
        progressTop.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) progressTop.setIndeterminate(true);
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private void goLogin() { startActivity(new Intent(this, LoginActivity.class)); finish(); }
}
