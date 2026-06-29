package com.dispatcher.companion.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Default ASR on the Redmi 14C: the platform SpeechRecognizer (Google
 * recognition service), kept listening **continuously** until the dispatcher
 * stops dispatch mode. It owns the microphone, so with speakerphone on it
 * hears both call sides — the practical capture path identified in Phase 1.
 *
 * Two behaviours the bare recognizer doesn't give us, added here:
 *  - No repeating activation "ding": [BeepSilencer] mutes the recognizer's
 *    beep streams for the whole session (call audio is untouched).
 *  - Truly continuous: the recognizer ends after each utterance/silence, so we
 *    re-arm it via [RecognizerRestartPolicy] until [stop] is called by hand.
 */
class SpeechRecognizerAsr(
    private val context: Context,
    private val onEvent: (AsrEvent) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val beepSilencer = BeepSilencer(context)
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var listeningArmed = false
    private var utteranceStartMs = 0L

    fun start() {
        if (running || !SpeechRecognizer.isRecognitionAvailable(context)) return
        running = true
        beepSilencer.mute() // silence the activation ding for the whole session
        main.post {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
            armListening()
        }
    }

    fun stop() {
        running = false
        main.post {
            main.removeCallbacksAndMessages(null)
            runCatching { recognizer?.cancel() }
            recognizer?.destroy()
            recognizer = null
            beepSilencer.unmute() // always restore the user's volume
        }
    }

    /** Re-arm after a delay, but only while the session is still running. */
    private fun scheduleRestart(delayMs: Long) {
        if (!running) return
        main.postDelayed({ armListening() }, delayMs)
    }

    private fun armListening() {
        if (!running || recognizer == null) return
        // Guard against double-arming (BUSY) — cancel any in-flight session first.
        if (listeningArmed) runCatching { recognizer?.cancel() }
        listeningArmed = true
        utteranceStartMs = SystemClock.elapsedRealtime()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Tolerate the natural pauses in a negotiation without ending early.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8_000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        }
        runCatching { recognizer?.startListening(intent) }
            .onFailure { scheduleRestart(RecognizerRestartPolicy.AFTER_RESULT.delayMs) }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle) {
            val text = partialResults
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            if (text.isNotBlank()) onEvent(AsrEvent.Partial(text, SystemClock.elapsedRealtime()))
        }

        override fun onResults(results: Bundle) {
            listeningArmed = false
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val conf = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: 0.8f
            if (!text.isNullOrBlank()) {
                onEvent(AsrEvent.Final(text, utteranceStartMs, SystemClock.elapsedRealtime(), conf.toDouble()))
            }
            scheduleRestart(RecognizerRestartPolicy.AFTER_RESULT.delayMs) // never stops on its own
        }

        override fun onError(error: Int) {
            listeningArmed = false
            val decision = RecognizerRestartPolicy.afterError(error)
            if (decision.restart) scheduleRestart(decision.delayMs)
            else stop() // permanent (e.g. permission revoked)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { utteranceStartMs = SystemClock.elapsedRealtime() }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
