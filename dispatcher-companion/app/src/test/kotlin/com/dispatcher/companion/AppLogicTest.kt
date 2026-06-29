package com.dispatcher.companion

import android.speech.SpeechRecognizer
import com.dispatcher.companion.asr.RecognizerRestartPolicy
import com.dispatcher.companion.export.CallArchive
import com.dispatcher.companion.export.Exporter
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.RateKind
import com.dispatcher.companion.service.RcCallParser
import com.dispatcher.companion.session.LocalSummaryGenerator
import com.dispatcher.companion.wizard.SetupChecklist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcCallParserTest {

    @Test
    fun `recognizes RingCentral packages`() {
        assertTrue(RcCallParser.isRingCentral("com.glip.mobile"))
        assertTrue(RcCallParser.isRingCentral("com.ringcentral.android"))
        assertFalse(RcCallParser.isRingCentral("com.whatsapp"))
    }

    @Test
    fun `ongoing call notifications are detected by cue or timer`() {
        assertTrue(RcCallParser.looksLikeActiveCall("Mark Reynolds", "Ongoing call", true))
        assertTrue(RcCallParser.looksLikeActiveCall("Mark Reynolds", "04:32", true))
        assertFalse(RcCallParser.looksLikeActiveCall("Mark Reynolds", "Missed call", false))
        assertFalse(RcCallParser.looksLikeActiveCall("New message", "Hey there", true))
    }

    @Test
    fun `caller name strips boilerplate`() {
        assertEquals("Mark Reynolds", RcCallParser.callerName("Mark Reynolds — Ongoing call"))
    }
}

class SetupChecklistTest {

    @Test
    fun `notification listener flat-string parsing`() {
        val flat = "com.other/app.Listener:com.dispatcher.companion/com.dispatcher.companion.service.RcNotificationListener"
        assertTrue(SetupChecklist.listenerEnabled(flat, "com.dispatcher.companion"))
        assertFalse(SetupChecklist.listenerEnabled(flat, "com.nope"))
        assertFalse(SetupChecklist.listenerEnabled(null, "com.dispatcher.companion"))
    }
}

class RecognizerRestartPolicyTest {

    @Test
    fun `quiet stretches re-arm quickly so listening never stops`() {
        for (code in listOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            val d = RecognizerRestartPolicy.afterError(code)
            assertTrue(d.restart)
            assertTrue(d.delayMs <= 200)
        }
    }

    @Test
    fun `recognizer busy backs off before retrying`() {
        val d = RecognizerRestartPolicy.afterError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertTrue(d.restart)
        assertTrue(d.delayMs >= 400, "busy must back off, was ${d.delayMs}")
    }

    @Test
    fun `transient network and audio errors restart with a longer delay`() {
        for (code in listOf(
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_AUDIO,
        )) {
            assertTrue(RecognizerRestartPolicy.afterError(code).restart)
        }
    }

    @Test
    fun `missing permission is permanent - the loop stops`() {
        val d = RecognizerRestartPolicy.afterError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertFalse(d.restart)
    }

    @Test
    fun `unknown error codes still retry rather than dying silently`() {
        assertTrue(RecognizerRestartPolicy.afterError(9999).restart)
    }
}

class LocalSummaryGeneratorTest {

    private val fields = mapOf(
        FieldKey.PICKUP to FieldValue("Dallas, TX", 0.9, FieldSource.REGEX),
        FieldKey.DELIVERY to FieldValue("Atlanta, GA", 0.9, FieldSource.REGEX),
        FieldKey.RATE to FieldValue("$2,000", 0.9, FieldSource.REGEX),
        FieldKey.WEIGHT to FieldValue("42,000 lbs", 0.9, FieldSource.REGEX),
    )
    private val rates = listOf(
        RateEvent(0, RateActor.BROKER, 1700.0, RateKind.OFFER),
        RateEvent(9_000, RateActor.DISPATCHER, 2000.0, RateKind.AGREED),
    )

