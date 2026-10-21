package com.mohammedyousri.glasstv.core

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.mohammedyousri.glasstv.AppGraph
import com.mohammedyousri.glasstv.data.RemoteProfile

/** Base for every Glass screen: routes all remote buttons through [RemoteKeys]. */
abstract class GlassActivity : ComponentActivity() {
    private var customRaw: String? = null
    private var custom: Map<Int, RemoteAction> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val settings = AppGraph.settings
        val raw = settings.customKeys.value
        if (raw != customRaw) {
            customRaw = raw
            custom = RemoteKeys.parseCustom(raw)
        }
        val profile = runCatching { RemoteProfile.valueOf(settings.remoteProfile.value) }.getOrDefault(RemoteProfile.AUTO)
        val translated = RemoteKeys.translate(event, profile, custom) ?: return true
        return super.dispatchKeyEvent(translated)
    }

    fun launch(intent: Intent?) {
        if (intent == null) return
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Not allowed on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
