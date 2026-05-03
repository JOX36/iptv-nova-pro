package com.jox3.tv;

import android.app.Application;
import android.content.res.Configuration;

import com.jox3.tv.util.ThemeManager;

public class JOX3App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Aplicar skin al iniciar la app
        ThemeManager.Skin skin = ThemeManager.getSkin(this);
        applyColors(skin);
    }

    private void applyColors(ThemeManager.Skin skin) {
        // Los colores se aplican via ThemeManager.getSkin()
        // que está disponible globalmente en toda la app
        ThemeManager.active = skin;
    }
}
