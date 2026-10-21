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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mohammedyousri.glasstv.R
import com.mohammedyousri.glasstv.data.Episode
import com.mohammedyousri.glasstv.data.Kind
import com.mohammedyousri.glasstv.data.Stream
import com.mohammedyousri.glasstv.ui.components.Artwork
import com.mohammedyousri.glasstv.ui.components.FocusSurface
import com.mohammedyousri.glasstv.ui.components.GlassOverlay
import com.mohammedyousri.glasstv.ui.components.PillButton
import com.mohammedyousri.glasstv.ui.components.TagBadge
import com.mohammedyousri.glasstv.ui.components.Txt
import com.mohammedyousri.glasstv.ui.components.formatDuration
import com.mohammedyousri.glasstv.ui.components.hueFor
import com.mohammedyousri.glasstv.ui.components.remoteClickable
import com.mohammedyousri.glasstv.ui.theme.GlassColors
import com.mohammedyousri.glasstv.ui.theme.GlassType

/** Poster art decoded at a tiny size and stretched: a cheap, convincing blur on any Android version. */
@Composable
fun BlurredBackdrop(url: String, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize()) {
        val hue = hueFor(name)
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(hue.copy(alpha = 0.5f), Color.Transparent))))
        if (url.isNotBlank() && !LocalInspectionMode.current) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).size(24, 36).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xF0050507), Color(0xB3050507), Color(0x66050507))),
            ),
        )
    }
}

@Composable
private fun MetaLine(stream: Stream) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (stream.year.isNotBlank()) Txt(stream.year, GlassType.Callout, color = GlassColors.TextSecondary)
        if (stream.rating.isNotBlank()) TagBadge("★ ${stream.rating.take(3)}")
        if (stream.group.isNotBlank()) Txt(stream.group, GlassType.Callout, color = GlassColors.TextSecondary, maxLines = 1)
    }
}

