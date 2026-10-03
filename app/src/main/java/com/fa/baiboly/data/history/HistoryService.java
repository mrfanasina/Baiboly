    package com.fa.baiboly.data.history;

    import android.content.ContentValues;
    import android.content.Context;
    import android.database.Cursor;
    import android.database.sqlite.SQLiteDatabase;

    import com.fa.baiboly.data.DatabaseHelper;
    import com.fa.baiboly.models.History;

    import java.util.ArrayList;
    import java.util.List;

    public class HistoryService {

        private final DatabaseHelper dbHelper;

        public HistoryService(Context context) {
            dbHelper = new DatabaseHelper(context);
        }

        // ➕ Ajouter
        public void addHistory(String reading, String type) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put("reading", reading);
            values.put("timestamp", System.currentTimeMillis());
            values.put("type", type);
            db.insertWithOnConflict(
                    "history",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
        }

        // 📤 Récupérer tout
        public List<History> getAllHistory() {
            List<History> list = new ArrayList<>();
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            Cursor cursor = db.rawQuery(
                    "SELECT id, reading, timestamp, type FROM history ORDER BY timestamp DESC",
                    null
            );

            if (cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(0);
                    String reading = cursor.getString(1);
                    long timestamp = cursor.getLong(2);
                    String type = cursor.getString(3);
                    list.add(new History(id, reading, timestamp, type));
                } while (cursor.moveToNext());
            }

            cursor.close();
            return list;
        }
        public List<History> getHistoryPrev() {
            List<History> list = new ArrayList<>();
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            Cursor cursor = db.rawQuery(
                    "SELECT id, reading, timestamp, type FROM history ORDER BY timestamp DESC LIMIT 5",
                    null
            );

            if (cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(0);
                    String reading = cursor.getString(1);
                    long timestamp = cursor.getLong(2);
                    String type = cursor.getString(3);
                    list.add(new History(id, reading, timestamp, type));
                } while (cursor.moveToNext());
            }

            cursor.close();
            return list;
        }

        // 🗑️ Supprimer par IDs
        public void deleteHistoryByIds(List<Integer> ids) {
            if (ids == null || ids.isEmpty()) return;
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            StringBuilder sb = new StringBuilder("DELETE FROM history WHERE id IN (");
            for (int i = 0; i < ids.size(); i++) {
                sb.append(ids.get(i));
                if (i < ids.size() - 1) sb.append(",");
            }
            sb.append(")");

            db.execSQL(sb.toString());
        }
    }