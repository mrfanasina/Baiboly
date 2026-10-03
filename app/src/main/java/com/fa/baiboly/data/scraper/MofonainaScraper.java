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
                // 🎵 SONG 1
                // =========================
                try {
                    if (parts.length > 1) {

                        Log.d("MOFONAINA", "Trying Song1 with: " + parts[1]);

                        Song s1 = safeSong(parts[1]);

                        if (s1 != null) {
                            Log.d("MOFONAINA", "Song1 OK = " + s1.getId());
                            data.setSong1(s1);
                        } else {
                            Log.d("MOFONAINA", "Song1 NULL ");
                        }
                    }
                } catch (Exception e) {
                    Log.e("MOFONAINA", "Song1 ERROR ", e);
                }

                // =========================
                // 🎵 SONG 2
                // =========================
                try {
                    if (parts.length > 5) {

                        Log.d("MOFONAINA", "Trying Song2 with: " + parts[5]);

                        Song s2 = safeSong(parts[5]);

                        if (s2 != null) {
                            Log.d("MOFONAINA", "Song2 OK = " + s2.getId());
                            data.setSong2(s2);
                        } else {
                            Log.d("MOFONAINA", "Song2 NULL");
                        }
                    } else {
                        Log.d("MOFONAINA", "Song2 skipped (parts.length <= 5)");
                    }
                } catch (Exception e) {
                    Log.e("MOFONAINA", "Song2 ERROR", e);
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

                    // Fix: Convert dot-separated chapter.verse to colon format
                    // "Sal 66.1-7" → "Sal 66:1-7"
                    // "Sal 66.1" → "Sal 66:1"
                    // But NOT "Sal 66" (chapter only)
                    text = text.replaceAll("(\\d+)\\.(\\d+)", "$1:$2");

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