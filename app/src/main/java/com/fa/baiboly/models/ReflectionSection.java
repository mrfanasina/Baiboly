package com.fa.baiboly.models;

import java.util.ArrayList;
import java.util.List;

public class ReflectionSection {

    private String title;
    private List<String> paragraphs = new ArrayList<>();
    public ReflectionSection() {}

    public List<String> getParagraphs() {
        return paragraphs;
    }
    public void setParagraphs(List<String> paragraphs) {
        this.paragraphs = paragraphs;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}