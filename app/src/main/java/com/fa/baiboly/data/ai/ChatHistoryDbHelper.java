package com.fa.baiboly.data.ai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistance des discussions IA (historique) dans une base SQLite dédiée
 * ("ai_chat.db" — jamais la DB Bible, qui est recopiée depuis les assets).
 *
 * Une "conversation" = groupe de messages user/IA, créé au premier échange
 * et rouvrable depuis l'écran d'historique.
 */
public class ChatHistoryDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "ai_chat.db";
    private static final int DB_VERSION = 1;

    /** Limite : nombre max de conversations gardées (les plus vieilles sont purgées). */
    private static final int MAX_CONVERSATIONS = 50;

    /** Table des conversations (titre + date). */
    private static final String TABLE_CONVOS = "chat_conversations";
    /** Table des messages (type 0=user, 1=IA). */
    private static final String TABLE_MSGS = "chat_messages";

    public ChatHistoryDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CONVOS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_MSGS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "conversation_id INTEGER NOT NULL, " +
                "type INTEGER NOT NULL, " +
                "text TEXT NOT NULL, " +
                "position INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 : rien à migrer encore
    }

    // =====================================================
    // ÉCRITURE
    // =====================================================

    /**
     * Ajoute un échange user/IA à une conversation (la crée si id = -1,
     * titre = 1re question tronquée). Retourne l'id de conversation.
     */
    public long appendExchange(long conversationId,
                               String userText,
                               String aiText,
                               String fallbackTitle) {

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            if (conversationId < 0) {
                ContentValues cv = new ContentValues();
                cv.put("title", truncate(fallbackTitle, 60));
                long now = System.currentTimeMillis();
                cv.put("created_at", now);
                cv.put("updated_at", now);
                conversationId = db.insert(TABLE_CONVOS, null, cv);
            } else {
                ContentValues cv = new ContentValues();
                cv.put("updated_at", System.currentTimeMillis());
                db.update(TABLE_CONVOS, cv, "id = ?",
                        new String[]{String.valueOf(conversationId)});
            }

            int nextPos = getMessageCount(db, conversationId);

            if (userText != null && !userText.isEmpty()) {
                insertMessage(db, conversationId, 0, userText, nextPos++);
            }
            if (aiText != null && !aiText.isEmpty()) {
                insertMessage(db, conversationId, 1, aiText, nextPos);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        purgeOldConversations(db);
        return conversationId;
    }

    private void insertMessage(SQLiteDatabase db, long conversationId,
                               int type, String text, int position) {
        ContentValues cv = new ContentValues();
        cv.put("conversation_id", conversationId);
        cv.put("type", type);
        cv.put("text", text);
        cv.put("position", position);
        db.insert(TABLE_MSGS, null, cv);
    }

    private int getMessageCount(SQLiteDatabase db, long conversationId) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MSGS
                + " WHERE conversation_id = ?",
                new String[]{String.valueOf(conversationId)});
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    /** Garde au plus MAX_CONVERSATIONS conversations (les plus récentes). */
    private void purgeOldConversations(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + TABLE_MSGS + " WHERE conversation_id IN " +
                "(SELECT id FROM " + TABLE_CONVOS + " WHERE id NOT IN " +
                "(SELECT id FROM " + TABLE_CONVOS + " ORDER BY updated_at DESC LIMIT " +
                MAX_CONVERSATIONS + "))");
        db.execSQL("DELETE FROM " + TABLE_CONVOS + " WHERE id NOT IN " +
                "(SELECT id FROM " + TABLE_CONVOS + " ORDER BY updated_at DESC LIMIT " +
                MAX_CONVERSATIONS + ")");
    }

    // =====================================================
    // LECTURE
    // =====================================================

    /** Conversation la plus récente, ou -1 si aucune. */
    public long getLatestConversationId() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM " + TABLE_CONVOS
                + " ORDER BY updated_at DESC LIMIT 1", null);
        long id = -1;
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();
        return id;
    }

    public static class ConversationEntry {
        public long id;
        public String title;
        public long updatedAt;
    }

    /** Toutes les conversations, les plus récentes d'abord. */
    public List<ConversationEntry> listConversations() {
        List<ConversationEntry> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, title, updated_at FROM " + TABLE_CONVOS
                + " ORDER BY updated_at DESC", null);
        while (c.moveToNext()) {
            ConversationEntry e = new ConversationEntry();
            e.id = c.getLong(0);
            e.title = c.getString(1);
            e.updatedAt = c.getLong(2);
            result.add(e);
        }
        c.close();
        return result;
    }

    /** Modèle simplifié pour reconstruire la conversation. */
    public static class StoredMessage {
        public int type;    // 0 = user, 1 = IA
        public String text;
    }

    /** Nombre de messages d'une conversation (pour l'affichage historique). */
    public int getMessageCount(long conversationId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MSGS
                        + " WHERE conversation_id = ?",
                new String[]{String.valueOf(conversationId)});
        int count = 0;
        if (c.moveToFirst()) count = c.getInt(0);
        c.close();
        return count;
    }

    /** Messages d'une conversation, dans l'ordre d'ajout. */
    public List<StoredMessage> getMessages(long conversationId) {
        List<StoredMessage> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT type, text FROM " + TABLE_MSGS
                        + " WHERE conversation_id = ? ORDER BY position",
                new String[]{String.valueOf(conversationId)});
        while (c.moveToNext()) {
            StoredMessage m = new StoredMessage();
            m.type = c.getInt(0);
            m.text = c.getString(1);
            result.add(m);
        }
        c.close();
        return result;
    }

    // =====================================================
    // EXPORT / SUPPRESSION
    // =====================================================

    /** JSON complet d'une conversation (export/sauvegarde future). */
    public String exportConversationJson(long conversationId) {
        try {
            JSONObject root = new JSONObject();
            root.put("conversationId", conversationId);
            JSONArray messages = new JSONArray();
            for (StoredMessage m : getMessages(conversationId)) {
                JSONObject o = new JSONObject();
                o.put("type", m.type);
                o.put("text", m.text);
                messages.put(o);
            }
            root.put("messages", messages);
            return root.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteConversation(long conversationId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MSGS, "conversation_id = ?",
                new String[]{String.valueOf(conversationId)});
        db.delete(TABLE_CONVOS, "id = ?",
                new String[]{String.valueOf(conversationId)});
    }

    public void deleteAllConversations() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MSGS, null, null);
        db.delete(TABLE_CONVOS, null, null);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
