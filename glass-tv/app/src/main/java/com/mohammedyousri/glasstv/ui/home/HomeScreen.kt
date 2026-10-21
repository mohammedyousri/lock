package com.mohammedyousri.glasstv.ui.home

import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mohammedyousri.glasstv.R
import com.mohammedyousri.glasstv.data.Program
import com.mohammedyousri.glasstv.data.Stream
import com.mohammedyousri.glasstv.ui.components.Artwork
import com.mohammedyousri.glasstv.ui.components.ClockText
import com.mohammedyousri.glasstv.ui.components.FocusSurface
import com.mohammedyousri.glasstv.ui.components.GIcon
import com.mohammedyousri.glasstv.ui.components.LiveBadge
import com.mohammedyousri.glasstv.ui.components.ProgressLine
import com.mohammedyousri.glasstv.ui.components.Txt
import com.mohammedyousri.glasstv.ui.theme.GlassColors
import com.mohammedyousri.glasstv.ui.theme.GlassType
import com.mohammedyousri.glasstv.ui.theme.Wallpaper
import com.mohammedyousri.glasstv.ui.theme.wallpaper
import kotlinx.coroutines.delay

data class ShelfChannel(val stream: Stream, val program: Program?)

private val SidePadding = 56.dp
private val TileGap = 26.dp

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    wallpaper: Wallpaper,
    showClock: Boolean,
    shelfTitle: String,
    shelf: List<ShelfChannel>,
    now: Long,
    moving: String?,
    focusResetSignal: Int,
    onOpen: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
    onPlay: (ShelfChannel) -> Unit,
    onMove: (id: String, delta: Int) -> Unit,
    onMoveDone: () -> Unit,
    columns: Int = 5,
) {
    BoxWithConstraints(Modifier.fillMaxSize().wallpaper(wallpaper)) {
        val tileW = (maxWidth - SidePadding * 2 - TileGap * (columns - 1)) / columns
        val tileH = tileW * 0.6f
        val shelfH = maxHeight * 0.6f
        val rowPitch = tileH + 46.dp

        var owner by remember { mutableStateOf(apps.firstOrNull()?.id) }
        var focusedRow by remember { mutableIntStateOf(0) }
        val requesters = remember { HashMap<String, FocusRequester>() }
        fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }
        val ownerApp = apps.firstOrNull { it.id == owner } ?: apps.firstOrNull()

        val atTop = focusedRow <= 0
        val offset by animateDpAsState(
            if (atTop) 0.dp else 96.dp - (shelfH + 16.dp + rowPitch * focusedRow),
            spring(dampingRatio = 0.86f, stiffness = 260f),
            label = "homeScroll",
        )
        val shelfAlpha by animateFloatAsState(if (atTop) 1f else 0f, tween(260), label = "shelfAlpha")

        LaunchedEffect(focusResetSignal, apps.isNotEmpty()) {
            val first = apps.firstOrNull() ?: return@LaunchedEffect
            delay(32)
            owner = first.id
            focusedRow = 0
            runCatching { requester(first.id).requestFocus() }
        }
        LaunchedEffect(apps, moving) {
            if (moving != null) runCatching { requester(moving).requestFocus() }
        }

        ShelfBackdrop(
            ownerApp,
            Modifier
                .fillMaxWidth()
                .height(shelfH + 70.dp)
                .graphicsLayer { alpha = shelfAlpha },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = offset.toPx() }
                .onPreviewKeyEvent { e ->
                    val id = moving ?: return@onPreviewKeyEvent false
                    val native = e.nativeKeyEvent
                    val delta = when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> -1
                        KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                        KeyEvent.KEYCODE_DPAD_UP -> -columns
                        KeyEvent.KEYCODE_DPAD_DOWN -> columns
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> 0
                        else -> return@onPreviewKeyEvent false
                    }
                    if (native.action == KeyEvent.ACTION_DOWN) {
                        if (delta != 0) onMove(id, delta)
                    } else if (native.action == KeyEvent.ACTION_UP && delta == 0) {
                        onMoveDone()
                    }
                    true
                },
        ) {
            Box(Modifier.fillMaxWidth().height(shelfH).graphicsLayer { alpha = shelfAlpha }) {
                ShelfContent(
                    owner = ownerApp,
                    shelfTitle = shelfTitle,
                    shelf = shelf,
                    now = now,
                    onPlay = onPlay,
                    onShelfFocused = { focusedRow = -1 },
                )
            }
            Spacer(Modifier.height(16.dp))
            apps.chunked(columns).forEachIndexed { r, row ->
                Row(
                    Modifier.padding(horizontal = SidePadding).height(rowPitch),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    row.forEach { app ->
                        AppTile(
                            app = app,
                            width = tileW,
                            height = tileH,
                            moving = moving == app.id,
                            requester = requester(app.id),
                            onFocused = {
                                owner = app.id
                                focusedRow = r
                            },
                            onClick = { onOpen(app) },
                            onLongClick = { onOptions(app) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(240.dp))
        }

        if (showClock) {
            ClockText(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 26.dp, end = SidePadding)
                    .graphicsLayer { alpha = shelfAlpha },
                style = GlassType.Title3,
            )
        }
        if (moving != null) {
            Txt(
                stringResource(R.string.move_hint),
                GlassType.Callout,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                color = GlassColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    width: Dp,
    height: Dp,
    moving: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val wiggle = if (moving) {
        val t = rememberInfiniteTransition(label = "wiggle")
        t.animateFloat(-1.6f, 1.6f, infiniteRepeatable(tween(130), RepeatMode.Reverse), label = "wiggleAngle").value
    } else {
        0f
    }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "labelAlpha")
    Column(
        Modifier.width(width).zIndex(if (focused) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .size(width, height)
                .graphicsLayer { rotationZ = wiggle }
                .focusRequester(requester),
            shape = RoundedCornerShape(12.dp),
            color = app.color,
            focusedScale = 1.14f,
            focusedElevation = 26.dp,
            onFocusChange = {
                focused = it
                if (it) onFocused()
            },
        ) {
            AppArt(app)
        }
        Spacer(Modifier.height(15.dp))
        Txt(
            app.label,
            GlassType.Callout,
            Modifier.graphicsLayer { alpha = labelAlpha },
            maxLines = 1,
        )
    }
}

@Composable
fun AppArt(app: AppEntry, modifier: Modifier = Modifier) {
    when {
        app.builtin == Builtin.TV -> Box(
            modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3346D3), Color(0xFF1B1F5E)))),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GIcon(Icons.Rounded.LiveTv, size = 30.dp)
                Txt(app.label, GlassType.Title2, maxLines = 1)
            }
        }
        app.builtin == Builtin.SETTINGS -> Box(
            modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF8E8E96), Color(0xFF3C3C44)))),
            contentAlignment = Alignment.Center,
        ) {
            GIcon(Icons.Rounded.Settings, size = 40.dp, tint = Color(0xFFEDEDF2))
        }
        app.banner != null -> Image(
            bitmap = app.banner,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier.fillMaxSize(),
        )
        else -> Box(
            modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(app.color, app.color.copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (app.icon != null) {
                Image(app.icon, app.label, Modifier.fillMaxHeight(0.56f), filterQuality = FilterQuality.High)
            } else {
                Txt(app.label, GlassType.Headline, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ShelfBackdrop(owner: AppEntry?, modifier: Modifier) {
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Crossfade(owner?.id, animationSpec = tween(420), label = "shelfBackdrop") { id ->
            val app = owner?.takeIf { it.id == id }
            Box(Modifier.fillMaxSize()) {
                if (app?.backdrop != null) {
                    Image(
                        app.backdrop,
                        null,
                        Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                    )
                }
                val glow = app?.color ?: Color(0xFF2B3BA8)
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(glow.copy(alpha = 0.75f), Color.Transparent),
                                center = Offset(size.width * 0.82f, 0f),
                                radius = size.width * 0.75f,
                            ),
                        )
                    },
                )
                Box(Modifier.fillMaxSize().background(Color(0x33000000)))
            }
        }
    }
}

