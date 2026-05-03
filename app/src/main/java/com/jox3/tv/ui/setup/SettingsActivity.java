package com.jox3.tv.ui.setup;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jox3.tv.ui.BaseActivity;

import com.jox3.tv.R;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;
import com.jox3.tv.util.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends BaseActivity {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        applyTheme();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        setupAccountInfo();
        setupSkins();
        setupActions();
    }

    private void setupAccountInfo() {
        AppState state = AppState.get();
        if (state.account == null) return;

        TextView tvHost        = findViewById(R.id.tv_host);
        TextView tvUser        = findViewById(R.id.tv_user);
        TextView tvExpiry      = findViewById(R.id.tv_expiry);
        TextView tvDays        = findViewById(R.id.tv_days);
        TextView tvConnections = findViewById(R.id.tv_connections);

        tvHost.setText(state.account.displayHost());
        tvUser.setText(state.account.user);
        tvExpiry.setText("Cargando...");
        tvDays.setText("...");
        tvConnections.setText("...");

        // Cargar info de cuenta desde el API
        exec.execute(() -> {
            try {
                String json = state.api.get(
                    state.account.host + "/player_api.php?username=" +
                    state.account.user + "&password=" + state.account.pass);

                com.google.gson.JsonObject root =
                    com.google.gson.JsonParser.parseString(json).getAsJsonObject();

                com.google.gson.JsonObject userInfo = root.has("user_info") ?
                    root.getAsJsonObject("user_info") : null;

                if (userInfo != null) {
                    // Fecha vencimiento
                    String expTimestamp = userInfo.has("exp_date") ?
                        userInfo.get("exp_date").getAsString() : "";
                    String expDate = "Sin fecha";
                    long daysLeft = 0;

                    if (!expTimestamp.isEmpty() && !expTimestamp.equals("null")) {
                        try {
                            long exp = Long.parseLong(expTimestamp) * 1000;
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                            expDate = sdf.format(new Date(exp));
                            daysLeft = (exp - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                        } catch (Exception ignored) {}
                    }

                    // Conexiones
                    String maxCon     = userInfo.has("max_connections") ?
                        userInfo.get("max_connections").getAsString() : "?";
                    String activeCon  = userInfo.has("active_cons") ?
                        userInfo.get("active_cons").getAsString() : "0";

                    final String finalExpDate = expDate;
                    final long finalDays = daysLeft;
                    final String finalCon = activeCon + "/" + maxCon;

                    mainHandler.post(() -> {
                        tvExpiry.setText(finalExpDate);
                        tvDays.setText(finalDays + " días");
                        tvDays.setTextColor(finalDays < 7 ?
                            Color.parseColor("#EF4444") :
                            finalDays < 30 ?
                            Color.parseColor("#FFC107") :
                            Color.parseColor("#00FF88"));
                        tvConnections.setText(finalCon);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvExpiry.setText("No disponible");
                    tvDays.setText("—");
                    tvConnections.setText("—");
                });
            }
        });
    }

    private void setupSkins() {
        LinearLayout container = findViewById(R.id.skin_container);
        if (container == null) return;

        String currentId = ThemeManager.getCurrentId(this);

        for (ThemeManager.Skin skin : ThemeManager.SKINS) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(4, 0, 4, 0);
            card.setLayoutParams(params);

            // Preview colores
            LinearLayout preview = new LinearLayout(this);
            preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48));
            preview.setOrientation(LinearLayout.HORIZONTAL);

            // Fondo
            View bgView = new View(this);
            bgView.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            bgView.setBackgroundColor(skin.bg);

            // Accent
            View accentView = new View(this);
            accentView.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            accentView.setBackgroundColor(skin.accent);

            // Accent2
            View accent2View = new View(this);
            accent2View.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            accent2View.setBackgroundColor(skin.accent2);

            preview.addView(bgView);
            preview.addView(accentView);
            preview.addView(accent2View);

            // Borde si está seleccionado
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.TRANSPARENT);
            if (skin.id.equals(currentId)) {
                border.setStroke(2, skin.accent);
            }
            preview.setBackground(border);

            // Nombre
            TextView tvName = new TextView(this);
            tvName.setText(skin.emoji + " " + skin.name);
            tvName.setTextSize(9);
            tvName.setTextColor(skin.id.equals(currentId) ? skin.accent : 0xFF888888);
            tvName.setGravity(android.view.Gravity.CENTER);
            tvName.setPadding(0, 4, 0, 0);

            card.addView(preview);
            card.addView(tvName);

            card.setOnClickListener(v -> {
                ThemeManager.setSkin(this, skin.id);
                // Reiniciar app completa para aplicar colores
                android.content.Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(getPackageName());
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK |
                                   android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                android.os.Process.killProcess(android.os.Process.myPid());
            });

            container.addView(card);
        }
    }

    private void setupActions() {
        // Versión
        TextView tvVersion = findViewById(R.id.tv_version);
        if (tvVersion != null) {
            try {
                String v = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
                tvVersion.setText("v" + v);
            } catch (Exception e) {
                tvVersion.setText("v1.0");
            }
        }

        // Cambiar cuenta
        View btnChange = findViewById(R.id.btn_change_account);
        if (btnChange != null) {
            btnChange.setOnClickListener(v -> {
                AppState.reset();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }

        // Limpiar indexación
        View btnClear = findViewById(R.id.btn_clear_index);
        if (btnClear != null) {
            btnClear.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Limpiar indexación")
                    .setMessage("Se borrará el índice de búsqueda. La app lo recargará al iniciar.")
                    .setPositiveButton("Limpiar", (d, w) -> {
                        for (com.jox3.tv.model.Category c : AppState.get().liveCats)   { c.items = null; c.loaded = false; }
                        for (com.jox3.tv.model.Category c : AppState.get().vodCats)    { c.items = null; c.loaded = false; }
                        for (com.jox3.tv.model.Category c : AppState.get().seriesCats) { c.items = null; c.loaded = false; }
                        Toast.makeText(this, "Indexación limpiada", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show()
            );
        }
    }
}
