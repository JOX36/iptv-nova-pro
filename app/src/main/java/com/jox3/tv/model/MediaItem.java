package com.jox3.tv.model;

import java.io.Serializable;

public class MediaItem implements Serializable {
    public static final String LIVE   = "live";
    public static final String VOD    = "vod";
    public static final String SERIES = "series";

    public String id;
    public String name;
    public String logo;
    public String cover;
    public String url;
    public String group;
    public String type;
    public String rating;
    public String plot;
    public String genre;
    public String year;
    public String cast;
    public String director;
    public String duration;
    public boolean isFav;
    public int seasons; // número de temporadas para series

    public MediaItem() {}

    public MediaItem(String id, String name, String logo, String url, String group, String type) {
        this.id = id; this.name = name; this.logo = logo;
        this.url = url; this.group = group; this.type = type;
    }

    public String quality() {
        if (name == null) return "";
        String n = name.toUpperCase();
        if (n.contains("4K") || n.contains("UHD") || n.contains("2160")) return "4K";
        if (n.contains("FHD") || n.contains("1080")) return "FHD";
        if (n.contains("HD")  || n.contains("720"))  return "HD";
        if (n.contains("SD")  || n.contains("480"))  return "SD";
        return "";
    }

    public String thumb() {
        if (cover != null && !cover.isEmpty()) return cover;
        return logo != null ? logo : "";
    }

    public String favKey() { return type + "_" + id; }
}
