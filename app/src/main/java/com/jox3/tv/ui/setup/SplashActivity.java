package com.jox3.tv.ui.setup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.jox3.tv.R;
import com.jox3.tv.model.Account;
import com.jox3.tv.ui.home.MainActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView logo    = findViewById(R.id.splash_logo);
        TextView tagline = findViewById(R.id.splash_tagline);

        // Animación fade + scale
        AnimationSet anim = new AnimationSet(true);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(900);
        android.view.animation.ScaleAnimation scale =
            new android.view.animation.ScaleAnimation(0.85f, 1f, 0.85f, 1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(900);
        scale.setInterpolator(new DecelerateInterpolator());
        anim.addAnimation(fade);
        anim.addAnimation(scale);
        logo.startAnimation(anim);

        tagline.setAlpha(0f);
        new Handler(Looper.getMainLooper()).postDelayed(() ->
            tagline.animate().alpha(1f).setDuration(500).start(), 600);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AppPrefs prefs = new AppPrefs(this);
            Account last = prefs.lastAccount();
            Intent intent;
            if (last != null) {
                AppState.get().setAccount(last);
                intent = new Intent(this, MainActivity.class);
            } else {
                intent = new Intent(this, LoginActivity.class);
            }
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 2200);
    }
}
