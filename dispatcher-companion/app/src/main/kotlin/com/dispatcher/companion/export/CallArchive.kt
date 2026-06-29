package com.dispatcher.companion.export

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.TranscriptSegment
import com.dispatcher.companion.report.CallReport

/**
 * Android-side entry point for the two separate call artifacts; delegates to
 * the shared pure [CallReport] so phone and desktop produce identical output.
 */
object CallArchive {
    fun transcriptText(segments: List<TranscriptSegment>): String =
        CallReport.transcriptText(segments)

    fun infoText(fields: Map<FieldKey, FieldValue>, rateEvents: List<RateEvent>): String =
        CallReport.infoText(fields, rateEvents)
}
