package com.dispatcher.companion.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Default ASR on the Redmi 14C: the platform SpeechRecognizer (Google
 * recognition service), restarted continuously for streaming dictation.
 * It owns the microphone, so with speakerphone on it hears both call sides —
 * the practical capture path identified in Phase 1.
 */
class SpeechRecognizerAsr(
    private val context: Context,
    private val onEvent: (AsrEvent) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var utteranceStartMs = 0L

    fun start() {
        if (running || !SpeechRecognizer.isRecognitionAvailable(context)) return
        running = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        listen()
    }

    fun stop() {
        running = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!running) return
        utteranceStartMs = SystemClock.elapsedRealtime()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        recognizer?.startListening(intent)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle) {
            val text = partialResults
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            if (text.isNotBlank()) onEvent(AsrEvent.Partial(text, SystemClock.elapsedRealtime()))
        }

        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            val conf = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: 0.8f
            if (!text.isNullOrBlank()) {
                onEvent(
                    AsrEvent.Final(text, utteranceStartMs, SystemClock.elapsedRealtime(), conf.toDouble())
                )
            }
            listen() // continuous: immediately re-arm
        }

        override fun onError(error: Int) {
            // NO_MATCH / SPEECH_TIMEOUT are normal silences — just re-arm.
            if (running) listen()
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { utteranceStartMs = SystemClock.elapsedRealtime() }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
