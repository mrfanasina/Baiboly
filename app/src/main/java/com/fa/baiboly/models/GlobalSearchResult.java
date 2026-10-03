package com.fa.baiboly.models;

public class GlobalSearchResult {

    public enum Type {
        VERSET,
        CHANT,
        FANEKENA,
        IA_CTA
    }

    private final Type type;
    private final String title;
    private final String snippet;
    private final String reference;
    private final String category; // sokajy brut du chant (null pour verset/fanekena) — sert au filtre par catégorie
    private final Object payload;

    public GlobalSearchResult(Type type, String title, String snippet, String reference,
                              String category, Object payload) {
        this.type = type;
        this.title = title;
        this.snippet = snippet;
        this.reference = reference;
        this.category = category;
        this.payload = payload;
    }

    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getSnippet() { return snippet; }
    public String getReference() { return reference; }
    public String getCategory() { return category; }
    public Object getPayload() { return payload; }
}