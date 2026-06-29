package com.dispatcher.companion.asr

import com.dispatcher.companion.model.Speaker

/**
 * Heuristic diarization for single-channel (microphone/speakerphone) capture
 * (FR-303). When capture gives separated channels, channel identity overrides
 * this entirely via [channelHint].
 *
 * Heuristics:
 *  - The dispatcher answers the call, so the first utterance defaults to
 *    DISPATCHER (configurable for outbound dials).
 *  - A gap >= [switchGapMs] between utterances suggests the floor changed.
 *  - Back-to-back utterances with tiny gaps stay with the current speaker.
 */
class TurnTakingDiarizer(
    firstSpeaker: Speaker = Speaker.DISPATCHER,
    private val switchGapMs: Long = 700,
) {
    private var current = firstSpeaker
    private var lastEndMs = Long.MIN_VALUE

    fun assign(utterance: Utterance, channelHint: Speaker? = null): Speaker {
        if (channelHint != null && channelHint != Speaker.UNKNOWN) {
            current = channelHint
            lastEndMs = utterance.tEndMs
            return current
        }
        if (lastEndMs != Long.MIN_VALUE) {
            val gap = utterance.tStartMs - lastEndMs
            if (gap >= switchGapMs) current = flip(current)
        }
        lastEndMs = utterance.tEndMs
        return current
    }

    private fun flip(s: Speaker) = when (s) {
        Speaker.DISPATCHER -> Speaker.BROKER
        Speaker.BROKER -> Speaker.DISPATCHER
        Speaker.UNKNOWN -> Speaker.UNKNOWN
    }
}
