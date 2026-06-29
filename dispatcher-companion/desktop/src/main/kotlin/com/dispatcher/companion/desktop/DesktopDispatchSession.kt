package com.dispatcher.companion.desktop

import com.dispatcher.companion.extraction.ConversationContext
import com.dispatcher.companion.extraction.FreightExtractor
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.PdwcrMerger
import com.dispatcher.companion.model.NegotiationAdvice
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment
import com.dispatcher.companion.negotiation.NegotiationAnalyzer

/**
 * Pure-JVM analog of the Android DispatchSession: drives the SAME engines
 * (FreightExtractor + NegotiationAnalyzer) with no Android, Compose, or DB
 * dependencies. This is the proof that the freight "brains" run unchanged on
 * a Windows PC — fed by the two-stream capture (mic = dispatcher, loopback =
 * broker), each utterance arrives already speaker-labelled.
 */
class DesktopDispatchSession {

    private val extractor = FreightExtractor()
    private val analyzer = NegotiationAnalyzer()
    private val context = ConversationContext()
    private var seq = 0
    private var clockMs = 0L

    val transcript = mutableListOf<TranscriptSegment>()
    val rateEvents = mutableListOf<RateEvent>()
    var fields: Map<FieldKey, FieldValue> = emptyMap()
        private set
    var advice: NegotiationAdvice? = null
        private set

    data class Update(
        val segment: TranscriptSegment,
        val fields: Map<FieldKey, FieldValue>,
        val newRateEvents: List<RateEvent>,
        val advice: NegotiationAdvice?,
    )

    /** Feed one speaker-labelled utterance; returns the resulting live state. */
    fun onUtterance(speaker: Speaker, text: String, durationMs: Long = 3_500): Update {
        val start = clockMs
        clockMs += durationMs + 500
        val segment = TranscriptSegment(++seq, speaker, text.trim(), start, start + durationMs, 0.9)
        transcript += segment

        // 1. Answer a question a previous utterance asked (Q&A calls).
        context.resolveAnswer(segment.text, fields)?.let { (key, value) ->
            fields = PdwcrMerger.merge(fields, key, FieldValue(value, 0.8, FieldSource.REGEX))
        }
        // 2. Normal per-utterance extraction.
        val result = extractor.extractSegment(segment, fields)
        fields = result.fields
        rateEvents += result.rateEvents
        // 3. Arm the expectation for the NEXT utterance.
        context.noteQuestion(segment.text)

        // Recompute advice from everything the broker has said so far.
        val brokerText = transcript.filter { it.speaker == Speaker.BROKER }
            .joinToString(" ") { it.text }
        if (brokerText.isNotBlank()) {
            analyzer.advise(rateEvents, analyzer.detectCues(brokerText))?.let { advice = it }
        }
        return Update(segment, fields, result.rateEvents, advice)
    }
}
