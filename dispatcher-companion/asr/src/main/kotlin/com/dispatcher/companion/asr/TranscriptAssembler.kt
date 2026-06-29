package com.dispatcher.companion.asr

import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment

/**
 * Turns the ASR event stream into the persistent transcript: partials update
 * a single live line (never persisted), finals commit monotonically-numbered
 * [TranscriptSegment]s — the idempotent-recovery contract of the calls DB.
 */
class TranscriptAssembler(private val diarizer: TurnTakingDiarizer) {

    private var seq = 0

    /** Current uncommitted hypothesis for the UI's live line, or null. */
    var liveLine: String? = null
        private set

    /**
     * @param channelHint speaker identity when capture is channel-separated.
     * @return a committed segment for [AsrEvent.Final], null for partials.
     */
    fun onEvent(event: AsrEvent, channelHint: Speaker? = null): TranscriptSegment? = when (event) {
        is AsrEvent.Partial -> {
            liveLine = event.text
            null
        }
        is AsrEvent.Final -> {
            liveLine = null
            val text = event.text.trim()
            if (text.isEmpty()) null
            else {
                val speaker = diarizer.assign(
                    Utterance(event.tStartMs, event.tEndMs, 0), channelHint
                )
                TranscriptSegment(
                    seq = ++seq,
                    speaker = speaker,
                    text = text,
                    tStartMs = event.tStartMs,
                    tEndMs = event.tEndMs,
                    confidence = event.confidence,
                )
            }
        }
    }

    /** Resume after crash recovery: continue numbering from the last persisted seq. */
    fun resumeFrom(lastPersistedSeq: Int) {
        seq = lastPersistedSeq
    }
}
