package com.mohammedyousri.glasstv.ui.tv

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mohammedyousri.glasstv.data.Program
import com.mohammedyousri.glasstv.data.Stream
import com.mohammedyousri.glasstv.ui.components.Artwork
import com.mohammedyousri.glasstv.ui.components.FocusSurface
import com.mohammedyousri.glasstv.ui.components.GIcon
import com.mohammedyousri.glasstv.ui.components.ProgressLine
import com.mohammedyousri.glasstv.ui.components.Txt
import com.mohammedyousri.glasstv.ui.theme.GlassColors
import com.mohammedyousri.glasstv.ui.theme.GlassType

val ScreenPadding = 56.dp

@Composable
fun ChannelCard(
    stream: Stream,
    program: Program?,
    now: Long,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 184.dp,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width).zIndex(if (focused) 1f else 0f)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(width, width * 0.5625f),
            shape = RoundedCornerShape(12.dp),
            color = Color(0x1FFFFFFF),
            focusedColor = Color(0x3DFFFFFF),
            focusedScale = 1.1f,
            onFocusChange = {
                focused = it
                if (it) onFocused()
            },
        ) {
            Artwork(stream.logo, stream.name, Modifier.fillMaxSize(), padding = 20.dp, monogramStyle = GlassType.Headline)
            if (favorite) {
                GIcon(Icons.Rounded.Star, Modifier.align(Alignment.TopEnd).padding(8.dp), tint = GlassColors.Star, size = 13.dp)
            }
            if (program != null) {
                ProgressLine(
                    program.progress(now),
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    height = 2.5.dp,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stream.number > 0) {
                Txt("${stream.number}", GlassType.Callout, color = GlassColors.TextTertiary, maxLines = 1)
                Spacer(Modifier.width(6.dp))
            }
            Txt(stream.name, GlassType.Headline, color = if (focused) GlassColors.Text else GlassColors.TextSecondary, maxLines = 1)
        }
        Txt(
            program?.title ?: stream.group,
            GlassType.Caption,
            color = GlassColors.TextTertiary,
            maxLines = 1,
        )
    }
}

@Composable
fun PosterCard(
    stream: Stream,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 118.dp,
    progress: Float? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width).zIndex(if (focused) 1f else 0f)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.size(width, width * 1.5f),
            shape = RoundedCornerShape(12.dp),
            color = Color(0x1FFFFFFF),
            focusedScale = 1.1f,
            onFocusChange = { focused = it },
        ) {
            Artwork(stream.logo, stream.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, monogramStyle = GlassType.Headline)
            if (favorite) {
                GIcon(Icons.Rounded.Star, Modifier.align(Alignment.TopEnd).padding(7.dp), tint = GlassColors.Star, size = 13.dp)
            }
            if (progress != null) {
                ProgressLine(
                    progress,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                    height = 2.5.dp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(stream.name, GlassType.Callout, color = if (focused) GlassColors.Text else GlassColors.TextSecondary, maxLines = 1)
    }
}

@Composable
fun RowHeader(title: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = ScreenPadding, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Txt(title, GlassType.Title3, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Txt("$count", GlassType.Callout, color = GlassColors.TextTertiary, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** A titled horizontal shelf of channels, as used throughout the Live tab. */
@Composable
fun ChannelRow(
    title: String,
    channels: List<Stream>,
    guide: (Stream) -> Program?,
    now: Long,
    favorites: Set<String>,
    onPlay: (List<Stream>, Int) -> Unit,
    onToggleFavorite: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    showCount: Boolean = true,
) {
    Column(modifier.padding(bottom = 26.dp)) {
        RowHeader(title, if (showCount) channels.size else null)
        LazyRow(
            Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(channels, key = { i, s -> "${s.key}#$i" }) { i, s ->
                ChannelCard(
                    stream = s,
                    program = guide(s),
                    now = now,
                    favorite = s.key in favorites,
                    onClick = { onPlay(channels, i) },
                    onLongClick = { onToggleFavorite(s) },
                )
            }
        }
    }
}

@Composable
fun PosterRow(
    title: String,
    items: List<Stream>,
    favorites: Set<String>,
    onOpen: (Stream) -> Unit,
    onToggleFavorite: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    progress: (Stream) -> Float? = { null },
) {
    Column(modifier.padding(bottom = 26.dp)) {
        RowHeader(title, items.size)
        LazyRow(
            Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(items, key = { i, s -> "${s.key}#$i" }) { _, s ->
                PosterCard(
                    stream = s,
                    favorite = s.key in favorites,
                    onClick = { onOpen(s) },
                    onLongClick = { onToggleFavorite(s) },
                    progress = progress(s),
                )
            }
        }
    }
}

@Composable
fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(title, GlassType.Title2, align = TextAlign.Center)
            Txt(body, GlassType.Body, color = GlassColors.TextSecondary, align = TextAlign.Center)
        }
    }
}
