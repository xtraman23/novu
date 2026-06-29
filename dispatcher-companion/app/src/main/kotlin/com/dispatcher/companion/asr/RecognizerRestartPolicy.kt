package com.dispatcher.companion.asr

import android.speech.SpeechRecognizer

/**
 * Pure restart policy for the continuous SpeechRecognizer loop (no Android
 * deps so it is JVM-testable). The recognizer is built for one-shot commands
 * and ends after every utterance/silence, so we re-arm it forever — but we
 * must back off on transient errors (RECOGNIZER_BUSY) and give up on
 * permanent ones (missing permission), or the loop spins or beeps endlessly.
 */
object RecognizerRestartPolicy {

    data class Decision(val restart: Boolean, val delayMs: Long)

    /** What to do after a successful final result: re-arm promptly. */
    val AFTER_RESULT = Decision(restart = true, delayMs = 120)

    fun afterError(errorCode: Int): Decision = when (errorCode) {
        // Normal "nothing said" outcomes during a quiet stretch of the call.
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Decision(restart = true, delayMs = 120)

        // Engine still finishing the previous session — wait a beat, then retry.
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Decision(restart = true, delayMs = 500)

        // Transient client/network/server hiccups — back off a little longer.
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_AUDIO -> Decision(restart = true, delayMs = 800)

        // Permanent — nothing we restart will fix. Stop the loop.
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Decision(restart = false, delayMs = 0)

        // Unknown codes: cautiously retry with a moderate delay.
        else -> Decision(restart = true, delayMs = 600)
    }
}
