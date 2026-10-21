package com.mohammedyousri.glasstv.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mohammedyousri.glasstv.R
import com.mohammedyousri.glasstv.data.HistoryEntry
import com.mohammedyousri.glasstv.data.Kind
import com.mohammedyousri.glasstv.data.Program
import com.mohammedyousri.glasstv.data.Stream
import com.mohammedyousri.glasstv.ui.components.GlassTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Movies or Series: one poster shelf per category, with "Continue Watching" on top. */
@Composable
fun LibraryTab(
    groups: List<Group>,
    continueWatching: List<Pair<Stream, HistoryEntry>>,
    favorites: Set<String>,
    onOpen: (Stream) -> Unit,
    onToggleFavorite: (Stream) -> Unit,
    onAtTop: (Boolean) -> Unit,
) {
    val state = rememberLazyListState()
    ReportAtTop(state, onAtTop)
    LazyColumn(state = state, contentPadding = PaddingValues(top = 84.dp, bottom = 60.dp)) {
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                val progress = continueWatching.associate { (s, h) ->
                    s.key to if (h.durationMs > 0) h.positionMs.toFloat() / h.durationMs else 0f
                }
                PosterRow(
                    stringResource(R.string.row_continue),
                    continueWatching.map { it.first },
                    favorites,
                    onOpen,
                    onToggleFavorite,
                    progress = { progress[it.key] },
                )
            }
        }
        items(groups, key = { "g:" + it.name }) { g ->
            PosterRow(g.name, g.items, favorites, onOpen, onToggleFavorite)
        }
    }
}

@Composable
fun FavoritesTab(
    channels: List<Stream>,
    movies: List<Stream>,
    series: List<Stream>,
    favorites: Set<String>,
    guide: (Stream) -> Program?,
    now: Long,
    onPlay: (List<Stream>, Int) -> Unit,
    onOpen: (Stream) -> Unit,
    onToggleFavorite: (Stream) -> Unit,
    onAtTop: (Boolean) -> Unit,
) {
    if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
        EmptyMessage(stringResource(R.string.empty_favorites_title), stringResource(R.string.empty_favorites_body))
        LaunchedEffect(Unit) { onAtTop(true) }
        return
    }
    val state = rememberLazyListState()
    ReportAtTop(state, onAtTop)
    LazyColumn(state = state, contentPadding = PaddingValues(top = 84.dp, bottom = 60.dp)) {
        if (channels.isNotEmpty()) {
            item(key = "channels") {
                ChannelRow(stringResource(R.string.channels), channels, guide, now, favorites, onPlay, onToggleFavorite)
            }
        }
        if (movies.isNotEmpty()) {
            item(key = "movies") { PosterRow(stringResource(R.string.tab_movies), movies, favorites, onOpen, onToggleFavorite) }
        }
        if (series.isNotEmpty()) {
            item(key = "series") { PosterRow(stringResource(R.string.tab_series), series, favorites, onOpen, onToggleFavorite) }
        }
    }
}

private data class SearchResults(val channels: List<Stream>, val movies: List<Stream>, val series: List<Stream>) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@Composable
fun SearchTab(
    live: List<Stream>,
    movies: List<Stream>,
    series: List<Stream>,
    favorites: Set<String>,
    guide: (Stream) -> Program?,
    now: Long,
    onPlay: (List<Stream>, Int) -> Unit,
    onOpen: (Stream) -> Unit,
    onToggleFavorite: (Stream) -> Unit,
    onAtTop: (Boolean) -> Unit,
    fieldRequester: FocusRequester = remember { FocusRequester() },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults(emptyList(), emptyList(), emptyList())) }
    LaunchedEffect(query, live, movies, series) {
        val q = query.trim()
        if (q.length < 2) {
            results = SearchResults(emptyList(), emptyList(), emptyList())
            return@LaunchedEffect
        }
        delay(250)
        results = withContext(Dispatchers.Default) {
            fun List<Stream>.match() = asSequence().filter { it.name.contains(q, ignoreCase = true) }.take(60).toList()
            SearchResults(live.match(), movies.match(), series.match())
        }
    }
    val state = rememberLazyListState()
    ReportAtTop(state, onAtTop)
    LazyColumn(state = state, contentPadding = PaddingValues(top = 84.dp, bottom = 60.dp)) {
        item(key = "field") {
            Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.Center) {
                GlassTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.width(520.dp).focusRequester(fieldRequester),
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                    leading = Icons.Rounded.Search,
                )
            }
        }
        if (query.trim().length >= 2 && results.isEmpty) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    EmptyMessage(stringResource(R.string.search_empty), stringResource(R.string.search_empty_body))
                }
            }
        }
        if (results.channels.isNotEmpty()) {
            item(key = "c") { ChannelRow(stringResource(R.string.channels), results.channels, guide, now, favorites, onPlay, onToggleFavorite) }
        }
        if (results.movies.isNotEmpty()) {
            item(key = "m") { PosterRow(stringResource(R.string.tab_movies), results.movies, favorites, onOpen, onToggleFavorite) }
        }
        if (results.series.isNotEmpty()) {
            item(key = "s") { PosterRow(stringResource(R.string.tab_series), results.series, favorites, onOpen, onToggleFavorite) }
        }
    }
}

fun continueWatching(history: List<HistoryEntry>, lookup: (String) -> Stream?, kind: Kind): List<Pair<Stream, HistoryEntry>> =
    history.asSequence()
        .filter { it.kind == kind && it.positionMs > 30_000 && (it.durationMs == 0L || it.positionMs < it.durationMs - 60_000) }
        .mapNotNull { h -> lookup(h.key)?.let { it to h } }
        .take(20)
        .toList()
