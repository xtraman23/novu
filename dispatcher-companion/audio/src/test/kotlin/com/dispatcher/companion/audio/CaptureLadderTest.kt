package com.dispatcher.companion.audio

import com.dispatcher.companion.model.CaptureMethodId
import com.dispatcher.companion.model.CaptureQuality
import com.dispatcher.companion.model.PcmChunk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeMethod(
    override val id: CaptureMethodId,
    private val result: ProbeResult,
) : CaptureMethod {
    override suspend fun probe() = result
    override fun start() = object : ActiveCapture {
        override val method = id
        override val quality = (result as ProbeResult.Ok).quality
        override val chunks = emptyFlow<PcmChunk>()
        override fun stop() {}
    }
}

class CaptureLadderTest {

    private fun ladder(vararg pairs: Pair<CaptureMethodId, ProbeResult>) =
        CaptureMethodLadder(pairs.map { FakeMethod(it.first, it.second) })

    @Test
    fun `first Ok method wins in priority order`() = runTest {
        val sel = ladder(
            CaptureMethodId.PLAYBACK_CAPTURE to ProbeResult.Silent,
            CaptureMethodId.VOIP_DIRECT to ProbeResult.Unavailable,
            CaptureMethodId.ACCESSIBILITY to ProbeResult.Ok(CaptureQuality.EXCELLENT),
            CaptureMethodId.MICROPHONE to ProbeResult.Ok(CaptureQuality.GOOD),
        ).select()!!
        assertEquals(CaptureMethodId.ACCESSIBILITY, sel.method.id)
        assertEquals(CaptureQuality.EXCELLENT, sel.probe.quality)
    }

    @Test
    fun `realistic Redmi 14C ladder falls through to microphone`() = runTest {
        val sel = ladder(
            CaptureMethodId.PLAYBACK_CAPTURE to ProbeResult.Silent,
            CaptureMethodId.VOIP_DIRECT to ProbeResult.Unavailable,
            CaptureMethodId.ACCESSIBILITY to ProbeResult.Unavailable,
            CaptureMethodId.MEDIA_PROJECTION to ProbeResult.Silent,
            CaptureMethodId.MICROPHONE to ProbeResult.Ok(CaptureQuality.GOOD),
        ).select()!!
        assertEquals(CaptureMethodId.MICROPHONE, sel.method.id)
        assertEquals(5, sel.report.size) // full diagnostics for the wizard
    }

    @Test
    fun `returns null when everything is denied`() = runTest {
        val sel = ladder(
            CaptureMethodId.PLAYBACK_CAPTURE to ProbeResult.Denied,
            CaptureMethodId.MICROPHONE to ProbeResult.Denied,
        ).select()
        assertNull(sel)
    }

    @Test
    fun `probe exceptions are contained and treated as Silent`() = runTest {
        val throwing = object : CaptureMethod {
            override val id = CaptureMethodId.VOIP_DIRECT
            override suspend fun probe(): ProbeResult = error("OEM crash")
            override fun start() = throw IllegalStateException()
        }
        val sel = CaptureMethodLadder(
            listOf(throwing, FakeMethod(CaptureMethodId.MICROPHONE, ProbeResult.Ok(CaptureQuality.LIMITED)))
        ).select()!!
        assertEquals(CaptureMethodId.MICROPHONE, sel.method.id)
        assertTrue(sel.report[CaptureMethodId.VOIP_DIRECT] is ProbeResult.Silent)
    }

    @Test
    fun `failover excludes the failed method`() = runTest {
        val l = ladder(
            CaptureMethodId.ACCESSIBILITY to ProbeResult.Ok(CaptureQuality.EXCELLENT),
            CaptureMethodId.MICROPHONE to ProbeResult.Ok(CaptureQuality.GOOD),
        )
        val sel = l.failover(setOf(CaptureMethodId.ACCESSIBILITY))!!
        assertEquals(CaptureMethodId.MICROPHONE, sel.method.id)
    }
}

class SilenceWatchdogTest {

    @Test
    fun `trips after sustained silence window`() {
        val w = SilenceWatchdog(windowMs = 5_000, rmsThreshold = 0.004)
        assertEquals(false, w.onChunk(0, 0.0))
        assertEquals(false, w.onChunk(2_000, 0.001))
        assertEquals(true, w.onChunk(5_000, 0.0))
        assertTrue(w.tripped)
    }

    @Test
    fun `voice activity resets the window`() {
        val w = SilenceWatchdog(windowMs = 5_000)
        w.onChunk(0, 0.0)
        w.onChunk(4_000, 0.2) // speech
        assertEquals(false, w.onChunk(8_000, 0.0))
        assertEquals(true, w.onChunk(9_000, 0.0).let { it || w.onChunk(13_000, 0.0) })
    }

    @Test
    fun `trips only once until reset`() {
        val w = SilenceWatchdog(windowMs = 1_000)
        w.onChunk(0, 0.0)
        assertEquals(true, w.onChunk(1_000, 0.0))
        assertEquals(false, w.onChunk(2_000, 0.0))
        w.reset()
        w.onChunk(3_000, 0.0)
        assertEquals(true, w.onChunk(4_000, 0.0))
    }
}

class PcmChunkTest {

    @Test
    fun `rms of silence is zero and full-scale is ~1`() {
        assertEquals(0.0, PcmChunk(ShortArray(160), 0).rms())
        val full = ShortArray(160) { Short.MAX_VALUE }
        assertTrue(PcmChunk(full, 0).rms() > 0.99)
    }
}
