package com.jox3.tv.model;

public class Account {
    public String host;
    public String user;
    public String pass;
    public String type; // "xtream" o "m3u"
    public String m3uUrl; // URL completa M3U si es M3U

    public Account() {}

    public Account(String host, String user, String pass) {
        this.host = host;
        this.user = user;
        this.pass = pass;
        this.type = "xtream";
    }

    public boolean isM3u() { return "m3u".equals(type); }

    public String key() { return host + "|" + user; }

    public String displayHost() {
        if (isM3u() && m3uUrl != null)
            return m3uUrl.replaceAll("^https?://", "").split("[:/]")[0];
        return host.replaceAll("^https?://", "").split(":")[0];
    }
}
