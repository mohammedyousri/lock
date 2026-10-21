package com.mohammedyousri.glasstv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mohammedyousri.glasstv.ui.theme.GlassColors
import com.mohammedyousri.glasstv.ui.theme.GlassType
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = GlassColors.Text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun GIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = GlassColors.Text, size: Dp = 18.dp) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** tvOS button: translucent at rest, solid white with dark text when focused. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    compact: Boolean = false,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = GlassColors.Fill,
        focusedColor = GlassColors.Focused,
        focusedScale = 1.08f,
        focusedElevation = 14.dp,
        sheen = false,
        onFocusChange = onFocusChange,
    ) { focused ->
        val fg = if (focused) GlassColors.OnFocused else GlassColors.Text
        Row(
            Modifier.padding(horizontal = if (compact) 14.dp else 22.dp, vertical = if (compact) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) GIcon(icon, tint = fg, size = if (compact) 14.dp else 17.dp)
            if (text.isNotEmpty()) Txt(text, if (compact) GlassType.Callout else GlassType.Headline, color = fg, maxLines = 1)
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    color: Color = GlassColors.Panel,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, GlassColors.Stroke, shape),
        content = content,
    )
}

@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = Color.White.copy(alpha = 0.22f),
    fill: Color = Color.White,
    height: Dp = 3.dp,
) {
    Box(modifier.height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape).background(fill))
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier, text: String = "LIVE") {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(GlassColors.Live).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Txt(text, GlassType.Badge, maxLines = 1)
    }
}

@Composable
fun TagBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, GlassColors.TextSecondary, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Txt(text, GlassType.Badge, color = GlassColors.TextSecondary, maxLines = 1)
    }
}

private val monogramHues = listOf(
    Color(0xFF3A5BD9), Color(0xFF8E44AD), Color(0xFFC0392B), Color(0xFF16A085),
    Color(0xFFD35400), Color(0xFF2C3E91), Color(0xFF7B8D24), Color(0xFF1F7A9C),
)

fun hueFor(name: String): Color = monogramHues[abs(name.hashCode()) % monogramHues.size]

/** Shown when a channel or app has no artwork: its name, set in bold on a tinted gradient. */
@Composable
fun Monogram(name: String, modifier: Modifier = Modifier, style: TextStyle = GlassType.Title3) {
    val hue = hueFor(name)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(hue.copy(alpha = 0.55f), hue.copy(alpha = 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        Txt(name, style, Modifier.padding(horizontal = 10.dp), maxLines = 2, align = TextAlign.Center)
    }
}

/** Network artwork with a monogram fallback when the URL is empty or fails to load. */
@Composable
fun Artwork(
    url: String,
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    padding: Dp = 0.dp,
    monogramStyle: TextStyle = GlassType.Title3,
) {
    var failed by remember(url) { mutableStateOf(url.isBlank()) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (failed || LocalInspectionMode.current) {
            Monogram(name, style = monogramStyle)
        } else {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().padding(padding),
                onError = { failed = true },
            )
        }
    }
}

/** Current wall-clock time, refreshed often enough for clocks and progress bars. */
@Composable
fun rememberNow(periodMs: Long = 20_000): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(periodMs)
        value = System.currentTimeMillis()
    }
}

@Composable
fun ClockText(modifier: Modifier = Modifier, style: TextStyle = GlassType.Title3, color: Color = GlassColors.Text) {
    val now by rememberNow(10_000)
    val format = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
    Txt(format.format(java.util.Date(now)), style, modifier, color = color, maxLines = 1)
}

fun formatTime(context: android.content.Context, millis: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

data class TabItem(val title: String, val icon: ImageVector? = null)

/**
 * The floating tvOS tab bar. Like tvOS, moving focus onto a tab selects it.
 */
@Composable
fun TabBar(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedRequester: FocusRequester = remember { FocusRequester() },
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Color(0x40000000))
            .background(GlassColors.Fill)
            .border(1.dp, GlassColors.Stroke, CircleShape)
            .padding(5.dp)
            .focusRestorer(selectedRequester),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, tab ->
            var focused by remember { mutableStateOf(false) }
            val isSelected = i == selected
            val bg = when {
                focused -> GlassColors.Focused
                isSelected -> GlassColors.FillStrong
                else -> Color.Transparent
            }
            val fg = when {
                focused -> GlassColors.OnFocused
                isSelected -> GlassColors.Text
                else -> GlassColors.TextSecondary
            }
            Row(
                Modifier
                    .then(if (isSelected) Modifier.focusRequester(selectedRequester) else Modifier)
                    .clip(CircleShape)
                    .background(bg)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused && !isSelected) onSelect(i)
                    }
                    .remoteClickable(onClick = { onSelect(i) })
                    .padding(horizontal = if (tab.title.isEmpty()) 10.dp else 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tab.icon?.let { GIcon(it, tint = fg, size = 16.dp) }
                if (tab.title.isNotEmpty()) Txt(tab.title, GlassType.Headline, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    leading: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = GlassType.Body.copy(color = if (focused) GlassColors.OnFocused else GlassColors.Text),
        cursorBrush = SolidColor(GlassColors.Accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(shape)
                    .background(if (focused) GlassColors.Focused else GlassColors.Fill)
                    .border(1.dp, if (focused) Color.Transparent else GlassColors.Stroke, shape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    GIcon(leading, tint = if (focused) GlassColors.OnFocused else GlassColors.TextSecondary, size = 16.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Txt(placeholder, GlassType.Body, color = if (focused) Color(0x99000000) else GlassColors.TextTertiary, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}

/**
 * Modal overlay. Traps focus inside (so the D-pad can't wander to the screen
 * underneath) and closes on Back.
 */
@Composable
fun GlassOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
    scrim: Color = Color(0xB3000000),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    if (initialFocus != null) {
        LaunchedEffect(Unit) {
            // Give the overlay a frame to lay out before moving focus into it.
            delay(16)
            runCatching { initialFocus.requestFocus() }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(scrim)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Txt(text, GlassType.Title3, modifier.padding(bottom = 10.dp), maxLines = 1)
}

/** Small helper for "time since" text in settings. */
fun ago(now: Long, then: Long): String {
    if (then <= 0) return "never"
    val minutes = (now - then) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}
