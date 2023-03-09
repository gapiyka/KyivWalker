package com.gapiyka.kyivwalker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

class Database(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val SQL_CREATE_ENTRIES =
        "CREATE TABLE $TABLE_NAME (" +
                "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                "$COLUMN_LAT DOUBLE," +
                "$COLUMN_LONG DOUBLE)"

    private val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS $TABLE_NAME"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    fun deleteData(lat: Double, long: Double) {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, "$COLUMN_LAT=? and $COLUMN_LONG=?",
            arrayOf(lat.toString(), long.toString()))
        db.close()
    }

    fun addData(lat: Double, long: Double) {
        val values = ContentValues()
        values.put(COLUMN_LAT, lat)
        values.put(COLUMN_LONG, long)
        val db = this.writableDatabase
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    private fun getData(): Cursor? {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_NAME", null)
    }

    fun getArrayListOfPoints(): ArrayList<Point> {
        val array = arrayListOf<Point>()
        val cursor = getData()
        if (cursor!!.moveToFirst()) {
            val colLatIndex = cursor.getColumnIndexOrThrow(COLUMN_LAT)
            val colLongIndex = cursor.getColumnIndexOrThrow(COLUMN_LONG)

            array.add(
                Point(
                    cursor.getDouble(colLatIndex),
                    cursor.getDouble(colLongIndex)
                )
            )

            while (cursor.moveToNext()) {
                array.add(
                    Point(
                        cursor.getDouble(colLatIndex),
                        cursor.getDouble(colLongIndex)
                    )
                )
            }
        }

        cursor.close()
        return array
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "Data.db"
        const val TABLE_NAME = "data"
        const val COLUMN_LAT = "lat"
        const val COLUMN_LONG = "long"
    }
}