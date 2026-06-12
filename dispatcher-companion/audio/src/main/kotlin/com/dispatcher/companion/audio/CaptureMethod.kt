package com.dispatcher.companion.audio

import com.dispatcher.companion.model.CaptureMethodId
import com.dispatcher.companion.model.CaptureQuality
import kotlinx.coroutines.flow.Flow

/** One 16 kHz mono PCM-16 buffer from the active capture. */
class PcmChunk(val samples: ShortArray, val timestampMs: Long) {
    /** Root-mean-square level, 0.0 (silence) .. 1.0 (full scale). */
    fun rms(): Double {
        if (samples.isEmpty()) return 0.0
        var acc = 0.0
        for (s in samples) acc += s.toDouble() * s.toDouble()
        return kotlin.math.sqrt(acc / samples.size) / Short.MAX_VALUE
    }
}

sealed class ProbeResult {
    /** Method cannot exist on this device/OS (API level, system permission). */
    data object Unavailable : ProbeResult()

    /** Method exists but the user has not granted what it needs. */
    data object Denied : ProbeResult()

    /** Method started but produced only silence (e.g. OEM blocks the path). */
    data object Silent : ProbeResult()

    /** Method works. [quality] is what the user-facing chip will show. */
    data class Ok(val quality: CaptureQuality) : ProbeResult()
}

interface ActiveCapture {
    val method: CaptureMethodId
    val quality: CaptureQuality
    val chunks: Flow<PcmChunk>
    fun stop()
}

interface CaptureMethod {
    val id: CaptureMethodId

    /** Cheap, side-effect-free feasibility test. Never throws. */
    suspend fun probe(): ProbeResult

    /** Start capturing. Only called after probe() returned Ok. */
    fun start(): ActiveCapture
}
