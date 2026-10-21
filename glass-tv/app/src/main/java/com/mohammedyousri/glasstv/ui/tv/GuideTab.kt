package com.mohammedyousri.glasstv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mohammedyousri.glasstv.R
import com.mohammedyousri.glasstv.data.Program
import com.mohammedyousri.glasstv.data.Stream
import com.mohammedyousri.glasstv.ui.components.Artwork
import com.mohammedyousri.glasstv.ui.components.GIcon
import com.mohammedyousri.glasstv.ui.components.GlassPanel
import com.mohammedyousri.glasstv.ui.components.LiveBadge
import com.mohammedyousri.glasstv.ui.components.ProgressLine
import com.mohammedyousri.glasstv.ui.components.Txt
import com.mohammedyousri.glasstv.ui.components.formatTime
import com.mohammedyousri.glasstv.ui.components.remoteClickable
import com.mohammedyousri.glasstv.ui.theme.GlassColors
import com.mohammedyousri.glasstv.ui.theme.GlassType
import kotlinx.coroutines.delay

/**
 * Three-pane guide: categories, channels with what's on now, and the
 * schedule for the focused channel.
 */
@Composable
fun GuideTab(
    groups: List<Group>,
    favoriteChannels: List<Stream>,
    favorites: Set<String>,
    guide: (Stream) -> Program?,
    schedule: suspend (Stream) -> List<Program>,
    now: Long,
    onPlay: (List<Stream>, Int) -> Unit,
    onToggleFavorite: (Stream) -> Unit,
) {
    val allLabel = stringResource(R.string.all_channels)
    val favLabel = stringResource(R.string.row_favorites)
    val categories = remember(groups, favoriteChannels) {
        buildList {
            add(Group(allLabel, groups.flatMap { it.items }))
            if (favoriteChannels.isNotEmpty()) add(Group(favLabel, favoriteChannels))
            addAll(groups)
        }
    }
    var selectedName by rememberSaveable { mutableStateOf(allLabel) }
    val selected = categories.firstOrNull { it.name == selectedName } ?: categories.first()
    var focused by remember { mutableStateOf<Stream?>(null) }

    Row(
        Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 84.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LazyColumn(
            Modifier.width(186.dp).fillMaxHeight().focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(categories, key = { _, g -> g.name }) { _, g ->
                CategoryItem(g.name, g.items.size, g.name == selected.name) { selectedName = g.name }
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(selected.name) { listState.scrollToItem(0) }
        LazyColumn(
            Modifier.width(372.dp).fillMaxHeight().focusRestorer(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            itemsIndexed(selected.items, key = { i, s -> "${s.key}#$i" }) { i, s ->
                GuideRow(
                    stream = s,
                    program = guide(s),
                    now = now,
                    favorite = s.key in favorites,
                    onFocused = { focused = s },
                    onClick = { onPlay(selected.items, i) },
                    onLongClick = { onToggleFavorite(s) },
                )
            }
        }

        ProgramPanel(
            stream = focused ?: selected.items.firstOrNull(),
            guide = guide,
            schedule = schedule,
            now = now,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun CategoryItem(name: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> GlassColors.Focused
        selected -> GlassColors.FillStrong
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .remoteClickable(onSelect)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) GlassColors.OnFocused else if (selected) GlassColors.Text else GlassColors.TextSecondary
        Txt(name, GlassType.Headline, Modifier.weight(1f), color = fg, maxLines = 1)
        Txt("$count", GlassType.Caption, color = if (focused) Color(0x99000000) else GlassColors.TextTertiary)
    }
}

@Composable
private fun GuideRow(
    stream: Stream,
    program: Program?,
    now: Long,
    favorite: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) GlassColors.OnFocused else GlassColors.Text
    val fg2 = if (focused) Color(0x99000000) else GlassColors.TextSecondary
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) GlassColors.Focused else GlassColors.Fill)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .remoteClickable(onClick, onLongClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(if (stream.number > 0) "${stream.number}" else "", GlassType.Callout, Modifier.width(34.dp), color = fg2, maxLines = 1)
        Box(Modifier.size(54.dp, 34.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x26FFFFFF))) {
            Artwork(stream.logo, stream.name, Modifier.fillMaxSize(), padding = 4.dp, monogramStyle = GlassType.Badge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(stream.name, GlassType.Headline, Modifier.weight(1f, fill = false), color = fg, maxLines = 1)
                if (favorite) {
                    Spacer(Modifier.width(5.dp))
                    GIcon(Icons.Rounded.Star, tint = GlassColors.Star, size = 11.dp)
                }
            }
            Txt(program?.title ?: stringResource(R.string.no_guide), GlassType.Caption, color = fg2, maxLines = 1)
            if (program != null) {
                Spacer(Modifier.height(4.dp))
                ProgressLine(
                    program.progress(now),
                    Modifier.fillMaxWidth(0.7f),
                    track = if (focused) Color(0x26000000) else Color(0x33FFFFFF),
                    fill = if (focused) GlassColors.OnFocused else Color.White,
                    height = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun ProgramPanel(
    stream: Stream?,
    guide: (Stream) -> Program?,
    schedule: suspend (Stream) -> List<Program>,
    now: Long,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val upcoming by produceState(emptyList<Program>(), stream?.key) {
        value = emptyList()
        val s = stream ?: return@produceState
        delay(180) // don't hit the database for every channel the user scrolls past
        value = schedule(s)
    }
    GlassPanel(modifier, color = Color(0x80141418)) {
        if (stream == null) return@GlassPanel
        val current = guide(stream)
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x26FFFFFF))) {
                    Artwork(stream.logo, stream.name, Modifier.fillMaxSize(), padding = 5.dp, monogramStyle = GlassType.Badge)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Txt(stream.name, GlassType.Title3, maxLines = 1)
                    Txt(stream.group, GlassType.Caption, color = GlassColors.TextTertiary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (current == null) {
                Txt(stringResource(R.string.no_guide), GlassType.Body, color = GlassColors.TextSecondary)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBadge(text = stringResource(R.string.now).uppercase())
                Spacer(Modifier.width(8.dp))
                Txt(
                    "${formatTime(context, current.start)} – ${formatTime(context, current.stop)}",
                    GlassType.Caption,
                    color = GlassColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Txt(current.title, GlassType.Title2, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            ProgressLine(current.progress(now), Modifier.fillMaxWidth())
            if (current.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Txt(current.description, GlassType.Body, color = GlassColors.TextSecondary, maxLines = 4)
            }
            val next = upcoming.filter { it.start >= current.stop }.take(5)
            if (next.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Txt(stringResource(R.string.up_next), GlassType.Headline, color = GlassColors.TextTertiary)
                Spacer(Modifier.height(6.dp))
                next.forEach { p ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Txt(formatTime(context, p.start), GlassType.Callout, Modifier.width(64.dp), color = GlassColors.TextSecondary, maxLines = 1)
                        Txt(p.title, GlassType.Callout, maxLines = 1)
                    }
                }
            }
        }
    }
}
