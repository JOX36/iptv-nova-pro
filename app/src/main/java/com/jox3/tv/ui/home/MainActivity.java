package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jox3.tv.ui.BaseActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
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
import com.jox3.tv.util.SkeletonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends BaseActivity {

    private String currentType = MediaItem.LIVE;
    private Category selectedCat;
    private boolean isSearchMode = false;
    private boolean isHomeMode = true;
    private boolean indexingStarted = false;
    private int indexTotal = 0;
    private int indexDone  = 0;

    private DrawerLayout drawerLayout;
    private CategoryAdapter catAdapter;
    private MediaAdapter mediaAdapter;
    private RecyclerView rvCats, rvContent;
    private LinearProgressIndicator progressTop;
    private SwipeRefreshLayout swipeRefresh;
    private TextInputEditText etSearch;
    private TextView tvAccount, tvSearchResults;
    private ViewGroup contentContainer;

    // Home
    private View homeView;
    private LinearLayout sectionContinue, rowContinue;
    private LinearLayout sectionLive, rowLive;
    private LinearLayout sectionVod, rowVod;
    private LinearLayout sectionSeries, rowSeries;
    private View homeEmpty;
    // Banner
    private View bannerContainer;
    private android.widget.ImageView bannerImage;
    private TextView bannerTitle, bannerGenre;
    private View bannerPlay;
    private android.widget.LinearLayout bannerDots;
    private List<MediaItem> bannerItems = new ArrayList<>();
    private int bannerIdx = 0;
    private final Runnable bannerRunnable = this::nextBanner;

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
        applyTheme();
        prefs = new AppPrefs(this);
        state = AppState.get();
        if (state.account == null) { goLogin(); return; }
        initViews();
        loadIndexCache(); // Cargar caché instantáneamente
        showHome();
        startIndexing();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isHomeMode && homeView != null) refreshHome();
    }

    // ── HOME ──
    private void showHome() {
        isHomeMode = true;
        swipeRefresh.setVisibility(View.GONE);
        rvContent.setVisibility(View.GONE);
        // Ocultar menú — buscador ocupa todo el ancho en Home
        View btnMenu = findViewById(R.id.btn_menu);
        if (btnMenu != null) btnMenu.setVisibility(View.GONE);
        drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        if (homeView == null) {
            homeView = LayoutInflater.from(this)
                .inflate(R.layout.fragment_home, contentContainer, false);
            contentContainer.addView(homeView);
            sectionContinue = homeView.findViewById(R.id.section_continue);
            rowContinue     = homeView.findViewById(R.id.row_continue);
            sectionLive     = homeView.findViewById(R.id.section_live);
            rowLive         = homeView.findViewById(R.id.row_live);
            sectionVod      = homeView.findViewById(R.id.section_vod);
            rowVod          = homeView.findViewById(R.id.row_vod);
            sectionSeries   = homeView.findViewById(R.id.section_series);
            rowSeries       = homeView.findViewById(R.id.row_series);
            homeEmpty       = homeView.findViewById(R.id.home_empty);

            homeView.findViewById(R.id.tv_live_more).setOnClickListener(v -> {
                isHomeMode = false; hideHome(); switchType(MediaItem.LIVE);
                ((BottomNavigationView) findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_live);
            });
            homeView.findViewById(R.id.tv_vod_more).setOnClickListener(v -> {
                isHomeMode = false; hideHome(); switchType(MediaItem.VOD);
                ((BottomNavigationView) findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_vod);
            });
            bannerContainer = homeView.findViewById(R.id.banner_container);
            bannerImage     = homeView.findViewById(R.id.banner_image);
            bannerTitle     = homeView.findViewById(R.id.banner_title);
            bannerGenre     = homeView.findViewById(R.id.banner_genre);
            bannerPlay      = homeView.findViewById(R.id.banner_play);
            bannerDots      = homeView.findViewById(R.id.banner_dots);

            homeView.findViewById(R.id.tv_series_more).setOnClickListener(v -> {
                isHomeMode = false; hideHome(); switchType(MediaItem.SERIES);
                ((BottomNavigationView) findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_series);
            });
        }
        homeView.setVisibility(View.VISIBLE);
        // Mostrar skeletons mientras carga
        if (rowVod != null && rowVod.getChildCount() == 0) {
            sectionVod.setVisibility(View.VISIBLE);
            SkeletonHelper.showSkeletons(rowVod, 5);
        }
        if (rowSeries != null && rowSeries.getChildCount() == 0) {
            sectionSeries.setVisibility(View.VISIBLE);
            SkeletonHelper.showSkeletons(rowSeries, 5);
        }
        refreshHome();
        loadHomeContent();
    }

    private void hideHome() {
        if (homeView != null) homeView.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.VISIBLE);
        rvContent.setVisibility(View.VISIBLE);
        // Mostrar menú y habilitar sidebar fuera del Home
        View btnMenu2 = findViewById(R.id.btn_menu);
        if (btnMenu2 != null) btnMenu2.setVisibility(View.VISIBLE);
        drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    private void refreshHome() {
        if (homeView == null || isDestroyed()) return;

        // Continuar viendo
        List<MediaItem> hist = prefs.history();
        if (!hist.isEmpty()) {
            sectionContinue.setVisibility(View.VISIBLE);
            homeEmpty.setVisibility(View.GONE);
            rowContinue.removeAllViews();
            for (MediaItem m : hist.subList(0, Math.min(15, hist.size())))
                addVodCard(rowContinue, m, true, true);
        }

        // Live desde caché o memoria
        List<MediaItem> liveItems = new ArrayList<>();
        for (Category c : state.liveCats)
            if (c.loaded && !c.items.isEmpty()) { liveItems.addAll(c.items); break; }
        if (liveItems.isEmpty()) liveItems = prefs.getCachedHome("live");
        if (!liveItems.isEmpty()) {
            sectionLive.setVisibility(View.VISIBLE);
            homeEmpty.setVisibility(View.GONE);
            rowLive.removeAllViews();
            List<MediaItem> show = liveItems.subList(0, Math.min(15, liveItems.size()));
            for (MediaItem m : show) addChannelCard(rowLive, m);
            prefs.saveCachedHome("live", show);
        }

        // Limpiar skeletons antes de poner contenido real
        if (rowVod != null) SkeletonHelper.clearSkeletons(rowVod);
        if (rowSeries != null) SkeletonHelper.clearSkeletons(rowSeries);

        // VOD desde caché o memoria
        List<MediaItem> vodItems = new ArrayList<>();
        for (Category c : state.vodCats)
            if (c.loaded && !c.items.isEmpty()) { vodItems.addAll(c.items); break; }
        if (vodItems.isEmpty()) vodItems = prefs.getCachedHome("vod");
        if (!vodItems.isEmpty()) {
            sectionVod.setVisibility(View.VISIBLE);
            homeEmpty.setVisibility(View.GONE);
            rowVod.removeAllViews();
            List<MediaItem> show = vodItems.subList(0, Math.min(15, vodItems.size()));
            for (MediaItem m : show) addVodCard(rowVod, m, false, false);
            prefs.saveCachedHome("vod", show);
        }

        // Banner — usar VOD o Series con mejor rating
        if (bannerContainer != null) {
            List<MediaItem> bItems = new ArrayList<>();
            for (com.jox3.tv.model.Category cat : state.vodCats)
                if (cat.loaded && !cat.items.isEmpty()) { bItems.addAll(cat.items.subList(0, Math.min(5, cat.items.size()))); break; }
            if (bItems.isEmpty()) for (com.jox3.tv.model.Category cat : state.seriesCats)
                if (cat.loaded && !cat.items.isEmpty()) { bItems.addAll(cat.items.subList(0, Math.min(5, cat.items.size()))); break; }
            if (!bItems.isEmpty()) {
                bannerItems = bItems;
                bannerIdx = 0;
                updateBanner();
                mainHandler.removeCallbacks(bannerRunnable);
                mainHandler.postDelayed(bannerRunnable, 5000);
            }
        }

        // Series desde caché o memoria
        List<MediaItem> seriesItems = new ArrayList<>();
        for (Category c : state.seriesCats)
            if (c.loaded && !c.items.isEmpty()) { seriesItems.addAll(c.items); break; }
        if (seriesItems.isEmpty()) seriesItems = prefs.getCachedHome("series");
        if (!seriesItems.isEmpty()) {
            sectionSeries.setVisibility(View.VISIBLE);
            homeEmpty.setVisibility(View.GONE);
            rowSeries.removeAllViews();
            List<MediaItem> show = seriesItems.subList(0, Math.min(15, seriesItems.size()));
            for (MediaItem m : show) addVodCard(rowSeries, m, false, false);
            prefs.saveCachedHome("series", show);
        }
    }

    // Carga contenido para el Home en background
    private void loadHomeContent() {
        exec.execute(() -> {
            try {
                if (state.liveCats.isEmpty())
                    state.liveCats.addAll(state.api.getLiveCats());
                if (!state.liveCats.isEmpty() && !state.liveCats.get(0).loaded) {
                    state.liveCats.get(0).items = state.api.getLiveStreams(state.liveCats.get(0).id);
                    state.liveCats.get(0).loaded = true;
                }

                if (state.vodCats.isEmpty())
                    state.vodCats.addAll(state.api.getVodCats());
                if (!state.vodCats.isEmpty() && !state.vodCats.get(0).loaded) {
                    state.vodCats.get(0).items = state.api.getVodStreams(state.vodCats.get(0).id);
                    state.vodCats.get(0).loaded = true;
                }

                if (state.seriesCats.isEmpty())
                    state.seriesCats.addAll(state.api.getSeriesCats());
                if (!state.seriesCats.isEmpty() && !state.seriesCats.get(0).loaded) {
                    state.seriesCats.get(0).items = state.api.getSeries(state.seriesCats.get(0).id);
                    state.seriesCats.get(0).loaded = true;
                }

                mainHandler.post(() -> {
                    if (!isDestroyed() && isHomeMode) refreshHome();
                });
            } catch (Exception ignored) {}
        });
    }

    private void updateBanner() {
        if (bannerItems.isEmpty() || bannerContainer == null) return;
        MediaItem m = bannerItems.get(bannerIdx);
        bannerContainer.setVisibility(View.VISIBLE);
        if (bannerTitle != null) bannerTitle.setText(m.name);
        if (bannerGenre != null) bannerGenre.setText(m.genre != null && !m.genre.isEmpty() ? m.genre : "");
        if (bannerImage != null && m.thumb() != null && !m.thumb().isEmpty())
            Glide.with(this).load(m.thumb()).centerCrop().into(bannerImage);
        if (bannerPlay != null) bannerPlay.setOnClickListener(v -> openItem(m));

        // Dots
        if (bannerDots != null) {
            bannerDots.removeAllViews();
            for (int i = 0; i < bannerItems.size(); i++) {
                View dot = new View(this);
                android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                        i == bannerIdx ? 16 : 6, 6);
                lp.setMargins(3, 0, 3, 0);
                dot.setLayoutParams(lp);
                dot.setBackgroundColor(i == bannerIdx ? 0xFF00D4FF : 0x66FFFFFF);
                bannerDots.addView(dot);
            }
        }
    }

    private void nextBanner() {
        if (bannerItems.isEmpty()) return;
        bannerIdx = (bannerIdx + 1) % bannerItems.size();
        if (bannerImage != null) {
            bannerImage.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                updateBanner();
                bannerImage.animate().alpha(1f).setDuration(300).start();
            }).start();
        }
        mainHandler.postDelayed(bannerRunnable, 5000);
    }

    private void addChannelCard(LinearLayout row, MediaItem m) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_home_channel, row, false);
        ImageView iv = card.findViewById(R.id.iv_logo);
        TextView ph  = card.findViewById(R.id.tv_ph);
        TextView tvName = card.findViewById(R.id.tv_name);
        tvName.setText(m.name);
        if (m.logo != null && !m.logo.isEmpty()) {
            Glide.with(this).load(m.logo).centerCrop().into(iv);
        } else { iv.setVisibility(View.GONE); ph.setVisibility(View.VISIBLE); }
        card.setOnClickListener(v -> openItem(m));
        row.addView(card);
    }

    private void addVodCard(LinearLayout row, MediaItem m, boolean showBadge, boolean showProgress) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_home_vod, row, false);
        ImageView iv    = card.findViewById(R.id.iv_cover);
        TextView ph     = card.findViewById(R.id.tv_ph);
        TextView tvName = card.findViewById(R.id.tv_name);
        TextView badge  = card.findViewById(R.id.tv_type_badge);
        View progBg     = card.findViewById(R.id.progress_bg);
        View progBar    = card.findViewById(R.id.progress_bar);

        tvName.setText(m.name);

        if (showBadge && m.type != null) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(m.type.equals(MediaItem.LIVE) ? "VIVO" :
                          m.type.equals(MediaItem.VOD)  ? "VOD"  : "SERIE");
            badge.setBackgroundColor(getColor(
                m.type.equals(MediaItem.LIVE)   ? R.color.red :
                m.type.equals(MediaItem.SERIES) ? R.color.accent2 : R.color.accent));
        } else {
            badge.setVisibility(View.GONE);
        }

        String thumb = m.thumb();
        if (thumb != null && !thumb.isEmpty()) {
            Glide.with(this).load(thumb).centerCrop().into(iv);
        } else { iv.setVisibility(View.GONE); ph.setVisibility(View.VISIBLE); }

        if (showProgress) {
            int pct = prefs.progressPct(m.id);
            // Para series buscar progreso del último episodio en cola
            if (pct == 0 && MediaItem.SERIES.equals(m.type)) {
                for (MediaItem ep : state.episodeQueue) {
                    int epPct = prefs.progressPct(ep.id);
                    if (epPct > pct) pct = epPct;
                }
            }
            if (pct > 2 && pct < 98) {
                progBg.setVisibility(View.VISIBLE);
                progBar.setVisibility(View.VISIBLE);
                final int finalPct = pct;
                progBar.post(() -> {
                    ViewGroup.LayoutParams lp = progBar.getLayoutParams();
                    lp.width = (int)(progBg.getWidth() * finalPct / 100f);
                    progBar.setLayoutParams(lp);
                });
            }
        }

        card.setOnClickListener(v -> openItem(m));
        row.addView(card);
    }

    // ── BÚSQUEDA — solo en lo cargado ──
    private void loadIndexCache() {
        // Cargar categorías e items desde caché guardado en disco
        if (state.liveCats.isEmpty()) {
            List<com.jox3.tv.model.Category> cached = prefs.loadIndex("live");
            if (!cached.isEmpty()) state.liveCats.addAll(cached);
        }
        if (state.vodCats.isEmpty()) {
            List<com.jox3.tv.model.Category> cached = prefs.loadIndex("vod");
            if (!cached.isEmpty()) state.vodCats.addAll(cached);
        }
        if (state.seriesCats.isEmpty()) {
            List<com.jox3.tv.model.Category> cached = prefs.loadIndex("series");
            if (!cached.isEmpty()) state.seriesCats.addAll(cached);
        }
    }

    private void startIndexing() {
        if (indexingStarted) return;
        indexingStarted = true;

        exec.execute(() -> {
            try {
                // 1. Cargar nombres de categorías de los 3 tipos
                if (state.liveCats.isEmpty())
                    state.liveCats.addAll(state.api.getLiveCats());
                if (state.vodCats.isEmpty())
                    state.vodCats.addAll(state.api.getVodCats());
                if (state.seriesCats.isEmpty())
                    state.seriesCats.addAll(state.api.getSeriesCats());

                indexTotal = state.liveCats.size() + state.vodCats.size() + state.seriesCats.size();
                indexDone  = 0;

                // Mostrar barra de progreso
                mainHandler.post(() -> {
                    View bar = findViewById(R.id.layout_index_progress);
                    if (bar != null) bar.setVisibility(View.VISIBLE);
                    android.widget.ProgressBar pb = findViewById(R.id.progress_index);
                    if (pb != null) pb.setMax(Math.max(indexTotal, 1));
                });

                // 2. Cargar items en paralelo con 5 hilos
                java.util.concurrent.ExecutorService pool =
                    java.util.concurrent.Executors.newFixedThreadPool(5);

                for (Category cat : state.liveCats) {
                    if (cat.loaded) { indexDone++; continue; }
                    final Category fc = cat;
                    pool.execute(() -> {
                        try {
                            fc.items = state.api.getLiveStreams(fc.id);
                            fc.loaded = true;
                        } catch (Exception ignored) {}
                        indexDone++;
                        updateIndexProgress();
                    });
                }
                for (Category cat : state.vodCats) {
                    if (cat.loaded) { indexDone++; continue; }
                    final Category fc = cat;
                    pool.execute(() -> {
                        try {
                            fc.items = state.api.getVodStreams(fc.id);
                            fc.loaded = true;
                        } catch (Exception ignored) {}
                        indexDone++;
                        updateIndexProgress();
                    });
                }
                for (Category cat : state.seriesCats) {
                    if (cat.loaded) { indexDone++; continue; }
                    final Category fc = cat;
                    pool.execute(() -> {
                        try {
                            fc.items = state.api.getSeries(fc.id);
                            fc.loaded = true;
                        } catch (Exception ignored) {}
                        indexDone++;
                        updateIndexProgress();
                    });
                }

                pool.shutdown();
                pool.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES);

                // Guardar caché en disco
                prefs.saveIndex("live",   state.liveCats);
                prefs.saveIndex("vod",    state.vodCats);
                prefs.saveIndex("series", state.seriesCats);

                // Indexación completa
                mainHandler.post(() -> {
                    View bar = findViewById(R.id.layout_index_progress);
                    if (bar != null) {
                        TextView status = findViewById(R.id.tv_index_status);
                        TextView pct    = findViewById(R.id.tv_index_pct);
                        if (status != null) status.setText("Listo ✓");
                        if (pct    != null) pct.setText("100%");
                        mainHandler.postDelayed(() -> bar.setVisibility(View.GONE), 2000);
                    }
                    if (isHomeMode) refreshHome();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    View bar = findViewById(R.id.layout_index_progress);
                    if (bar != null) bar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void updateIndexProgress() {
        mainHandler.post(() -> {
            if (indexTotal <= 0) return;
            android.widget.ProgressBar pb = findViewById(R.id.progress_index);
            TextView status = findViewById(R.id.tv_index_status);
            TextView pct    = findViewById(R.id.tv_index_pct);
            if (pb     != null) pb.setProgress(indexDone);
            if (pct    != null) pct.setText((int)(indexDone * 100f / indexTotal) + "%");
            if (status != null) {
                int lv = (int) state.liveCats.stream().filter(x->x.loaded).count();
                int vd = (int) state.vodCats.stream().filter(x->x.loaded).count();
                int sr = (int) state.seriesCats.stream().filter(x->x.loaded).count();
                status.setText("TV:" + lv + " VOD:" + vd + " Series:" + sr);
            }
        });
    }

    private void startVoiceSearch() {
        try {
            android.speech.RecognizerIntent intent = null;
            Intent voiceIntent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            voiceIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            voiceIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Buscar...");
            voiceIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
            startActivityForResult(voiceIntent, 9001);
        } catch (Exception e) {
            Toast.makeText(this, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9001 && resultCode == RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String query = results.get(0);
                etSearch.setText(query);
                doSearch(query);
            }
        }
    }

    private void doSearch(String q) {
        if (q.trim().isEmpty()) return;
        String ql = q.toLowerCase();

        List<MediaItem> liveR   = searchIn(state.liveCats,   ql);
        List<MediaItem> vodR    = searchIn(state.vodCats,    ql);
        List<MediaItem> seriesR = searchIn(state.seriesCats, ql);

        // Usar setSearchResults para mostrar grupos con headers
        mediaAdapter.setSearchResults(liveR, vodR, seriesR);

        int total = liveR.size() + vodR.size() + seriesR.size();
        if (tvSearchResults != null) {
            tvSearchResults.setText(total == 0 ?
                "Sin resultados para \"" + q + "\"" :
                total + " resultado" + (total > 1 ? "s" : ""));
            tvSearchResults.setVisibility(View.VISIBLE);
        }
        showProgress(false);
    }

    private List<MediaItem> searchIn(List<Category> cats, String q) {
        List<MediaItem> r = new ArrayList<>();
        for (Category c : cats)
            if (c.loaded)
                r.addAll(c.items.stream()
                    .filter(i -> i.name != null && i.name.toLowerCase().contains(q))
                    .collect(Collectors.toList()));
        return r;
    }

    // ── VISTAS ──
    private void initViews() {
        drawerLayout    = findViewById(R.id.drawer_layout);
        progressTop     = findViewById(R.id.progress_top);
        swipeRefresh    = findViewById(R.id.swipe_refresh);
        etSearch        = findViewById(R.id.et_search);
        tvAccount       = findViewById(R.id.tv_account);
        rvCats          = findViewById(R.id.rv_categories);
        rvContent       = findViewById(R.id.rv_content);
        tvSearchResults = findViewById(R.id.tv_search_results);
        contentContainer = findViewById(R.id.content_container);

        tvAccount.setText(state.account.displayHost());
        tvAccount.setOnClickListener(v -> goLogin());

        // Micrófono para búsqueda por voz
        View btnVoice = findViewById(R.id.btn_voice);
        if (btnVoice != null) btnVoice.setOnClickListener(v -> startVoiceSearch());

        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null)
            btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, com.jox3.tv.ui.setup.SettingsActivity.class)));

        View btnMenu = findViewById(R.id.btn_menu);
        if (btnMenu != null) btnMenu.setOnClickListener(v -> {
            View sidebar = findViewById(R.id.sidebar);
            if (sidebar != null) {
                if (drawerLayout.isDrawerOpen(sidebar)) drawerLayout.closeDrawers();
                else drawerLayout.openDrawer(sidebar);
            }
        });

        TextView tabCats = findViewById(R.id.stab_cats);
        TextView tabFavs = findViewById(R.id.stab_favs);
        TextView tabHist = findViewById(R.id.stab_hist);
        if (tabCats != null) tabCats.setOnClickListener(v -> { showCats();    highlight(tabCats, tabFavs, tabHist); });
        if (tabFavs != null) tabFavs.setOnClickListener(v -> { showFavs();    highlight(tabFavs, tabCats, tabHist); });
        if (tabHist != null) tabHist.setOnClickListener(v -> { showHistory(); highlight(tabHist, tabCats, tabFavs); });

        catAdapter = new CategoryAdapter(cat -> {
            isSearchMode = false; isHomeMode = false;
            if (tvSearchResults != null) tvSearchResults.setVisibility(View.GONE);
            hideHome(); loadItems(cat); drawerLayout.closeDrawers();
        });
        rvCats.setLayoutManager(new LinearLayoutManager(this));
        rvCats.setAdapter(catAdapter);

        mediaAdapter = new MediaAdapter(currentType, prefs, new MediaAdapter.Listener() {
            @Override public void onClick(MediaItem item) { openItem(item); }
            @Override public void onFav(MediaItem item, boolean isFav) {
                Toast.makeText(MainActivity.this,
                    isFav ? "Agregado a favoritos" : "Eliminado de favoritos",
                    Toast.LENGTH_SHORT).show();
            }
        });
        GridLayoutManager gridMgr = new GridLayoutManager(this, 2);
        rvContent.setLayoutManager(gridMgr);
        rvContent.setAdapter(mediaAdapter);
        mediaAdapter.attachToGrid(gridMgr, 2);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        if (nav != null) nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                isSearchMode = false; isHomeMode = true; showHome();
            } else if (id == R.id.nav_live) {
                isSearchMode = false; isHomeMode = false; hideHome(); switchType(MediaItem.LIVE);
            } else if (id == R.id.nav_vod) {
                isSearchMode = false; isHomeMode = false; hideHome(); switchType(MediaItem.VOD);
            } else if (id == R.id.nav_series) {
                isSearchMode = false; isHomeMode = false; hideHome(); switchType(MediaItem.SERIES);
            } else if (id == R.id.nav_search) {
                isSearchMode = true; isHomeMode = false; hideHome(); etSearch.requestFocus();
            }
            return true;
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = s.toString().trim();
                searchHandler.removeCallbacks(searchRunnable);
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

        swipeRefresh.setColorSchemeColors(getColor(R.color.accent));
        swipeRefresh.setProgressBackgroundColorSchemeColor(getColor(R.color.bg2));
        swipeRefresh.setOnRefreshListener(() -> {
            if (isHomeMode) { loadHomeContent(); swipeRefresh.setRefreshing(false); return; }
            if (!isSearchMode && selectedCat != null) {
                selectedCat.loaded = false; loadItems(selectedCat);
            }
            swipeRefresh.setRefreshing(false);
        });
    }

    private void highlight(TextView active, TextView... others) {
        if (active == null) return;
        active.setTextColor(getColor(R.color.accent));
        for (TextView t : others) if (t != null) t.setTextColor(getColor(R.color.muted));
    }

    private void switchType(String type) {
        currentType = type;
        selectedCat = null;
        if (tvSearchResults != null) tvSearchResults.setVisibility(View.GONE);
        mediaAdapter = new MediaAdapter(type, prefs, new MediaAdapter.Listener() {
            @Override public void onClick(MediaItem item) { openItem(item); }
            @Override public void onFav(MediaItem item, boolean isFav) {}
        });
        GridLayoutManager gm = new GridLayoutManager(this, 2);
        rvContent.setLayoutManager(gm);
        rvContent.setAdapter(mediaAdapter);
        mediaAdapter.attachToGrid(gm, 2);
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
                cat.items = items; cat.loaded = true;
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

    private void showCats() { catAdapter.setItems(state.cats(currentType)); }

    private void showFavs() {
        List<MediaItem> all = new ArrayList<>();
        for (Category c : state.cats(currentType)) if (c.loaded) all.addAll(c.items);
        mediaAdapter.setItems(all.stream().filter(i -> prefs.isFav(i.favKey())).collect(Collectors.toList()));
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
            PlayerActivity.requestClose = true;
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

    @Override
    public void onBackPressed() {
        if (isHomeMode) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Salir")
                .setMessage("¿Deseas salir de JOX3 TV?")
                .setPositiveButton("Salir", (d, w) -> finish())
                .setNegativeButton("Cancelar", null)
                .show();
        } else {
            // Si no estamos en Home, volver al Home
            isHomeMode = true;
            ((com.google.android.material.bottomnavigation.BottomNavigationView)
                findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_home);
        }
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private void goLogin() { startActivity(new Intent(this, LoginActivity.class)); finish(); }

    
}
