package com.dispatcher.companion.asr

import android.content.Context
import android.media.AudioManager

/**
 * Suppresses the SpeechRecognizer start/stop "ding" for the whole dispatch
 * session by muting the streams the recognizer beep plays on (varies by OEM:
 * MUSIC on stock, SYSTEM/NOTIFICATION on some Xiaomi builds). Call audio rides
 * STREAM_VOICE_CALL, which we never touch, so the broker still hears you and
 * you still hear them — only the recognizer beep is silenced.
 *
 * mute() and unmute() are balanced; unmute() restores the user's prior volume.
 */
class BeepSilencer(context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val streams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_NOTIFICATION,
    )
    private val saved = HashMap<Int, Int>()
    private var muted = false

    fun mute() {
        if (muted) return
        muted = true
        for (s in streams) {
            saved[s] = runCatching { am.getStreamVolume(s) }.getOrDefault(0)
            runCatching { am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0) }
        }
    }

    fun unmute() {
        if (!muted) return
        muted = false
        for (s in streams) {
            // Prefer an explicit unmute; fall back to restoring the saved level.
            val ok = runCatching { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) }.isSuccess
            if (!ok) saved[s]?.let { runCatching { am.setStreamVolume(s, it, 0) } }
        }
        saved.clear()
    }
}
