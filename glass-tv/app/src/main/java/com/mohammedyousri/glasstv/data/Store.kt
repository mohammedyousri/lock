package com.mohammedyousri.glasstv.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement

/**
 * Plain SQLite storage. Bulk imports go through compiled statements inside a
 * single transaction, which keeps big playlists and guides fast on the Mi Box's
 * eMMC storage.
 */
class Store(context: Context, name: String? = "glass.db") :
    SQLiteOpenHelper(context, name, null, VERSION) {

    init {
        // Lets the UI keep reading while a long guide import holds the write transaction.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE source(
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL,
                url TEXT NOT NULL, username TEXT, password TEXT, epg_url TEXT, user_agent TEXT,
                last_refresh INTEGER DEFAULT 0, last_epg INTEGER DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE stream(
                id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL, source_id INTEGER NOT NULL,
                kind INTEGER NOT NULL, name TEXT NOT NULL, number INTEGER, grp TEXT, logo TEXT,
                url TEXT, tvg_id TEXT, user_agent TEXT, referrer TEXT, remote_id TEXT,
                rating TEXT, year TEXT, plot TEXT, ext TEXT)""",
        )
        db.execSQL("CREATE INDEX stream_source ON stream(source_id, kind)")
        db.execSQL(
            """CREATE TABLE program(
                source_id INTEGER NOT NULL, channel TEXT NOT NULL, start INTEGER NOT NULL,
                stop INTEGER NOT NULL, title TEXT, descr TEXT)""",
        )
        db.execSQL("CREATE INDEX program_channel ON program(channel, stop)")
        db.execSQL("CREATE INDEX program_now ON program(start, stop)")
        db.execSQL("CREATE TABLE epg_alias(source_id INTEGER NOT NULL, name TEXT NOT NULL, channel TEXT NOT NULL)")
        db.execSQL("CREATE TABLE favorite(key TEXT PRIMARY KEY, kind INTEGER NOT NULL, added INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE history(key TEXT PRIMARY KEY, kind INTEGER NOT NULL, watched_at INTEGER NOT NULL,
                position INTEGER DEFAULT 0, duration INTEGER DEFAULT 0)""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf("source", "stream", "program", "epg_alias", "favorite", "history").forEach {
            db.execSQL("DROP TABLE IF EXISTS $it")
        }
        onCreate(db)
    }

    // ---- sources ----

    fun sources(): List<Source> = readableDatabase.rawQuery("SELECT * FROM source ORDER BY id", null).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    Source(
                        id = c.long("id"),
                        name = c.str("name"),
                        type = runCatching { SourceType.valueOf(c.str("type")) }.getOrDefault(SourceType.M3U),
                        url = c.str("url"),
                        username = c.str("username"),
                        password = c.str("password"),
                        epgUrl = c.str("epg_url"),
                        userAgent = c.str("user_agent"),
                        lastRefresh = c.long("last_refresh"),
                        lastEpgRefresh = c.long("last_epg"),
                    ),
                )
            }
        }
    }

    fun insertSource(s: Source): Long = writableDatabase.insertOrThrow("source", null, s.toValues())

    fun updateSource(s: Source) {
        writableDatabase.update("source", s.toValues(), "id=?", arrayOf(s.id.toString()))
    }

    fun deleteSource(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val args = arrayOf(id.toString())
            db.delete("stream", "source_id=?", args)
            db.delete("program", "source_id=?", args)
            db.delete("epg_alias", "source_id=?", args)
            db.delete("source", "id=?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Source.toValues() = ContentValues().apply {
        put("name", name)
        put("type", type.name)
        put("url", url)
        put("username", username)
        put("password", password)
        put("epg_url", epgUrl)
        put("user_agent", userAgent)
        put("last_refresh", lastRefresh)
        put("last_epg", lastEpgRefresh)
    }

    // ---- streams ----

    /** Replaces all streams of [kinds] for a source with whatever [produce] emits. */
    fun replaceStreams(sourceId: Long, kinds: Set<Kind>, produce: ((Stream) -> Unit) -> Unit): Int {
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            kinds.forEach { db.delete("stream", "source_id=? AND kind=?", arrayOf(sourceId.toString(), it.ordinal.toString())) }
            val st = db.compileStatement(
                """INSERT INTO stream(key, source_id, kind, name, number, grp, logo, url, tvg_id, user_agent,
                    referrer, remote_id, rating, year, plot, ext) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            )
            produce { s ->
                if (s.kind !in kinds) return@produce
                st.clearBindings()
                st.bindString(1, s.key)
                st.bindLong(2, sourceId)
                st.bindLong(3, s.kind.ordinal.toLong())
                st.bindString(4, s.name)
                st.bindLong(5, s.number.toLong())
                st.bindString(6, s.group)
                st.bindString(7, s.logo)
                st.bindString(8, s.url)
                st.bindString(9, s.tvgId)
                st.bindString(10, s.userAgent)
                st.bindString(11, s.referrer)
                st.bindString(12, s.remoteId)
                st.bindString(13, s.rating)
                st.bindString(14, s.year)
                st.bindString(15, s.plot)
                st.bindString(16, s.extension)
                st.executeInsert()
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun streams(kind: Kind): List<Stream> =
        readableDatabase.rawQuery("SELECT * FROM stream WHERE kind=? ORDER BY source_id, id", arrayOf(kind.ordinal.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.toStream()) } }

    fun channelEpgIds(sourceId: Long): Set<String> = readableDatabase.rawQuery(
        "SELECT DISTINCT tvg_id FROM stream WHERE source_id=? AND kind=0 AND tvg_id<>''",
        arrayOf(sourceId.toString()),
    ).use { c -> buildSet { while (c.moveToNext()) add(epgKey(c.getString(0))) } }

    fun channelNamesWithoutEpgId(sourceId: Long): Set<String> = readableDatabase.rawQuery(
        "SELECT name FROM stream WHERE source_id=? AND kind=0 AND (tvg_id IS NULL OR tvg_id='')",
        arrayOf(sourceId.toString()),
    ).use { c -> buildSet { while (c.moveToNext()) add(normalizeName(c.getString(0))) } }

    private fun Cursor.toStream() = Stream(
        id = long("id"),
        key = str("key"),
        sourceId = long("source_id"),
        kind = Kind.entries[int("kind")],
        name = str("name"),
        number = int("number"),
        group = str("grp"),
        logo = str("logo"),
        url = str("url"),
        tvgId = str("tvg_id"),
        userAgent = str("user_agent"),
        referrer = str("referrer"),
        remoteId = str("remote_id"),
        rating = str("rating"),
        year = str("year"),
        plot = str("plot"),
        extension = str("ext"),
    )

    // ---- guide ----

    /** Opens a guide import for one source. Call [GuideWriter.commit] when done. */
    fun guideWriter(sourceId: Long): GuideWriter = GuideWriter(writableDatabase, sourceId)

    class GuideWriter(private val db: SQLiteDatabase, private val sourceId: Long) {
        private val insertProgram: SQLiteStatement
        private val insertAlias: SQLiteStatement
        var programCount = 0
            private set

        init {
            db.beginTransaction()
            val args = arrayOf(sourceId.toString())
            db.delete("program", "source_id=?", args)
            db.delete("epg_alias", "source_id=?", args)
            insertProgram = db.compileStatement("INSERT INTO program(source_id, channel, start, stop, title, descr) VALUES(?,?,?,?,?,?)")
            insertAlias = db.compileStatement("INSERT INTO epg_alias(source_id, name, channel) VALUES(?,?,?)")
        }

        fun alias(normalizedName: String, channel: String) {
            insertAlias.bindLong(1, sourceId)
            insertAlias.bindString(2, normalizedName)
            insertAlias.bindString(3, channel)
            insertAlias.executeInsert()
        }

        fun program(p: Program) {
            insertProgram.bindLong(1, sourceId)
            insertProgram.bindString(2, p.channelId)
            insertProgram.bindLong(3, p.start)
            insertProgram.bindLong(4, p.stop)
            insertProgram.bindString(5, p.title)
            insertProgram.bindString(6, p.description)
            insertProgram.executeInsert()
            programCount++
        }

        fun commit() {
            db.setTransactionSuccessful()
            db.endTransaction()
        }

        fun abort() {
            db.endTransaction()
        }
    }

    /** normalized channel name -> guide channel id, for channels without a tvg-id. */
    fun epgAliases(): Map<String, String> =
        readableDatabase.rawQuery("SELECT name, channel FROM epg_alias", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    /** What's on right now, keyed by guide channel id. */
    fun nowPlaying(now: Long): Map<String, Program> = readableDatabase.rawQuery(
        "SELECT channel, start, stop, title, descr FROM program WHERE start<=? AND stop>?",
        arrayOf(now.toString(), now.toString()),
    ).use { c -> buildMap { while (c.moveToNext()) c.toProgram().let { put(it.channelId, it) } } }

    fun schedule(channel: String, from: Long, limit: Int): List<Program> = readableDatabase.rawQuery(
        "SELECT channel, start, stop, title, descr FROM program WHERE channel=? AND stop>? ORDER BY start LIMIT ?",
        arrayOf(channel, from.toString(), limit.toString()),
    ).use { c -> buildList { while (c.moveToNext()) add(c.toProgram()) } }

    fun pruneGuide(before: Long) {
        writableDatabase.delete("program", "stop<?", arrayOf(before.toString()))
    }

    private fun Cursor.toProgram() = Program(getString(0), getLong(1), getLong(2), getString(3).orEmpty(), getString(4).orEmpty())

    // ---- favorites & history ----

    fun favorites(): List<String> =
        readableDatabase.rawQuery("SELECT key FROM favorite ORDER BY added", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun setFavorite(key: String, kind: Kind, favorite: Boolean) {
        if (favorite) {
            writableDatabase.insertWithOnConflict(
                "favorite", null,
                ContentValues().apply {
                    put("key", key)
                    put("kind", kind.ordinal)
                    put("added", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } else {
            writableDatabase.delete("favorite", "key=?", arrayOf(key))
        }
    }

    fun history(limit: Int = 200): List<HistoryEntry> = readableDatabase.rawQuery(
        "SELECT key, kind, watched_at, position, duration FROM history ORDER BY watched_at DESC LIMIT ?",
        arrayOf(limit.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(HistoryEntry(c.getString(0), Kind.entries[c.getInt(1)], c.getLong(2), c.getLong(3), c.getLong(4)))
        }
    }

    fun saveHistory(e: HistoryEntry) {
        writableDatabase.insertWithOnConflict(
            "history", null,
            ContentValues().apply {
                put("key", e.key)
                put("kind", e.kind.ordinal)
                put("watched_at", e.watchedAt)
                put("position", e.positionMs)
                put("duration", e.durationMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    companion object {
        const val VERSION = 1
    }
}

private fun Cursor.idx(name: String) = getColumnIndexOrThrow(name)
private fun Cursor.str(name: String): String = getString(idx(name)).orEmpty()
private fun Cursor.long(name: String): Long = getLong(idx(name))
private fun Cursor.int(name: String): Int = getInt(idx(name))
