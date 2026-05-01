package com.jox3.tv.model;

public class Account {
    public String host;
    public String user;
    public String pass;

    public Account() {}

    public Account(String host, String user, String pass) {
        this.host = host;
        this.user = user;
        this.pass = pass;
    }

    public String key() { return host + "|" + user; }

    public String displayHost() {
        return host.replaceAll("^https?://", "").split(":")[0];
    }
}
