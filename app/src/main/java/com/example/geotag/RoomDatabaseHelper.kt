package com.example.geotag
import android.database.sqlite.SQLiteException



import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RoomDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    data class Room(
        val roomName: String,
        val minLat: Float,
        val maxLat: Float,
        val minLon: Float,
        val maxLon: Float
    )

    companion object {
        private const val DATABASE_NAME = "GeoTag.db"
        private const val DATABASE_VERSION = 3

        // User table
        private const val TABLE_USERS = "Users"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"

        // Calibrated rooms table
        private const val TABLE_ROOMS = "CalibratedRooms"
        private const val COLUMN_ROOM_NAME = "roomName"
        private const val COLUMN_MIN_LAT = "minLat"
        private const val COLUMN_MAX_LAT = "maxLat"
        private const val COLUMN_MIN_LON = "minLon"
        private const val COLUMN_MAX_LON = "maxLon"
        private const val COLUMN_USER_ID = "userId"

        // Legacy corner columns in CalibratedRooms table
        private const val COLUMN_LAT1 = "lat1"
        private const val COLUMN_LON1 = "lon1"
        private const val COLUMN_LAT2 = "lat2"
        private const val COLUMN_LON2 = "lon2"
        private const val COLUMN_LAT3 = "lat3"
        private const val COLUMN_LON3 = "lon3"
        private const val COLUMN_LAT4 = "lat4"
        private const val COLUMN_LON4 = "lon4"

        // Lights table
        private const val TABLE_LIGHTS = "Lights"
        private const val COLUMN_LIGHT_NAME = "lightName"
        private const val COLUMN_BRIGHTNESS = "brightness"
        private const val COLUMN_MANUAL_CONTROL = "manualControl"

        // Polygon corners table
        private const val TABLE_ROOM_POLYGONS = "RoomPolygons"
        private const val COLUMN_POLY_ROOM_NAME = "roomName"
        private const val COLUMN_POLY_POINT_INDEX = "pointIndex"
        private const val COLUMN_POLY_LAT = "lat"
        private const val COLUMN_POLY_LON = "lon"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create Users table
        val createUserTableQuery = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_EMAIL TEXT UNIQUE,
                $COLUMN_PASSWORD TEXT
            )
        """.trimIndent()

        // Create CalibratedRooms table
        val createRoomsTableQuery = """
            CREATE TABLE $TABLE_ROOMS (
                $COLUMN_ROOM_NAME TEXT PRIMARY KEY,
                $COLUMN_USER_ID INTEGER,
                $COLUMN_MIN_LAT REAL,
                $COLUMN_MAX_LAT REAL,
                $COLUMN_MIN_LON REAL,
                $COLUMN_MAX_LON REAL,
                FOREIGN KEY ($COLUMN_USER_ID) REFERENCES $TABLE_USERS($COLUMN_USER_ID)
            )
        """.trimIndent()

        // Create Lights table
        val createLightsTableQuery = """
            CREATE TABLE $TABLE_LIGHTS (
                $COLUMN_LIGHT_NAME TEXT,
                $COLUMN_ROOM_NAME TEXT,
                $COLUMN_BRIGHTNESS INTEGER,
                $COLUMN_MANUAL_CONTROL INTEGER,
                FOREIGN KEY ($COLUMN_ROOM_NAME) REFERENCES $TABLE_ROOMS($COLUMN_ROOM_NAME)
            )
        """.trimIndent()

        db.execSQL(createUserTableQuery)
        db.execSQL(createRoomsTableQuery)
        db.execSQL(createLightsTableQuery)

        // Create RoomPolygons table for storing calibration corners
        val createPolyTableQuery = """
            CREATE TABLE $TABLE_ROOM_POLYGONS (
                $COLUMN_POLY_ROOM_NAME TEXT,
                $COLUMN_POLY_POINT_INDEX INTEGER,
                $COLUMN_POLY_LAT REAL,
                $COLUMN_POLY_LON REAL,
                PRIMARY KEY($COLUMN_POLY_ROOM_NAME, $COLUMN_POLY_POINT_INDEX),
                FOREIGN KEY($COLUMN_POLY_ROOM_NAME) REFERENCES $TABLE_ROOMS($COLUMN_ROOM_NAME)
            )
        """.trimIndent()
        db.execSQL(createPolyTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ROOM_POLYGONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LIGHTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ROOMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    /**
     * Optional example if you store BSSID in the same table as roomName.
     * If you have a separate BSSID column, adjust the query accordingly.
     */
    fun getRoomLabelForBSSID(bssid: String): String? {
        val db = this.readableDatabase
        val cursor: Cursor = db.query(
            TABLE_ROOMS,
            arrayOf(COLUMN_ROOM_NAME),
            "$COLUMN_ROOM_NAME = ?",
            arrayOf(bssid),
            null, null, null
        )
        var label: String? = null
        if (cursor.moveToFirst()) {
            label = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROOM_NAME))
        }
        cursor.close()
        return label
    }

    fun deleteCalibratedRoom(userId: Int, roomName: String) {
        val db = writableDatabase
        db.delete(
            TABLE_ROOMS,
            "$COLUMN_USER_ID=? AND $COLUMN_ROOM_NAME=?",
            arrayOf(userId.toString(), roomName)
        )
        db.close()
    }

    // User management
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
        val query = "SELECT $COLUMN_USER_ID FROM $TABLE_USERS WHERE $COLUMN_EMAIL = ? AND $COLUMN_PASSWORD = ?"
        val cursor = db.rawQuery(query, arrayOf(email, password))
        return if (cursor.moveToFirst()) {
            val userId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USER_ID))
            cursor.close()
            userId
        } else {
            cursor.close()
            -1 // Invalid credentials
        }
    }

    /**
     * Saves a room bounding box, ensuring minLat <= maxLat and minLon <= maxLon.
     */
    fun saveCalibratedRoom(userId: Int, roomName: String, lat1: Float, lat2: Float, lon1: Float, lon2: Float) {
        val db = writableDatabase

        // Ensure correct bounding box order
        val minLat = minOf(lat1, lat2)
        val maxLat = maxOf(lat1, lat2)
        val minLon = minOf(lon1, lon2)
        val maxLon = maxOf(lon1, lon2)

        val values = ContentValues().apply {
            put(COLUMN_USER_ID, userId)
            put(COLUMN_ROOM_NAME, roomName)
            put(COLUMN_MIN_LAT, minLat)
            put(COLUMN_MAX_LAT, maxLat)
            put(COLUMN_MIN_LON, minLon)
            put(COLUMN_MAX_LON, maxLon)
        }
        db.insertWithOnConflict(TABLE_ROOMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Retrieves all rooms for the given userId.
     * Uses polygon corners if available, otherwise falls back to stored boundaries.
     */
    fun getCalibratedRooms(userId: String): List<Room> {
        val db = readableDatabase
        val rooms = mutableListOf<Room>()
        // Query only room names for this user
        val cursor = db.query(
            TABLE_ROOMS,
            arrayOf(COLUMN_ROOM_NAME),
            "$COLUMN_USER_ID = ?",
            arrayOf(userId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val name = it.getString(it.getColumnIndexOrThrow(COLUMN_ROOM_NAME))
                    // Try polygon corners first
                    val polygon = getRoomPolygon(name)
                    val (minLat, maxLat, minLon, maxLon) = if (polygon != null && polygon.size >= 4) {
                        val lats = polygon.map { it.lat }
                        val lons = polygon.map { it.lon }
                        listOf(
                            lats.minOrNull() ?: 0f,
                            lats.maxOrNull() ?: 0f,
                            lons.minOrNull() ?: 0f,
                            lons.maxOrNull() ?: 0f
                        )
                    } else {
                        // Fallback to stored axis-aligned boundaries
                        getRoomBoundaries(name)?.let { (minPair, maxPair) ->
                            listOf(minPair.first, maxPair.first, minPair.second, maxPair.second)
                        } ?: listOf(0f, 0f, 0f, 0f)
                    }
                    rooms.add(Room(name, minLat, maxLat, minLon, maxLon))
                } while (it.moveToNext())
            }
        }
        return rooms
    }

    /**
     * Retrieves min/max latitude and longitude for the given room.
     * Tries stored minLat/minLon columns first, falls back to four corner columns if needed.
     */
    fun getRoomBoundaries(roomName: String): Pair<Pair<Float, Float>, Pair<Float, Float>>? {
        val db = readableDatabase
        // Attempt to read legacy min/max columns
        try {
            val cursor = db.query(
                TABLE_ROOMS,
                arrayOf(COLUMN_MIN_LAT, COLUMN_MAX_LAT, COLUMN_MIN_LON, COLUMN_MAX_LON),
                "$COLUMN_ROOM_NAME = ?",
                arrayOf(roomName),
                null, null, null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val minLat = it.getFloat(it.getColumnIndexOrThrow(COLUMN_MIN_LAT))
                    val maxLat = it.getFloat(it.getColumnIndexOrThrow(COLUMN_MAX_LAT))
                    val minLon = it.getFloat(it.getColumnIndexOrThrow(COLUMN_MIN_LON))
                    val maxLon = it.getFloat(it.getColumnIndexOrThrow(COLUMN_MAX_LON))
                    return Pair(Pair(minLat, minLon), Pair(maxLat, maxLon))
                }
            }
        } catch (e: SQLiteException) {
            // missing columns/table: ignore
        }

        // Fallback to four corner columns
        val cursor2 = db.query(
            TABLE_ROOMS,
            arrayOf(
                COLUMN_LAT1, COLUMN_LON1,
                COLUMN_LAT2, COLUMN_LON2,
                COLUMN_LAT3, COLUMN_LON3,
                COLUMN_LAT4, COLUMN_LON4
            ),
            "$COLUMN_ROOM_NAME = ?",
            arrayOf(roomName),
            null, null, null
        )
        cursor2.use {
            if (it.moveToFirst()) {
                val lats = listOf(
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LAT1)),
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LAT2)),
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LAT3)),
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LAT4))
                )
                val lons = listOf(
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LON1)),
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LON2)),
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LON3)),
                    it.getFloat(it.getColumnIndexOrThrow(COLUMN_LON4))
                )
                val minLat = lats.minOrNull() ?: return null
                val maxLat = lats.maxOrNull() ?: return null
                val minLon = lons.minOrNull() ?: return null
                val maxLon = lons.maxOrNull() ?: return null
                return Pair(Pair(minLat, minLon), Pair(maxLat, maxLon))
            }
        }
        return null
    }

    // Light management
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
            TABLE_LIGHTS,
            null,
            "$COLUMN_ROOM_NAME=?",
            arrayOf(roomName),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val lightName = it.getString(it.getColumnIndexOrThrow(COLUMN_LIGHT_NAME))
                    val brightness = it.getInt(it.getColumnIndexOrThrow(COLUMN_BRIGHTNESS))
                    val manualControl = it.getInt(it.getColumnIndexOrThrow(COLUMN_MANUAL_CONTROL)) == 1
                    lights.add(RoomSetupActivity.Light(lightName, brightness, manualControl))
                } while (it.moveToNext())
            }
        }
        return lights
    }

    /**
     * Saves a polygon (ordered corners) for the given room.
     */
    fun saveRoomPolygon(roomName: String, polygon: List<LatLngPoint>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Clear existing corners
            db.delete(TABLE_ROOM_POLYGONS, "$COLUMN_POLY_ROOM_NAME=?", arrayOf(roomName))
            // Insert each corner with its index to preserve order
            val values = ContentValues()
            for ((index, point) in polygon.withIndex()) {
                values.clear()
                values.put(COLUMN_POLY_ROOM_NAME, roomName)
                values.put(COLUMN_POLY_POINT_INDEX, index)
                values.put(COLUMN_POLY_LAT, point.lat)
                values.put(COLUMN_POLY_LON, point.lon)
                db.insert(TABLE_ROOM_POLYGONS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Retrieves the ordered polygon corners for the given room.
     */
    fun getRoomPolygon(roomName: String): List<LatLngPoint>? {
        val db = readableDatabase
        val corners = mutableListOf<LatLngPoint>()
        // Try querying the polygon table; if it doesn't exist, fallback
        val cursor = try {
            db.query(
                TABLE_ROOM_POLYGONS,
                arrayOf(COLUMN_POLY_POINT_INDEX, COLUMN_POLY_LAT, COLUMN_POLY_LON),
                "$COLUMN_POLY_ROOM_NAME = ?",
                arrayOf(roomName),
                null,
                null,
                "$COLUMN_POLY_POINT_INDEX ASC"
            )
        } catch (e: SQLiteException) {
            // Table doesn't exist or other DB error
            return null
        }

        cursor.use {
            if (!it.moveToFirst()) return null
            do {
                val lat = it.getFloat(it.getColumnIndexOrThrow(COLUMN_POLY_LAT))
                val lon = it.getFloat(it.getColumnIndexOrThrow(COLUMN_POLY_LON))
                corners.add(LatLngPoint(lat, lon))
            } while (it.moveToNext())
        }
        return corners
    }
}