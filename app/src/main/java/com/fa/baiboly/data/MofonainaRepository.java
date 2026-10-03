package com.fa.baiboly.data;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.fa.baiboly.data.bible.BibleService;
import com.fa.baiboly.data.cache.MofonainaCache;
import com.fa.baiboly.data.parser.ReadingParser;
import com.fa.baiboly.data.parser.SongParser;
import com.fa.baiboly.data.scraper.MofonainaScraper;
import com.fa.baiboly.models.MofonainaData;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;

public class MofonainaRepository {

    private MofonainaScraper scraper;
    private MofonainaCache cache;

    public MofonainaRepository(Context context) {

        ReadingParser readingParser = new ReadingParser();
        readingParser.setService(new BibleService(context));

        scraper = new MofonainaScraper(
                readingParser,
                new SongParser()
        );

        cache = new MofonainaCache(context);
    }

    public MofonainaData get(String url) throws Exception {

        // =========================
        // 🔥 1. CHECK CACHE
        // =========================
        String cachedHtml = cache.getHtml();
        String cachedDate = cache.getDate();

        if (cachedHtml != null && cachedDate != null && isToday(cachedDate)) {
            Document doc = Jsoup.parse(cachedHtml);
            return scraper.scrape(doc);
        }

        // =========================
        // 🌐 2. STEP 1 : HOME PAGE
        // =========================
        Document homeDoc = Jsoup.connect(url)
                .timeout(10000)
                .get();

        Element link = homeDoc.selectFirst("div.get-started.text-start.text-lg-end > a");
        if (link == null) throw new Exception("Main link not found");
        Log.d("SALAMO", "Main link = " + link.text());
        String fullUrl = link.absUrl("href");

        // =========================
        // 🌐 3. STEP 2 : ARTICLE PAGE
        // =========================
        Document articleDoc = Jsoup.connect(fullUrl)
                .timeout(10000)
                .get();

        MofonainaData data = scraper.scrape(articleDoc);

        // =========================
        // 💾 4. CACHE
        // =========================
        cache.save(articleDoc.outerHtml(), extractDate(articleDoc));

        return data;
    }
    // =========================
    // 📅 EXTRACT DATE
    // =========================
    private String extractDate(Document doc) {

        Element content = doc.selectFirst("div.post-content");
        if (content == null) return null;

        Element dateEl = content.selectFirst("h5");

        return dateEl != null ? dateEl.text() : null;
    }

    // =========================
    // 📅 CHECK TODAY
    // =========================
    private boolean isToday(String rawDate) {

        try {
            rawDate = rawDate.replaceAll(".*faha", "").trim();

            String[] parts = rawDate.split(" ");

            int day = Integer.parseInt(parts[0]);
            int month = mapMonth(parts[1]);
            int year = Integer.parseInt(parts[2]);

            LocalDate cached = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cached = LocalDate.of(year, month, day);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return cached.equals(LocalDate.now());
            } else {
                return false;
            }

        } catch (Exception e) {
            return false;
        }
    }

    public void sync(String url) throws Exception {

        String cachedDate = cache.getDate();

        if (cachedDate != null && isToday(cachedDate)) {
            return;
        }

        Document homeDoc = Jsoup.connect(url)
                .timeout(10000)
                .get();

        Element link = homeDoc.selectFirst("a.btn");

        if (link == null) {
            throw new Exception("Main link not found");
        }

        String fullUrl = link.absUrl("href");

        Document articleDoc = Jsoup.connect(fullUrl)
                .timeout(10000)
                .get();

        cache.save(
                articleDoc.outerHtml(),
                extractDate(articleDoc)
        );
    }
    // =========================
    // 📅 MONTH MAP
    // =========================

    public MofonainaData getFromCache() throws Exception {

        String html = cache.getHtml();

        if (html == null) {
            return null;
        }

        Document doc = Jsoup.parse(html);

        return scraper.scrape(doc);
    }
    private int mapMonth(String month) {

        switch (month.toLowerCase()) {
            case "janoary": return 1;
            case "febroary": return 2;
            case "martsa": return 3;
            case "aprily": return 4;
            case "mey": return 5;
            case "jona": return 6;
            case "jolay": return 7;
            case "aogositra": return 8;
            case "septambra": return 9;
            case "oktobra": return 10;
            case "novambra": return 11;
            case "desambra": return 12;
            default: return 1;
        }
    }
}