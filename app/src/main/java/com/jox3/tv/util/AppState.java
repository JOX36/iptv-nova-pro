package com.jox3.tv.util;

import com.jox3.tv.api.M3uParser;
import com.jox3.tv.api.XtreamApi;
import com.jox3.tv.model.Account;
import com.jox3.tv.model.Category;
import com.jox3.tv.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

public class AppState {
    private static AppState I;
    public static AppState get() { if (I == null) I = new AppState(); return I; }
    public static void reset()   { I = null; }

    public Account account;
    public XtreamApi api = new XtreamApi();

    public List<Category> liveCats   = new ArrayList<>();
    public List<Category> vodCats    = new ArrayList<>();
    public List<Category> seriesCats = new ArrayList<>();

    // Datos M3U indexados por categoría
    public M3uParser.ParseResult m3uData = null;

    public MediaItem current;
    public List<MediaItem> channelList = new ArrayList<>();
    public int channelIdx = -1;

    public void setAccount(Account a) {
        account = a;
        api.setAccount(a);
        liveCats.clear();
        vodCats.clear();
        seriesCats.clear();
        m3uData = null;
    }

    public void setM3uData(M3uParser.ParseResult data) {
        m3uData = data;
        // Poblar las listas de categorías desde M3U
        liveCats.clear();
        vodCats.clear();
        seriesCats.clear();
        liveCats.addAll(data.liveCats);
        vodCats.addAll(data.vodCats);
        seriesCats.addAll(data.seriesCats);

        // Marcar items como cargados
        for (Category c : liveCats) {
            String group = c.name;
            if (data.liveItems.containsKey(group)) {
                c.items = data.liveItems.get(group);
                c.loaded = true;
            }
        }
        for (Category c : vodCats) {
            String group = c.name;
            if (data.vodItems.containsKey(group)) {
                c.items = data.vodItems.get(group);
                c.loaded = true;
            }
        }
        for (Category c : seriesCats) {
            String group = c.name;
            if (data.seriesItems.containsKey(group)) {
                c.items = data.seriesItems.get(group);
                c.loaded = true;
            }
        }
    }

    public boolean isM3u() {
        return account != null && account.isM3u();
    }

    public List<Category> cats(String type) {
        switch (type) {
            case MediaItem.LIVE:   return liveCats;
            case MediaItem.VOD:    return vodCats;
            case MediaItem.SERIES: return seriesCats;
            default: return new ArrayList<>();
        }
    }
}
