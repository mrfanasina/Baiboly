package com.fa.baiboly.data.mapper;

import android.database.Cursor;

import com.fa.baiboly.models.Book;

public class BookMapper {

    public static Book fromCursor(Cursor c) {
        return new Book(
                c.getInt(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("color")),
                c.getString(c.getColumnIndexOrThrow("short_name")),
                c.getString(c.getColumnIndexOrThrow("long_name"))
        );
    }
}