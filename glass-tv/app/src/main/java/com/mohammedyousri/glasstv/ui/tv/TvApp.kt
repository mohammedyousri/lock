package com.mohammedyousri.glasstv.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohammedyousri.glasstv.PlaybackQueue
import com.mohammedyousri.glasstv.R
import com.mohammedyousri.glasstv.data.Kind
import com.mohammedyousri.glasstv.data.Program
import com.mohammedyousri.glasstv.data.Stream
import com.mohammedyousri.glasstv.data.SyncState
import com.mohammedyousri.glasstv.data.TvBackend
import com.mohammedyousri.glasstv.ui.components.GIcon
import com.mohammedyousri.glasstv.ui.components.GlassPanel
import com.mohammedyousri.glasstv.ui.components.PillButton
import com.mohammedyousri.glasstv.ui.components.TabBar
import com.mohammedyousri.glasstv.ui.components.TabItem
import com.mohammedyousri.glasstv.ui.components.Txt
import com.mohammedyousri.glasstv.ui.components.rememberNow
import com.mohammedyousri.glasstv.ui.theme.GlassColors
import com.mohammedyousri.glasstv.ui.theme.GlassType
import com.mohammedyousri.glasstv.ui.theme.Wallpaper
import com.mohammedyousri.glasstv.ui.theme.wallpaper
import kotlinx.coroutines.delay

enum class TvTab { LIVE, GUIDE, MOVIES, SERIES, FAVORITES, SEARCH }

enum class SourceKind { M3U, XTREAM, PHONE, DEMO }

@Composable
fun TvApp(
    backend: TvBackend,
    wallpaper: Wallpaper,
    onPlay: (PlaybackQueue) -> Unit,
    onOpenSettings: () -> Unit,
    onAddSource: (SourceKind) -> Unit,
    onExit: () -> Unit,
    initialTab: TvTab = TvTab.LIVE,
    initialDetail: Stream? = null,
) {
    val loaded by backend.loaded.collectAsState()
    val sources by backend.sources.collectAsState()
    val live by backend.live.collectAsState()
    val movies by backend.movies.collectAsState()
    val series by backend.series.collectAsState()
    val favorites by backend.favorites.collectAsState()
    val history by backend.history.collectAsState()
    val nowPlaying by backend.nowPlaying.collectAsState()
    val sync by backend.sync.collectAsState()
    val now by rememberNow()
    val guide: (Stream) -> Program? = remember(nowPlaying) { { backend.nowOn(it) } }

    val other = stringResource(R.string.other)
    val liveGroups = remember(live) { groupStreams(live, other) }
    val movieGroups = remember(movies) { groupStreams(movies, other) }
    val seriesGroups = remember(series) { groupStreams(series, other) }
    val favStreams = remember(favorites, live, movies, series) { favorites.mapNotNull(backend::stream) }
    val favChannels = favStreams.filter { it.kind == Kind.LIVE }
    val recents = remember(history, live) {
        history.asSequence().filter { it.kind == Kind.LIVE }.mapNotNull { backend.stream(it.key) }.distinctBy { it.key }.take(15).toList()
    }
    val cwMovies = remember(history, movies) { continueWatching(history, backend::stream, Kind.MOVIE) }
    val cwSeries = remember(history, series) {
        continueWatching(history, { backend.stream(it.substringBefore(":ep:")) }, Kind.SERIES).distinctBy { it.first.key }
    }

    val tabs = buildList {
        add(TvTab.LIVE)
        add(TvTab.GUIDE)
        if (movies.isNotEmpty()) add(TvTab.MOVIES)
        if (series.isNotEmpty()) add(TvTab.SERIES)
        add(TvTab.FAVORITES)
        add(TvTab.SEARCH)
    }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    if (tab !in tabs) tab = TvTab.LIVE
    var atTop by remember { mutableStateOf(true) }
    var barFocused by remember { mutableStateOf(false) }
    var pendingBarFocus by remember { mutableStateOf(false) }
    var barSignal by remember { mutableIntStateOf(0) }
    val tabRequester = remember { FocusRequester() }
    val heroRequester = remember { FocusRequester() }
    val welcomeRequester = remember { FocusRequester() }
    var detail by remember { mutableStateOf(initialDetail) }

    val play: (List<Stream>, Int) -> Unit = { list, i -> onPlay(PlaybackQueue(list, i)) }

    LaunchedEffect(loaded, sources.isEmpty()) {
        if (!loaded) return@LaunchedEffect
        delay(120)
        runCatching {
            when {
                sources.isEmpty() -> welcomeRequester.requestFocus()
                tab == TvTab.LIVE && live.isNotEmpty() -> heroRequester.requestFocus()
                else -> tabRequester.requestFocus()
            }
        }
    }
    LaunchedEffect(barSignal) {
        if (barSignal == 0) return@LaunchedEffect
        pendingBarFocus = true
        delay(60)
        runCatching { tabRequester.requestFocus() }
        pendingBarFocus = false
    }

    BackHandler(enabled = detail == null) {
        if (barFocused || sources.isEmpty()) onExit() else barSignal++
    }

    Box(Modifier.fillMaxSize().wallpaper(wallpaper)) {
        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Txt(stringResource(R.string.loading), GlassType.Title3, color = GlassColors.TextSecondary)
            }
            sources.isEmpty() -> Welcome(welcomeRequester, onAddSource, onOpenSettings)
            else -> {
                Box(Modifier.fillMaxSize()) {
                    val onAtTop: (Boolean) -> Unit = { atTop = it }
                    when (tab) {
                        TvTab.LIVE -> LiveTab(
                            liveGroups, recents, favChannels, favorites, guide, now, heroRequester,
                            play, backend::toggleFavorite, onAtTop,
                        )
                        TvTab.GUIDE -> {
                            LaunchedEffect(Unit) { atTop = true }
                            GuideTab(liveGroups, favChannels, favorites, guide, { backend.schedule(it, 12) }, now, play, backend::toggleFavorite)
                        }
                        TvTab.MOVIES -> LibraryTab(movieGroups, cwMovies, favorites, { detail = it }, backend::toggleFavorite, onAtTop)
                        TvTab.SERIES -> LibraryTab(seriesGroups, cwSeries, favorites, { detail = it }, backend::toggleFavorite, onAtTop)
                        TvTab.FAVORITES -> FavoritesTab(
                            favChannels,
                            favStreams.filter { it.kind == Kind.MOVIE },
                            favStreams.filter { it.kind == Kind.SERIES },
                            favorites, guide, now, play, { detail = it }, backend::toggleFavorite, onAtTop,
                        )
                        TvTab.SEARCH -> SearchTab(live, movies, series, favorites, guide, now, play, { detail = it }, backend::toggleFavorite, onAtTop)
                    }
                }
                AnimatedVisibility(
                    visible = atTop || barFocused || pendingBarFocus,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                ) {
                    Row(
                        Modifier.padding(top = 22.dp).onFocusChanged { barFocused = it.hasFocus },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabBar(
                            tabs = tabs.map { it.item() },
                            selected = tabs.indexOf(tab),
                            onSelect = { i ->
                                tab = tabs[i]
                                atTop = true
                            },
                            selectedRequester = tabRequester,
                        )
                        Spacer(Modifier.width(12.dp))
                        PillButton("", onOpenSettings, icon = Icons.Rounded.Settings, compact = true)
                    }
                }
                SyncBanner(sync, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
            }
        }

        detail?.let { item ->
            if (item.kind == Kind.SERIES) {
                SeriesDetail(
                    series = item,
                    loadEpisodes = backend::episodes,
                    favorite = item.key in favorites,
                    onPlay = { list, i -> onPlay(PlaybackQueue(list, i, startPositionMs = backend.resumePosition(list[i].key))) },
                    onToggleFavorite = { backend.toggleFavorite(item) },
                    onBack = { detail = null },
                )
            } else {
                MovieDetail(
                    movie = item,
                    loadDetails = backend::movieDetails,
                    resumeMs = backend.resumePosition(item.key),
                    favorite = item.key in favorites,
                    onPlay = { start -> onPlay(PlaybackQueue(listOf(item), 0, startPositionMs = start)) },
                    onToggleFavorite = { backend.toggleFavorite(item) },
                    onBack = { detail = null },
                )
            }
        }
    }
}

