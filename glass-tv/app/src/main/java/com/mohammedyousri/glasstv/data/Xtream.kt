package com.mohammedyousri.glasstv.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.URLEncoder

/** Minimal JSON helpers on top of the platform's streaming reader. */
internal object JsonUtil {
    /** Reads one value into Map / List / String / null. Numbers and booleans become strings. */
    fun readValue(r: JsonReader): Any? = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val m = LinkedHashMap<String, Any?>()
            r.beginObject()
            while (r.hasNext()) m[r.nextName()] = readValue(r)
            r.endObject()
            m
        }
        JsonToken.BEGIN_ARRAY -> {
            val l = ArrayList<Any?>()
            r.beginArray()
            while (r.hasNext()) l += readValue(r)
            r.endArray()
            l
        }
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean().toString()
        JsonToken.NULL -> {
            r.nextNull()
            null
        }
        else -> {
            r.skipValue()
            null
        }
    }

    /**
     * Streams every object in a top-level array. Some panels return an object
     * keyed by id instead of an array, so that shape is accepted too.
     */
    fun forEachObject(reader: Reader, block: (Map<String, Any?>) -> Unit) {
        val r = JsonReader(reader)
        r.isLenient = true
        when (r.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray()
                while (r.hasNext()) {
                    @Suppress("UNCHECKED_CAST")
                    (readValue(r) as? Map<String, Any?>)?.let(block)
                }
                r.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                r.beginObject()
                while (r.hasNext()) {
                    r.nextName()
                    @Suppress("UNCHECKED_CAST")
                    (readValue(r) as? Map<String, Any?>)?.let(block)
                }
                r.endObject()
            }
            else -> r.skipValue()
        }
    }

    fun read(reader: Reader): Any? {
        val r = JsonReader(reader)
        r.isLenient = true
        return readValue(r)
    }
}

internal fun Map<String, Any?>.str(key: String): String = when (val v = this[key]) {
    null -> ""
    is String -> v
    is List<*> -> v.firstOrNull()?.toString().orEmpty()
    else -> ""
}

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.obj(key: String): Map<String, Any?> = this[key] as? Map<String, Any?> ?: emptyMap()

data class XtreamAccount(val status: String, val expiresAt: Long?, val maxConnections: Int?)

data class XtreamLogin(val server: String, val username: String, val password: String)

