package com.fa.baiboly.models;

public class Fanekena {
    private int id;
    private String code;
    private String text;
    private String titre;

    public Fanekena(int id, String code, String text) {
        this(id, code, text, null);
    }

    public Fanekena(int id, String code, String text, String titre) {
        this.id = id;
        this.code = code;
        this.text = text;
        this.titre = titre;
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

    public String getTitre() {
        return titre;
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

    public void setTitre(String titre) {
        this.titre = titre;
    }
}
