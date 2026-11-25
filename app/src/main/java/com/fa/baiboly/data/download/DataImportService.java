package com.fa.baiboly.data.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fa.baiboly.data.AinaMofonainaDbHelper;
import com.fa.baiboly.data.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import / export JSON des contenus de la base :
 *
 * - PERIKOPA  (format "baiboly-perikopa")
 *   { "format": "baiboly-perikopa", "version": 1, "year": 2027,
 *     "months": [ { "month_name": "Janoary", "theme": "…",
 *       "days": [ { "date": "2027-01-01", "name": "…", "season": "advento",
 *                   "readings": ["Sal 111:1-10", "Jao 1:1-5"] } ] } ] }
 *
 * - AINA SY FAHASALAMANA  (format "baiboly-aina", identique au
 *   assets/mofonaina.json déjà utilisé par l'application)
 *   { "cover": { "title": "AINA SY FAHASALAMANA", … },
 *     "days": [ { "id": "2027-01-01", "date_iso": "2027-01-01", … } ] }
 *
 * Une fois importé, le contenu vit dans la base SQLite : il n'est plus
 * jamais supprimé par le nettoyage des téléchargements.
 */
public class DataImportService {

    public static final String DATASET_PERIKOPA = "perikopa";
    public static final String DATASET_AINA = "aina";

    public static final String FORMAT_PERIKOPA = "baiboly-perikopa";
    public static final String FORMAT_AINA = "baiboly-aina";

    /** Résultat d'un import. */
    public static class ImportResult {
        public boolean success;
        public String message = "";
        /** Nombre de jours écrits (perikopa) ou ajoutés (aina). */
        public int days;

        static ImportResult error(String message) {
            ImportResult r = new ImportResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }

    private final Context context;
    private final DatabaseHelper dbHelper;
    private final AinaMofonainaDbHelper ainaDb;
    private final DownloadHistoryStore store;

    public DataImportService(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new DatabaseHelper(this.context);
        this.ainaDb = new AinaMofonainaDbHelper(this.context);
        this.store = new DownloadHistoryStore(this.context);
    }

    // =========================================================
    // IMPORT
    // =========================================================

    /**
     * Importe un fichier JSON. Le type est déduit du contenu quand
     * {@code datasetHint} est null.
     */
    public ImportResult importFile(File file, String datasetHint) {
        if (file == null || !file.exists()) {
            return ImportResult.error("Tsy misy rakitra");
        }
        try {
            String text = readFile(file);
            JSONObject root = new JSONObject(text);

            String dataset = datasetHint;
            if (dataset == null || dataset.isEmpty()) {
                dataset = root.has("months") ? DATASET_PERIKOPA
                        : root.has("days") ? DATASET_AINA : null;
            }
            if (dataset == null) {
                return ImportResult.error("Format tsy fantatra");
            }

            ImportResult result = DATASET_PERIKOPA.equals(dataset)
                    ? importPerikopa(root) : importAina(root);

            if (result.success) {
                store.markDataSetImported(dataset, file.getName());
            }
            return result;
        } catch (Exception e) {
            return ImportResult.error("Tsy nety : " + e.getMessage());
        }
    }

    /** Importe (ou met à jour) le perikopa d'une année. */
    public ImportResult importPerikopa(JSONObject root) {
        ImportResult result = new ImportResult();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, Long> seasonIds = new HashMap<>();
        int dayCount = 0;

        db.beginTransaction();
        try {
            JSONArray months = root.optJSONArray("months");
            if (months == null) {
                // Variante : liste de jours à plat.
                months = new JSONArray();
                JSONObject wrapper = new JSONObject();
                wrapper.put("days", root.optJSONArray("days"));
                months.put(wrapper);
            }

            for (int m = 0; m < months.length(); m++) {
                JSONObject month = months.optJSONObject(m);
                if (month == null) continue;

                String monthName = month.optString("month_name", "");
                String theme = month.optString("theme", "");
                JSONArray days = month.optJSONArray("days");
                if (days == null) continue;

                long monthId = -1;
                for (int d = 0; d < days.length(); d++) {
                    JSONObject day = days.optJSONObject(d);
                    if (day == null) continue;
                    String date = day.optString("date", "");
                    if (date.length() < 10) continue;
                    if (monthName.isEmpty()) monthName = malagasyMonth(date);
                    if (monthId < 0) {
                        monthId = upsertMonth(db, yearOf(date), monthName, theme);
                    }

                    String season = day.optString("season", "");
                    long seasonId = seasonIdFor(db, seasonIds, season, yearOf(date));

                    long dayId = findDayId(db, date);
                    ContentValues dv = new ContentValues();
                    dv.put("month_id", monthId);
                    dv.put("date", date);
                    dv.put("name", day.optString("name", ""));
                    dv.put("season_id", seasonId);
                    if (dayId > 0) {
                        db.update("perikopa_days", dv, "id = ?",
                                new String[]{String.valueOf(dayId)});
                        db.delete("perikopa_readings", "day_id = ?",
                                new String[]{String.valueOf(dayId)});
                    } else {
                        dayId = db.insert("perikopa_days", null, dv);
                    }

                    JSONArray readings = day.optJSONArray("readings");
                    if (readings == null) readings = day.optJSONArray("verses");
                    if (readings != null) {
                        for (int r = 0; r < readings.length(); r++) {
                            String verse = readings.optString(r, "").trim();
                            if (verse.isEmpty()) continue;
                            ContentValues rv = new ContentValues();
                            rv.put("day_id", dayId);
                            rv.put("verse", verse);
                            db.insert("perikopa_readings", null, rv);
                        }
                    }
                    dayCount++;
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            result.success = false;
            result.message = "Tsy nety : " + e.getMessage();
            return result;
        } finally {
            db.endTransaction();
        }

        result.success = true;
        result.days = dayCount;
        result.message = dayCount + " andro perikopa voatahiry";
        return result;
    }

    /** Importe (ou met à jour) les méditations Aina sy Fahasalamana. */
    public ImportResult importAina(JSONObject root) {
        ImportResult result = new ImportResult();
        JSONArray days = root.optJSONArray("days");
        if (days == null) return ImportResult.error("Tsy misy \"days\"");

        SQLiteDatabase db = ainaDb.getWritableDatabase();
        int written = 0;

        db.beginTransaction();
        try {
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.optJSONObject(i);
                if (day == null) continue;

                String id = day.optString("id", day.optString("date_iso", ""));
                if (id.isEmpty()) continue;

                ContentValues v = new ContentValues();
                v.put("id", id);
                v.put("date_iso", day.optString("date_iso", id));
                v.put("day_index", day.optInt("day_index", 0));
                v.put("day_name_mg", day.optString("day_name_mg", ""));
                v.put("day_name_en", day.optString("day_name_en", ""));
                v.put("month_mg", day.optString("month_mg", ""));
                v.put("month_fr", day.optString("month_fr", ""));
                v.put("year", day.optInt("year", 0));
                v.put("bible_reference", day.optString("bible_reference", ""));
                v.put("theme_slogan", day.optString("theme_slogan", ""));
                v.put("meditation", day.optString("meditation", ""));
                v.put("message_today", day.optString("message_today", ""));
                v.put("prayer", day.optString("prayer", ""));

                db.insertWithOnConflict("aina_mofonaina_days", null, v,
                        SQLiteDatabase.CONFLICT_REPLACE);
                written++;
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            result.success = false;
            result.message = "Tsy nety : " + e.getMessage();
            return result;
        } finally {
            db.endTransaction();
        }

        result.success = true;
        result.days = written;
        result.message = written + " andro Aina voatahiry";
        return result;
    }

    // =========================================================
    // EXPORT (reconstruction depuis la base, pour le transfert)
    // =========================================================

    /** Construit le JSON perikopa d'une année depuis la base. */
    public JSONObject exportPerikopa(int year) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_PERIKOPA);
        root.put("version", 1);
        root.put("year", year);
        root.put("generated_at", System.currentTimeMillis());

        JSONArray monthsOut = new JSONArray();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor mc = db.rawQuery(
                "SELECT id, month_name, theme FROM perikopa_months " +
                        "WHERE year = ? ORDER BY id ASC",
                new String[]{String.valueOf(year)});
        try {
            while (mc.moveToNext()) {
                long monthId = mc.getLong(0);
                JSONObject month = new JSONObject();
                month.put("month_name", mc.getString(1));
                month.put("theme", mc.getString(2) == null ? "" : mc.getString(2));

                JSONArray daysOut = new JSONArray();
                Cursor dc = db.rawQuery(
                        "SELECT d.id, d.date, d.name, IFNULL(s.season, '') " +
                                "FROM perikopa_days d " +
                                "LEFT JOIN liturgical_seasons s ON s.id = d.season_id " +
                                "WHERE d.month_id = ? ORDER BY d.date ASC",
                        new String[]{String.valueOf(monthId)});
                try {
                    while (dc.moveToNext()) {
                        JSONObject day = new JSONObject();
                        day.put("date", dc.getString(1));
                        day.put("name", dc.getString(2) == null ? "" : dc.getString(2));
                        day.put("season", dc.getString(3));
                        day.put("readings", readingsFor(db, dc.getLong(0)));
                        daysOut.put(day);
                    }
                } finally {
                    dc.close();
                }
                month.put("days", daysOut);
                monthsOut.put(month);
            }
        } finally {
            mc.close();
        }

        root.put("months", monthsOut);
        return root;
    }

    private JSONArray readingsFor(SQLiteDatabase db, long dayId) {
        JSONArray arr = new JSONArray();
        Cursor c = db.rawQuery(
                "SELECT verse FROM perikopa_readings WHERE day_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(dayId)});
        while (c.moveToNext()) {
            arr.put(c.getString(0) == null ? "" : c.getString(0));
        }
        c.close();
        return arr;
    }

    /** Construit le JSON Aina depuis la base (couverture incluse). */
    public JSONObject exportAina() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_AINA);
        root.put("version", 1);
        root.put("generated_at", System.currentTimeMillis());

        JSONObject cover = new JSONObject();
        cover.put("title", "AINA SY FAHASALAMANA");
        cover.put("subtitle", "Mofonaina isan'andro");
        cover.put("institution", "LYCEE FJKM RANDZAVOLA IVOHIDAHY – FIANARANTSOA");
        root.put("cover", cover);

        JSONArray daysOut = new JSONArray();
        SQLiteDatabase db = ainaDb.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, date_iso, day_index, day_name_mg, day_name_en, " +
                        "month_mg, month_fr, year, bible_reference, theme_slogan, " +
                        "meditation, message_today, prayer " +
                        "FROM aina_mofonaina_days ORDER BY date_iso ASC", null);
        try {
            while (c.moveToNext()) {
                JSONObject day = new JSONObject();
                day.put("id", c.getString(0));
                day.put("date_iso", c.getString(1));
                day.put("day_index", c.getInt(2));
                day.put("day_name_mg", c.getString(3));
                day.put("day_name_en", c.getString(4));
                day.put("month_mg", c.getString(5));
                day.put("month_fr", c.getString(6));
                day.put("year", c.getInt(7));
                day.put("bible_reference", c.getString(8));
                day.put("theme_slogan", c.getString(9));
                day.put("meditation", c.getString(10));
                day.put("message_today", c.getString(11));
                day.put("prayer", c.getString(12));
                daysOut.put(day);
            }
        } finally {
            c.close();
        }
        root.put("days", daysOut);
        return root;
    }

    /** Écrit un JSON dans un fichier (UTF-8). */
    public static File writeJson(JSONObject json, File out) throws Exception {
        out.getParentFile().mkdirs();
        OutputStream os = new FileOutputStream(out);
        try {
            os.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            os.flush();
        } finally {
            os.close();
        }
        return out;
    }

    /** Vrai si le jeu de données est déjà présent dans la base. */
    public boolean hasPerikopa(int year) {
        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM perikopa_months WHERE year = ?",
                new String[]{String.valueOf(year)});
        boolean has = c.moveToFirst() && c.getInt(0) > 0;
        c.close();
        return has;
    }

    public boolean hasAina() {
        Cursor c = ainaDb.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM aina_mofonaina_days", null);
        boolean has = c.moveToFirst() && c.getInt(0) > 0;
        c.close();
        return has;
    }

    /** Supprime le perikopa d'une année (utilisé par le transfert / nettoyage). */
    public int deletePerikopaYear(int year) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int removed = 0;
        Cursor c = db.rawQuery(
                "SELECT id FROM perikopa_days WHERE date LIKE ?",
                new String[]{year + "%"});
        List<Long> ids = new ArrayList<>();
        while (c.moveToNext()) ids.add(c.getLong(0));
        c.close();
        for (Long id : ids) {
            db.delete("perikopa_readings", "day_id = ?",
                    new String[]{String.valueOf(id)});
            db.delete("perikopa_days", "id = ?",
                    new String[]{String.valueOf(id)});
            removed++;
        }
        db.delete("perikopa_months", "year = ?",
                new String[]{String.valueOf(year)});
        store.clearDataSetImported(DATASET_PERIKOPA + "-" + year);
        return removed;
    }

    /** Supprime toutes les méditations Aina importées. */
    public int deleteAina() {
        int removed = ainaDb.getWritableDatabase().delete(
                "aina_mofonaina_days", null, null);
        store.clearDataSetImported(DATASET_AINA);
        return removed;
    }

    // =========================================================
    // OUTILS
    // =========================================================
    private long upsertMonth(SQLiteDatabase db, int year, String monthName,
                             String theme) {
        Cursor c = db.rawQuery(
                "SELECT id FROM perikopa_months WHERE year = ? AND month_name = ?",
                new String[]{String.valueOf(year), monthName});
        long id = -1;
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();

        ContentValues v = new ContentValues();
        v.put("year", year);
        v.put("month_name", monthName);
        v.put("theme", theme == null ? "" : theme);
        if (id > 0) {
            db.update("perikopa_months", v, "id = ?",
                    new String[]{String.valueOf(id)});
            return id;
        }
        return db.insert("perikopa_months", null, v);
    }

    private long findDayId(SQLiteDatabase db, String date) {
        Cursor c = db.rawQuery(
                "SELECT id FROM perikopa_days WHERE date = ? LIMIT 1",
                new String[]{date});
        long id = -1;
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();
        return id;
    }

    private long seasonIdFor(SQLiteDatabase db, Map<String, Long> cache,
                             String season, int year) {
        String key = season == null ? "" : season.trim().toLowerCase();
        if (key.isEmpty()) return -1;
        Long cached = cache.get(key);
        if (cached != null) return cached;

        Cursor c = db.rawQuery(
                "SELECT id FROM liturgical_seasons WHERE LOWER(season) = ? " +
                        "ORDER BY id ASC LIMIT 1",
                new String[]{key});
        long id = -1;
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();

        if (id < 0) {
            ContentValues v = new ContentValues();
            v.put("year", year);
            v.put("season", key);
            id = db.insert("liturgical_seasons", null, v);
        }
        cache.put(key, id);
        return id;
    }

    private static int yearOf(String date) {
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String malagasyMonth(String date) {
        int month;
        try {
            month = Integer.parseInt(date.substring(5, 7));
        } catch (Exception e) {
            return "";
        }
        String[] names = {"Janoary", "Febroary", "Martsa", "Aprily", "May",
                "Jona", "Jolay", "Aogositra", "Septambra", "Oktobra",
                "Novambra", "Desambra"};
        return month >= 1 && month <= 12 ? names[month - 1] : "";
    }

    private static String readFile(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) > 0) bos.write(buffer, 0, len);
        fis.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
