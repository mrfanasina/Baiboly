    package com.fa.baiboly.data.bible;

    import android.content.Context;
    import android.database.Cursor;
    import android.util.Log;

    import com.fa.baiboly.data.mapper.BookMapper;
    import com.fa.baiboly.models.Annotation;
    import com.fa.baiboly.models.Book;
    import com.fa.baiboly.models.Chapter;
    import com.fa.baiboly.models.Reading;
    import com.fa.baiboly.models.SearchResult;
    import com.fa.baiboly.models.Verse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

    public class BibleService {

        private final BibleRepository repository;

        // In-memory cache for verse objects (avoids re-querying DB).
        // ConcurrentHashMap : le cache est lu/écrit depuis le thread de fond
        // qui prépare les pages du pager.
        private final Map<String, List<Verse>> verseCache = new ConcurrentHashMap<>();

        public BibleService(Context context) {
            this.repository = new BibleRepository(context);
        }

        // =========================
        // 📚 BOOKS
        // =========================
        public List<Book> getBooks(String testament, String lang) {

            List<Book> list = new ArrayList<>();

            Cursor c = repository.getBooks(testament, lang);

            if (c != null && c.moveToFirst()) {
                do {

                    int id = c.getInt(0);
                    String shortName = c.getString(1);
                    String longName = c.getString(2);
                    String color = c.getString(3);

                    list.add(new Book(id, color, shortName, longName));

                } while (c.moveToNext());

                c.close();
            }

            return list;
        }

        /**
         * Tous les livres dans l'ordre canonique (Ancien Testament puis
         * Nouveau Testament). Sert au swipe continu entre tous les
         * chapitres de la Bible dans VersesActivity.
         */
        public List<Book> getAllBooksOrdered() {
            List<Book> all = new ArrayList<>();
            all.addAll(getBooks("AT", "mg"));
            all.addAll(getBooks("NT", "mg"));
            return all;
        }

        // =========================
        // 📖 CHAPTERS
        // =========================
        /**
         * Tous les chapitres de la Bible dans l'ordre canonique,
         * formatés "Gen 1", "Gen 2", ... "Apo 22" (une seule requête SQL).
         * Sert au swipe continu entre tous les chapitres.
         */
        public List<String> getAllChapterPages() {

            List<String> pages = new ArrayList<>();
            Cursor c = repository.getAllChaptersOrdered();

            if (c != null) {
                try {
                    int idxBook = c.getColumnIndexOrThrow("short_name");
                    int idxChapter = c.getColumnIndexOrThrow("chapter");

                    while (c.moveToNext()) {
                        pages.add(c.getString(idxBook) + " " + c.getInt(idxChapter));
                    }
                } finally {
                    c.close();
                }
            }

            return pages;
        }
        public List<Chapter> getChapters(int bookId) {

            List<Chapter> list = new ArrayList<>();

            Cursor c = repository.getChapters(bookId);

            if (c != null && c.moveToFirst()) {
                do {
                    list.add(new Chapter(
                            bookId,
                            c.getInt(0),
                            c.getInt(1)
                    ));
                } while (c.moveToNext());

                c.close();
            }

            return list;
        }

        // =========================
        // 🔍 SEARCH
        // =========================
        public List<String> search(String query) {

            List<String> list = new ArrayList<>();

            Cursor c = repository.search(query);

            if (c != null && c.moveToFirst()) {
                do {
                    list.add(c.getInt(0) + ". " + c.getString(1));
                } while (c.moveToNext());

                c.close();
            }

            return list;
        }

        public List<Annotation> getAnnotationsForVerse(int verseId) {
            Log.d("ANN_DEBUG", verseId + "verse");
            List<Annotation> list = new ArrayList<>();

            Cursor c = repository.getAnnotationsForVerse(verseId);

            if (c != null) {

                while (c.moveToNext()) {

                    Annotation annotation = new Annotation(
                            c.getString(
                                    c.getColumnIndexOrThrow("word")
                            ),

                            c.getString(
                                    c.getColumnIndexOrThrow("annotation")
                            ),

                            c.getInt(
                                    c.getColumnIndexOrThrow("start_position")
                            ),

                            c.getInt(
                                    c.getColumnIndexOrThrow("end_position")
                            )
                    );

                    list.add(annotation);
                }

                c.close();
            }

            return list;
        }
        // =========================
        // 📖 READING OBJECTS (For RecyclerView Adapter)
        // =========================
    public List<Verse> getVerseObjectsFromReading(Reading reading) {
        if (reading == null) return new ArrayList<>();

        // Check in-memory cache first
        String cacheKey = reading.toString();
        List<Verse> cached = verseCache.get(cacheKey);
        if (cached != null) return cached;

        List<Verse> list = new ArrayList<>();
        try {
            Cursor c = repository.getVersesFromReading(
                        reading.getBook().getShortName(),
                        reading.getStartChapter(),
                        reading.getStartVerse(),
                        reading.getEndChapter(),
                        reading.getEndVerse()
                );

                if (c != null && c.moveToFirst()) {
                    // On récupère les index dynamiquement pour ne plus se tromper
                    // Dans ton SQL, la colonne s'appelle "verse"
                    int idxVerse = c.getColumnIndex("verse");
                    int idxText = c.getColumnIndex("text");
                    int idxTitle = c.getColumnIndex("title");
                    int idxChapter = c.getColumnIndex("chapter");
                    int idxId = c.getColumnIndex("id");

                    do {
                        // Récupération sécurisée
                        int verseNumber = (idxVerse != -1) ? c.getInt(idxVerse) : 0;
                        String text = (idxText != -1) ? c.getString(idxText) : "";
                        String title = (idxTitle != -1) ? c.getString(idxTitle) : null;
                        int id = (idxId != -1) ? c.getInt(idxId) : 0;
                        // Si la colonne "chapter" n'est pas dans le SELECT, on prend celui du reading

                        // On crée l'objet Verse
                        Log.d("ID ", id + " id");
                        Verse v = new Verse(id,  verseNumber, text, title);

                        list.add(v);
                    } while (c.moveToNext());
                    c.close();
                }
            } catch (Exception e) {
                Log.e("DB_ERROR", "Erreur lors de la lecture des versets", e);
            }
            // Store in cache
            verseCache.put(cacheKey, list);
            return list;
        }

        // =====================================================
        // 🔍 SEARCH (LIEN UI)
        // =====================================================
        public List<SearchResult> searchVerses(String query, String lang) {
            List<SearchResult> results = new ArrayList<>();

            // On appelle la nouvelle méthode optimisée du repository
            Cursor c = repository.searchVerses(query, lang);

            if (c != null) {
                try {
                    // On récupère les colonnes une seule fois pour la performance
                    int idxId = c.getColumnIndexOrThrow("_id");
                    int idxBook = c.getColumnIndexOrThrow("book_name");
                    int idxChap = c.getColumnIndexOrThrow("chapter");
                    int idxVerse = c.getColumnIndexOrThrow("verse");
                    int idxText = c.getColumnIndexOrThrow("text");

                    while (c.moveToNext()) {
                        results.add(new SearchResult(
                                c.getInt(idxId),
                                c.getString(idxBook),
                                c.getInt(idxChap),
                                c.getInt(idxVerse),
                                c.getString(idxText)
                        ));
                    }
                } catch (Exception e) {
                    Log.e("SEARCH_SERVICE", "Erreur lors de la recherche : " + e.getMessage());
                } finally {
                    c.close();
                }
            }

            return results;
        }

        public List<Book> getBooksByIds(List<Integer> ids) {

            List<Book> books = new ArrayList<>();

            Cursor c = repository.getBooksByIds(ids);

            if (c != null) {

                try {

                    while (c.moveToNext()) {
                        books.add(BookMapper.fromCursor(c));
                    }

                } finally {
                    c.close();
                }
            }

            return books;
        }

        /**
         * Livre par numéro canonique (1..66) : résolution DIRECTE par
         * numéro, sans re-parser le nom. Utilisé par ReadingParser une
         * fois que BibleRefParser a identifié le livre.
         */
        public Book getBookById(int bookId) {

            Cursor c = repository.getBookById(bookId);

            if (c == null) return null;

            try {
                if (c.moveToFirst()) {
                    return BookMapper.fromCursor(c);
                }
                return null;
            } finally {
                c.close();
            }
        }

        public Book findBookByBookName(String bookName) {

            if (bookName == null) return null;

            Integer bookId = repository.resolveBookNumber(bookName);

            if (bookId == null) return null;

            Cursor c = repository.getBookById(bookId);

            if (c == null) return null;

            Book book = null;

            if (c.moveToFirst()) {
                book = BookMapper.fromCursor(c);
            }

            c.close();

            return book;
        }
    }