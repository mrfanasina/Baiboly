package com.fa.baiboly.data.parser;

import com.fa.baiboly.models.Song;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SongParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    /**
     * Sokajy reconnus dans le texte de la liturgie : "FFPM 790", "FF 3",
     * "Hira 12", "Antema 5"...
     * \b (mot entier) : "ffpm" n'est jamais mangé par "ff".
     */
    private static final Pattern CATEGORY_PATTERN = Pattern.compile(
            "\\b(ffpm|ff|antema|tsanta|safif)\\b");

    public Song parse(String text) {

        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }

        String lowered = text.trim().toLowerCase();
        String category = detectCategory(lowered);

        Matcher matcher = NUMBER_PATTERN.matcher(lowered);

        if (!matcher.find()) {
            throw new IllegalArgumentException("No song number found in: " + text);
        }

        int num = Integer.parseInt(matcher.group(1));

        return new Song(category + "_" + num, num, category, null);
    }

    /**
     * Catégorie du chant d'après le texte :
     *  - "Hira..." : ancienne écriture du site = FFPM ;
     *  - sinon, premier sokajy reconnu ("FFPM 790. 1, 2" -> ffpm) ;
     *  - sinon, repli "ff" (comportement historique).
     */
    private static String detectCategory(String lowered) {
        if (lowered.startsWith("hira")) {
            return "ffpm";
        }
        Matcher m = CATEGORY_PATTERN.matcher(lowered);
        return m.find() ? m.group(1) : "ff";
    }
}