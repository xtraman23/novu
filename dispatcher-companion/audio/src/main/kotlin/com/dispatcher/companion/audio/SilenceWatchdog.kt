package com.dispatcher.companion.audio

/**
 * Trips when the active capture produces ~silence for [windowMs] while a call
 * is known to be active (FR-201 mid-call failover trigger).
 * Fed by PcmChunk RMS values; pure logic.
 */
class SilenceWatchdog(
    private val windowMs: Long = 5_000,
    private val rmsThreshold: Double = 0.004,
) {
    private var silentSinceMs: Long = -1
    var tripped: Boolean = false
        private set

    /** @return true the first time the watchdog trips. */
    fun onChunk(timestampMs: Long, rms: Double): Boolean {
        if (tripped) return false
        if (rms > rmsThreshold) {
            silentSinceMs = -1
            return false
        }
        if (silentSinceMs < 0) silentSinceMs = timestampMs
        if (timestampMs - silentSinceMs >= windowMs) {
            tripped = true
            return true
        }
        return false
    }

    fun reset() {
        silentSinceMs = -1
        tripped = false
    }
}
