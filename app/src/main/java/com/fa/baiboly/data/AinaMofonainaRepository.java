package com.fa.baiboly.data;

import android.content.Context;
import android.util.Log;

import com.fa.baiboly.models.MofonainaDay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for the "Aina sy Fahasalamana" daily devotional data.
 * Reads from assets/mofonaina.json and stores in SQLite.
 * Skips entries that already exist (dedup by id).
 */
public class AinaMofonainaRepository {

    private static final String TAG = "AinaMofonaina";
    private static final String JSON_FILE = "mofonaina.json";

    private final Context context;
    private final AinaMofonainaDbHelper dbHelper;

    public AinaMofonainaRepository(Context context) {
        this.context = context;
        this.dbHelper = new AinaMofonainaDbHelper(context);
    }

    /**
     * Load JSON from assets and insert into SQLite.
     * Only inserts new entries (skips existing ones).
     * Returns the number of newly inserted rows.
     */
    public int syncFromJson() {
        try {
            List<MofonainaDay> days = parseJson();
            int inserted = dbHelper.insertDays(days);
            Log.d(TAG, "Synced " + inserted + " new entries (total: "
                    + days.size() + " in JSON, " + dbHelper.getEntryCount() + " in DB)");
            return inserted;
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync mofonaina JSON", e);
            return 0;
        }
    }

    /**
     * Get today's mofonaina entry
     */
    public MofonainaDay getToday() {
        return dbHelper.getTodayEntry();
    }

    /**
     * Get entry for a specific date
     */
    public MofonainaDay getByDate(String dateIso) {
        return dbHelper.getByDateIso(dateIso);
    }

    /**
     * Get all entries for a month
     */
    public List<MofonainaDay> getByMonth(int year, int month) {
        return dbHelper.getEntriesByMonth(year, month);
    }

    /**
     * Check if DB has been populated
     */
    public boolean hasData() {
        return dbHelper.hasData();
    }

    /**
     * Get the author name from the cover section of the JSON
     */
    public String getAuthor() {
        try {
            String json = loadJsonFromAssets();
            JSONObject root = new JSONObject(json);
            JSONObject cover = root.getJSONObject("cover");
            return cover.optString("author", "");
        } catch (Exception e) {
            Log.e(TAG, "Failed to read author", e);
            return "";
        }
    }

    /**
     * Parse the mofonaina.json file from assets
     */
    private List<MofonainaDay> parseJson() throws Exception {
        List<MofonainaDay> days = new ArrayList<>();

        String json = loadJsonFromAssets();
        JSONObject root = new JSONObject(json);

        JSONArray daysArray = root.getJSONArray("days");

        for (int i = 0; i < daysArray.length(); i++) {
            JSONObject dayObj = daysArray.getJSONObject(i);
            MofonainaDay day = new MofonainaDay();

            day.setId(dayObj.optString("id", ""));
            day.setDateIso(dayObj.optString("date_iso", ""));
            day.setDayIndex(dayObj.optInt("day_index", 0));
            day.setDayNameMg(dayObj.optString("day_name_mg", ""));
            day.setDayNameEn(dayObj.optString("day_name_en", ""));
            day.setMonthMg(dayObj.optString("month_mg", ""));
            day.setMonthFr(dayObj.optString("month_fr", ""));
            day.setYear(dayObj.optInt("year", 2026));
            day.setBibleReference(dayObj.optString("bible_reference", ""));
            day.setThemeSlogan(dayObj.optString("theme_slogan", ""));
            day.setMeditation(dayObj.optString("meditation", ""));
            day.setMessageToday(dayObj.optString("message_today", ""));
            day.setPrayer(dayObj.optString("prayer", ""));

            days.add(day);
        }

        Log.d(TAG, "Parsed " + days.size() + " days from JSON");
        return days;
    }

    /**
     * Read the JSON file from assets
     */
    private String loadJsonFromAssets() throws Exception {
        InputStream is = context.getAssets().open(JSON_FILE);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();
        return new String(buffer, StandardCharsets.UTF_8);
    }
}
