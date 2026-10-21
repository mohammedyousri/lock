package com.mohammedyousri.glasstv.data

import java.io.BufferedReader

/**
 * Streaming parser for M3U / M3U8 IPTV playlists, including the common
 * extensions: tvg-* attributes, group-title, #EXTGRP, #EXTVLCOPT headers and
 * Kodi-style "url|User-Agent=..." suffixes.
 */
object M3uParser {

    data class Entry(
        val name: String,
        val url: String,
        val tvgId: String = "",
        val tvgName: String = "",
        val logo: String = "",
        val group: String = "",
        val number: Int = 0,
        val userAgent: String = "",
        val referrer: String = "",
    )

    data class Header(val epgUrls: List<String>)

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)=("([^"]*)"|'([^']*)'|([^\s,]+))""")

    /**
     * Parses [reader] and calls [onEntry] for every playable entry, so huge
     * playlists never need to be held in memory twice.
     */
    fun parse(reader: BufferedReader, onEntry: (Entry) -> Unit): Header {
        val epgUrls = mutableListOf<String>()
        var pendingInfo: String? = null
        var pendingGroup = ""
        var pendingUa = ""
        var pendingRef = ""
        var first = true

        reader.lineSequence().forEach { rawLine ->
            var line = rawLine.trim()
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter("#EXTM3U", ""))
                    listOf("url-tvg", "x-tvg-url", "tvg-url").forEach { k ->
                        attrs[k]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let(epgUrls::addAll)
                    }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingInfo = line.substringAfter(':', "")
                }
                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':', "").trim()
                }
                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val opt = line.substringAfter(':', "")
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> pendingUa = value
                        "http-referrer", "http-referer" -> pendingRef = value
                    }
                }
                line.startsWith("#") -> Unit // other directives (#KODIPROP, comments...)
                else -> {
                    val info = pendingInfo
                    if (info != null || line.contains("://")) {
                        onEntry(buildEntry(info.orEmpty(), line, pendingGroup, pendingUa, pendingRef))
                    }
                    pendingInfo = null
                    pendingGroup = ""
                    pendingUa = ""
                    pendingRef = ""
                }
            }
        }
        return Header(epgUrls.distinct())
    }

    fun parseAll(reader: BufferedReader): Pair<Header, List<Entry>> {
        val entries = ArrayList<Entry>()
        val header = parse(reader) { entries += it }
        return header to entries
    }

    private fun buildEntry(info: String, urlLine: String, extGroup: String, vlcUa: String, vlcRef: String): Entry {
        // The title is everything after the first comma that sits outside quotes.
        var inQuote = false
        var split = -1
        for (i in info.indices) {
            val c = info[i]
            if (c == '"') inQuote = !inQuote
            if (c == ',' && !inQuote) {
                split = i
                break
            }
        }
        val attrPart = if (split >= 0) info.substring(0, split) else info
        val title = if (split >= 0) info.substring(split + 1).trim() else ""
        val attrs = parseAttributes(attrPart)

        var url = urlLine
        var ua = vlcUa
        var ref = vlcRef
        // Kodi style: http://host/stream.m3u8|User-Agent=Foo&Referer=http://bar
        val pipe = url.indexOf('|')
        if (pipe > 0) {
            val opts = url.substring(pipe + 1)
            url = url.substring(0, pipe)
            opts.split('&').forEach { kv ->
                val k = kv.substringBefore('=').trim().lowercase()
                val v = java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8")
                when (k) {
                    "user-agent" -> ua = v
                    "referer", "referrer" -> ref = v
                }
            }
        }

        val tvgName = attrs["tvg-name"].orEmpty()
        val name = title.ifEmpty { tvgName }.ifEmpty { url.substringAfterLast('/') }
        val number = (attrs["tvg-chno"] ?: attrs["channel-number"] ?: attrs["tvg-num"])
            ?.trim()?.toIntOrNull() ?: 0
        return Entry(
            name = name,
            url = url.trim(),
            tvgId = attrs["tvg-id"].orEmpty().trim(),
            tvgName = tvgName,
            logo = (attrs["tvg-logo"] ?: attrs["logo"]).orEmpty().trim(),
            group = (attrs["group-title"] ?: extGroup).trim(),
            number = number,
            userAgent = ua.ifEmpty { attrs["user-agent"].orEmpty() },
            referrer = ref,
        )
    }

    internal fun parseAttributes(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        attrRegex.findAll(text).forEach { m ->
            val key = m.groupValues[1].lowercase()
            val value = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: ""
            out.putIfAbsent(key, value)
        }
        return out
    }

    /** Guesses whether an M3U entry is a movie (Xtream "m3u_plus" exports mix them in). */
    fun kindOf(entry: Entry): Kind {
        val u = entry.url.lowercase()
        return when {
            "/movie/" in u -> Kind.MOVIE
            "/series/" in u -> Kind.MOVIE
            else -> Kind.LIVE
        }
    }
}
