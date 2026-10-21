package com.mohammedyousri.glasstv.data

/**
 * Freely available public test streams, so the app can be tried before a real
 * playlist is added. No third-party channels are bundled.
 */
object Demo {
    private data class D(val name: String, val group: String, val url: String, val kind: Kind = Kind.LIVE, val year: String = "", val plot: String = "")

    private val items = listOf(
        D("Akamai Live", "Demo Live", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
        D("Big Buck Bunny", "Demo Live", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
        D("Tears of Steel", "Demo Live", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"),
        D("Sintel", "Demo Live", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"),
        D("HLS Test Pattern", "Test Signals", "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"),
        D(
            "Big Buck Bunny", "Blender Open Movies", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            Kind.MOVIE, "2008", "A giant rabbit takes revenge on three bullying rodents. An open movie by the Blender Foundation.",
        ),
        D(
            "Elephants Dream", "Blender Open Movies", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            Kind.MOVIE, "2006", "Two characters explore a surreal, ever-changing machine world. The first Blender open movie.",
        ),
        D(
            "Sintel", "Blender Open Movies", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            Kind.MOVIE, "2010", "A lonely girl searches for the baby dragon she once rescued.",
        ),
        D(
            "Tears of Steel", "Blender Open Movies", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            Kind.MOVIE, "2012", "Warriors and scientists in future Amsterdam try to save the world from destructive robots.",
        ),
    )

    fun streams(sourceId: Long): List<Stream> = items.mapIndexed { i, d ->
        Stream(
            key = "demo:${d.kind.name.lowercase()}:$i",
            sourceId = sourceId,
            kind = d.kind,
            name = d.name,
            number = if (d.kind == Kind.LIVE) i + 1 else 0,
            group = d.group,
            url = d.url,
            tvgId = if (d.kind == Kind.LIVE) "demo.$i" else "",
            year = d.year,
            plot = d.plot,
        )
    }

    private val shows = listOf(
        "Morning Loop" to "A relaxing loop to start the day.",
        "Open Movie Hour" to "Short films made with free and open-source tools.",
        "Signal Check" to "Colour bars, tones and test patterns.",
        "Studio Session" to "Behind the scenes of an animation studio.",
        "Night Drive" to "Ambient visuals for late hours.",
        "Weekend Special" to "Highlights from the week.",
    )

    /** A generated guide for the demo channels, aligned to the hour. */
    fun programs(now: Long): List<Program> {
        val hour = 3_600_000L
        val start = now - now % hour - 2 * hour
        return items.withIndex().filter { it.value.kind == Kind.LIVE }.flatMap { (i, _) ->
            (0 until 30).map { slot ->
                val s = shows[(slot + i) % shows.size]
                Program("demo.$i", start + slot * hour, start + (slot + 1) * hour, s.first, s.second)
            }
        }
    }
}
