package com.dispatcher.companion.model

/** One 16 kHz mono PCM-16 buffer flowing from capture to ASR. */
class PcmChunk(val samples: ShortArray, val timestampMs: Long) {
    /** Root-mean-square level, 0.0 (silence) .. 1.0 (full scale). */
    fun rms(): Double {
        if (samples.isEmpty()) return 0.0
        var acc = 0.0
        for (s in samples) acc += s.toDouble() * s.toDouble()
        return kotlin.math.sqrt(acc / samples.size) / Short.MAX_VALUE
    }
}
