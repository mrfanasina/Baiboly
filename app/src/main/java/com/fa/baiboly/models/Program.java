package com.fa.baiboly.models;

import java.util.ArrayList;
import java.util.List;

public class Program {
    private long id;
    private String name;
    private long createdAt;
    private List<ProgramItem> items;

    public Program() {
        this.items = new ArrayList<>();
    }

    public Program(long id, String name, long createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.items = new ArrayList<>();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<ProgramItem> getItems() { return items; }
    public void setItems(List<ProgramItem> items) { this.items = items; }

    public int getItemCount() { return items != null ? items.size() : 0; }
}
