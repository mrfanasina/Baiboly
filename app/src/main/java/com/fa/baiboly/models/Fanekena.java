package com.fa.baiboly.models;

public class Fanekena {
    private int id;
    private String code;
    private String text;

    public Fanekena(int id, String code, String text) {
        this.id = id;
        this.code = code;
        this.text = text;
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setText(String text) {
        this.text = text;
    }
}
