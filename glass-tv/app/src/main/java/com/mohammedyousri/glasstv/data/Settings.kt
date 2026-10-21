package com.mohammedyousri.glasstv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A SharedPreferences value that is also observable from Compose. */
class Pref<T>(
    private val prefs: SharedPreferences,
    private val key: String,
    default: T,
    read: SharedPreferences.(String, T) -> T,
    private val write: SharedPreferences.Editor.(String, T) -> Unit,
) {
    private val state = MutableStateFlow(prefs.read(key, default))
    val flow: StateFlow<T> = state.asStateFlow()

    var value: T
        get() = state.value
        set(v) {
            state.value = v
            prefs.edit().apply { write(key, v) }.apply()
        }
}

enum class RemoteProfile { AUTO, STANDARD, APPLE }

enum class UserAgentPreset(val value: String) {
    DEFAULT(""),
    VLC("VLC/3.0.21 LibVLC/3.0.21"),
    CHROME("Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"),
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("glass_settings", Context.MODE_PRIVATE)

    private fun string(key: String, def: String) =
        Pref(prefs, key, def, { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })

    private fun bool(key: String, def: Boolean) =
        Pref(prefs, key, def, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    /** Wallpaper name, see ui.theme.Wallpaper. */
    val wallpaper = string("wallpaper", "AURORA")
    val showClock = bool("show_clock", true)

    /** Comma separated package names, in home-screen order. */
    val appOrder = string("app_order", "")
    val hiddenApps = string("hidden_apps", "")

    val remoteProfile = string("remote_profile", RemoteProfile.AUTO.name)

    /** Learned remote buttons: "keyCode=ACTION;keyCode=ACTION". */
    val customKeys = string("custom_keys", "")

    val openLastChannel = bool("open_last_channel", false)
    val arrowsZap = bool("arrows_zap", true)
    val xtreamHls = bool("xtream_hls", false)
    val userAgent = string("user_agent", UserAgentPreset.DEFAULT.name)
    val lastChannel = string("last_channel", "")

    val homeHelper = bool("home_helper", false)
    val startOnBoot = bool("start_on_boot", false)

    fun csv(pref: Pref<String>): List<String> = pref.value.split(',').filter { it.isNotBlank() }
}
