package com.mohammedyousri.glasstv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream

sealed interface SyncState {
    data object Idle : SyncState
    data class Working(val message: String) : SyncState
    data class Failed(val message: String) : SyncState
}

/** Everything the TV app shows, loaded once into memory and kept in sync with the database. */
/** What the TV screens need; implemented by [Repository] and by fakes in tests. */
interface TvBackend {
    val loaded: StateFlow<Boolean>
    val sources: StateFlow<List<Source>>
    val live: StateFlow<List<Stream>>
    val movies: StateFlow<List<Stream>>
    val series: StateFlow<List<Stream>>
    val nowPlaying: StateFlow<Map<String, Program>>
    val favorites: StateFlow<Set<String>>
    val history: StateFlow<List<HistoryEntry>>
    val sync: StateFlow<SyncState>
    fun stream(key: String): Stream?
    fun nowOn(s: Stream): Program?
    suspend fun schedule(s: Stream, limit: Int = 12): List<Program>
    fun toggleFavorite(s: Stream)
    suspend fun episodes(series: Stream): List<Episode>
    suspend fun movieDetails(movie: Stream): Stream
    fun resumePosition(key: String): Long
}

class Repository(
    private val store: Store,
    private val http: OkHttpClient,
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val filesDir: File,
) : TvBackend {
    private val mutex = Mutex()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    override val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    private val _live = MutableStateFlow<List<Stream>>(emptyList())
    override val live: StateFlow<List<Stream>> = _live.asStateFlow()

    private val _movies = MutableStateFlow<List<Stream>>(emptyList())
    override val movies: StateFlow<List<Stream>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<List<Stream>>(emptyList())
    override val series: StateFlow<List<Stream>> = _series.asStateFlow()

    private val _nowPlaying = MutableStateFlow<Map<String, Program>>(emptyMap())
    override val nowPlaying: StateFlow<Map<String, Program>> = _nowPlaying.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    override val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    override val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    override val sync: StateFlow<SyncState> = _sync.asStateFlow()

    @Volatile private var aliases: Map<String, String> = emptyMap()
    @Volatile private var byKey: Map<String, Stream> = emptyMap()
    private val episodeCache = HashMap<String, List<Episode>>()

    fun start() {
        scope.launch(Dispatchers.IO) {
            reload()
            store.pruneGuide(System.currentTimeMillis() - 6 * HOUR)
            while (isActive) {
                delay(30_000)
                _nowPlaying.value = store.nowPlaying(System.currentTimeMillis())
            }
        }
    }

    private suspend fun reload() = withContext(Dispatchers.IO) {
        _sources.value = store.sources()
        aliases = store.epgAliases()
        val live = store.streams(Kind.LIVE)
        val movies = store.streams(Kind.MOVIE)
        val series = store.streams(Kind.SERIES)
        byKey = HashMap<String, Stream>(live.size + movies.size + series.size).apply {
            (live + movies + series).forEach { put(it.key, it) }
        }
        _live.value = live
        _movies.value = movies
        _series.value = series
        _favorites.value = LinkedHashSet(store.favorites())
        _history.value = store.history()
        _nowPlaying.value = store.nowPlaying(System.currentTimeMillis())
        _loaded.value = true
    }

    override fun stream(key: String): Stream? = byKey[key]

    fun epgIdOf(s: Stream): String =
        if (s.tvgId.isNotBlank()) epgKey(s.tvgId) else aliases[normalizeName(s.name)].orEmpty()

    override fun nowOn(s: Stream): Program? = epgIdOf(s).takeIf { it.isNotEmpty() }?.let { _nowPlaying.value[it] }

    override suspend fun schedule(s: Stream, limit: Int): List<Program> = withContext(Dispatchers.IO) {
        val id = epgIdOf(s)
        if (id.isEmpty()) emptyList() else store.schedule(id, System.currentTimeMillis(), limit)
    }

    // ---- sources ----

    suspend fun addSource(source: Source): Result<Source> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (source.type == SourceType.XTREAM) xtream(source).authenticate()
                val id = store.insertSource(source)
                val saved = source.copy(id = id)
                try {
                    importStreams(saved)
                } catch (e: Exception) {
                    store.deleteSource(id)
                    throw e
                }
                reload()
                store.sources().first { it.id == id }
            }.onFailure { _sync.value = SyncState.Idle }
        }
    }.onSuccess { saved -> scope.launch { refreshGuide(saved) } }

    suspend fun removeSource(source: Source) = mutex.withLock {
        withContext(Dispatchers.IO) {
            store.deleteSource(source.id)
            reload()
        }
    }

    suspend fun refresh(source: Source, guide: Boolean = true): Result<Unit> {
        val result = mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    importStreams(source)
                    reload()
                }.onFailure { _sync.value = SyncState.Failed(it.message ?: "Refresh failed") }
            }
        }
        if (guide && result.isSuccess) refreshGuide(store.sources().firstOrNull { it.id == source.id } ?: source)
        return result
    }

    /** Refreshes playlists older than a day and guides older than 12 hours. */
    fun refreshIfStale() {
        scope.launch {
            val now = System.currentTimeMillis()
            for (s in store.sources()) {
                if (now - s.lastRefresh > 24 * HOUR) {
                    refresh(s, guide = true)
                } else if (now - s.lastEpgRefresh > 12 * HOUR) {
                    refreshGuide(s)
                }
            }
        }
    }

    fun refreshAll() {
        scope.launch { store.sources().forEach { refresh(it) } }
    }

    private fun importStreams(source: Source) {
        _sync.value = SyncState.Working("Loading ${source.name}…")
        when (source.type) {
            SourceType.DEMO -> {
                val items = Demo.streams(source.id)
                store.replaceStreams(source.id, setOf(Kind.LIVE, Kind.MOVIE)) { emit -> items.forEach(emit) }
            }
            SourceType.M3U -> {
                var header: M3uParser.Header? = null
                val count = open(source.url, source.userAgent).use { input ->
                    store.replaceStreams(source.id, setOf(Kind.LIVE, Kind.MOVIE)) { emit ->
                        var liveIndex = 0
                        header = M3uParser.parse(input.bufferedReader()) { e ->
                            val kind = M3uParser.kindOf(e)
                            if (kind == Kind.LIVE) liveIndex++
                            emit(
                                Stream(
                                    key = "m${source.id}:${e.tvgId}:${e.name}",
                                    sourceId = source.id,
                                    kind = kind,
                                    name = e.name,
                                    number = if (kind == Kind.LIVE) e.number.takeIf { it > 0 } ?: liveIndex else 0,
                                    group = e.group,
                                    logo = e.logo,
                                    url = e.url,
                                    tvgId = e.tvgId,
                                    userAgent = e.userAgent,
                                    referrer = e.referrer,
                                ),
                            )
                        }
                    }
                }
                if (count == 0) throw IOException("The playlist has no channels")
                val epg = header?.epgUrls.orEmpty()
                if (source.epgUrl.isBlank() && epg.isNotEmpty()) {
                    store.updateSource(source.copy(epgUrl = epg.joinToString(",")))
                }
            }
            SourceType.XTREAM -> {
                val x = xtream(source)
                store.replaceStreams(source.id, setOf(Kind.LIVE)) { emit -> x.liveStreams(source.id, emit) }
                // Movies and series are optional extras; a failure there shouldn't lose live TV.
                runCatching { store.replaceStreams(source.id, setOf(Kind.MOVIE)) { emit -> x.movies(source.id, emit) } }
                runCatching { store.replaceStreams(source.id, setOf(Kind.SERIES)) { emit -> x.series(source.id, emit) } }
            }
        }
        val fresh = store.sources().firstOrNull { it.id == source.id } ?: source
        store.updateSource(fresh.copy(lastRefresh = System.currentTimeMillis()))
        _sync.value = SyncState.Idle
    }

    suspend fun refreshGuide(source: Source) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val writer = store.guideWriter(source.id)
            try {
                if (source.type == SourceType.DEMO) {
                    Demo.programs(now).forEach(writer::program)
                } else {
                    val urls = guideUrls(source)
                    if (urls.isEmpty()) {
                        writer.abort()
                        return@withContext
                    }
                    _sync.value = SyncState.Working("Updating guide for ${source.name}…")
                    val wanted = store.channelEpgIds(source.id).toMutableSet()
                    val unmatchedNames = store.channelNamesWithoutEpgId(source.id).toMutableSet()
                    val parser = XmltvParser(now - 2 * HOUR, now + 3 * 24 * HOUR)
                    for (url in urls) {
                        runCatching {
                            open(url, source.userAgent).use { input ->
                                parser.parse(
                                    input,
                                    object : XmltvParser.Sink {
                                        override fun onChannel(id: String, displayNames: List<String>) {
                                            for (n in displayNames) {
                                                val k = normalizeName(n)
                                                if (k.isNotEmpty() && unmatchedNames.remove(k)) {
                                                    writer.alias(k, id)
                                                    wanted += id
                                                }
                                            }
                                        }

                                        override fun wants(channelId: String) = channelId in wanted

                                        override fun onProgram(program: Program) = writer.program(program)
                                    },
                                )
                            }
                        }
                    }
                }
                writer.commit()
                val fresh = store.sources().firstOrNull { it.id == source.id } ?: source
                store.updateSource(fresh.copy(lastEpgRefresh = now))
            } catch (e: Exception) {
                runCatching { writer.abort() }
                _sync.value = SyncState.Failed(e.message ?: "Guide update failed")
                return@withContext
            }
            aliases = store.epgAliases()
            _sources.value = store.sources()
            _nowPlaying.value = store.nowPlaying(now)
            _sync.value = SyncState.Idle
        }
    }

    private fun guideUrls(source: Source): List<String> {
        val explicit = source.epgUrl.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (explicit.isNotEmpty()) return explicit
        return if (source.type == SourceType.XTREAM) listOf(xtream(source).xmltvUrl()) else emptyList()
    }

    private fun open(url: String, userAgent: String): InputStream {
        if (url.startsWith("file:")) return File(url.removePrefix("file://").removePrefix("file:")).inputStream()
        val req = Request.Builder().url(url).apply {
            val ua = userAgent.ifBlank { defaultUserAgent() }
            if (ua.isNotBlank()) header("User-Agent", ua)
        }.build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("Server returned HTTP ${resp.code}")
        }
        return resp.body.byteStream()
    }

    private fun xtream(source: Source) =
        XtreamClient(http, source.url, source.username, source.password, source.userAgent.ifBlank { defaultUserAgent() })

    /** Saves an uploaded playlist file (from the phone companion page) and returns its file: URL. */
    fun savePlaylistFile(name: String, content: ByteArray): String {
        val dir = File(filesDir, "playlists").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}-${name.filter { it.isLetterOrDigit() || it == '.' }.take(40)}")
        file.writeBytes(content)
        return "file://${file.absolutePath}"
    }

    // ---- favorites, history ----

    fun isFavorite(s: Stream) = s.key in _favorites.value

    override fun toggleFavorite(s: Stream) {
        val fav = s.key !in _favorites.value
        _favorites.value = LinkedHashSet(_favorites.value).apply { if (fav) add(s.key) else remove(s.key) }
        scope.launch(Dispatchers.IO) { store.setFavorite(s.key, s.kind, fav) }
    }

    fun recordWatch(s: Stream, positionMs: Long = 0, durationMs: Long = 0) {
        val entry = HistoryEntry(s.key, s.kind, System.currentTimeMillis(), positionMs, durationMs)
        _history.value = listOf(entry) + _history.value.filter { it.key != s.key }
        if (s.kind == Kind.LIVE) settings.lastChannel.value = s.key
        scope.launch(Dispatchers.IO) { store.saveHistory(entry) }
    }

    override fun resumePosition(key: String): Long {
        val h = _history.value.firstOrNull { it.key == key } ?: return 0
        val nearEnd = h.durationMs > 0 && h.positionMs > h.durationMs - 60_000
        return if (h.positionMs > 30_000 && !nearEnd) h.positionMs else 0
    }

    // ---- playback helpers ----

    fun defaultUserAgent(): String =
        runCatching { UserAgentPreset.valueOf(settings.userAgent.value).value }.getOrElse { settings.userAgent.value }

    fun userAgentFor(s: Stream): String {
        val sourceUa = _sources.value.firstOrNull { it.id == s.sourceId }?.userAgent.orEmpty()
        return s.userAgent.ifBlank { sourceUa }.ifBlank { defaultUserAgent() }
    }

    fun playbackUrl(s: Stream): String {
        val isXtreamLive = s.kind == Kind.LIVE && s.key.startsWith("x")
        return if (isXtreamLive && settings.xtreamHls.value) s.url.replace(Regex("""\.ts$"""), ".m3u8") else s.url
    }

    override suspend fun episodes(series: Stream): List<Episode> = withContext(Dispatchers.IO) {
        synchronized(episodeCache) { episodeCache[series.key] }?.let { return@withContext it }
        val source = _sources.value.firstOrNull { it.id == series.sourceId } ?: return@withContext emptyList()
        if (source.type != SourceType.XTREAM) return@withContext emptyList()
        val list = runCatching { xtream(source).seriesEpisodes(series.remoteId) }.getOrDefault(emptyList())
        synchronized(episodeCache) { episodeCache[series.key] = list }
        list
    }

    /** Fills in plot/year/rating for Xtream movies, which list endpoints often omit. */
    override suspend fun movieDetails(movie: Stream): Stream = withContext(Dispatchers.IO) {
        if (movie.plot.isNotBlank()) return@withContext movie
        val source = _sources.value.firstOrNull { it.id == movie.sourceId }
        if (source?.type != SourceType.XTREAM) return@withContext movie
        val info = xtream(source).movieInfo(movie.remoteId) ?: return@withContext movie
        movie.copy(
            plot = info["plot"].orEmpty(),
            year = movie.year.ifBlank { info["year"].orEmpty() },
            rating = movie.rating.ifBlank { info["rating"].orEmpty() },
        )
    }

    companion object {
        const val HOUR = 3_600_000L
    }
}
