package com.fa.baiboly.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.fa.baiboly.models.MofonainaDay;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database for the "Aina sy Fahasalamana" daily devotional entries.
 * Stores data loaded from mofonaina.json with deduplication by id.
 */
public class AinaMofonainaDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "aina_mofonaina.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE = "aina_mofonaina_days";

    // Column names
    private static final String COL_ID = "id";
    private static final String COL_DATE_ISO = "date_iso";
    private static final String COL_DAY_INDEX = "day_index";
    private static final String COL_DAY_NAME_MG = "day_name_mg";
    private static final String COL_DAY_NAME_EN = "day_name_en";
    private static final String COL_MONTH_MG = "month_mg";
    private static final String COL_MONTH_FR = "month_fr";
    private static final String COL_YEAR = "year";
    private static final String COL_BIBLE_REF = "bible_reference";
    private static final String COL_THEME_SLOGAN = "theme_slogan";
    private static final String COL_MEDITATION = "meditation";
    private static final String COL_MESSAGE_TODAY = "message_today";
    private static final String COL_PRAYER = "prayer";

    public AinaMofonainaDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE + " ("
                + COL_ID + " TEXT PRIMARY KEY, "
                + COL_DATE_ISO + " TEXT, "
                + COL_DAY_INDEX + " INTEGER, "
                + COL_DAY_NAME_MG + " TEXT, "
                + COL_DAY_NAME_EN + " TEXT, "
                + COL_MONTH_MG + " TEXT, "
                + COL_MONTH_FR + " TEXT, "
                + COL_YEAR + " INTEGER, "
                + COL_BIBLE_REF + " TEXT, "
                + COL_THEME_SLOGAN + " TEXT, "
                + COL_MEDITATION + " TEXT, "
                + COL_MESSAGE_TODAY + " TEXT, "
                + COL_PRAYER + " TEXT"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    /**
     * Insert or ignore a single day entry.
     * Uses PRIMARY KEY (id) to avoid duplicates.
     */
    public long insertDay(MofonainaDay day) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ID, day.getId());
        values.put(COL_DATE_ISO, day.getDateIso());
        values.put(COL_DAY_INDEX, day.getDayIndex());
        values.put(COL_DAY_NAME_MG, day.getDayNameMg());
        values.put(COL_DAY_NAME_EN, day.getDayNameEn());
        values.put(COL_MONTH_MG, day.getMonthMg());
        values.put(COL_MONTH_FR, day.getMonthFr());
        values.put(COL_YEAR, day.getYear());
        values.put(COL_BIBLE_REF, day.getBibleReference());
        values.put(COL_THEME_SLOGAN, day.getThemeSlogan());
        values.put(COL_MEDITATION, day.getMeditation());
        values.put(COL_MESSAGE_TODAY, day.getMessageToday());
        values.put(COL_PRAYER, day.getPrayer());

        // INSERT OR IGNORE: skip if id already exists (dedup)
        return db.insertWithOnConflict(TABLE, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    /**
     * Bulk insert days, skipping any that already exist.
     * Returns the number of newly inserted rows.
     */
    public int insertDays(List<MofonainaDay> days) {
        SQLiteDatabase db = this.getWritableDatabase();
        int inserted = 0;

        db.beginTransaction();
        try {
            for (MofonainaDay day : days) {
                ContentValues values = new ContentValues();
                values.put(COL_ID, day.getId());
                values.put(COL_DATE_ISO, day.getDateIso());
                values.put(COL_DAY_INDEX, day.getDayIndex());
                values.put(COL_DAY_NAME_MG, day.getDayNameMg());
                values.put(COL_DAY_NAME_EN, day.getDayNameEn());
                values.put(COL_MONTH_MG, day.getMonthMg());
                values.put(COL_MONTH_FR, day.getMonthFr());
                values.put(COL_YEAR, day.getYear());
                values.put(COL_BIBLE_REF, day.getBibleReference());
                values.put(COL_THEME_SLOGAN, day.getThemeSlogan());
                values.put(COL_MEDITATION, day.getMeditation());
                values.put(COL_MESSAGE_TODAY, day.getMessageToday());
                values.put(COL_PRAYER, day.getPrayer());

                long result = db.insertWithOnConflict(TABLE, null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (result != -1) {
                    inserted++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return inserted;
    }

    /**
     * Get today's mofonaina entry based on current date.
     * Falls back to the most recent entry if today's doesn't exist.
     */
    public MofonainaDay getTodayEntry() {
        String todayIso = getTodayIso();
        MofonainaDay entry = getByDateIso(todayIso);
        if (entry != null) return entry;

        // Fallback: get most recent entry
        return getMostRecentEntry();
    }

    /**
     * Get a specific day entry by its date (e.g. "2026-09-03")
     */
    public MofonainaDay getByDateIso(String dateIso) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE, null,
                COL_DATE_ISO + " = ?",
                new String[]{dateIso},
                null, null, null);

        MofonainaDay day = null;
        if (cursor.moveToFirst()) {
            day = cursorToDay(cursor);
        }
        cursor.close();
        return day;
    }

    /**
     * Get the most recent entry (by date descending)
     */
    public MofonainaDay getMostRecentEntry() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE, null,
                null, null,
                null, null,
                COL_DATE_ISO + " DESC",
                "1");

        MofonainaDay day = null;
        if (cursor.moveToFirst()) {
            day = cursorToDay(cursor);
        }
        cursor.close();
        return day;
    }

    /**
     * Get all entries for a given month and year
     */
    public List<MofonainaDay> getEntriesByMonth(int year, int month) {
        List<MofonainaDay> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String likePattern = String.format("%d-%02d-%", year, month);
        Cursor cursor = db.query(TABLE, null,
                COL_DATE_ISO + " LIKE ?",
                new String[]{likePattern},
                null, null,
                COL_DAY_INDEX + " ASC");

        while (cursor.moveToNext()) {
            list.add(cursorToDay(cursor));
        }
        cursor.close();
        return list;
    }

    /**
     * Get total number of stored entries
     */
    public int getEntryCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    /**
     * Check if the table has any data
     */
    public boolean hasData() {
        return getEntryCount() > 0;
    }

    /**
     * Convert cursor row to MofonainaDay object
     */
    private MofonainaDay cursorToDay(Cursor cursor) {
        MofonainaDay day = new MofonainaDay();
        day.setId(cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)));
        day.setDateIso(cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE_ISO)));
        day.setDayIndex(cursor.getInt(cursor.getColumnIndexOrThrow(COL_DAY_INDEX)));
        day.setDayNameMg(cursor.getString(cursor.getColumnIndexOrThrow(COL_DAY_NAME_MG)));
        day.setDayNameEn(cursor.getString(cursor.getColumnIndexOrThrow(COL_DAY_NAME_EN)));
        day.setMonthMg(cursor.getString(cursor.getColumnIndexOrThrow(COL_MONTH_MG)));
        day.setMonthFr(cursor.getString(cursor.getColumnIndexOrThrow(COL_MONTH_FR)));
        day.setYear(cursor.getInt(cursor.getColumnIndexOrThrow(COL_YEAR)));
        day.setBibleReference(cursor.getString(cursor.getColumnIndexOrThrow(COL_BIBLE_REF)));
        day.setThemeSlogan(cursor.getString(cursor.getColumnIndexOrThrow(COL_THEME_SLOGAN)));
        day.setMeditation(cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDITATION)));
        day.setMessageToday(cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_TODAY)));
        day.setPrayer(cursor.getString(cursor.getColumnIndexOrThrow(COL_PRAYER)));
        return day;
    }

    /**
     * Returns today's date in ISO format: "2026-09-03"
     */
    private String getTodayIso() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int y = cal.get(java.util.Calendar.YEAR);
        int m = cal.get(java.util.Calendar.MONTH) + 1;
        int d = cal.get(java.util.Calendar.DAY_OF_MONTH);
        return String.format("%d-%02d-%02d", y, m, d);
    }
}
