package com.dispatcher.companion.report

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.RateKind
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallReportTest {

    private val segments = listOf(
        TranscriptSegment(1, Speaker.BROKER, "I have a load Dallas to Atlanta", 0, 2_000, 0.9),
        TranscriptSegment(2, Speaker.DISPATCHER, "What's the rate", 3_000, 4_000, 0.9),
        TranscriptSegment(3, Speaker.BROKER, "Nineteen hundred", 65_000, 66_000, 0.9),
    )
    private val fields = mapOf(
        FieldKey.PICKUP to FieldValue("Dallas, TX", 0.9, FieldSource.REGEX),
        FieldKey.RATE to FieldValue("$1,900", 0.9, FieldSource.REGEX),
        FieldKey.SPECIAL_REQUIREMENTS to FieldValue("FCFS, Hazmat", 0.8, FieldSource.REGEX),
    )
    private val rates = listOf(RateEvent(65_000, RateActor.BROKER, 1900.0, RateKind.OFFER))

    @Test
    fun `transcript keeps every line with speaker and timestamp`() {
        val t = CallReport.transcriptText(segments)
        assertTrue("BROKER: I have a load" in t)
        assertTrue("DISPATCHER: What's the rate" in t)
        assertTrue("[01:05]" in t)
        assertEquals(3, t.lines().count { it.startsWith("[") })
    }

    @Test
    fun `info has structured fields but no conversation text`() {
        val info = CallReport.infoText(fields, rates)
        assertTrue("Pickup: Dallas, TX" in info)
        assertTrue("Special requirements: FCFS, Hazmat" in info)
        assertTrue("Rate negotiation" in info)
        assertFalse("What's the rate" in info)
    }
}
