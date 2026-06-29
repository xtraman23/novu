package com.dispatcher.companion.wizard

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

enum class CheckState { GRANTED, DENIED, MANUAL }

data class SetupItem(
    val title: String,
    val why: String,
    val state: CheckState,
    val fix: Intent,
)

/** Xiaomi/HyperOS setup wizard model (FR-1000). */
object SetupChecklist {

    /** Pure helper, JVM-testable: is our listener in the enabled_notification_listeners flat string? */
    fun listenerEnabled(flat: String?, packageName: String): Boolean =
        flat?.split(':')?.any { it.substringBefore('/') == packageName } ?: false

    fun build(context: Context): List<SetupItem> {
        val pkg = context.packageName
        val appDetails = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")
        )
        val nm = context.getSystemService(NotificationManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)

        fun state(granted: Boolean) = if (granted) CheckState.GRANTED else CheckState.DENIED

        return listOf(
            SetupItem(
                "Microphone", "Required for live transcription",
                state(
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                ),
                appDetails,
            ),
            SetupItem(
                "Notifications", "Shows the dispatch-mode status",
                state(nm.areNotificationsEnabled()), appDetails,
            ),
            SetupItem(
                "Notification access", "Detects RingCentral calls automatically",
                state(
                    listenerEnabled(
                        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"),
                        pkg,
                    )
                ),
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            ),
            SetupItem(
                "Display over other apps", "Floating copilot over RingCentral",
                state(Settings.canDrawOverlays(context)),
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")),
            ),
            SetupItem(
                "Battery — no restrictions", "Keeps dispatch mode alive on HyperOS",
                state(pm.isIgnoringBatteryOptimizations(pkg)),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            ),
            SetupItem(
                "Autostart (Xiaomi)", "Lets the app relaunch after HyperOS kills it",
                CheckState.MANUAL, // MIUI/HyperOS exposes no query API for this
                miuiAutostartIntent(context) ?: appDetails,
            ),
        )
    }

    /** Xiaomi security-center autostart page; null when not a MIUI/HyperOS device. */
    private fun miuiAutostartIntent(context: Context): Intent? {
        val intent = Intent("miui.intent.action.OP_AUTO_START").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        return intent.takeIf {
            context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }
}
