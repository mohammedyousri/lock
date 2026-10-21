package com.mohammedyousri.glasstv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mohammedyousri.glasstv.R
import com.mohammedyousri.glasstv.data.Program
import com.mohammedyousri.glasstv.data.Stream
import com.mohammedyousri.glasstv.ui.components.Artwork
import com.mohammedyousri.glasstv.ui.components.LiveBadge
import com.mohammedyousri.glasstv.ui.components.PillButton
import com.mohammedyousri.glasstv.ui.components.ProgressLine
import com.mohammedyousri.glasstv.ui.components.Txt
import com.mohammedyousri.glasstv.ui.components.formatTime
import com.mohammedyousri.glasstv.ui.components.hueFor
import com.mohammedyousri.glasstv.ui.theme.GlassColors
import com.mohammedyousri.glasstv.ui.theme.GlassType

data class Group(val name: String, val items: List<Stream>)

fun groupStreams(streams: List<Stream>, fallback: String): List<Group> {
    val map = LinkedHashMap<String, MutableList<Stream>>()
    streams.forEach { map.getOrPut(it.group.ifBlank { fallback }) { ArrayList() }.add(it) }
    return map.map { Group(it.key, it.value) }
}

@Composable
fun ReportAtTop(state: LazyListState, onAtTop: (Boolean) -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 80 }
            .collect { onAtTop(it) }
    }
}

@Composable
fun LiveTab(
    groups: List<Group>,
    recents: List<Stream>,
    favoriteChannels: List<Stream>,
    favorites: Set<String>,
    guide: (Stream) -> Program?,
    now: Long,
    heroRequester: FocusRequester,
    onPlay: (List<Stream>, Int) -> Unit,
    onToggleFavorite: (Stream) -> Unit,
    onAtTop: (Boolean) -> Unit,
) {
    val state = rememberLazyListState()
    ReportAtTop(state, onAtTop)
    val featured = recents.firstOrNull() ?: favoriteChannels.firstOrNull() ?: groups.firstOrNull()?.items?.firstOrNull()
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 60.dp)) {
        if (featured != null) {
            item(key = "hero") {
                Hero(
                    stream = featured,
                    program = guide(featured),
                    now = now,
                    favorite = featured.key in favorites,
                    requester = heroRequester,
                    onWatch = {
                        val list = if (recents.isNotEmpty()) recents else listOf(featured)
                        onPlay(list, list.indexOf(featured).coerceAtLeast(0))
                    },
                    onFavorite = { onToggleFavorite(featured) },
                )
            }
        }
        if (recents.isNotEmpty()) {
            item(key = "recent") {
                ChannelRow(stringResource(R.string.row_recent), recents, guide, now, favorites, onPlay, onToggleFavorite, showCount = false)
            }
        }
        if (favoriteChannels.isNotEmpty()) {
            item(key = "favorites") {
                ChannelRow(stringResource(R.string.row_favorites), favoriteChannels, guide, now, favorites, onPlay, onToggleFavorite)
            }
        }
        items(groups, key = { "g:" + it.name }) { g ->
            ChannelRow(g.name, g.items, guide, now, favorites, onPlay, onToggleFavorite)
        }
    }
}

@Composable
private fun Hero(
    stream: Stream,
    program: Program?,
    now: Long,
    favorite: Boolean,
    requester: FocusRequester,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
) {
    val context = LocalContext.current
    val hue = hueFor(stream.name)
    Box(
        Modifier
            .fillMaxWidth()
            .height(318.dp)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(hue.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.35f),
                        radius = size.width * 0.5f,
                    ),
                )
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = ScreenPadding + 10.dp, top = 40.dp)
                .size(300.dp, 169.dp)
                .graphicsLayer {
                    shadowElevation = 28.dp.toPx()
                    shape = RoundedCornerShape(18.dp)
                    clip = true
                }
                .background(Color(0x26FFFFFF)),
        ) {
            Artwork(stream.logo, stream.name, Modifier.fillMaxSize(), padding = 36.dp, monogramStyle = GlassType.Title1)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = ScreenPadding, bottom = 28.dp)
                .width(440.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBadge(text = stringResource(R.string.live))
                Spacer(Modifier.width(8.dp))
                Txt(stream.group, GlassType.Callout, color = GlassColors.TextSecondary, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Txt(stream.name, GlassType.LargeTitle, maxLines = 1)
            if (program != null) {
                Spacer(Modifier.height(4.dp))
                Txt(program.title, GlassType.Title3, color = GlassColors.TextSecondary, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(formatTime(context, program.start), GlassType.Caption, color = GlassColors.TextTertiary)
                    ProgressLine(program.progress(now), Modifier.padding(horizontal = 10.dp).width(160.dp))
                    Txt(formatTime(context, program.stop), GlassType.Caption, color = GlassColors.TextTertiary)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    stringResource(R.string.watch_live),
                    onWatch,
                    Modifier.focusRequester(requester),
                    icon = Icons.Rounded.PlayArrow,
                )
                PillButton(
                    stringResource(if (favorite) R.string.favorited else R.string.favorite),
                    onFavorite,
                    icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                )
            }
        }
    }
}
