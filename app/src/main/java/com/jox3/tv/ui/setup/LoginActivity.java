package com.jox3.tv.ui.setup;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.jox3.tv.R;
import com.jox3.tv.adapter.AccountAdapter;
import com.jox3.tv.model.Account;
import com.jox3.tv.ui.home.MainActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.net.URL;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etHost, etUser, etPass;
    private TextView tvError;
    private AppPrefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs   = new AppPrefs(this);
        etHost  = findViewById(R.id.et_host);
        etUser  = findViewById(R.id.et_user);
        etPass  = findViewById(R.id.et_pass);
        tvError = findViewById(R.id.tv_error);

        Button btnLogin = findViewById(R.id.btn_login);
        btnLogin.setOnClickListener(v -> doLogin());

        // Cuentas guardadas
        List<Account> accounts = prefs.accounts();
        RecyclerView rv = findViewById(R.id.rv_accounts);
        TextView tvTitle = findViewById(R.id.tv_saved_title);
        if (!accounts.isEmpty()) {
            tvTitle.setVisibility(View.VISIBLE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(this));
            rv.setAdapter(new AccountAdapter(accounts,
                acc -> {
                    etHost.setText(acc.host);
                    etUser.setText(acc.user);
                    etPass.setText(acc.pass);
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
        String hostRaw = etHost.getText() != null ? etHost.getText().toString().trim() : "";
        String user    = etUser.getText() != null ? etUser.getText().toString().trim() : "";
        String pass    = etPass.getText() != null ? etPass.getText().toString().trim() : "";

        // Parsear URL completa si se pega
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

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}
