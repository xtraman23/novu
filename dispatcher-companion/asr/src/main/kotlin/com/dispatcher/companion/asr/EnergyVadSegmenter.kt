package com.dispatcher.companion.asr

import com.dispatcher.companion.model.PcmChunk

/** A contiguous stretch of detected speech. */
data class Utterance(val tStartMs: Long, val tEndMs: Long, val frames: Int)

/**
 * Energy-gated voice-activity segmentation. Feeds offline recognizers that
 * want utterance-sized input, and drives the diarizer's gap measurement.
 *
 * Speech opens after [openFrames] consecutive frames above [rmsThreshold];
 * it closes after [closeSilenceMs] of continuous silence.
 */
class EnergyVadSegmenter(
    private val rmsThreshold: Double = 0.01,
    private val openFrames: Int = 2,
    private val closeSilenceMs: Long = 600,
) {
    private var inSpeech = false
    private var aboveCount = 0
    private var speechStartMs = 0L
    private var lastVoiceMs = 0L
    private var frames = 0

    /** @return a completed [Utterance] when one closes on this chunk, else null. */
    fun onChunk(chunk: PcmChunk): Utterance? {
        val voiced = chunk.rms() > rmsThreshold
        val t = chunk.timestampMs
        if (!inSpeech) {
            if (voiced) {
                if (aboveCount == 0) speechStartMs = t
                aboveCount++
                if (aboveCount >= openFrames) {
                    inSpeech = true
                    lastVoiceMs = t
                    frames = aboveCount
                }
            } else {
                aboveCount = 0
            }
            return null
        }
        frames++
        if (voiced) {
            lastVoiceMs = t
            return null
        }
        if (t - lastVoiceMs >= closeSilenceMs) {
            inSpeech = false
            aboveCount = 0
            val u = Utterance(speechStartMs, lastVoiceMs, frames)
            frames = 0
            return u
        }
        return null
    }

    /** Flush a trailing open utterance at end of call. */
    fun flush(): Utterance? {
        if (!inSpeech) return null
        inSpeech = false
        aboveCount = 0
        return Utterance(speechStartMs, maxOf(lastVoiceMs, speechStartMs), frames).also { frames = 0 }
    }
}
