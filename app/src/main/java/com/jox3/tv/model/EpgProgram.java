package com.jox3.tv.model;

public class EpgProgram {
    public String title;
    public String description;
    public long startTime; // unix timestamp ms
    public long endTime;   // unix timestamp ms

    public EpgProgram(String title, String description, long start, long end) {
        this.title = title;
        this.description = description;
        this.startTime = start;
        this.endTime = end;
    }

    public boolean isNow() {
        long now = System.currentTimeMillis();
        return now >= startTime && now < endTime;
    }

    public int progressPct() {
        long now = System.currentTimeMillis();
        if (now < startTime) return 0;
        if (now >= endTime) return 100;
        long duration = endTime - startTime;
        return (int)((now - startTime) * 100 / duration);
    }

    public String timeRange() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(startTime)) + " - " + sdf.format(new java.util.Date(endTime));
    }
}
