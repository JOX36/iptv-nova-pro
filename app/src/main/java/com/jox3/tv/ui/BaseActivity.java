package com.jox3.tv.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.jox3.tv.util.ThemeManager;

public abstract class BaseActivity extends AppCompatActivity {

    protected ThemeManager.Skin skin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        skin = ThemeManager.getSkin(this);
        // Aplicar color de fondo de la ventana
        getWindow().getDecorView().setBackgroundColor(skin.bg);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-aplicar skin por si cambió
        ThemeManager.Skin newSkin = ThemeManager.getSkin(this);
        if (!newSkin.id.equals(skin.id)) {
            skin = newSkin;
            recreate();
        }
    }

    /**
     * Aplica el skin recursivamente a todas las vistas del layout
     * Llama esto después de setContentView
     */
    protected void applyTheme(View root) {
        if (root == null) return;
        applyToView(root);
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTheme(vg.getChildAt(i));
            }
        }
    }

    private void applyToView(View v) {
        String tag = v.getTag() != null ? v.getTag().toString() : "";
        if (tag.equals("bg"))     v.setBackgroundColor(skin.bg);
        if (tag.equals("bg2"))    v.setBackgroundColor(skin.bg2);
        if (tag.equals("bg3"))    v.setBackgroundColor(skin.bg3);
        if (tag.equals("accent")) v.setBackgroundColor(skin.accent);

        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            if (tag.equals("text_accent"))   tv.setTextColor(skin.accent);
            if (tag.equals("text_muted"))    tv.setTextColor(skin.muted);
            if (tag.equals("text_main"))     tv.setTextColor(skin.text);
        }
        if (v instanceof ProgressBar) {
            android.graphics.PorterDuff.Mode mode = android.graphics.PorterDuff.Mode.SRC_IN;
            ((ProgressBar)v).getProgressDrawable().setColorFilter(skin.accent, mode);
        }
    }
}

