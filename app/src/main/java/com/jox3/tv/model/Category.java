package com.jox3.tv.model;

import java.util.ArrayList;
import java.util.List;

public class Category {
    public String id;
    public String name;
    public boolean loaded;
    public List<MediaItem> items = new ArrayList<>();

    public Category(String id, String name) {
        this.id = id; this.name = name;
    }
}
