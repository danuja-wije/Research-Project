package com.example.geotag

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Helper for storing user, room calibration corners, and light settings.
 */
class RoomDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    data class RoomEntry(val roomName: String)
    data class RoomCorners(val roomName: String, val corners: List<Pair<Float, Float>>)

    companion object {
        private const val DATABASE_NAME = "GeoTag.db"
        private const val DATABASE_VERSION = 3

        // Users table
        private const val TABLE_USERS = "Users"
        private const val COLUMN_USER_ID = "userId"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"

        // Calibrated rooms table
        private const val TABLE_ROOMS = "CalibratedRooms"
        private const val COLUMN_ROOM_NAME = "roomName"
        private const val COLUMN_USER_FK    = "userId"
        private val LAT_COLUMNS = arrayOf("lat1","lat2","lat3","lat4")
        private val LON_COLUMNS = arrayOf("lon1","lon2","lon3","lon4")

        // Lights table
        private const val TABLE_LIGHTS = "Lights"
        private const val COLUMN_LIGHT_NAME     = "lightName"
        private const val COLUMN_BRIGHTNESS     = "brightness"
        private const val COLUMN_MANUAL_CONTROL = "manualControl"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Users
        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
              $COLUMN_USER_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
              $COLUMN_EMAIL     TEXT UNIQUE,
              $COLUMN_PASSWORD  TEXT
            )
        """.trimIndent())

        // Calibrated rooms with 4 corners
        db.execSQL("""
            CREATE TABLE $TABLE_ROOMS (
              $COLUMN_ROOM_NAME TEXT PRIMARY KEY,
              $COLUMN_USER_FK   INTEGER,
              ${LAT_COLUMNS[0]} REAL, ${LON_COLUMNS[0]} REAL,
              ${LAT_COLUMNS[1]} REAL, ${LON_COLUMNS[1]} REAL,
              ${LAT_COLUMNS[2]} REAL, ${LON_COLUMNS[2]} REAL,
              ${LAT_COLUMNS[3]} REAL, ${LON_COLUMNS[3]} REAL,
              FOREIGN KEY($COLUMN_USER_FK) REFERENCES $TABLE_USERS($COLUMN_USER_ID)
            )
        """.trimIndent())

        // Lights
        db.execSQL("""
            CREATE TABLE $TABLE_LIGHTS (
              $COLUMN_LIGHT_NAME     TEXT,
              $COLUMN_ROOM_NAME      TEXT,
              $COLUMN_BRIGHTNESS     INTEGER,
              $COLUMN_MANUAL_CONTROL INTEGER,
              FOREIGN KEY($COLUMN_ROOM_NAME) REFERENCES $TABLE_ROOMS($COLUMN_ROOM_NAME)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LIGHTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ROOMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    // ----- User management -----

    fun registerUser(email: String, password: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, password)
        }
        return db.insert(TABLE_USERS, null, values)
    }

    fun loginUser(email: String, password: String): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_USER_ID FROM $TABLE_USERS WHERE $COLUMN_EMAIL=? AND $COLUMN_PASSWORD=?",
            arrayOf(email, password)
        )
        val id = if (cursor.moveToFirst()) cursor.getInt(0) else -1
        cursor.close()
        return id
    }

    // ----- Room calibration -----

    /**
     * Save exactly four corner points (lat,lon) for this room.
     */
    fun saveCalibratedRoom(
        userId: Int,
        roomName: String,
        corners: List<Pair<Float, Float>>
    ) {
        require(corners.size == 4) { "Must supply exactly 4 corners" }
        val cv = ContentValues().apply {
            put(COLUMN_ROOM_NAME, roomName)
            put(COLUMN_USER_FK, userId)
            for (i in 0 until 4) {
                put(LAT_COLUMNS[i], corners[i].first)
                put(LON_COLUMNS[i], corners[i].second)
            }
        }
        writableDatabase.insertWithOnConflict(
            TABLE_ROOMS,
            null,
            cv,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Retrieve just the list of room names calibrated by this user.
     */
    fun getCalibratedRooms(userId: String): List<RoomEntry> {
        val db = readableDatabase
        val rooms = mutableListOf<RoomEntry>()
        val cursor = db.query(
            TABLE_ROOMS, arrayOf(COLUMN_ROOM_NAME),
            "$COLUMN_USER_FK=?", arrayOf(userId),
            null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                rooms += RoomEntry(it.getString(it.getColumnIndexOrThrow(COLUMN_ROOM_NAME)))
            }
        }
        return rooms
    }

    /**
     * Retrieve the four corner points for a given room.
     */
    fun getRoomCorners(roomName: String): List<Pair<Float, Float>>? {
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_ROOMS,
            LAT_COLUMNS + LON_COLUMNS,
            "$COLUMN_ROOM_NAME=?", arrayOf(roomName),
            null, null, null
        )
        if (!cursor.moveToFirst()) { cursor.close(); return null }
        val corners = (0 until 4).map { i ->
            cursor.getFloat(cursor.getColumnIndexOrThrow(LAT_COLUMNS[i])) to
            cursor.getFloat(cursor.getColumnIndexOrThrow(LON_COLUMNS[i]))
        }
        cursor.close()
        return corners
    }

    /**
     * Delete the calibration record for a given room and user.
     */
    fun deleteCalibratedRoom(userId: Int, roomName: String) {
        writableDatabase.delete(
            TABLE_ROOMS,
            "$COLUMN_USER_FK = ? AND $COLUMN_ROOM_NAME = ?",
            arrayOf(userId.toString(), roomName)
        )
    }

    // ----- Light management -----

    fun saveLights(roomName: String, lights: List<RoomSetupActivity.Light>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_LIGHTS, "$COLUMN_ROOM_NAME=?", arrayOf(roomName))
            for (light in lights) {
                val values = ContentValues().apply {
                    put(COLUMN_ROOM_NAME, roomName)
                    put(COLUMN_LIGHT_NAME, light.name)
                    put(COLUMN_BRIGHTNESS, light.brightness)
                    put(COLUMN_MANUAL_CONTROL, if (light.manualControl) 1 else 0)
                }
                db.insert(TABLE_LIGHTS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadLights(roomName: String): List<RoomSetupActivity.Light> {
        val db = readableDatabase
        val lights = mutableListOf<RoomSetupActivity.Light>()
        val cursor = db.query(
            TABLE_LIGHTS, null,
            "$COLUMN_ROOM_NAME=?", arrayOf(roomName),
            null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                lights += RoomSetupActivity.Light(
                    it.getString(it.getColumnIndexOrThrow(COLUMN_LIGHT_NAME)),
                    it.getInt(it.getColumnIndexOrThrow(COLUMN_BRIGHTNESS)),
                    it.getInt(it.getColumnIndexOrThrow(COLUMN_MANUAL_CONTROL)) == 1
                )
            }
        }
        return lights
    }
}