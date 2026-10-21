package com.mohammedyousri.glasstv.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohammedyousri.glasstv.R

object GlassColors {
    val Background = Color(0xFF07070B)
    val Text = Color.White
    val TextSecondary = Color(0xA3FFFFFF)
    val TextTertiary = Color(0x66FFFFFF)
    val Fill = Color(0x1AFFFFFF)
    val FillStrong = Color(0x2EFFFFFF)
    val Stroke = Color(0x1FFFFFFF)
    val Panel = Color(0xE6202027)
    val PanelSolid = Color(0xFF1C1C22)
    val Focused = Color.White
    val OnFocused = Color(0xFF0E0E12)
    val Live = Color(0xFFFF453A)
    val Accent = Color(0xFF0A84FF)
    val Star = Color(0xFFFFD60A)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Sizes follow tvOS' type ramp, scaled from 1080p pixels to TV dp. */
object GlassType {
    val LargeTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.6).sp)
    val Title1 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
    val Title2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp)
    val Title3 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp)
    val Headline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val Callout = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp)
    val Badge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 0.6.sp)
}

/** Static "light field" wallpapers, drawn once as layered radial gradients. */
enum class Wallpaper(val title: String, val base: Color, val glows: List<Pair<Offset, Color>>) {
    AURORA(
        "Aurora", Color(0xFF06070F),
        listOf(Offset(0.12f, 0.05f) to Color(0xFF3B2E91), Offset(0.92f, 0.28f) to Color(0xFF0D5F80), Offset(0.55f, 1.05f) to Color(0xFF6A1D5E)),
    ),
    SUNSET(
        "Sunset", Color(0xFF110708),
        listOf(Offset(0.1f, 0.1f) to Color(0xFF8F3A1C), Offset(0.95f, 0.2f) to Color(0xFF7B1D4E), Offset(0.45f, 1.05f) to Color(0xFF3D1F7A)),
    ),
    OCEAN(
        "Ocean", Color(0xFF03101A),
        listOf(Offset(0.15f, 0.0f) to Color(0xFF0D4F7C), Offset(0.9f, 0.4f) to Color(0xFF0A6B6B), Offset(0.4f, 1.1f) to Color(0xFF1C2D7C)),
    ),
    FOREST(
        "Forest", Color(0xFF05100A),
        listOf(Offset(0.1f, 0.1f) to Color(0xFF1C5B3A), Offset(0.9f, 0.3f) to Color(0xFF0D4A50), Offset(0.5f, 1.1f) to Color(0xFF3B5A1C)),
    ),
    GRAPHITE(
        "Graphite", Color(0xFF0B0B0D),
        listOf(Offset(0.15f, 0.05f) to Color(0xFF34343E), Offset(0.9f, 0.35f) to Color(0xFF26262E), Offset(0.5f, 1.1f) to Color(0xFF3A3A44)),
    ),
    ;

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: AURORA
    }
}

fun Modifier.wallpaper(w: Wallpaper): Modifier = drawWithCache {
    val r = maxOf(size.width, size.height)
    val brushes = w.glows.map { (pos, color) ->
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(pos.x * size.width, pos.y * size.height),
            radius = r * 0.62f,
        )
    }
    val vignette = Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))
    onDrawBehind {
        drawRect(w.base)
        brushes.forEach { drawRect(it) }
        drawRect(vignette)
    }
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(GlassColors.Accent, GlassColors.Accent.copy(alpha = 0.4f)),
    ) {
        content()
    }
}

@Composable
fun WallpaperBox(wallpaper: Wallpaper, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().wallpaper(wallpaper), content = content)
}