@Composable
private fun ShelfContent(
    owner: AppEntry?,
    shelfTitle: String,
    shelf: List<ShelfChannel>,
    now: Long,
    onPlay: (ShelfChannel) -> Unit,
    onShelfFocused: () -> Unit,
) {
    when {
        owner == null -> Unit
        owner.builtin == Builtin.TV && shelf.isNotEmpty() -> Column(Modifier.fillMaxSize().padding(top = 52.dp)) {
            Txt(shelfTitle, GlassType.Title3, Modifier.padding(start = SidePadding, bottom = 14.dp), color = GlassColors.TextSecondary)
            LazyRow(
                contentPadding = PaddingValues(horizontal = SidePadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(shelf, key = { _, c -> c.stream.key }) { _, item ->
                    ShelfCard(item, now, onClick = { onPlay(item) }, onFocused = onShelfFocused)
                }
            }
        }
        else -> Column(
            Modifier.fillMaxSize().padding(start = SidePadding, end = SidePadding, bottom = 26.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            val subtitle = when (owner.builtin) {
                Builtin.TV -> stringResource(R.string.shelf_tv_empty)
                Builtin.SETTINGS -> stringResource(R.string.shelf_settings)
                null -> stringResource(R.string.shelf_open_hint)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Txt(owner.label, GlassType.LargeTitle, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Txt(subtitle, GlassType.Body, color = GlassColors.TextSecondary, maxLines = 2)
                }
                if (owner.banner != null || owner.builtin != null) {
                    Box(
                        Modifier
                            .size(272.dp, 153.dp)
                            .graphicsLayer {
                                shadowElevation = 30.dp.toPx()
                                shape = RoundedCornerShape(16.dp)
                                clip = true
                            },
                    ) { AppArt(owner) }
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(item: ShelfChannel, now: Long, onClick: () -> Unit, onFocused: () -> Unit) {
    Column(Modifier.width(232.dp)) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier.size(232.dp, 130.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0x26FFFFFF),
            focusedColor = Color(0x40FFFFFF),
            focusedScale = 1.1f,
            onFocusChange = { if (it) onFocused() },
        ) {
            Artwork(item.stream.logo, item.stream.name, Modifier.fillMaxSize(), padding = 26.dp)
            LiveBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            item.program?.let {
                ProgressLine(
                    it.progress(now),
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt(item.stream.name, GlassType.Headline, maxLines = 1)
        Txt(item.program?.title.orEmpty(), GlassType.Caption, color = GlassColors.TextSecondary, maxLines = 1)
    }
}
