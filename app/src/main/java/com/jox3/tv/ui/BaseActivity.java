package com.jox3.tv.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.jox3.tv.util.ThemeManager;

public abstract class BaseActivity extends AppCompatActivity {

    protected ThemeManager.Skin skin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        skin = ThemeManager.getSkin(this);
    }

    /**
     * Llama esto DESPUÉS de setContentView() para aplicar colores
     */
    protected void applyTheme() {
        View root = getWindow().getDecorView().getRootView();
        applyToTree(root);
    }

    private void applyToTree(View v) {
        if (v == null) return;

        // Fondo por tag
        Object tag = v.getTag();
        if (tag != null) {
            switch (tag.toString()) {
                case "bg":     v.setBackgroundColor(skin.bg);     break;
                case "bg2":    v.setBackgroundColor(skin.bg2);    break;
                case "bg3":    v.setBackgroundColor(skin.bg3);    break;
                case "accent": v.setBackgroundColor(skin.accent); break;
                case "border": v.setBackgroundColor(skin.border); break;
            }
        }

        // TextView colores por tag
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            if (tag != null) {
                switch (tag.toString()) {
                    case "text_accent":  tv.setTextColor(skin.accent);  break;
                    case "text_accent2": tv.setTextColor(skin.accent2); break;
                    case "text_muted":   tv.setTextColor(skin.muted);   break;
                    case "text_muted2":  tv.setTextColor(skin.muted2);  break;
                    case "text_main":    tv.setTextColor(skin.text);    break;
                    case "text_yellow":  tv.setTextColor(skin.yellow);  break;
                    case "text_red":     tv.setTextColor(skin.red);     break;
                }
            }
        }

        // CardView
        if (v instanceof CardView) {
            ((CardView) v).setCardBackgroundColor(skin.bg2);
        }

        // ProgressBar tint
        if (v instanceof ProgressBar) {
            android.graphics.PorterDuff.Mode mode = android.graphics.PorterDuff.Mode.SRC_IN;
            ((ProgressBar)v).getProgressDrawable()
                .setColorFilter(skin.accent, mode);
        }

        // Recorrer hijos
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyToTree(vg.getChildAt(i));
            }
        }
    }
}
