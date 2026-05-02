package com.jox3.tv.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ThemeManager {

    private static final String PREFS = "jox3_theme";
    private static final String KEY   = "skin";

    public static final String SKIN_OCEAN  = "ocean";
    public static final String SKIN_MATRIX = "matrix";
    public static final String SKIN_FIRE   = "fire";
    public static final String SKIN_CYBER  = "cyber";
    public static final String SKIN_MINIMAL= "minimal";

    public static class Skin {
        public String id, name, emoji;
        public int bg, bg2, bg3, border, accent, accent2, yellow, red, text, muted, muted2;

        public Skin(String id, String name, String emoji,
                    String bg, String bg2, String bg3, String border,
                    String accent, String accent2, String yellow, String red,
                    String text, String muted, String muted2) {
            this.id = id; this.name = name; this.emoji = emoji;
            this.bg      = Color.parseColor(bg);
            this.bg2     = Color.parseColor(bg2);
            this.bg3     = Color.parseColor(bg3);
            this.border  = Color.parseColor(border);
            this.accent  = Color.parseColor(accent);
            this.accent2 = Color.parseColor(accent2);
            this.yellow  = Color.parseColor(yellow);
            this.red     = Color.parseColor(red);
            this.text    = Color.parseColor(text);
            this.muted   = Color.parseColor(muted);
            this.muted2  = Color.parseColor(muted2);
        }
    }

    public static final Skin[] SKINS = {
        new Skin(SKIN_OCEAN, "Océano", "🌊",
            "#0a0e14","#0d1219","#121820","#1a2535",
            "#00D4FF","#00FF88","#FFC107","#EF4444",
            "#E0F4FF","#4A7A8A","#7AB8CC"),
        new Skin(SKIN_MATRIX, "Matrix", "💚",
            "#000000","#001a00","#002200","#003300",
            "#00FF41","#39FF14","#CCFF00","#FF0040",
            "#CCFFCC","#1a5c1a","#33aa33"),
        new Skin(SKIN_FIRE, "Fuego", "🔥",
            "#0d0500","#150800","#1a0a00","#2a1200",
            "#FF6600","#FF3300","#FFD700","#FF1100",
            "#FFE8CC","#7A3A00","#CC7A00"),
        new Skin(SKIN_CYBER, "Cyber", "⚡",
            "#000d0a","#001410","#001a14","#002a1e",
            "#00FFD1","#7FFF00","#F0FF00","#FF3366",
            "#E0FFF8","#1a6655","#33ccaa"),
        new Skin(SKIN_MINIMAL, "Minimal", "◻",
            "#0a0a0a","#111111","#1a1a1a","#2a2a2a",
            "#FFFFFF","#CCCCCC","#FFD700","#FF4444",
            "#FFFFFF","#555555","#999999"),
    };

    private static Skin current = null;

    public static Skin getSkin(Context ctx) {
        if (current != null) return current;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = sp.getString(KEY, SKIN_OCEAN);
        current = findSkin(id);
        return current;
    }

    public static void setSkin(Context ctx, String id) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, id).apply();
        current = findSkin(id);
    }

    public static Skin findSkin(String id) {
        for (Skin s : SKINS) if (s.id.equals(id)) return s;
        return SKINS[0];
    }

    public static String getCurrentId(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SKIN_OCEAN);
    }

    /**
     * Aplica el skin a toda la Activity — reinicia la Activity para reflejar cambios
     */
    public static void applyAndRestart(Activity activity, String skinId) {
        setSkin(activity, skinId);
        activity.recreate(); // Reinicia la Activity aplicando el nuevo tema
    }
}
