package com.jox3.tv.ui.player;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    public static volatile boolean requestClose = false;

    // Compatibilidad con ChannelAdapter existente
    public static final String EXTRA_URL   = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_TYPE  = "extra_type";

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout topBar, bottomBar;
    private LinearLayout layoutSeek, layoutLiveBtns, layoutVodBtns;
    private TextView tvName, tvResolution, tvStatus, btnBack, btnFav;

    // Live buttons
    private Button btnPrev, btnNext, btnPipLive, btnStop;

    // VOD/Series buttons
    private Button btnPlayPause, btnRewind, btnForward;
    private Button btnAudio, btnSubs, btnPip, btnStopVod;

    // Seek
    private SeekBar seekBar;
    private TextView tvPosition, tvDuration;

    private MediaItem item;
    private AppPrefs prefs;
    private AppState state;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean barsVisible = false;
    private boolean isTv = false;
    private boolean isInPip = false;
    private boolean playerReleased = false;
    private boolean seekBarTracking = false;
    private int retryCount = 0;

    private AudioManager audioManager;
    private float gestStartX, gestStartY;
    private boolean gestActive, gestIsVol, gestIsBright, gestIsSeek;
    private int gestStartVol;
    private float gestStartBright;
    private long seekStartPos;

    private BroadcastReceiver pipCloseReceiver;

    private final Runnable pipCheckRunnable = new Runnable() {
        @Override public void run() {
            if (requestClose && !playerReleased) {
                requestClose = false;
                exitPlayer();
                return;
            }
            if (isInPip) handler.postDelayed(this, 500);
        }
    };

    private final Runnable seekUpdateRunnable = new Runnable() {
        @Override public void run() {
            updateSeekBar();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setFullscreen();
        setContentView(R.layout.activity_player);
        hideSystemBars();

        prefs = new AppPrefs(this);
        state = AppState.get();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        isTv = getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK);

        item = (MediaItem) getIntent().getSerializableExtra("item");

        // Compatibilidad con ChannelAdapter que usa EXTRA_URL/TITLE/TYPE
        if (item == null) {
            String url   = getIntent().getStringExtra(EXTRA_URL);
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            String type  = getIntent().getStringExtra(EXTRA_TYPE);
            if (url != null) {
                String itemType = "live".equals(type) ? MediaItem.LIVE :
                                  "series".equals(type) ? MediaItem.SERIES : MediaItem.VOD;
                item = new MediaItem("0", title != null ? title : "", "", url, "", itemType);
            }
        }

        if (item == null) { finish(); return; }

        if (item.type.equals(MediaItem.SERIES)) {
            Intent i = new Intent(this, com.jox3.tv.ui.series.SeriesActivity.class);
            i.putExtra("item", item);
            startActivity(i);
            finish();
            return;
        }

        // Receiver para cerrar PiP
        pipCloseReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) { exitPlayer(); }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipCloseReceiver,
                new IntentFilter("com.jox3.tv.CLOSE_PIP"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pipCloseReceiver, new IntentFilter("com.jox3.tv.CLOSE_PIP"));
        }

        initViews();
        initPlayer();
        if (isTv) showBars();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        MediaItem newItem = (MediaItem) intent.getSerializableExtra("item");
        if (newItem != null) {
            isInPip = false;
            item = newItem;
            tvName.setText(item.name);
            tvResolution.setVisibility(View.GONE);
            updateFavBtn();
            setupButtonsForType();
            initPlayer();
            showBars();
        }
    }

    private void setFullscreen() {
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            getWindow().getDecorView().setSystemUiVisibility(flags);
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                v -> { if ((v & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) setFullscreen(); });
        }
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                wic.hide(android.view.WindowInsets.Type.statusBars() |
                         android.view.WindowInsets.Type.navigationBars());
                wic.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        playerView      = findViewById(R.id.player_view);
        topBar          = findViewById(R.id.top_bar);
        bottomBar       = findViewById(R.id.bottom_bar);
        tvName          = findViewById(R.id.tv_name);
        tvResolution    = findViewById(R.id.tv_resolution);
        tvStatus        = findViewById(R.id.tv_status);
        btnBack         = findViewById(R.id.btn_back);
        btnFav          = findViewById(R.id.btn_fav);
        layoutSeek      = findViewById(R.id.layout_seek);
        layoutLiveBtns  = findViewById(R.id.layout_live_btns);
        layoutVodBtns   = findViewById(R.id.layout_vod_btns);

        // Live
        btnPrev         = findViewById(R.id.btn_prev);
        btnNext         = findViewById(R.id.btn_next);
        btnPipLive      = findViewById(R.id.btn_pip_live);
        btnStop         = findViewById(R.id.btn_stop);

        // VOD
        btnPlayPause    = findViewById(R.id.btn_play_pause);
        btnRewind       = findViewById(R.id.btn_rewind);
        btnForward      = findViewById(R.id.btn_forward);
        btnAudio        = findViewById(R.id.btn_audio);
        btnSubs         = findViewById(R.id.btn_subs);
        btnPip          = findViewById(R.id.btn_pip);
        btnStopVod      = findViewById(R.id.btn_stop_vod);

        // Seek
        seekBar         = findViewById(R.id.seek_bar);
        tvPosition      = findViewById(R.id.tv_position);
        tvDuration      = findViewById(R.id.tv_duration);

        playerView.setPadding(0, 0, 0, 0);
        tvName.setText(item.name);
        updateFavBtn();

        // Listeners comunes
        btnBack.setOnClickListener(v -> exitPlayer());
        btnFav.setOnClickListener(v -> { prefs.toggleFav(item.favKey()); updateFavBtn(); });

        // Live
        btnPrev.setOnClickListener(v -> navigateChannel(-1));
        btnNext.setOnClickListener(v -> navigateChannel(1));
        btnPipLive.setOnClickListener(v -> enterPip());
        btnStop.setOnClickListener(v -> exitPlayer());

        // VOD/Series
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> {
            if (player != null)
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
        });
        btnForward.setOnClickListener(v -> {
            if (player != null)
                player.seekTo(Math.min(player.getDuration(),
                    player.getCurrentPosition() + 10000));
        });
        btnAudio.setOnClickListener(v -> showAudioTracks());
        btnSubs.setOnClickListener(v -> showSubtitleTracks());
        btnPip.setOnClickListener(v -> enterPip());
        btnStopVod.setOnClickListener(v -> exitPlayer());

        // SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && player != null)
                    tvPosition.setText(formatTime((long)(p / 100.0 * player.getDuration())));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                seekBarTracking = true;
                handler.removeCallbacks(seekUpdateRunnable);
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                seekBarTracking = false;
                if (player != null)
                    player.seekTo((long)(sb.getProgress() / 100.0 * player.getDuration()));
                handler.post(seekUpdateRunnable);
            }
        });

        setupButtonsForType();

        playerView.setOnClickListener(v -> { if (!isTv && !isInPip) toggleBars(); });
        playerView.setOnTouchListener(this::onTouch);
    }

    private void setupButtonsForType() {
        boolean isLive = item.type.equals(MediaItem.LIVE);
        if (item.type.equals(MediaItem.VOD)) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        } else {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        }
        layoutLiveBtns.setVisibility(isLive ? View.VISIBLE : View.GONE);
        layoutVodBtns.setVisibility(isLive ? View.GONE : View.VISIBLE);
        layoutSeek.setVisibility(isLive ? View.GONE : View.VISIBLE);
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            btnPlayPause.setText("▶");
        } else {
            player.play();
            btnPlayPause.setText("⏸");
        }
    }

    private void showAudioTracks() {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        List<String> names = new ArrayList<>();
        List<TrackGroup> groups = new ArrayList<>();

        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < g.length; i++) {
                    androidx.media3.common.Format f = g.getTrackFormat(i);
                    String lang = f.language != null ? f.language.toUpperCase() : "Audio " + (groups.size() + 1);
                    String label = f.label != null ? f.label : lang;
                    names.add(label);
                    groups.add(g.getMediaTrackGroup());
                }
            }
        }

        if (names.isEmpty()) {
            Toast.makeText(this, "Sin pistas de audio disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Seleccionar audio")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                TrackGroup tg = groups.get(which);
                player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters().buildUpon()
                        .setOverrideForType(new TrackSelectionOverride(tg, 0))
                        .build());
                btnAudio.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#00D4FF")));
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showSubtitleTracks() {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        List<String> names = new ArrayList<>();
        names.add("Desactivar");
        List<TrackGroup> groups = new ArrayList<>();
        groups.add(null);

        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < g.length; i++) {
                    androidx.media3.common.Format f = g.getTrackFormat(i);
                    String lang = f.language != null ? f.language.toUpperCase() : "Sub " + (groups.size());
                    String label = f.label != null ? f.label : lang;
                    names.add(label);
                    groups.add(g.getMediaTrackGroup());
                }
            }
        }

        if (names.size() == 1) {
            Toast.makeText(this, "Sin subtítulos disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Subtítulos")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                if (which == 0) {
                    // Desactivar
                    player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters().buildUpon()
                            .setDisabledTrackTypes(
                                com.google.common.collect.ImmutableSet.of(C.TRACK_TYPE_TEXT))
                            .build());
                    btnSubs.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#1a2535")));
                } else {
                    TrackGroup tg = groups.get(which);
                    player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters().buildUpon()
                            .setOverrideForType(new TrackSelectionOverride(tg, 0))
                            .build());
                    btnSubs.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#00D4FF")));
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void initPlayer() {
        if (player != null) { player.release(); player = null; }
        playerReleased = false;
        setStatus("Cargando...");
        btnPlayPause.setText("⏸");

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        String url = item.url;
        androidx.media3.common.MediaItem mi;
        if (url.contains(".m3u8") || url.contains("type=m3u")) {
            mi = new androidx.media3.common.MediaItem.Builder()
                .setUri(url)
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                .build();
        } else if (url.contains(".mkv") || url.contains(".avi") || url.contains(".ts")) {
            // Forzar tipo video para contenedores que ExoPlayer puede no detectar
            mi = new androidx.media3.common.MediaItem.Builder()
                .setUri(url)
                .setMimeType("video/x-matroska")
                .build();
        } else {
            mi = androidx.media3.common.MediaItem.fromUri(url);
        }

        player.setMediaItem(mi);
        player.prepare();
        player.setPlayWhenReady(true);
        player.addListener(makeListener());

        // Preguntar continuar VOD
        if (item.type.equals(MediaItem.VOD)) {
            long pos = prefs.getPos(item.id);
            long dur = prefs.getDur(item.id);
            if (pos > 10000 && dur > 0 && (int)(pos * 100 / dur) < 95) {
                askResume(pos);
            }
        }
    }

    private void askResume(long savedPos) {
        new AlertDialog.Builder(this)
            .setTitle("Continuar viendo")
            .setMessage("¿Deseas continuar desde donde lo dejaste?")
            .setPositiveButton("Continuar", (d, w) -> {
                if (player != null) player.seekTo(savedPos);
            })
            .setNegativeButton("Desde el inicio", (d, w) ->
                prefs.saveProgress(item.id, 0, 0))
            .setCancelable(false)
            .show();
    }

    private Player.Listener makeListener() {
        return new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_READY) {
                    setStatus(item.type.equals(MediaItem.LIVE) ? "EN VIVO" : "");
                    retryCount = 0;
                    if (!isTv && !barsVisible) showBars();
                    if (!item.type.equals(MediaItem.LIVE))
                        handler.post(seekUpdateRunnable);
                }
                if (s == Player.STATE_BUFFERING) setStatus("Cargando...");
                if (s == Player.STATE_ENDED)     handler.removeCallbacks(seekUpdateRunnable);
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (btnPlayPause != null)
                    btnPlayPause.setText(isPlaying ? "⏸" : "▶");
            }
            @Override public void onPlayerError(@NonNull PlaybackException e) {
                if (item.type.equals(MediaItem.LIVE) && retryCount < 3) {
                    retryCount++;
                    setStatus("Reconectando " + retryCount + "/3...");
                    handler.postDelayed(() -> initPlayer(), 3000);
                } else setStatus("Error");
            }
            @Override public void onVideoSizeChanged(@NonNull VideoSize vs) {
                if (vs.width > 0 && vs.height > 0) {
                    String q = vs.height >= 2160 ? "4K" :
                               vs.height >= 1080 ? "FHD" :
                               vs.height >= 720  ? "HD" : "SD";
                    runOnUiThread(() -> {
                        tvResolution.setText(vs.width + "x" + vs.height + " " + q);
                        tvResolution.setVisibility(View.VISIBLE);
                    });
                }
            }
        };
    }

    private void updateSeekBar() {
        if (player == null || seekBar == null || seekBarTracking) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        if (dur > 0) {
            seekBar.setProgress((int)(pos * 100 / dur));
            tvPosition.setText(formatTime(pos));
            tvDuration.setText(formatTime(dur));
        }
    }

    private String formatTime(long ms) {
        long s = ms / 1000, m = s / 60; s %= 60;
        long h = m / 60; m %= 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s)
                     : String.format("%d:%02d", m, s);
    }

    private void navigateChannel(int dir) {
        if (state.channelList.isEmpty() || state.channelIdx < 0) return;
        int next = state.channelIdx + dir;
        if (next < 0) next = state.channelList.size() - 1;
        if (next >= state.channelList.size()) next = 0;
        state.channelIdx = next;
        item = state.channelList.get(next);
        tvName.setText(item.name);
        tvResolution.setVisibility(View.GONE);
        updateFavBtn();
        isInPip = false;
        playerView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            initPlayer();
            playerView.animate().alpha(1f).setDuration(300).start();
        }).start();
    }

    private void toggleBars() { if (barsVisible) hideBars(); else showBars(); }

    private void showBars() {
        barsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        if (!isTv) {
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(this::hideBars, 4000);
            if (!item.type.equals(MediaItem.LIVE))
                handler.post(seekUpdateRunnable);
        }
    }

    private void hideBars() {
        if (isTv) return;
        barsVisible = false;
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
    }

    private void setStatus(String s) { runOnUiThread(() -> tvStatus.setText(s)); }
    private void updateFavBtn() { btnFav.setText(prefs.isFav(item.favKey()) ? "★" : "☆"); }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                topBar.setVisibility(View.GONE);
                bottomBar.setVisibility(View.GONE);
                handler.removeCallbacksAndMessages(null);
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9)).build();
                enterPictureInPictureMode(params);
            } catch (Exception e) {
                Toast.makeText(this, "PiP no disponible", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration conf) {
        super.onPictureInPictureModeChanged(inPip, conf);
        isInPip = inPip;
        if (inPip) {
            requestClose = false;
            handler.postDelayed(pipCheckRunnable, 500);
        } else {
            handler.removeCallbacks(pipCheckRunnable);
            if (isFinishing()) {
                exitPlayer();
            } else {
                setFullscreen();
                hideSystemBars();
                showBars();
                if (player != null && !player.isPlaying()) player.play();
            }
        }
    }

    private void saveProgress() {
        if (player != null && item != null && !item.type.equals(MediaItem.LIVE))
            prefs.saveProgress(item.id, player.getCurrentPosition(), player.getDuration());
    }

    private void exitPlayer() { {
        if (playerReleased) return;
        playerReleased = true;
        handler.removeCallbacks(seekUpdateRunnable);
        handler.removeCallbacks(pipCheckRunnable);
        handler.removeCallbacksAndMessages(null);
        saveProgress();
        if (player != null) {
            player.setPlayWhenReady(false);
            player.stop();
            player.clearMediaItems();
            player.release();
            player = null;
        }
        if (playerView != null) playerView.setPlayer(null);
        if (!isFinishing()) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean onTouch(View v, MotionEvent e) {
        if (isInPip) return false;
        float w = v.getWidth(), h = v.getHeight();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                gestStartX = e.getX(); gestStartY = e.getY();
                gestActive = false;
                gestIsVol    = gestStartX < w / 3f;
                gestIsBright = gestStartX > w * 2f / 3f;
                gestIsSeek   = !gestIsVol && !gestIsBright;
                if (gestIsVol)
                    gestStartVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (gestIsBright) {
                    float b = getWindow().getAttributes().screenBrightness;
                    if (b < 0) try {
                        b = Settings.System.getInt(getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS) / 255f;
                    } catch (Exception ignored) { b = 0.5f; }
                    gestStartBright = b;
                }
                if (gestIsSeek && player != null) seekStartPos = player.getCurrentPosition();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - gestStartX;
                float dy = e.getY() - gestStartY;
                if (!gestActive && (Math.abs(dx) > 20 || Math.abs(dy) > 20)) gestActive = true;
                if (!gestActive) break;
                if (gestIsVol && Math.abs(dy) > Math.abs(dx)) {
                    int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int vol = Math.max(0, Math.min(max, (int)(gestStartVol - dy / h * max)));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                } else if (gestIsBright && Math.abs(dy) > Math.abs(dx)) {
                    float bright = Math.max(0.01f, Math.min(1f, gestStartBright - dy / h));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = bright;
                    getWindow().setAttributes(lp);
                } else if (gestIsSeek && Math.abs(dx) > Math.abs(dy) && player != null
                           && !item.type.equals(MediaItem.LIVE)) {
                    long pos = Math.max(0, Math.min(player.getDuration(),
                        seekStartPos + (long)(dx / w * 120000)));
                    player.seekTo(pos);
                }
                break;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (!isTv || e.getAction() != android.view.KeyEvent.ACTION_DOWN)
            return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePlayPause(); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
            case android.view.KeyEvent.KEYCODE_MEDIA_REWIND:
                if (player != null && !item.type.equals(MediaItem.LIVE))
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                else navigateChannel(-1); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
            case android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (player != null && !item.type.equals(MediaItem.LIVE))
                    player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 10000));
                else navigateChannel(1); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_UP:   navigateChannel(-1); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: navigateChannel(1);  return true;
            case android.view.KeyEvent.KEYCODE_BACK:
            case android.view.KeyEvent.KEYCODE_MEDIA_STOP:
                exitPlayer(); return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onPause() {
        super.onPause();
        boolean inPipNow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && isInPictureInPictureMode();
        if (!inPipNow && !playerReleased && player != null) player.pause();
    }

    @Override protected void onResume() {
        super.onResume();
        setFullscreen();
        hideSystemBars();
        if (!isInPip && player != null && !playerReleased) player.play();
    }

    @Override protected void onStop() {
        super.onStop();
        saveProgress();
        if (isInPip && !isFinishing()) exitPlayer();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (pipCloseReceiver != null) {
            try { unregisterReceiver(pipCloseReceiver); } catch (Exception ignored) {}
        }
        if (!playerReleased) exitPlayer();
    }

    @Override public void onBackPressed() {
        boolean inPipNow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && isInPictureInPictureMode();
        if (isInPip || inPipNow) { moveTaskToBack(false); return; }
        exitPlayer();
    }
}
