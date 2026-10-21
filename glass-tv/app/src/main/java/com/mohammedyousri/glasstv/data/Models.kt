package com.mohammedyousri.glasstv.data

enum class SourceType { M3U, XTREAM, DEMO }

/** A playlist provider configured by the user. */
data class Source(
    val id: Long = 0,
    val name: String,
    val type: SourceType,
    /** M3U playlist URL, or Xtream server base URL. */
    val url: String,
    val username: String = "",
    val password: String = "",
    /** Optional XMLTV guide URL. Xtream sources fall back to the server's xmltv.php. */
    val epgUrl: String = "",
    val userAgent: String = "",
    val lastRefresh: Long = 0,
    val lastEpgRefresh: Long = 0,
)

enum class Kind { LIVE, MOVIE, SERIES }

/**
 * Anything that shows up in the TV app: a live channel, a movie, or a series.
 * Series aren't playable directly; their episodes are fetched on demand.
 */
data class Stream(
    val id: Long = 0,
    /** Stable identity used for favorites and history; survives playlist refreshes. */
    val key: String,
    val sourceId: Long,
    val kind: Kind,
    val name: String,
    val number: Int = 0,
    val group: String = "",
    val logo: String = "",
    val url: String = "",
    val tvgId: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    /** Provider id (Xtream stream_id / series_id). */
    val remoteId: String = "",
    val rating: String = "",
    val year: String = "",
    val plot: String = "",
    /** File extension for Xtream movies (mp4, mkv...). */
    val extension: String = "",
)

data class Program(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String = "",
) {
    fun progress(now: Long): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)

    fun isLive(now: Long) = now in start until stop
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val plot: String = "",
    val image: String = "",
    val durationSecs: Int = 0,
)

data class HistoryEntry(
    val key: String,
    val kind: Kind,
    val watchedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
)

/** Lowercased, trimmed id used to join streams to XMLTV channels. */
fun epgKey(raw: String): String = raw.trim().lowercase()

/** Normalizes a display name so "BBC One HD" and "bbc one" can be matched to a guide channel. */
fun normalizeName(name: String): String {
    val lower = name.lowercase()
        .replace(Regex("""\[[^]]*]|\([^)]*\)"""), " ")
        .replace(Regex("""\b(fhd|uhd|hd|sd|4k|hevc|h265|1080p|720p|backup)\b"""), " ")
    return lower.filter { it.isLetterOrDigit() }
}
