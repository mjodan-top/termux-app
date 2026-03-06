package com.termux.app.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database helper for storing and retrieving SSH chat messages.
 */
public class ChatDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "termux_chat.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String COL_ID = "id";
    private static final String COL_SESSION_ID = "session_id";
    private static final String COL_CONTENT = "content";
    private static final String COL_TYPE = "type";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_REMOTE_HOST = "remote_host";

    private static final String SQL_CREATE_TABLE =
        "CREATE TABLE " + TABLE_MESSAGES + " (" +
            COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_SESSION_ID + " TEXT, " +
            COL_CONTENT + " TEXT, " +
            COL_TYPE + " INTEGER, " +
            COL_TIMESTAMP + " INTEGER, " +
            COL_REMOTE_HOST + " TEXT" +
        ")";

    private static final String SQL_CREATE_INDEX_SESSION =
        "CREATE INDEX idx_session_id ON " + TABLE_MESSAGES + " (" + COL_SESSION_ID + ")";

    private static final String SQL_CREATE_INDEX_TIMESTAMP =
        "CREATE INDEX idx_timestamp ON " + TABLE_MESSAGES + " (" + COL_TIMESTAMP + ")";

    private static ChatDatabase sInstance;

    public static synchronized ChatDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ChatDatabase(context.getApplicationContext());
        }
        return sInstance;
    }

    private ChatDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
        db.execSQL(SQL_CREATE_INDEX_SESSION);
        db.execSQL(SQL_CREATE_INDEX_TIMESTAMP);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        onCreate(db);
    }

    /**
     * Insert a chat message into the database.
     *
     * @param message the message to insert
     * @return the row id of the newly inserted row, or -1 on error
     */
    public synchronized long insertMessage(ChatMessage message) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SESSION_ID, message.getSessionId());
        values.put(COL_CONTENT, message.getContent());
        values.put(COL_TYPE, message.getType());
        values.put(COL_TIMESTAMP, message.getTimestamp());
        values.put(COL_REMOTE_HOST, message.getRemoteHost());
        long id = db.insert(TABLE_MESSAGES, null, values);
        if (id != -1) {
            message.setId(id);
        }
        return id;
    }

    /**
     * Get messages for a session with pagination, ordered by timestamp ascending.
     *
     * @param sessionId the session identifier
     * @param limit     maximum number of messages to return
     * @param offset    number of messages to skip
     * @return list of chat messages
     */
    public synchronized List<ChatMessage> getMessages(String sessionId, int limit, int offset) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
            TABLE_MESSAGES,
            null,
            COL_SESSION_ID + " = ?",
            new String[]{sessionId},
            null, null,
            COL_TIMESTAMP + " ASC",
            offset + ", " + limit
        );
        try {
            while (cursor.moveToNext()) {
                messages.add(cursorToMessage(cursor));
            }
        } finally {
            cursor.close();
        }
        return messages;
    }

    /**
     * Get the most recent messages for a session, returned in ascending timestamp order.
     *
     * @param sessionId the session identifier
     * @param limit     maximum number of messages to return
     * @return list of chat messages ordered by timestamp ascending
     */
    public synchronized List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        // Sub-query to get the last N rows, then order ascending
        String sql = "SELECT * FROM (SELECT * FROM " + TABLE_MESSAGES +
            " WHERE " + COL_SESSION_ID + " = ? ORDER BY " + COL_TIMESTAMP + " DESC LIMIT ?) ORDER BY " + COL_TIMESTAMP + " ASC";
        Cursor cursor = db.rawQuery(sql, new String[]{sessionId, String.valueOf(limit)});
        try {
            while (cursor.moveToNext()) {
                messages.add(cursorToMessage(cursor));
            }
        } finally {
            cursor.close();
        }
        return messages;
    }

    /**
     * Delete all messages for a given session.
     *
     * @param sessionId the session identifier
     * @return the number of rows deleted
     */
    public synchronized int deleteSession(String sessionId) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_MESSAGES, COL_SESSION_ID + " = ?", new String[]{sessionId});
    }

    /**
     * Get a list of all distinct session IDs.
     *
     * @return list of unique session IDs
     */
    public synchronized List<String> getAllSessions() {
        List<String> sessions = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
            true,
            TABLE_MESSAGES,
            new String[]{COL_SESSION_ID},
            null, null,
            null, null,
            COL_SESSION_ID + " ASC",
            null
        );
        try {
            while (cursor.moveToNext()) {
                sessions.add(cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_ID)));
            }
        } finally {
            cursor.close();
        }
        return sessions;
    }

    private ChatMessage cursorToMessage(Cursor cursor) {
        return new ChatMessage(
            cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            cursor.getString(cursor.getColumnIndexOrThrow(COL_SESSION_ID)),
            cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT)),
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_TYPE)),
            cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
            cursor.getString(cursor.getColumnIndexOrThrow(COL_REMOTE_HOST))
        );
    }
}
