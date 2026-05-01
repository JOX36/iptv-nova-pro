package com.jox3.tv.ui.setup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.jox3.tv.R;
import com.jox3.tv.adapter.AccountAdapter;
import com.jox3.tv.api.M3uParser;
import com.jox3.tv.model.Account;
import com.jox3.tv.ui.home.MainActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etHost, etUser, etPass;
    private TextView tvError;
    private ProgressBar progressBar;
    private AppPrefs prefs;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs       = new AppPrefs(this);
        etHost      = findViewById(R.id.et_host);
        etUser      = findViewById(R.id.et_user);
        etPass      = findViewById(R.id.et_pass);
        tvError     = findViewById(R.id.tv_error);
        progressBar = findViewById(R.id.progress_bar);

        Button btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(v -> doLogin());

        List<Account> accounts = prefs.accounts();
        RecyclerView rv = findViewById(R.id.rv_accounts);
        TextView tvTitle = findViewById(R.id.tv_saved_title);
        if (!accounts.isEmpty()) {
            tvTitle.setVisibility(View.VISIBLE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(new AccountAdapter(accounts,
                acc -> {
                    etHost.setText(acc.isM3u() ? acc.m3uUrl : acc.host);
                    etUser.setText(acc.user != null ? acc.user : "");
                    etPass.setText(acc.pass != null ? acc.pass : "");
                },
                acc -> {
                    prefs.removeAccount(acc);
                    accounts.remove(acc);
                    rv.getAdapter().notifyDataSetChanged();
                    if (accounts.isEmpty()) {
                        tvTitle.setVisibility(View.GONE);
                        rv.setVisibility(View.GONE);
                    }
                }
            ));
        }
    }

    private void doLogin() {
        String input = etHost.getText() != null ? etHost.getText().toString().trim() : "";
        String user  = etUser.getText() != null ? etUser.getText().toString().trim() : "";
        String pass  = etPass.getText() != null ? etPass.getText().toString().trim() : "";

        if (input.isEmpty()) { showError("Ingresa una URL o servidor"); return; }

        // Detectar si es M3U o Xtream
        if (isM3uUrl(input)) {
            loginM3u(input);
        } else {
            loginXtream(input, user, pass);
        }
    }

    private boolean isM3uUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("get.php") || lower.contains(".m3u") ||
               lower.contains("type=m3u") || lower.contains("type=m3u_plus");
    }

    private void loginM3u(String m3uUrl) {
        // Intentar extraer user/pass de la URL M3U
        String user = "", pass = "", host = m3uUrl;
        try {
            URL u = new URL(m3uUrl);
            String q = u.getQuery();
            if (q != null) {
                for (String p : q.split("&")) {
                    if (p.startsWith("username=")) user = p.substring(9);
                    if (p.startsWith("password=")) pass = p.substring(9);
                }
            }
            host = u.getProtocol() + "://" + u.getHost() +
                   (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception ignored) {}

        Account acc = new Account(host, user, pass);
        acc.type   = "m3u";
        acc.m3uUrl = m3uUrl;

        showLoading(true);
        final String finalUser = user;
        final String finalPass = pass;

        exec.execute(() -> {
            try {
                String content = AppState.get().api.get(m3uUrl);
                M3uParser.ParseResult result = M3uParser.parse(content);

                mainHandler.post(() -> {
                    showLoading(false);
                    prefs.saveAccount(acc);
                    AppState.get().setAccount(acc);
                    AppState.get().setM3uData(result);
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    showError("Error cargando lista: " + e.getMessage());
                });
            }
        });
    }

    private void loginXtream(String hostRaw, String user, String pass) {
        String host = hostRaw;
        try {
            URL u = new URL(hostRaw);
            String q = u.getQuery();
            if (q != null && q.contains("username=")) {
                for (String p : q.split("&")) {
                    if (p.startsWith("username=")) user = p.substring(9);
                    if (p.startsWith("password=")) pass = p.substring(9);
                }
                host = u.getProtocol() + "://" + u.getHost() +
                       (u.getPort() > 0 ? ":" + u.getPort() : "");
            }
        } catch (Exception ignored) {}

        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showError("Completa todos los campos");
            return;
        }

        Account acc = new Account(host, user, pass);
        prefs.saveAccount(acc);
        AppState.get().setAccount(acc);

        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        if (tvError != null) {
            tvError.setText(msg);
            tvError.setVisibility(View.VISIBLE);
        }
    }
}
