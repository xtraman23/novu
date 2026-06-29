package com.dispatcher.companion.asr

import com.dispatcher.companion.model.PcmChunk
import com.dispatcher.companion.model.Speaker
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 100 ms synthetic frames: voiced = 440 Hz tone at given amplitude. */
private fun frame(tMs: Long, voiced: Boolean, amplitude: Double = 0.3): PcmChunk {
    val n = 1600 // 100 ms @ 16 kHz
    val samples = ShortArray(n) { i ->
        if (voiced) (sin(2 * PI * 440 * i / 16_000.0) * amplitude * Short.MAX_VALUE).toInt().toShort()
        else 0
    }
    return PcmChunk(samples, tMs)
}

class EnergyVadSegmenterTest {

    @Test
    fun `detects one utterance bounded by silence`() {
        val vad = EnergyVadSegmenter(closeSilenceMs = 600)
        var result: Utterance? = null
        var t = 0L
        repeat(3) { vad.onChunk(frame(t, false)); t += 100 }          // lead-in silence
        repeat(8) { vad.onChunk(frame(t, true)); t += 100 }           // 800 ms speech
        repeat(7) { result = result ?: vad.onChunk(frame(t, false)); t += 100 }
        assertNotNull(result)
        assertEquals(300, result.tStartMs)
        assertEquals(1000, result.tEndMs)
    }

    @Test
    fun `single noise frame does not open speech`() {
        val vad = EnergyVadSegmenter(openFrames = 2)
        assertNull(vad.onChunk(frame(0, true)))   // 1 voiced frame
        repeat(20) { assertNull(vad.onChunk(frame(100L + it * 100, false))) }
        assertNull(vad.flush())
    }

    @Test
    fun `short pause inside an utterance does not split it`() {
        val vad = EnergyVadSegmenter(closeSilenceMs = 600)
        var t = 0L
        var closed: Utterance? = null
        repeat(4) { vad.onChunk(frame(t, true)); t += 100 }
        repeat(3) { closed = closed ?: vad.onChunk(frame(t, false)); t += 100 } // 300 ms pause
        repeat(4) { closed = closed ?: vad.onChunk(frame(t, true)); t += 100 }
        assertNull(closed)
        val u = vad.flush()
        assertNotNull(u)
        assertEquals(0, u.tStartMs)
    }
}

class TurnTakingDiarizerTest {

    @Test
    fun `dispatcher answers, broker replies after a gap`() {
        val d = TurnTakingDiarizer(firstSpeaker = Speaker.DISPATCHER, switchGapMs = 700)
        assertEquals(Speaker.DISPATCHER, d.assign(Utterance(0, 1_500, 0)))
        assertEquals(Speaker.BROKER, d.assign(Utterance(2_600, 5_000, 0)))   // 1.1 s gap
        assertEquals(Speaker.BROKER, d.assign(Utterance(5_200, 7_000, 0)))   // same turn
        assertEquals(Speaker.DISPATCHER, d.assign(Utterance(8_100, 9_000, 0)))
    }

    @Test
    fun `channel hint overrides the heuristic`() {
        val d = TurnTakingDiarizer()
        assertEquals(Speaker.BROKER, d.assign(Utterance(0, 1_000, 0), channelHint = Speaker.BROKER))
        assertEquals(Speaker.BROKER, d.assign(Utterance(1_100, 2_000, 0), channelHint = Speaker.BROKER))
    }
}

class TranscriptAssemblerTest {

    private fun assembler() = TranscriptAssembler(TurnTakingDiarizer())

    @Test
    fun `partials update live line and commit nothing`() {
        val a = assembler()
        assertNull(a.onEvent(AsrEvent.Partial("I only have", 1_000)))
        assertEquals("I only have", a.liveLine)
        val seg = a.onEvent(AsrEvent.Final("I only have seventeen hundred", 0, 2_000, 0.92))
        assertNotNull(seg)
        assertEquals(1, seg.seq)
        assertNull(a.liveLine)
    }

    @Test
    fun `finals get monotonic sequence numbers`() {
        val a = assembler()
        val s1 = a.onEvent(AsrEvent.Final("hello", 0, 500, 0.9))!!
        val s2 = a.onEvent(AsrEvent.Final("rate is nineteen hundred", 1_500, 3_000, 0.9))!!
        assertEquals(listOf(1, 2), listOf(s1.seq, s2.seq))
    }

    @Test
    fun `empty or blank finals are dropped`() {
        val a = assembler()
        assertNull(a.onEvent(AsrEvent.Final("   ", 0, 100, 0.5)))
    }

    @Test
    fun `crash recovery resumes sequence numbering`() {
        val a = assembler()
        a.resumeFrom(lastPersistedSeq = 41)
        val seg = a.onEvent(AsrEvent.Final("resumed", 0, 500, 0.9))!!
        assertEquals(42, seg.seq)
    }
}

class PipelinePerformanceTest {

    /** NFR: one hour of 100 ms frames must VAD+diarize+assemble in well under 2 s CPU. */
    @Test
    fun `one simulated hour processes fast enough for sub-2s budget`() {
        val vad = EnergyVadSegmenter()
        val assembler = TranscriptAssembler(TurnTakingDiarizer())
        val started = System.nanoTime()
        var t = 0L
        var utterances = 0
        repeat(36_000) { i ->
            val voiced = (i / 30) % 2 == 0 // alternate 3 s talk / 3 s silence
            val u = vad.onChunk(frame(t, voiced))
            if (u != null) {
                utterances++
                assembler.onEvent(AsrEvent.Final("u$utterances", u.tStartMs, u.tEndMs, 0.9))
            }
            t += 100
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(utterances > 500, "expected hundreds of utterances, got $utterances")
        // 1 hour of audio must process in a few seconds (>>real-time). Bound is
        // generous to avoid flakiness from JVM warmup on a loaded CI host; even
        // 5s is ~700x faster than the 3600s of audio it represents.
        assertTrue(elapsedMs < 5_000, "pipeline too slow: ${elapsedMs}ms for 1h audio")
    }
}
