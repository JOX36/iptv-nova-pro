package com.jox3.tv.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jox3.tv.model.Account;
import com.jox3.tv.model.MediaItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AppPrefs {
    private static final String P    = "jox3tv";
    private static final String ACCS = "accounts";
    private static final String LAST = "last";
    private static final String FAVS = "favs";
    private static final String HIST = "hist";

    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    public AppPrefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    // ── Cuentas ──
    public List<Account> accounts() {
        try { return gson.fromJson(sp.getString(ACCS, "[]"),
                new TypeToken<List<Account>>(){}.getType()); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    public void saveAccount(Account a) {
        List<Account> list = accounts();
        list.removeIf(x -> x.key().equals(a.key()));
        list.add(0, a);
        if (list.size() > 8) list = list.subList(0, 8);
        sp.edit().putString(ACCS, gson.toJson(list)).apply();
        sp.edit().putString(LAST, gson.toJson(a)).apply();
    }

    public void removeAccount(Account a) {
        List<Account> list = accounts();
        list.removeIf(x -> x.key().equals(a.key()));
        sp.edit().putString(ACCS, gson.toJson(list)).apply();
    }

    public Account lastAccount() {
        try { return gson.fromJson(sp.getString(LAST, null), Account.class); }
        catch (Exception e) { return null; }
    }

    // ── Favoritos ──
    public Set<String> favs() {
        return new LinkedHashSet<>(sp.getStringSet(FAVS, new LinkedHashSet<>()));
    }

    public boolean isFav(String key) { return favs().contains(key); }

    public boolean toggleFav(String key) {
        Set<String> f = favs();
        boolean added;
        if (f.contains(key)) { f.remove(key); added = false; }
        else                 { f.add(key);    added = true;  }
        sp.edit().putStringSet(FAVS, f).apply();
        return added;
    }

    // ── Historial ──
    public List<MediaItem> history() {
        try { return gson.fromJson(sp.getString(HIST, "[]"),
                new TypeToken<List<MediaItem>>(){}.getType()); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    public void addHistory(MediaItem item) {
        List<MediaItem> h = history();
        h.removeIf(x -> x.id != null && x.id.equals(item.id) &&
                         x.type != null && x.type.equals(item.type));
        h.add(0, item);
        if (h.size() > 60) h = h.subList(0, 60);
        sp.edit().putString(HIST, gson.toJson(h)).apply();
    }

    // ── Progreso VOD ──
    public void saveProgress(String id, long pos, long dur) {
        sp.edit().putLong("p_" + id, pos).putLong("d_" + id, dur).apply();
    }

    public long getPos(String id) { return sp.getLong("p_" + id, 0); }
    public long getDur(String id) { return sp.getLong("d_" + id, 0); }

    public int progressPct(String id) {
        long d = getDur(id);
        return d > 0 ? (int)(getPos(id) * 100 / d) : 0;
    }

    // ── Caché Home — para mostrar contenido instantáneamente ──
    public void saveCachedHome(String type, List<MediaItem> items) {
        try {
            sp.edit().putString("home_" + type, gson.toJson(items)).apply();
        } catch (Exception ignored) {}
    }

    public List<MediaItem> getCachedHome(String type) {
        try {
            String json = sp.getString("home_" + type, null);
            if (json == null) return new ArrayList<>();
            return gson.fromJson(json, new TypeToken<List<MediaItem>>(){}.getType());
        } catch (Exception e) { return new ArrayList<>(); }
    }
}
