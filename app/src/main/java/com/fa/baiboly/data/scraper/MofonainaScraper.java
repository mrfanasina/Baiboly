    package com.fa.baiboly.data.scraper;

    import android.util.Log;

    import com.fa.baiboly.data.parser.ReadingParser;
    import com.fa.baiboly.data.parser.SongParser;
    import com.fa.baiboly.models.*;
    import org.jsoup.nodes.Document;
    import org.jsoup.nodes.Element;

    import java.util.ArrayList;
    import java.util.List;

    public class MofonainaScraper {

        private ReadingParser readingParser;
        private SongParser songParser;

        public MofonainaScraper(ReadingParser readingParser,
                                SongParser songParser) {
            this.readingParser = readingParser;
            this.songParser = songParser;
        }

        public MofonainaData scrape(Document doc) {

            MofonainaData data = new MofonainaData();

            Element content = doc.selectFirst("div.post-content");
            if (content == null) return data;

            Element dateEl = content.selectFirst("h5");
            if (dateEl != null) {
                data.setDate(dateEl.text().trim());
            }
            // =========================
            // 🔥 TITLE
            // =========================
            Element titleEl = content.selectFirst("h2");
            if (titleEl != null) {
                data.setTitle(titleEl.text().trim());
            }

            // =========================
            // 🔥 LITURGY
            // =========================
            Element liturgyEl = content.selectFirst("p em");

            if (liturgyEl != null) {

                String rawText = liturgyEl.text();
                Log.d("MOFONAINA", "RAW LITURGY = " + rawText);

                String[] parts = rawText.split("/");

                Log.d("MOFONAINA", "PARTS COUNT = " + parts.length);

                for (int i = 0; i < parts.length; i++) {
                    Log.d("MOFONAINA", "PART[" + i + "] = " + parts[i]);
                }

                // =========================
                // 📖 READING 1
                // =========================
                try {
                    if (parts.length > 0) {

                        Log.d("SALAMO", "Trying Reading1 with: " + parts[0]);

                        Reading r = safeReading(parts[0]);

                        if (r != null) {
                            Log.d("SALAMO", "Reading1 OK = " + r.toString());
                            data.setBibleReading1(r);
                        } else {
                            Log.d("SALAMO", "Reading1 NULL ");
                        }
                    }
                } catch (Exception e) {
                    Log.e("MOFONAINA", "Reading1 ERROR ", e);
                }


                // =========================
                // 🎵 SONGS : toute partie après la lecture qui contient un
                // numéro de chant est une chanson. Le nombre de parties varie
                // selon les jours : aujourd'hui
                // "Sal.../ FFPM 790.../ Vavaka/ Mofonaina/ FF 3" (5 parties),
                // la 2e chanson est en parts[4] — on ne peut plus figer
                // l'index. Song1 = 1re chanson trouvée, Song2 = la dernière.
                // =========================
                List<Song> songs = new ArrayList<>();
                try {
                    for (int i = 1; i < parts.length; i++) {
                        Song s = safeSong(parts[i]);
                        if (s != null) {
                            Log.d("MOFONAINA", "Song candidate[" + i + "] = " + s.getId());
                            songs.add(s);
                        }
                    }
                    if (!songs.isEmpty()) {
                        data.setSong1(songs.get(0));
                        Log.d("MOFONAINA", "Song1 = " + songs.get(0).getId());
                        if (songs.size() > 1) {
                            Song last = songs.get(songs.size() - 1);
                            data.setSong2(last);
                            Log.d("MOFONAINA", "Song2 = " + last.getId());
                        }
                    }
                } catch (Exception e) {
                    Log.e("MOFONAINA", "Songs ERROR", e);
                }
            }
            // =========================
            // 🔥 BIBLE TEXT
            // =========================
            Element bibleBlock = content.selectFirst("div.soratra-masina");

            if (bibleBlock != null) {

                Element h5 = bibleBlock.selectFirst("h5");
                if (h5 != null) {
                    Log.d("MOFONAINA", "Reading OK = " + h5.text());

                    data.setVerseOfDay(
                            safeReading(h5.text())
                    );
                }

                // garder le texte biblique
                data.setBibleText(bibleBlock.html());
            }
            // =========================
            // 🔥 REFLECTION + QUESTION
            // =========================
            Element reflectionBlock = content.selectFirst("div.mb-3");

            if (reflectionBlock != null) {

                List<ReflectionSection> sections = new ArrayList<>();

                ReflectionSection currentSection = null;

                String mainTitle = null;
                String author = null;

                for (Element el : reflectionBlock.children()) {

                    String text = el.text().trim();

                    if (text.isEmpty()) continue;

                    // =========================
                    // AUTHOR
                    // =========================
                    if (el.text().contains("Foibe Sampana Sekoly Alahady")) {

                        author = text;
                        continue;
                    }
                    boolean hasStrong =
                            el.selectFirst("strong") != null;

                    // =========================
                    // MAIN TITLE
                    // First strong block
                    // =========================
                    if (hasStrong && mainTitle == null) {

                        mainTitle = text;

                        continue;
                    }

                    // =========================
                    // SECTION TITLE
                    // Every next strong becomes a section
                    // =========================
                    if (hasStrong) {

                        currentSection = new ReflectionSection();

                        currentSection.setTitle(text);

                        sections.add(currentSection);

                        continue;
                    }

                    // =========================
                    // PARAGRAPHS
                    // =========================
                    // Intro paragraphs before first section get their own section
                    if (currentSection == null) {
                        currentSection = new ReflectionSection();
                        currentSection.setTitle(null);
                        sections.add(currentSection);
                    }
                    currentSection
                            .getParagraphs()
                            .add(text);
                }
                data.setTitle(mainTitle);
                data.setSections(sections);
                data.setAuthor(author);
            }
            return data;
        }

        // =========================
        // 🔧 SAFE PARSERS
        // =========================

            private Reading safeReading(String text) {
                try {
                    if (text == null) return null;

                    text = text.trim();

                    // Espaces insécables du HTML ignorés par \\s des regex
                    text = text.replace('\u00a0', ' ');

                    // Format "Sal. 108. 1. 4" (chapitre. verset début.
                    // verset fin, séparés par des points) → "Sal. 108:1-4".
                    // Testé AVANT la règle deux nombres, il absorbe la liste
                    // complète.
                    text = text.replaceAll("(\\d+)\\s*\\.\\s*(\\d+)\\s*\\.\\s*(\\d+)",
                            "$1:$2-$3");

                    // Fix: Convert dot-separated chapter.verse to colon format
                    // "Sal 66.1-7" → "Sal 66:1-7"
                    // "Sal 66.1" → "Sal 66:1"
                    // But NOT "Sal 66" (chapter only) — espaces autour du
                    // point tolérés ("Sal 66. 1")
                    text = text.replaceAll("(\\d+)\\s*\\.\\s*(\\d+)", "$1:$2");

                    // Fix format
                    text = text.replace(",", ":");

                    Log.d("SAFE_READING", "Parser input = " + text);

                    Reading r = readingParser.parse(text);
                    if (r != null) {
                        Log.d("SAFE_READING", "Parser OK = " + r.toString());
                    }
                    return r;

                } catch (Exception e) {
                    return null;
                }
            }

        private Song safeSong(String text) {
            try {
                if (text == null) return null;

                text = text.trim();
                return songParser.parse(text);

            } catch (Exception e) {
                return null;
            }
        }
    }