/** Client for the Xtream Codes "player_api.php" API that most IPTV providers expose. */
class XtreamClient(
    private val http: OkHttpClient,
    server: String,
    private val username: String,
    private val password: String,
    private val userAgent: String = "",
) {
    val base: String = normalizeServer(server)

    private fun api(action: String? = null, extra: String = ""): String {
        val u = URLEncoder.encode(username, "UTF-8")
        val p = URLEncoder.encode(password, "UTF-8")
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=$u&password=$p$a$extra"
    }

    private fun <T> get(url: String, block: (Reader) -> T): T {
        val req = Request.Builder().url(url).apply {
            if (userAgent.isNotBlank()) header("User-Agent", userAgent)
        }.build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body.byteStream()
            return InputStreamReader(body, Charsets.UTF_8).buffered(64 * 1024).use(block)
        }
    }

    fun authenticate(): XtreamAccount {
        @Suppress("UNCHECKED_CAST")
        val root = get(api()) { JsonUtil.read(it) } as? Map<String, Any?>
            ?: throw IOException("Unexpected server response")
        val info = root.obj("user_info")
        if (info.str("auth") == "0" || info.isEmpty()) throw SecurityException("Wrong username or password")
        return XtreamAccount(
            status = info.str("status"),
            expiresAt = info.str("exp_date").toLongOrNull()?.times(1000),
            maxConnections = info.str("max_connections").toIntOrNull(),
        )
    }

    private fun categories(action: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        get(api(action)) { r -> JsonUtil.forEachObject(r) { out[it.str("category_id")] = it.str("category_name") } }
        return out
    }

    fun liveStreams(sourceId: Long, onStream: (Stream) -> Unit) {
        val cats = categories("get_live_categories")
        get(api("get_live_streams")) { r ->
            JsonUtil.forEachObject(r) { parseLive(sourceId, it, cats)?.let(onStream) }
        }
    }

    fun movies(sourceId: Long, onStream: (Stream) -> Unit) {
        val cats = categories("get_vod_categories")
        get(api("get_vod_streams")) { r ->
            JsonUtil.forEachObject(r) { parseMovie(sourceId, it, cats)?.let(onStream) }
        }
    }

    fun series(sourceId: Long, onStream: (Stream) -> Unit) {
        val cats = categories("get_series_categories")
        get(api("get_series")) { r ->
            JsonUtil.forEachObject(r) { parseSeries(sourceId, it, cats)?.let(onStream) }
        }
    }

    fun seriesEpisodes(seriesId: String): List<Episode> =
        get(api("get_series_info", "&series_id=$seriesId")) { r -> parseEpisodes(JsonUtil.read(r)) }

    /** Returns plot / year / rating for a movie, or null if the server has nothing extra. */
    fun movieInfo(vodId: String): Map<String, String>? = runCatching {
        @Suppress("UNCHECKED_CAST")
        val root = get(api("get_vod_info", "&vod_id=$vodId")) { JsonUtil.read(it) } as? Map<String, Any?>
        val info = root?.obj("info") ?: return null
        mapOf(
            "plot" to info.str("plot").ifEmpty { info.str("description") },
            "year" to info.str("releasedate").take(4),
            "rating" to info.str("rating"),
            "genre" to info.str("genre"),
            "duration" to info.str("duration"),
            "backdrop" to info.str("backdrop_path"),
        )
    }.getOrNull()

    fun xmltvUrl(): String {
        val u = URLEncoder.encode(username, "UTF-8")
        val p = URLEncoder.encode(password, "UTF-8")
        return "$base/xmltv.php?username=$u&password=$p"
    }

    fun parseLive(sourceId: Long, o: Map<String, Any?>, cats: Map<String, String>): Stream? {
        val id = o.str("stream_id").ifEmpty { return null }
        return Stream(
            key = "x$sourceId:live:$id",
            sourceId = sourceId,
            kind = Kind.LIVE,
            name = o.str("name").trim(),
            number = o.str("num").toIntOrNull() ?: 0,
            group = cats[o.str("category_id")].orEmpty(),
            logo = o.str("stream_icon"),
            url = "$base/live/$username/$password/$id.ts",
            tvgId = o.str("epg_channel_id"),
            remoteId = id,
        )
    }

    fun parseMovie(sourceId: Long, o: Map<String, Any?>, cats: Map<String, String>): Stream? {
        val id = o.str("stream_id").ifEmpty { return null }
        val ext = o.str("container_extension").ifEmpty { "mp4" }
        return Stream(
            key = "x$sourceId:movie:$id",
            sourceId = sourceId,
            kind = Kind.MOVIE,
            name = o.str("name").trim(),
            number = o.str("num").toIntOrNull() ?: 0,
            group = cats[o.str("category_id")].orEmpty(),
            logo = o.str("stream_icon"),
            url = "$base/movie/$username/$password/$id.$ext",
            remoteId = id,
            rating = o.str("rating").takeIf { it != "0" }.orEmpty(),
            year = o.str("year").ifEmpty { o.str("releaseDate").take(4) },
            plot = o.str("plot"),
            extension = ext,
        )
    }

    fun parseSeries(sourceId: Long, o: Map<String, Any?>, cats: Map<String, String>): Stream? {
        val id = o.str("series_id").ifEmpty { return null }
        return Stream(
            key = "x$sourceId:series:$id",
            sourceId = sourceId,
            kind = Kind.SERIES,
            name = o.str("name").trim(),
            number = o.str("num").toIntOrNull() ?: 0,
            group = cats[o.str("category_id")].orEmpty(),
            logo = o.str("cover"),
            remoteId = id,
            rating = o.str("rating").takeIf { it != "0" }.orEmpty(),
            year = o.str("releaseDate").ifEmpty { o.str("release_date") }.take(4),
            plot = o.str("plot"),
        )
    }

    fun parseEpisodes(root: Any?): List<Episode> {
        @Suppress("UNCHECKED_CAST")
        val episodes = (root as? Map<String, Any?>)?.get("episodes") ?: return emptyList()
        // Usually {"1": [..], "2": [..]}; some panels send a flat list or a list of lists.
        val flat: List<Any?> = when (episodes) {
            is Map<*, *> -> episodes.values.flatMap { (it as? List<*>) ?: emptyList() }
            is List<*> -> episodes.flatMap { (it as? List<*>) ?: listOf(it) }
            else -> emptyList()
        }
        return flat.mapNotNull { e ->
            @Suppress("UNCHECKED_CAST")
            val m = e as? Map<String, Any?> ?: return@mapNotNull null
            val id = m.str("id").ifEmpty { return@mapNotNull null }
            val ext = m.str("container_extension").ifEmpty { "mp4" }
            val info = m.obj("info")
            Episode(
                id = id,
                title = m.str("title"),
                season = m.str("season").toIntOrNull() ?: 1,
                number = m.str("episode_num").toIntOrNull() ?: 0,
                url = "$base/series/$username/$password/$id.$ext",
                plot = info.str("plot"),
                image = info.str("movie_image"),
                durationSecs = info.str("duration_secs").toIntOrNull() ?: 0,
            )
        }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim()
            if (!s.contains("://")) s = "http://$s"
            s = s.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/xmltv.php")
            return s.trimEnd('/')
        }

        /** Recognizes "http://host/get.php?username=..&password=.." playlist links. */
        fun loginFromPlaylistUrl(url: String): XtreamLogin? {
            val trimmed = url.trim()
            if (!trimmed.contains("get.php", ignoreCase = true)) return null
            val query = trimmed.substringAfter('?', "")
            val params = query.split('&').associate {
                it.substringBefore('=').lowercase() to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            val user = params["username"].orEmpty()
            val pass = params["password"].orEmpty()
            if (user.isEmpty() || pass.isEmpty()) return null
            return XtreamLogin(normalizeServer(trimmed), user, pass)
        }
    }
}