@Composable
private fun TvTab.item(): TabItem = when (this) {
    TvTab.LIVE -> TabItem(stringResource(R.string.tab_live))
    TvTab.GUIDE -> TabItem(stringResource(R.string.tab_guide))
    TvTab.MOVIES -> TabItem(stringResource(R.string.tab_movies))
    TvTab.SERIES -> TabItem(stringResource(R.string.tab_series))
    TvTab.FAVORITES -> TabItem(stringResource(R.string.tab_favorites))
    TvTab.SEARCH -> TabItem("", Icons.Rounded.Search)
}

@Composable
private fun SyncBanner(sync: SyncState, modifier: Modifier) {
    val text = when (sync) {
        is SyncState.Working -> sync.message
        else -> return
    }
    GlassPanel(modifier, shape = RoundedCornerShape(50)) {
        Txt(text, GlassType.Callout, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = GlassColors.TextSecondary)
    }
}

@Composable
private fun Welcome(requester: FocusRequester, onAdd: (SourceKind) -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(118.dp, 72.dp)
                .graphicsLayer {
                    shadowElevation = 24.dp.toPx()
                    shape = RoundedCornerShape(16.dp)
                    clip = true
                }
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3346D3), Color(0xFF1B1F5E)))),
            contentAlignment = Alignment.Center,
        ) {
            GIcon(Icons.Rounded.LiveTv, size = 36.dp)
        }
        Spacer(Modifier.height(26.dp))
        Txt(stringResource(R.string.welcome_title), GlassType.LargeTitle, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(
            stringResource(R.string.welcome_body),
            GlassType.Body,
            Modifier.width(520.dp),
            color = GlassColors.TextSecondary,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PillButton(stringResource(R.string.add_m3u), { onAdd(SourceKind.M3U) }, Modifier.focusRequester(requester), icon = Icons.Rounded.Link)
            PillButton(stringResource(R.string.add_xtream), { onAdd(SourceKind.XTREAM) }, icon = Icons.Rounded.VpnKey)
            PillButton(stringResource(R.string.add_phone), { onAdd(SourceKind.PHONE) }, icon = Icons.Rounded.Smartphone)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PillButton(stringResource(R.string.try_demo), { onAdd(SourceKind.DEMO) }, icon = Icons.Rounded.PlayCircle, compact = true)
            PillButton(stringResource(R.string.tile_settings), onOpenSettings, icon = Icons.Rounded.Settings, compact = true)
        }
    }
}
