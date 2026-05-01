package com.jox3.tv.api;

import com.jox3.tv.model.Category;
import com.jox3.tv.model.MediaItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3uParser {

    public static class ParseResult {
        public List<Category> liveCats   = new ArrayList<>();
        public List<Category> vodCats    = new ArrayList<>();
        public List<Category> seriesCats = new ArrayList<>();
        public Map<String, List<MediaItem>> liveItems   = new LinkedHashMap<>();
        public Map<String, List<MediaItem>> vodItems    = new LinkedHashMap<>();
        public Map<String, List<MediaItem>> seriesItems = new LinkedHashMap<>();
    }

    public static ParseResult parse(String m3uContent) {
        ParseResult result = new ParseResult();
        BufferedReader reader = new BufferedReader(new StringReader(m3uContent));
        String line;
        String currentExtInf = null;

        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF")) {
                    currentExtInf = line;
                } else if (line.startsWith("http") && currentExtInf != null) {
                    String url  = line.trim();
                    String name = extractAttr(currentExtInf, "tvg-name");
                    if (name.isEmpty()) name = extractName(currentExtInf);
                    String logo  = extractAttr(currentExtInf, "tvg-logo");
                    String group = extractAttr(currentExtInf, "group-title");
                    String id    = extractAttr(currentExtInf, "tvg-id");
                    if (id.isEmpty()) id = String.valueOf(url.hashCode());

                    // Detectar tipo por URL y grupo
                    String type = detectType(url, group, currentExtInf);

                    MediaItem item = new MediaItem(id, name, logo, url, group, type);
                    item.cover = logo;

                    // Agregar a su categoría
                    switch (type) {
                        case MediaItem.LIVE:
                            if (!result.liveItems.containsKey(group))
                                result.liveItems.put(group, new ArrayList<>());
                            result.liveItems.get(group).add(item);
                            break;
                        case MediaItem.VOD:
                            if (!result.vodItems.containsKey(group))
                                result.vodItems.put(group, new ArrayList<>());
                            result.vodItems.get(group).add(item);
                            break;
                        case MediaItem.SERIES:
                            if (!result.seriesItems.containsKey(group))
                                result.seriesItems.put(group, new ArrayList<>());
                            result.seriesItems.get(group).add(item);
                            break;
                    }
                    currentExtInf = null;
                }
            }
        } catch (IOException ignored) {}

        // Construir categorías
        int catId = 1;
        for (String g : result.liveItems.keySet())
            result.liveCats.add(new Category(String.valueOf(catId++), g.isEmpty() ? "General" : g));
        for (String g : result.vodItems.keySet())
            result.vodCats.add(new Category(String.valueOf(catId++), g.isEmpty() ? "Películas" : g));
        for (String g : result.seriesItems.keySet())
            result.seriesCats.add(new Category(String.valueOf(catId++), g.isEmpty() ? "Series" : g));

        return result;
    }

    private static String detectType(String url, String group, String extinf) {
        String u = url.toLowerCase();
        String g = group.toLowerCase();
        String e = extinf.toLowerCase();

        // Series — detectar por patrones S01E01
        if (u.matches(".*s\\d+e\\d+.*") || e.matches(".*s\\d+e\\d+.*")) return MediaItem.SERIES;
        if (g.contains("serie") || g.contains("series")) return MediaItem.SERIES;

        // VOD — extensiones de video
        if (u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") ||
            u.endsWith(".mov") || u.endsWith(".wmv") || u.endsWith(".m4v")) return MediaItem.VOD;
        if (u.contains("/movie/")) return MediaItem.VOD;
        if (g.contains("movie") || g.contains("pelicula") || g.contains("film") ||
            g.contains("vod") || g.contains("cine")) return MediaItem.VOD;

        // Live por defecto
        return MediaItem.LIVE;
    }

    private static String extractAttr(String extinf, String attr) {
        Pattern p = Pattern.compile(attr + "=\"([^\"]*)\"");
        Matcher m = p.matcher(extinf);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String extractName(String extinf) {
        int comma = extinf.lastIndexOf(',');
        return comma >= 0 ? extinf.substring(comma + 1).trim() : "";
    }
}
