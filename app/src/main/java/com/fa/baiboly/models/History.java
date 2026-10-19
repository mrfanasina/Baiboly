package com.fa.baiboly.models;

public class History {

    private int id;
    private String reading;
    private long timestamp;

    private String type;

    public History(int id, String reading, long timestamp, String type) {
        this.id = id;
        this.reading = reading;
        this.timestamp = timestamp;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public String getReading() {
        return reading;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }
}