@Composable
fun MovieDetail(
    movie: Stream,
    loadDetails: suspend (Stream) -> Stream,
    resumeMs: Long,
    favorite: Boolean,
    onPlay: (startMs: Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val details by produceState(movie, movie.key) { value = loadDetails(movie) }
    val first = remember { FocusRequester() }
    GlassOverlay(onDismiss = onBack, initialFocus = first, scrim = GlassColors.Background) {
        BlurredBackdrop(movie.logo, movie.name)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(220.dp, 330.dp)
                    .graphicsLayer {
                        shadowElevation = 30.dp.toPx()
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                    }
                    .background(GlassColors.Fill),
            ) {
                Artwork(movie.logo, movie.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, monogramStyle = GlassType.Title2)
            }
            Spacer(Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                Txt(details.name, GlassType.LargeTitle, maxLines = 2)
                Spacer(Modifier.height(10.dp))
                MetaLine(details)
                Spacer(Modifier.height(14.dp))
                if (details.plot.isNotBlank()) {
                    Txt(details.plot, GlassType.Body, color = GlassColors.TextSecondary, maxLines = 6)
                    Spacer(Modifier.height(22.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (resumeMs > 0) {
                        PillButton(
                            stringResource(R.string.resume_at, formatDuration(resumeMs)),
                            { onPlay(resumeMs) },
                            Modifier.focusRequester(first),
                            icon = Icons.Rounded.PlayArrow,
                        )
                        PillButton(stringResource(R.string.from_start), { onPlay(0) }, icon = Icons.Rounded.Replay)
                    } else {
                        PillButton(stringResource(R.string.play), { onPlay(0) }, Modifier.focusRequester(first), icon = Icons.Rounded.PlayArrow)
                    }
                    PillButton(
                        stringResource(if (favorite) R.string.favorited else R.string.favorite),
                        onToggleFavorite,
                        icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    )
                }
            }
        }
    }
}

/** Turns episodes into playable streams; their history rolls up to the series. */
fun episodeStreams(series: Stream, episodes: List<Episode>): List<Stream> = episodes.map { e ->
    Stream(
        key = "${series.key}:ep:${e.id}",
        sourceId = series.sourceId,
        kind = Kind.SERIES,
        name = "S${e.season} E${e.number} · ${e.title.ifBlank { series.name }}",
        group = series.name,
        logo = e.image.ifBlank { series.logo },
        url = e.url,
        plot = e.plot,
    )
}

@Composable
fun SeriesDetail(
    series: Stream,
    loadEpisodes: suspend (Stream) -> List<Episode>,
    favorite: Boolean,
    onPlay: (List<Stream>, Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val episodes by produceState<List<Episode>?>(null, series.key) { value = loadEpisodes(series) }
    val seasons = remember(episodes) { episodes.orEmpty().map { it.season }.distinct().sorted() }
    var season by remember(seasons) { mutableStateOf(seasons.firstOrNull() ?: 1) }
    val inSeason = remember(episodes, season) { episodes.orEmpty().filter { it.season == season } }
    val first = remember { FocusRequester() }

    GlassOverlay(onDismiss = onBack, initialFocus = first, scrim = GlassColors.Background) {
        BlurredBackdrop(series.logo, series.name)
        Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 44.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(120.dp, 180.dp)
                        .graphicsLayer {
                            shadowElevation = 24.dp.toPx()
                            shape = RoundedCornerShape(12.dp)
                            clip = true
                        }
                        .background(GlassColors.Fill),
                ) {
                    Artwork(series.logo, series.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, monogramStyle = GlassType.Headline)
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Txt(series.name, GlassType.Title1, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    MetaLine(series)
                    Spacer(Modifier.height(10.dp))
                    if (series.plot.isNotBlank()) Txt(series.plot, GlassType.Body, color = GlassColors.TextSecondary, maxLines = 3)
                    Spacer(Modifier.height(14.dp))
                    PillButton(
                        stringResource(if (favorite) R.string.favorited else R.string.favorite),
                        onToggleFavorite,
                        Modifier.then(if (episodes.isNullOrEmpty()) Modifier.focusRequester(first) else Modifier),
                        icon = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        compact = true,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            when {
                episodes == null -> Txt(stringResource(R.string.loading), GlassType.Body, color = GlassColors.TextSecondary)
                episodes!!.isEmpty() -> Txt(stringResource(R.string.no_episodes), GlassType.Body, color = GlassColors.TextSecondary)
                else -> {
                    LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(seasons) { _, s ->
                            SeasonPill(stringResource(R.string.season_n, s), s == season) { season = s }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val playable = remember(inSeason) { episodeStreams(series, inSeason) }
                    LazyColumn(
                        Modifier.focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp),
                    ) {
                        itemsIndexed(inSeason, key = { _, e -> e.id }) { i, e ->
                            EpisodeRow(
                                e,
                                Modifier.then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                            ) { onPlay(playable, i) }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(episodes) {
        if (!episodes.isNullOrEmpty()) runCatching { first.requestFocus() }
    }
}

@Composable
private fun SeasonPill(text: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> GlassColors.Focused
        selected -> GlassColors.FillStrong
        else -> GlassColors.Fill
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .remoteClickable(onSelect)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Txt(text, GlassType.Headline, color = if (focused) GlassColors.OnFocused else GlassColors.Text)
    }
}

@Composable
private fun EpisodeRow(e: Episode, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(86.dp),
        shape = RoundedCornerShape(14.dp),
        color = GlassColors.Fill,
        focusedColor = Color(0x40FFFFFF),
        focusedScale = 1.03f,
        sheen = false,
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(124.dp, 70.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x26FFFFFF))) {
                Artwork(e.image, "E${e.number}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop, monogramStyle = GlassType.Headline)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("${e.number}. ${e.title}", GlassType.Headline, Modifier.weight(1f, fill = false), maxLines = 1)
                    if (e.durationSecs > 0) {
                        Spacer(Modifier.width(10.dp))
                        Txt(formatDuration(e.durationSecs * 1000L), GlassType.Caption, color = GlassColors.TextTertiary)
                    }
                }
                if (e.plot.isNotBlank()) Txt(e.plot, GlassType.Caption, color = GlassColors.TextSecondary, maxLines = 2)
            }
        }
    }
}
