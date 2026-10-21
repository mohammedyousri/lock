package com.mohammedyousri.glasstv.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Streaming XMLTV guide parser. Guides can be hundreds of megabytes, so it
 * never builds a DOM: it reports channels and in-window programmes through
 * callbacks and lets the caller decide what to keep.
 */
class XmltvParser(
    private val windowStart: Long,
    private val windowEnd: Long,
) {
    interface Sink {
        /** Called for each <channel>; ids are already passed through [epgKey]. */
        fun onChannel(id: String, displayNames: List<String>) {}

        /** Return true when programmes for this channel id should be kept. */
        fun wants(channelId: String): Boolean = true

        fun onProgram(program: Program)
    }

    fun parse(input: InputStream, sink: Sink) {
        val stream = maybeGunzip(input)
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(stream, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser, sink)
                    "programme" -> readProgramme(parser, sink)
                }
            }
            event = parser.next()
        }
    }

    private fun readChannel(p: XmlPullParser, sink: Sink) {
        val id = epgKey(p.getAttributeValue(null, "id").orEmpty())
        val names = ArrayList<String>(2)
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.START_TAG && p.name == "display-name") {
                p.nextText().trim().takeIf { it.isNotEmpty() }?.let(names::add)
            }
        }
        if (id.isNotEmpty()) sink.onChannel(id, names)
    }

    private fun readProgramme(p: XmlPullParser, sink: Sink) {
        val channel = epgKey(p.getAttributeValue(null, "channel").orEmpty())
        val start = parseTime(p.getAttributeValue(null, "start"))
        val stop = parseTime(p.getAttributeValue(null, "stop"))
        val keep = channel.isNotEmpty() && start != null && stop != null &&
            stop > windowStart && start < windowEnd && sink.wants(channel)
        if (!keep) {
            skip(p)
            return
        }
        var title = ""
        var desc = ""
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.START_TAG) {
                when (p.name) {
                    "title" -> if (title.isEmpty()) title = p.nextText().trim()
                    "desc" -> if (desc.isEmpty()) desc = p.nextText().trim()
                }
            }
        }
        sink.onProgram(Program(channel, start!!, stop!!, title, desc))
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    companion object {
        fun maybeGunzip(input: InputStream): InputStream {
            val buffered = if (input.markSupported()) input else BufferedInputStream(input, 64 * 1024)
            buffered.mark(2)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()
            return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024), 64 * 1024) else buffered
        }

        /**
         * Parses XMLTV timestamps like "20240131183000 +0100" (offset optional,
         * UTC assumed) into epoch milliseconds without allocating formatters.
         */
        fun parseTime(raw: String?): Long? {
            if (raw == null) return null
            val s = raw.trim()
            if (s.length < 12) return null
            fun num(from: Int, len: Int): Int? {
                var v = 0
                for (i in from until from + len) {
                    if (i >= s.length) return null
                    val c = s[i]
                    if (c !in '0'..'9') return null
                    v = v * 10 + (c - '0')
                }
                return v
            }
            val year = num(0, 4) ?: return null
            val month = num(4, 2) ?: return null
            val day = num(6, 2) ?: return null
            val hour = num(8, 2) ?: return null
            val minute = num(10, 2) ?: return null
            val second = if (s.length >= 14) num(12, 2) ?: 0 else 0

            var offsetMinutes = 0
            val tz = s.substring(minOf(14, s.length)).trim()
            if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
                val h = tz.substring(1, 3).toIntOrNull()
                val m = tz.substring(3, 5).toIntOrNull()
                if (h != null && m != null) {
                    offsetMinutes = (h * 60 + m) * if (tz[0] == '-') -1 else 1
                }
            }
            val days = daysFromCivil(year, month, day)
            val seconds = days * 86400L + hour * 3600L + minute * 60L + second - offsetMinutes * 60L
            return seconds * 1000L
        }

        // Howard Hinnant's algorithm: days since 1970-01-01 for a proleptic Gregorian date.
        private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
            val y = if (m <= 2) y0 - 1 else y0
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (m + 9) % 12
            val doy = (153 * mp + 2) / 5 + d - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097L + doe - 719468L
        }
    }
}
