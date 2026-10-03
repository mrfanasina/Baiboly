package com.fa.baiboly.data.parser;

import android.util.Log;

import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.models.Book;
import com.fa.baiboly.models.Reading;

/**
 * Adaptateur fin vers le parser centralisé {@link BibleRefParser}.
 *
 * Toute la logique (normalisation, acronymes prioritaires, nombres collés,
 * plages multi-chapitres, préfixes 1/2/3 et I/II/III) vit désormais dans
 * BibleRefParser, utilisée aussi par la détection dans les textes libres
 * (Mofonaina, chat IA) : une seule table de livres, un seul comportement.
 */
public class ReadingParser {

    private BibleService service;

    /**
     * Parses a string reference into a Reading object.
     * Handles complex names like "Asan'ny Apostoly 7:54-8:1", "Jao 3:16",
     * "Heb11,1", "II Sam 1:1"...
     *
     * @param input The raw string (e.g., "Jao 3:16", "I Kor 13:1-13", "Jao 3")
     * @return Reading object or null if parsing fails
     */
    public Reading parse(String input) {
        if (input == null || input.trim().isEmpty()) return null;

        BibleRefParser.ParsedRef ref = BibleRefParser.parse(input);
        if (ref == null) {
            Log.e("READING_PARSER", "Ref not recognized: " + input);
            return null;
        }

        Book book = null;
        if (service != null) {
            book = service.getBookById(ref.bookNumber);
        }

        if (book == null) {
            Log.e("READING_PARSER", "Book not found in DB: #" + ref.bookNumber);
            return null;
        }

        return new Reading(
                book,
                ref.startChapter,
                ref.startVerse,
                ref.endChapter,
                ref.endVerse
        );
    }

    public void setService(BibleService service) {
        this.service = service;
    }
}
