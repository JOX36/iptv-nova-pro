package com.jox3.tv.util;

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

    public MediaItem current;
    public List<MediaItem> channelList = new ArrayList<>();
    public int channelIdx = -1;

    public void setAccount(Account a) {
        account = a;
        api.setAccount(a);
        liveCats.clear();
        vodCats.clear();
        seriesCats.clear();
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
