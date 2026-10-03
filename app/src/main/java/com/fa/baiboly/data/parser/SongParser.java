package com.fa.baiboly.data.parser;

import com.fa.baiboly.models.Song;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    public Song parse(String text) {

        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }

        text = text.trim().toLowerCase();

        String category;
        if (text.startsWith("hira")) {
            category = "ffpm";
        } else {
            category = "ff";
        }

        Matcher matcher = NUMBER_PATTERN.matcher(text);

        if (!matcher.find()) {
            throw new IllegalArgumentException("No song number found in: " + text);
        }

        int num = Integer.parseInt(matcher.group(1));

        return new Song(category + "_" + num, num, category, null);
    }
}