package com.jox3.tv.ui.player;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Intent;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.jox3.tv.R;
import com.jox3.tv.api.XtreamApi;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout topBar, bottomBar;
    private TextView tvName, tvResolution, tvStatus;
    private ImageButton btnBack, btnFav, btnPrev, btnNext, btnPip;

    private com.jox3.tv.model.MediaItem item;
    private AppPrefs prefs;
    private AppState state;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean barsVisible = false;
    private boolean isTv = false;
    private int retryCount = 0;

    // Gestos
    private AudioManager audioManager;
    private float gestStartX, gestStartY;
    private boolean gestActive, gestIsVol, gestIsBright, gestIsSeek;
    private int gestStartVol;
    private float gestStartBright;
    private long seekStartPos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_player);

        prefs = new AppPrefs(this);
        state = AppState.get();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        isTv = getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK);

        item = (com.jox3.tv.model.MediaItem) getIntent().getSerializableExtra("item");
        if (item == null) { finish(); return; }

        initViews();
        initPlayer();
        if (isTv) showBars(); // TV: siempre visible
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        playerView  = findViewById(R.id.player_view);
        topBar      = findViewById(R.id.top_bar);
        bottomBar   = findViewById(R.id.bottom_bar);
        tvName      = findViewById(R.id.tv_name);
        tvResolution= findViewById(R.id.tv_resolution);
        tvStatus    = findViewById(R.id.tv_status);
        btnBack     = findViewById(R.id.btn_back);
        btnFav      = findViewById(R.id.btn_fav);
        btnPrev     = findViewById(R.id.btn_prev);
        btnNext     = findViewById(R.id.btn_next);
        btnPip      = findViewById(R.id.btn_pip);

        tvName.setText(item.name);
        updateFavBtn();

        btnBack.setOnClickListener(v -> finish());
        btnFav.setOnClickListener(v -> { prefs.toggleFav(item.favKey()); updateFavBtn(); });
        btnPrev.setOnClickListener(v -> navigateChannel(-1));
        btnNext.setOnClickListener(v -> navigateChannel(1));
        btnPip.setOnClickListener(v -> enterPip());

        // Tap para mostrar barras
        playerView.setOnClickListener(v -> { if (isTv) return; toggleBars(); });

        // Gestos
        playerView.setOnTouchListener(this::onTouch);
    }

    private void initPlayer() {
        if (player != null) { player.release(); player = null; }
        setStatus("⏳ Cargando…");

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        String url = item.url;
        androidx.media3.common.MediaItem mi;
        if (url.endsWith(".m3u8")) {
            mi = new androidx.media3.common.MediaItem.Builder().setUri(url)
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build();
        } else {
            mi = androidx.media3.common.MediaItem.fromUri(url);
        }
        player.setMediaItem(mi);
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_READY) {
                    setStatus(item.type.equals(com.jox3.tv.model.MediaItem.LIVE)
                        ? "🔴 EN VIVO" : "▶ VOD");
                    retryCount = 0;
                }
                if (s == Player.STATE_BUFFERING) setStatus("⏳ Buffering…");
            }
            @Override public void onPlayerError(@NonNull PlaybackException e) {
                if (item.type.equals(com.jox3.tv.model.MediaItem.LIVE) && retryCount < 3) {
                    retryCount++;
                    setStatus("🔄 Reconectando… (" + retryCount + "/3)");
                    handler.postDelayed(() -> initPlayer(), 3000);
                } else {
                    setStatus("❌ Error");
                }
            }
            @Override public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize vs) {
                if (vs.width > 0 && vs.height > 0) {
                    String q = vs.height >= 2160 ? "4K" : vs.height >= 1080 ? "FHD" :
                               vs.height >= 720  ? "HD" : "SD";
                    runOnUiThread(() -> {
                        tvResolution.setText(vs.width + "×" + vs.height + " " + q);
                        tvResolution.setVisibility(View.VISIBLE);
                    });
                }
            }
        });
    }

    private void navigateChannel(int dir) {
        if (state.channelList.isEmpty() || state.channelIdx < 0) return;
        int next = state.channelIdx + dir;
        if (next < 0) next = state.channelList.size() - 1;
        if (next >= state.channelList.size()) next = 0;
        state.channelIdx = next;
        item = state.channelList.get(next);
        tvName.setText(item.name);
        updateFavBtn();
        playerView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            initPlayer();
            playerView.animate().alpha(1f).setDuration(300).start();
        }).start();
    }

    private void toggleBars() {
        if (barsVisible) hideBars(); else showBars();
    }

    private void showBars() {
        barsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        if (!isTv) {
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(this::hideBars, 4000);
        }
    }

    private void hideBars() {
        if (isTv) return;
        barsVisible = false;
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
    }

    private void setStatus(String s) {
        runOnUiThread(() -> tvStatus.setText(s));
    }

    private void updateFavBtn() {
        btnFav.setImageResource(prefs.isFav(item.favKey()) ?
            android.R.drawable.btn_star_big_on :
            android.R.drawable.btn_star_big_off);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9)).build());
        }
    }

    // ── Gestos ──
    @SuppressLint("ClickableViewAccessibility")
    private boolean onTouch(View v, MotionEvent e) {
        float w = v.getWidth(), h = v.getHeight();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                gestStartX = e.getX(); gestStartY = e.getY();
                gestActive = false;
                gestIsVol     = gestStartX < w / 3f;
                gestIsBright  = gestStartX > w * 2f / 3f;
                gestIsSeek    = !gestIsVol && !gestIsBright;
                if (gestIsVol)   gestStartVol    = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
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
                    int vol = (int)(gestStartVol - dy / h * max);
                    vol = Math.max(0, Math.min(max, vol));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                } else if (gestIsBright && Math.abs(dy) > Math.abs(dx)) {
                    float bright = gestStartBright - dy / h;
                    bright = Math.max(0.01f, Math.min(1f, bright));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = bright;
                    getWindow().setAttributes(lp);
                } else if (gestIsSeek && Math.abs(dx) > Math.abs(dy) && player != null) {
                    long seekMs = (long)(dx / w * 120000);
                    long pos = Math.max(0, Math.min(player.getDuration(), seekStartPos + seekMs));
                    player.seekTo(pos);
                }
                break;
        }
        return false;
    }

    // ── TV D-Pad ──
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (!isTv || e.getAction() != android.view.KeyEvent.ACTION_DOWN)
            return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) {
                    if (player.isPlaying()) player.pause();
                    else player.play();
                }
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
            case android.view.KeyEvent.KEYCODE_MEDIA_REWIND:
                if (player != null && !item.type.equals(com.jox3.tv.model.MediaItem.LIVE))
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                else navigateChannel(-1);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
            case android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (player != null && !item.type.equals(com.jox3.tv.model.MediaItem.LIVE))
                    player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 10000));
                else navigateChannel(1);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_UP:
                navigateChannel(-1); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                navigateChannel(1);  return true;
            case android.view.KeyEvent.KEYCODE_BACK:
                finish(); return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onPause()   { super.onPause();   if (player != null) player.pause(); }
    @Override protected void onResume()  { super.onResume();  if (player != null) player.play();  }
    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
