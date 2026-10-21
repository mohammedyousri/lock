package com.mohammedyousri.glasstv

import android.app.Application
import com.mohammedyousri.glasstv.data.Kind
import com.mohammedyousri.glasstv.data.Repository
import com.mohammedyousri.glasstv.data.Settings
import com.mohammedyousri.glasstv.data.Store
import com.mohammedyousri.glasstv.data.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** What the player should play, and what channel up/down moves through. */
data class PlaybackQueue(
    val items: List<Stream>,
    val index: Int,
    val startPositionMs: Long = 0,
    /** Overrides the stream URL, used for series episodes. */
    val urlOverride: Map<String, String> = emptyMap(),
) {
    val current: Stream get() = items[index.coerceIn(0, items.lastIndex)]
    val isLive: Boolean get() = current.kind == Kind.LIVE
}

/** Hand-rolled service locator; the app is small enough not to need a DI framework. */
object AppGraph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val settings: Settings by lazy { Settings(app) }
    val store: Store by lazy { Store(app) }
    val repo: Repository by lazy { Repository(store, http, settings, scope, app.filesDir).also { it.start() } }

    @Volatile var queue: PlaybackQueue? = null

    fun init(application: Application) {
        app = application
    }
}

class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