    @Test
    fun `summary contains lane, rate, outcome and negotiation path`() {
        val s = LocalSummaryGenerator.generate(fields, rates, emptyList(), "BOOKED")
        assertEquals("Dallas, TX → Atlanta, GA", s.lane)
        assertTrue("$2,000" in s.summaryShort)
        assertTrue("BOOKED" in s.summaryShort)
        assertTrue("$1,700 → $2,000" in s.summaryDetailed)
        assertTrue(s.summaryBullets.lines().all { it.startsWith("- ") })
        assertTrue("rate confirmation" in s.followUpActions)
    }

    @Test
    fun `empty extraction still yields a usable summary`() {
        val s = LocalSummaryGenerator.generate(emptyMap(), emptyList(), emptyList(), "FOLLOW_UP")
        assertEquals("Lane unknown", s.lane)
        assertTrue("Call broker back" in s.followUpActions)
    }
}

class CallArchiveTest {

    private val segments = listOf(
        TranscriptSegment(1, Speaker.BROKER, "I have a load Dallas to Atlanta", 0, 2_000, 0.9),
        TranscriptSegment(2, Speaker.DISPATCHER, "What's the rate", 3_000, 4_000, 0.9),
        TranscriptSegment(3, Speaker.BROKER, "Nineteen hundred", 65_000, 66_000, 0.9),
    )
    private val fields = mapOf(
        FieldKey.PICKUP to FieldValue("Dallas, TX", 0.9, FieldSource.REGEX),
        FieldKey.DELIVERY to FieldValue("Atlanta, GA", 0.9, FieldSource.REGEX),
        FieldKey.RATE to FieldValue("$1,900", 0.9, FieldSource.REGEX),
        FieldKey.SPECIAL_REQUIREMENTS to FieldValue("FCFS, Hazmat", 0.8, FieldSource.REGEX),
    )
    private val rates = listOf(RateEvent(65_000, RateActor.BROKER, 1900.0, RateKind.OFFER))

    @Test
    fun `transcript keeps every line with speaker and timestamp`() {
        val t = CallArchive.transcriptText(segments)
        assertTrue("BROKER: I have a load" in t)
        assertTrue("DISPATCHER: What's the rate" in t)
        assertTrue("[01:05]" in t) // 65s → mm:ss
        assertEquals(3, t.lines().count { it.startsWith("[") })
    }

    @Test
    fun `info file has structured fields but no conversational text`() {
        val info = CallArchive.infoText(fields, rates)
        assertTrue("Pickup: Dallas, TX" in info)
        assertTrue("Rate: $1,900" in info)
        assertTrue("Special requirements: FCFS, Hazmat" in info)
        assertTrue("Rate negotiation" in info)
        assertFalse("What's the rate" in info) // transcript text must not leak in
    }

    @Test
    fun `info file omits empty fields`() {
        val info = CallArchive.infoText(mapOf(FieldKey.PICKUP to FieldValue("Memphis", 0.9, FieldSource.REGEX)), emptyList())
        assertTrue("Pickup: Memphis" in info)
        assertFalse("Delivery:" in info)
        assertFalse("Rate negotiation" in info)
    }
}

class ExporterTest {

    private val summary = LocalSummaryGenerator.generate(
        mapOf(FieldKey.PICKUP to FieldValue("Dallas, TX", 0.9, FieldSource.REGEX)),
        emptyList(), emptyList(), "BOOKED",
    )

    @Test
    fun `txt export carries the full summary`() {
        val txt = Exporter.toTxt(summary)
        assertTrue("CALL SUMMARY" in txt)
        assertTrue("Follow-up:" in txt)
    }

    @Test
    fun `csv export is well-formed and escapes quotes`() {
        val csv = Exporter.toCsv(summary.copy(keyDetails = """He said "book it""""))
        val lines = csv.lines()
        assertEquals(2, lines.size)
        assertEquals(5, lines[0].split(',').size)
        assertTrue("\"\"book it\"\"" in lines[1])
    }
}
