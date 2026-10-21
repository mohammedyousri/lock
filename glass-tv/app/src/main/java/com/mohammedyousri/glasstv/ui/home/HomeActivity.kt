package com.mohammedyousri.glasstv.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mohammedyousri.glasstv.AppGraph
import com.mohammedyousri.glasstv.PlaybackQueue
import com.mohammedyousri.glasstv.R
import com.mohammedyousri.glasstv.core.GlassActivity
import com.mohammedyousri.glasstv.data.Kind
import com.mohammedyousri.glasstv.ui.components.GlassOverlay
import com.mohammedyousri.glasstv.ui.components.GlassPanel
import com.mohammedyousri.glasstv.ui.components.PillButton
import com.mohammedyousri.glasstv.ui.components.Txt
import com.mohammedyousri.glasstv.ui.components.rememberNow
import com.mohammedyousri.glasstv.ui.player.PlayerActivity
import com.mohammedyousri.glasstv.ui.settings.SettingsActivity
import com.mohammedyousri.glasstv.ui.theme.GlassTheme
import com.mohammedyousri.glasstv.ui.theme.GlassType
import com.mohammedyousri.glasstv.ui.theme.Wallpaper
import com.mohammedyousri.glasstv.ui.tv.TvActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = AppGraph.settings
    private var all: List<AppEntry> = AppLoader.builtins().localized(app)

    val apps = MutableStateFlow(AppLoader.arrange(all, settings).first)
    val moving = MutableStateFlow<String?>(null)
    val homeSignal = MutableStateFlow(0)

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            all = AppLoader.builtins().localized(getApplication()) + AppLoader.installed(getApplication())
            if (moving.value == null) publish()
        }
    }

    private fun publish() {
        apps.value = AppLoader.arrange(all, settings).first
    }

    fun startMove(id: String) {
        moving.value = id
    }

    fun move(id: String, delta: Int) {
        val list = apps.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        apps.value = list
    }

    fun finishMove() {
        val hidden = settings.csv(settings.hiddenApps)
        settings.appOrder.value = (apps.value.map { it.id } + hidden).joinToString(",")
        moving.value = null
    }

    fun hide(id: String) {
        settings.hiddenApps.value = (settings.csv(settings.hiddenApps) + id).distinct().joinToString(",")
        publish()
    }

    fun signalHome() {
        moving.value = null
        homeSignal.value++
    }

    private fun List<AppEntry>.localized(context: Context) = map {
        when (it.builtin) {
            Builtin.TV -> it.copy(label = context.getString(R.string.tile_tv))
            Builtin.SETTINGS -> it.copy(label = context.getString(R.string.tile_settings))
            null -> it
        }
    }
}

/**
 * The Apple TV-style home screen. Registered as a HOME activity so it can
 * replace the stock Android TV launcher.
 */
class HomeActivity : GlassActivity() {
    private val vm: HomeViewModel by viewModels()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        setContent {
            GlassTheme {
                HomeRoute(vm, isHome)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        vm.reload()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.signalHome()
    }

    @Composable
    private fun HomeRoute(vm: HomeViewModel, isHome: Boolean) {
        val repo = AppGraph.repo
        val settings = AppGraph.settings
        val apps by vm.apps.collectAsStateWithLifecycle()
        val moving by vm.moving.collectAsStateWithLifecycle()
        val signal by vm.homeSignal.collectAsStateWithLifecycle()
        val wallpaper by settings.wallpaper.flow.collectAsStateWithLifecycle()
        val showClock by settings.showClock.flow.collectAsStateWithLifecycle()
        val history by repo.history.collectAsStateWithLifecycle()
        val favorites by repo.favorites.collectAsStateWithLifecycle()
        val live by repo.live.collectAsStateWithLifecycle()
        repo.nowPlaying.collectAsStateWithLifecycle()
        val now by rememberNow()

        val recent = remember(history, live) {
            history.filter { it.kind == Kind.LIVE }.mapNotNull { repo.stream(it.key) }.take(10)
        }
        val favs = remember(favorites, live) { favorites.mapNotNull { repo.stream(it) }.filter { it.kind == Kind.LIVE }.take(10) }
        val useRecent = recent.isNotEmpty()
        val shelfStreams = if (useRecent) recent else favs
        val shelf = shelfStreams.map { ShelfChannel(it, repo.nowOn(it)) }

        var options by remember { mutableStateOf<AppEntry?>(null) }

        BackHandler(enabled = isHome) { /* the home screen has nowhere to go back to */ }

        HomeScreen(
            apps = apps,
            wallpaper = Wallpaper.of(wallpaper),
            showClock = showClock,
            shelfTitle = stringResource(if (useRecent) R.string.row_recent else R.string.row_favorites),
            shelf = shelf,
            now = now,
            moving = moving,
            focusResetSignal = signal,
            onOpen = { app -> open(app) },
            onOptions = { options = it },
            onPlay = { item ->
                AppGraph.queue = PlaybackQueue(shelfStreams, shelfStreams.indexOf(item.stream).coerceAtLeast(0))
                launch(Intent(this, PlayerActivity::class.java))
            },
            onMove = vm::move,
            onMoveDone = vm::finishMove,
        )

        options?.let { app ->
            AppOptions(
                app = app,
                onDismiss = { options = null },
                onMove = {
                    options = null
                    vm.startMove(app.id)
                },
                onHide = {
                    options = null
                    vm.hide(app.id)
                },
                onInfo = {
                    options = null
                    launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.id, null)))
                },
                onUninstall = {
                    options = null
                    launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.id}")))
                },
            )
        }
    }

    private fun open(app: AppEntry) {
        when (app.builtin) {
            Builtin.TV -> launch(Intent(this, TvActivity::class.java))
            Builtin.SETTINGS -> launch(Intent(this, SettingsActivity::class.java))
            null -> launch(AppLoader.launchIntent(this, app.id))
        }
    }
}

@Composable
private fun AppOptions(
    app: AppEntry,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val first = remember { FocusRequester() }
    GlassOverlay(onDismiss = onDismiss, initialFocus = first) {
        GlassPanel(Modifier.width(340.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Txt(app.label, GlassType.Title2, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                PillButton(stringResource(R.string.app_move), onMove, Modifier.fillMaxWidth().focusRequester(first), icon = Icons.Rounded.OpenWith)
                if (app.builtin != Builtin.SETTINGS) {
                    PillButton(stringResource(R.string.app_hide), onHide, Modifier.fillMaxWidth(), icon = Icons.Rounded.VisibilityOff)
                }
                if (app.builtin == null) {
                    PillButton(stringResource(R.string.app_info), onInfo, Modifier.fillMaxWidth(), icon = Icons.Rounded.Info)
                    if (!app.system) {
                        PillButton(stringResource(R.string.app_uninstall), onUninstall, Modifier.fillMaxWidth(), icon = Icons.Rounded.Delete)
                    }
                }
                PillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}
