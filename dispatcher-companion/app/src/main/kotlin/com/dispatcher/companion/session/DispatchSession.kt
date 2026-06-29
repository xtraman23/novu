package com.dispatcher.companion.session

import com.dispatcher.companion.asr.AsrEvent
import com.dispatcher.companion.asr.TranscriptAssembler
import com.dispatcher.companion.asr.TurnTakingDiarizer
import com.dispatcher.companion.db.DispatcherDb
import com.dispatcher.companion.extraction.FreightExtractor
import com.dispatcher.companion.model.CallSummary
import com.dispatcher.companion.model.CaptureMethodId
import com.dispatcher.companion.model.CaptureQuality
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.NegotiationAdvice
import com.dispatcher.companion.model.PdwcrMerger
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.TranscriptSegment
import com.dispatcher.companion.negotiation.NegotiationAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One live call: ASR events in → transcript + PDWCR + advice out, everything
 * persisted incrementally (crash-safe). UI and overlay are pure observers.
 */
class DispatchSession(private val db: DispatcherDb?) {

    private val extractor = FreightExtractor()
    private val analyzer = NegotiationAnalyzer()
    private var assembler = TranscriptAssembler(TurnTakingDiarizer())
    private var callId: Long = -1

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _quality = MutableStateFlow<CaptureQuality?>(null)
    val quality: StateFlow<CaptureQuality?> = _quality.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val transcript: StateFlow<List<TranscriptSegment>> = _transcript.asStateFlow()

    private val _liveLine = MutableStateFlow<String?>(null)
    val liveLine: StateFlow<String?> = _liveLine.asStateFlow()

    private val _fields = MutableStateFlow<Map<FieldKey, FieldValue>>(emptyMap())
    val fields: StateFlow<Map<FieldKey, FieldValue>> = _fields.asStateFlow()

    private val _advice = MutableStateFlow<NegotiationAdvice?>(null)
    val advice: StateFlow<NegotiationAdvice?> = _advice.asStateFlow()

    private val _summary = MutableStateFlow<CallSummary?>(null)
    val summary: StateFlow<CallSummary?> = _summary.asStateFlow()

    private val rateEvents = mutableListOf<RateEvent>()

    /** Snapshot of the negotiation path for the info export. */
    fun rateEventsSnapshot(): List<RateEvent> = rateEvents.toList()

    @Synchronized
    fun start(method: CaptureMethodId, quality: CaptureQuality) {
        if (_active.value) return
        assembler = TranscriptAssembler(TurnTakingDiarizer())
        rateEvents.clear()
        _transcript.value = emptyList()
        _fields.value = emptyMap()
        _advice.value = null
        _summary.value = null
        _quality.value = quality
        callId = db?.startCall(method, quality) ?: -1
        _active.value = true
    }

    fun onAsrEvent(event: AsrEvent) {
        if (!_active.value) return
        val segment = assembler.onEvent(event)
        _liveLine.value = assembler.liveLine
        if (segment == null) return

        _transcript.value = _transcript.value + segment
        db?.takeIf { callId > 0 }?.insertSegment(callId, segment)

        val result = extractor.extractSegment(segment, _fields.value)
        _fields.value = result.fields
        result.rateEvents.forEach { e ->
            rateEvents += e
            db?.takeIf { callId > 0 }?.insertRateEvent(callId, e)
        }
        if (result.rateEvents.isNotEmpty() || _advice.value == null) {
            val brokerText = _transcript.value
                .filter { it.speaker == com.dispatcher.companion.model.Speaker.BROKER }
                .joinToString(" ") { it.text }
            analyzer.advise(rateEvents, analyzer.detectCues(brokerText))?.let { _advice.value = it }
        }
        db?.takeIf { callId > 0 }?.saveLoadFields(callId, _fields.value)
    }

    /** Manual field edit from the UI — sticky against the extractors (FR-404). */
    fun editField(key: FieldKey, value: String) {
        _fields.value = PdwcrMerger.merge(_fields.value, key, FieldValue(value, 1.0, FieldSource.MANUAL))
        db?.takeIf { callId > 0 }?.saveLoadFields(callId, _fields.value)
    }

    @Synchronized
    fun stop(outcome: String): CallSummary {
        _active.value = false
        _liveLine.value = null
        val summary = LocalSummaryGenerator.generate(_fields.value, rateEvents, _transcript.value, outcome)
        _summary.value = summary
        if (callId > 0) {
            db?.endCall(callId, outcome)
            db?.saveSummary(callId, summary, generatedBy = "LOCAL")
        }
        return summary
    }
}
