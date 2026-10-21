package com.mohammedyousri.glasstv.core

import android.view.InputDevice
import android.view.KeyEvent
import com.mohammedyousri.glasstv.data.RemoteProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The handful of things a TV remote needs to do. */
enum class RemoteAction(val keyCode: Int) {
    SELECT(KeyEvent.KEYCODE_DPAD_CENTER),
    BACK(KeyEvent.KEYCODE_BACK),
    UP(KeyEvent.KEYCODE_DPAD_UP),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
    PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    OPTIONS(KeyEvent.KEYCODE_MENU),
    CHANNEL_UP(KeyEvent.KEYCODE_CHANNEL_UP),
    CHANNEL_DOWN(KeyEvent.KEYCODE_CHANNEL_DOWN),
    GUIDE(KeyEvent.KEYCODE_GUIDE),
}

data class KeyInfo(val keyCode: Int, val name: String, val device: String, val translatedTo: String?)

/**
 * Translates whatever a remote sends into the canonical keys the UI understands.
 *
 * Apple's Siri Remote talks Bluetooth HID to non-Apple devices; depending on its
 * generation, its back button arrives as KEYCODE_BACK or KEYCODE_MENU and the
 * clickpad as D-pad keys. Game controllers send BUTTON_A/B. Anything else can be
 * taught with the "Learn buttons" screen, stored as [custom].
 */
object RemoteKeys {
    private val _last = MutableStateFlow<KeyInfo?>(null)
    val lastKey: StateFlow<KeyInfo?> = _last.asStateFlow()

    /** When set, the next raw key press is delivered here instead of to the UI. */
    @Volatile var capture: ((KeyEvent) -> Unit)? = null

    private val builtIn = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to RemoteAction.SELECT,
        KeyEvent.KEYCODE_BUTTON_SELECT to RemoteAction.SELECT,
        KeyEvent.KEYCODE_BUTTON_B to RemoteAction.BACK,
        KeyEvent.KEYCODE_ESCAPE to RemoteAction.BACK,
        KeyEvent.KEYCODE_PAGE_UP to RemoteAction.CHANNEL_UP,
        KeyEvent.KEYCODE_PAGE_DOWN to RemoteAction.CHANNEL_DOWN,
        KeyEvent.KEYCODE_MEDIA_PLAY to RemoteAction.PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PAUSE to RemoteAction.PLAY_PAUSE,
        KeyEvent.KEYCODE_BUTTON_X to RemoteAction.PLAY_PAUSE,
        KeyEvent.KEYCODE_TV_INPUT to RemoteAction.GUIDE,
        KeyEvent.KEYCODE_INFO to RemoteAction.OPTIONS,
    )

    fun parseCustom(raw: String): Map<Int, RemoteAction> = raw.split(';').mapNotNull { part ->
        val code = part.substringBefore('=').trim().toIntOrNull() ?: return@mapNotNull null
        val action = runCatching { RemoteAction.valueOf(part.substringAfter('=').trim()) }.getOrNull() ?: return@mapNotNull null
        code to action
    }.toMap()

    fun formatCustom(map: Map<Int, RemoteAction>): String = map.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    fun isAppleRemote(deviceName: String?): Boolean {
        val n = deviceName?.lowercase() ?: return false
        return "siri" in n || "apple" in n
    }

    /** Pure mapping used by [translate]; returns the canonical key code. */
    fun map(keyCode: Int, deviceName: String?, profile: RemoteProfile, custom: Map<Int, RemoteAction>): Int {
        custom[keyCode]?.let { return it.keyCode }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val apple = profile == RemoteProfile.APPLE || (profile == RemoteProfile.AUTO && isAppleRemote(deviceName))
            // On Apple remotes "Menu" is the back button.
            return if (apple) KeyEvent.KEYCODE_BACK else keyCode
        }
        builtIn[keyCode]?.let { return it.keyCode }
        return keyCode
    }

    /**
     * Returns the event the UI should see, or null if it was captured by the
     * learning screen.
     */
    fun translate(event: KeyEvent, profile: RemoteProfile, custom: Map<Int, RemoteAction>): KeyEvent? {
        val deviceName = runCatching { InputDevice.getDevice(event.deviceId)?.name }.getOrNull()
        capture?.let { cb ->
            if (event.keyCode != KeyEvent.KEYCODE_HOME && event.keyCode != KeyEvent.KEYCODE_POWER) {
                if (event.action == KeyEvent.ACTION_UP) {
                    _last.value = KeyInfo(event.keyCode, KeyEvent.keyCodeToString(event.keyCode), deviceName.orEmpty(), null)
                    cb(event)
                }
                return null
            }
        }
        val mapped = map(event.keyCode, deviceName, profile, custom)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            _last.value = KeyInfo(
                event.keyCode,
                KeyEvent.keyCodeToString(event.keyCode),
                deviceName.orEmpty(),
                if (mapped != event.keyCode) KeyEvent.keyCodeToString(mapped) else null,
            )
        }
        if (mapped == event.keyCode) return event
        return KeyEvent(
            event.downTime, event.eventTime, event.action, mapped, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
        )
    }

    fun isSelect(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
}
