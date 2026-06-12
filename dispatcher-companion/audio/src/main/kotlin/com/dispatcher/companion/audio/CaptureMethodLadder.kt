package com.dispatcher.companion.audio

import com.dispatcher.companion.model.CaptureMethodId

/**
 * Probes capture methods in priority order (FR-201) and picks the first
 * working one. Pure logic — methods are injected, so this is JVM-testable.
 */
class CaptureMethodLadder(private val methods: List<CaptureMethod>) {

    data class Selection(
        val method: CaptureMethod,
        val probe: ProbeResult.Ok,
        /** Probe outcome per method id, for the setup wizard diagnostics screen. */
        val report: Map<CaptureMethodId, ProbeResult>,
    )

    /** @return the best working method, or null if nothing works (mic denied). */
    suspend fun select(): Selection? {
        val report = LinkedHashMap<CaptureMethodId, ProbeResult>()
        var winner: Pair<CaptureMethod, ProbeResult.Ok>? = null
        for (m in methods) {
            val r = runCatching { m.probe() }.getOrElse { ProbeResult.Silent }
            report[m.id] = r
            if (winner == null && r is ProbeResult.Ok) winner = m to r
        }
        return winner?.let { (m, ok) -> Selection(m, ok, report) }
    }

    /** Failover after a mid-call silence trip: re-select excluding [failed]. */
    suspend fun failover(failed: Set<CaptureMethodId>): Selection? =
        CaptureMethodLadder(methods.filter { it.id !in failed }).select()
}
