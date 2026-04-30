package com.jox3.tv.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jox3.tv.model.Account;
import com.jox3.tv.model.Category;
import com.jox3.tv.model.EpgProgram;
import com.jox3.tv.model.MediaItem;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class XtreamApi {

    private Account acc;
    private final OkHttpClient http;

    public XtreamApi() { http = unsafeClient(); }

    public void setAccount(Account a) { acc = a; }

    public String get(String url) throws IOException {
        Request r = new Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0").build();
        try (Response resp = http.newCall(r).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            return resp.body().string();
        }
    }

    private String api(String action) {
        return acc.host + "/player_api.php?username=" + acc.user +
               "&password=" + acc.pass + "&action=" + action;
    }

    // ── Categorías ──
    public List<Category> getLiveCats()   throws IOException { return parseCats(get(api("get_live_categories"))); }
    public List<Category> getVodCats()    throws IOException { return parseCats(get(api("get_vod_categories"))); }
    public List<Category> getSeriesCats() throws IOException { return parseCats(get(api("get_series_categories"))); }

    private List<Category> parseCats(String json) {
        List<Category> list = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                String id   = o.get("category_id").getAsString();
                String name = o.has("category_name") ? o.get("category_name").getAsString() : "?";
                list.add(new Category(id, name));
            }
        } catch (Exception ignored) {}
        return list;
    }

    // ── Streams ──
    public List<MediaItem> getLiveStreams(String catId) throws IOException {
        String url = api("get_live_streams") + (catId != null ? "&category_id=" + catId : "");
        return parseLive(get(url));
    }

    public List<MediaItem> getVodStreams(String catId) throws IOException {
        String url = api("get_vod_streams") + (catId != null ? "&category_id=" + catId : "");
        return parseVod(get(url));
    }

    public List<MediaItem> getSeries(String catId) throws IOException {
        String url = api("get_series") + (catId != null ? "&category_id=" + catId : "");
        return parseSeries(get(url));
    }

    private List<MediaItem> parseLive(String json) {
        List<MediaItem> list = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                String id    = o.get("stream_id").getAsString();
                String name  = o.has("name")         ? o.get("name").getAsString()         : "?";
                String logo  = o.has("stream_icon")  ? o.get("stream_icon").getAsString()  : "";
                String url   = acc.host + "/live/" + acc.user + "/" + acc.pass + "/" + id + ".m3u8";
                String group = o.has("category_id")  ? o.get("category_id").getAsString()  : "";
                list.add(new MediaItem(id, name, logo, url, group, MediaItem.LIVE));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private List<MediaItem> parseVod(String json) {
        List<MediaItem> list = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                String id     = o.get("stream_id").getAsString();
                String name   = o.has("name")        ? o.get("name").getAsString()        : "?";
                String cover  = o.has("stream_icon") ? o.get("stream_icon").getAsString() : "";
                String rating = o.has("rating")      ? o.get("rating").getAsString()      : "";
                String genre  = o.has("genre")       ? o.get("genre").getAsString()       : "";
                String group  = o.has("category_id") ? o.get("category_id").getAsString() : "";

                // Usar container_extension del API — es la extensión real del archivo
                String ext = "mp4";
                if (o.has("container_extension") && !o.get("container_extension").isJsonNull()) {
                    String e = o.get("container_extension").getAsString().trim();
                    if (!e.isEmpty()) ext = e;
                }

                // Usar direct_source si está disponible, si no construir URL estándar
                String directSource = "";
                if (o.has("direct_source") && !o.get("direct_source").isJsonNull())
                    directSource = o.get("direct_source").getAsString().trim();

                String url = (!directSource.isEmpty() && directSource.startsWith("http")) ?
                    directSource :
                    acc.host + "/movie/" + acc.user + "/" + acc.pass + "/" + id + "." + ext;

                MediaItem item = new MediaItem(id, name, cover, url, group, MediaItem.VOD);
                item.cover  = cover;
                item.rating = rating;
                item.genre  = genre;
                list.add(item);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private List<MediaItem> parseSeries(String json) {
        List<MediaItem> list = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                String id     = o.get("series_id").getAsString();
                String name   = o.has("name")   ? o.get("name").getAsString()   : "?";
                String cover  = o.has("cover")  ? o.get("cover").getAsString()  : "";
                String rating = o.has("rating") ? o.get("rating").getAsString() : "";
                String group  = o.has("category_id") ? o.get("category_id").getAsString() : "";
                MediaItem item = new MediaItem(id, name, cover, "", group, MediaItem.SERIES);
                item.cover  = cover;
                item.rating = rating;
                // Leer número de temporadas — campo num_seasons del API Xtream
                if (o.has("num_seasons") && !o.get("num_seasons").isJsonNull()) {
                    try { item.seasons = o.get("num_seasons").getAsInt(); }
                    catch (Exception ignored2) { item.seasons = 1; }
                } else {
                    item.seasons = 1;
                }
                list.add(item);
            }
        } catch (Exception ignored) {}
        return list;
    }

    // ── Info VOD ──
    public MediaItem getVodInfo(String vodId) throws IOException {
        String json = get(api("get_vod_info") + "&vod_id=" + vodId);
        MediaItem item = new MediaItem();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject info = root.has("info") ? root.getAsJsonObject("info") : null;
            if (info != null) {
                item.plot     = str(info, "plot");
                item.genre    = str(info, "genre");
                item.cast     = str(info, "cast");
                item.director = str(info, "director");
                item.year     = str(info, "releasedate");
                item.duration = str(info, "duration");
                item.rating   = str(info, "rating");
                item.cover    = str(info, "movie_image");
            }
        } catch (Exception ignored) {}
        return item;
    }

    // ── Info Series ──
    public JsonObject getSeriesInfo(String seriesId) throws IOException {
        String json = get(api("get_series_info") + "&series_id=" + seriesId);
        try { return JsonParser.parseString(json).getAsJsonObject(); }
        catch (Exception e) { return new JsonObject(); }
    }

    private String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }


    // ── EPG ──
    public List<EpgProgram> getShortEpg(String streamId) throws IOException {
        String json = get(api("get_short_epg") + "&stream_id=" + streamId + "&limit=2");
        return parseEpg(json);
    }

    public List<EpgProgram> getFullEpg(String streamId) throws IOException {
        String json = get(api("get_simple_data_table") + "&stream_id=" + streamId);
        return parseEpg(json);
    }

    private List<EpgProgram> parseEpg(String json) {
        List<EpgProgram> list = new ArrayList<>();
        try {
            com.google.gson.JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            com.google.gson.JsonArray listings = root.has("epg_listings") ?
                root.getAsJsonArray("epg_listings") : new com.google.gson.JsonArray();

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getDefault());

            for (int i = 0; i < listings.size(); i++) {
                com.google.gson.JsonObject o = listings.get(i).getAsJsonObject();
                String title = "";
                String desc  = "";

                // El título puede venir en base64
                if (o.has("title")) {
                    try {
                        title = new String(android.util.Base64.decode(
                            o.get("title").getAsString(), android.util.Base64.DEFAULT));
                    } catch (Exception e) {
                        title = o.get("title").getAsString();
                    }
                }
                if (o.has("description")) {
                    try {
                        desc = new String(android.util.Base64.decode(
                            o.get("description").getAsString(), android.util.Base64.DEFAULT));
                    } catch (Exception e) {
                        desc = o.get("description").getAsString();
                    }
                }

                String startStr = o.has("start") ? o.get("start").getAsString() : "";
                String endStr   = o.has("end")   ? o.get("end").getAsString()   :
                                  o.has("stop")  ? o.get("stop").getAsString()  : "";

                long startMs = 0, endMs = 0;
                try { startMs = sdf.parse(startStr).getTime(); } catch (Exception ignored) {}
                try { endMs   = sdf.parse(endStr).getTime();   } catch (Exception ignored) {}

                // Algunos servidores dan timestamp Unix directamente
                if (startMs == 0 && o.has("start_timestamp")) {
                    try { startMs = o.get("start_timestamp").getAsLong() * 1000; } catch (Exception ignored) {}
                }
                if (endMs == 0 && o.has("stop_timestamp")) {
                    try { endMs = o.get("stop_timestamp").getAsLong() * 1000; } catch (Exception ignored) {}
                }

                if (!title.isEmpty())
                    list.add(new com.jox3.tv.model.EpgProgram(title.trim(), desc.trim(), startMs, endMs));
            }
        } catch (Exception ignored) {}
        return list;
    }

    // ── SSL bypass ──
    private OkHttpClient unsafeClient() {
        try {
            X509TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{tm}, null);
            return new OkHttpClient.Builder()
                .sslSocketFactory(sc.getSocketFactory(), tm)
                .hostnameVerifier((h, s) -> true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        } catch (Exception e) { return new OkHttpClient(); }
    }
}
