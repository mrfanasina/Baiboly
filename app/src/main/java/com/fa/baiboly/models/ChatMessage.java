package com.fa.baiboly.models;

public class ChatMessage {

    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;
    public static final int TYPE_LOADING = 2;

    private final int type;
    private final String text;

    public ChatMessage(int type, String text) {
        this.type = type;
        this.text = text;
    }

    public int getType() {
        return type;
    }

    public String getText() {
        return text;
    }
}