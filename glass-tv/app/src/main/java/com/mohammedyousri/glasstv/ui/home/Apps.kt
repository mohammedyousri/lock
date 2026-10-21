package com.mohammedyousri.glasstv.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.mohammedyousri.glasstv.data.Settings

enum class Builtin { TV, SETTINGS }

data class AppEntry(
    /** Package name, or "builtin:TV" style ids for Glass' own tiles. */
    val id: String,
    val label: String,
    val banner: ImageBitmap? = null,
    val icon: ImageBitmap? = null,
    /** Dominant artwork colour; tints icon-only tiles and the top shelf. */
    val color: Color = Color(0xFF2A2A33),
    /** A tiny copy of the artwork; drawn stretched it looks like a heavy blur. */
    val backdrop: ImageBitmap? = null,
    val builtin: Builtin? = null,
    val system: Boolean = false,
)

object AppLoader {
    const val TV_ID = "builtin:TV"
    const val SETTINGS_ID = "builtin:SETTINGS"

    fun builtins() = listOf(
        AppEntry(TV_ID, "TV", color = Color(0xFF2B3BA8), builtin = Builtin.TV),
        AppEntry(SETTINGS_ID, "Settings", color = Color(0xFF55555F), builtin = Builtin.SETTINGS),
    )

    /** Every launchable app, TV (leanback) apps first, without Glass itself. */
    fun installed(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        fun add(ri: ResolveInfo, isTvApp: Boolean) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) return
            runCatching { out += entryFor(pm, ri, isTvApp) }
        }
        leanback.forEach { add(it, true) }
        phone.forEach { add(it, false) }
        return out
    }

    private fun entryFor(pm: PackageManager, ri: ResolveInfo, isTvApp: Boolean): AppEntry {
        val ai = ri.activityInfo
        val bannerDrawable: Drawable? = if (isTvApp) ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) else null
        val iconDrawable = ri.loadIcon(pm)
        val banner = bannerDrawable?.let { safeBitmap(it, 320, 180) }
        val icon = safeBitmap(iconDrawable, 144, 144)
        val art = banner ?: icon
        val color = art?.let { dominant(it) } ?: Color(0xFF2A2A33)
        val backdrop = art?.let { it.scale(24, if (art === banner) 14 else 24, filter = true) }
        return AppEntry(
            id = ai.packageName,
            label = ri.loadLabel(pm).toString(),
            banner = banner?.asImageBitmap(),
            icon = icon?.asImageBitmap(),
            color = color,
            backdrop = backdrop?.asImageBitmap(),
            system = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        )
    }

    private fun safeBitmap(d: Drawable, w: Int, h: Int): Bitmap? = runCatching {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
        val scale = minOf(w.toFloat() / iw, h.toFloat() / ih, 2f)
        d.toBitmap((iw * scale).toInt().coerceAtLeast(1), (ih * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    }.getOrNull()

    private fun dominant(b: Bitmap): Color {
        val p = Palette.from(b).maximumColorCount(12).generate()
        val swatch = p.vibrantSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch ?: return Color(0xFF2A2A33)
        val c = Color(swatch.rgb)
        // Keep tiles readable with white text by capping brightness.
        return Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f)
    }

    fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Applies the saved order and hidden list; new apps are appended alphabetically. */
    fun arrange(all: List<AppEntry>, settings: Settings): Pair<List<AppEntry>, List<AppEntry>> {
        val order = settings.csv(settings.appOrder)
        val hidden = settings.csv(settings.hiddenApps).toSet()
        val byId = all.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }.toMutableList()
        val rest = all.filter { it.id !in order }
        val tv = rest.firstOrNull { it.id == TV_ID }
        val settingsTile = rest.firstOrNull { it.id == SETTINGS_ID }
        val others = rest.filter { it.builtin == null }.sortedWith(
            compareByDescending<AppEntry> { it.banner != null }.thenBy { it.label.lowercase() },
        )
        if (tv != null) ordered.add(0, tv)
        ordered += others
        if (settingsTile != null) ordered += settingsTile
        val visible = ordered.filter { it.id !in hidden }
        val hiddenApps = ordered.filter { it.id in hidden }
        return visible to hiddenApps
    }
}
