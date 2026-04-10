package com.tarunguptaraja.coldemailer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class EmailHistory(
    val id: Long = 0,
    val email: String,
    val subject: String,
    val dateSent: Long // timestamp
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "ColdEmailerHistory.db"
        const val TABLE_HISTORY = "history"
        const val COLUMN_ID = "_id"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_SUBJECT = "subject"
        const val COLUMN_DATE_SENT = "date_sent"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_HISTORY + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_EMAIL + " TEXT,"
                + COLUMN_SUBJECT + " TEXT,"
                + COLUMN_DATE_SENT + " INTEGER" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY)
        onCreate(db)
    }

    fun addHistory(email: String, subject: String, dateSent: Long): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_EMAIL, email)
        values.put(COLUMN_SUBJECT, subject)
        values.put(COLUMN_DATE_SENT, dateSent)
        val id = db.insert(TABLE_HISTORY, null, values)
        db.close()
        return id
    }

    fun getAllHistory(): List<EmailHistory> {
        val historyList = ArrayList<EmailHistory>()
        val selectQuery = "SELECT * FROM $TABLE_HISTORY ORDER BY $COLUMN_DATE_SENT DESC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL))
                val subject = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SUBJECT))
                val dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE_SENT))
                historyList.add(EmailHistory(id, email, subject, dateSent))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return historyList
    }
    
    fun deleteHistory(id: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_HISTORY, "$COLUMN_ID=?", arrayOf(id.toString()))
        db.close()
    }
}
