package com.fa.baiboly.models;

public class Annotation {

    private String word;
    private String annotation;
    private int startPosition;
    private int endPosition;

    public Annotation(String word, String annotation, int startPosition, int endPosition) {
        this.word = word;
        this.annotation = annotation;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public String getWord() {
        return word;
    }

    public String getAnnotation() {
        return annotation;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }
}