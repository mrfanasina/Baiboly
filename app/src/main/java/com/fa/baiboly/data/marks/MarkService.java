package com.fa.baiboly.data.marks;

import android.content.Context;
import android.database.Cursor;

import com.fa.baiboly.models.VerseMark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarkService {

    public static final String TYPE_HIGHLIGHT = "highlight";
    public static final String TYPE_FAVORITE = "favorite";

    private final MarkRepository repository;

    public MarkService(Context context) {
        repository = new MarkRepository(context);
    }

    public void setHighlight(String bookShort, int chapter, int verse, String colorHex) {
        repository.addOrUpdateMark(bookShort, chapter, verse, TYPE_HIGHLIGHT, colorHex);
    }

    public void removeHighlight(String bookShort, int chapter, int verse) {
        repository.removeMark(bookShort, chapter, verse, TYPE_HIGHLIGHT);
    }

    public void toggleFavorite(String bookShort, int chapter, int verse, boolean isFav) {
        if (isFav) {
            repository.addOrUpdateMark(bookShort, chapter, verse, TYPE_FAVORITE, null);
        } else {
            repository.removeMark(bookShort, chapter, verse, TYPE_FAVORITE);
        }
    }

    public boolean isFavorite(String bookShort, int chapter, int verse) {
        return repository.exists(bookShort, chapter, verse, TYPE_FAVORITE);
    }

    /** verseNumber -> liste de marques (highlight et/ou favorite) pour tout un chapitre */
    public Map<Integer, List<VerseMark>> getMarksForChapter(String bookShort, int chapter) {

        Map<Integer, List<VerseMark>> map = new HashMap<>();
        Cursor c = repository.getMarksForChapter(bookShort, chapter);

        if (c != null) {
            try {
                int idxVerse = c.getColumnIndexOrThrow("verse");
                int idxType = c.getColumnIndexOrThrow("type");
                int idxColor = c.getColumnIndexOrThrow("color");
                int idxId = c.getColumnIndexOrThrow("id");

                while (c.moveToNext()) {
                    VerseMark m = new VerseMark();
                    m.setId(c.getInt(idxId));
                    m.setBookShortName(bookShort);
                    m.setChapter(chapter);
                    m.setVerseNumber(c.getInt(idxVerse));
                    m.setType(c.getString(idxType));
                    m.setColor(c.isNull(idxColor) ? null : c.getString(idxColor));

                    List<VerseMark> list = map.get(m.getVerseNumber());
                    if (list == null) {
                        list = new ArrayList<>();
                        map.put(m.getVerseNumber(), list);
                    }
                    list.add(m);
                }
            } finally {
                c.close();
            }
        }

        return map;
    }
}