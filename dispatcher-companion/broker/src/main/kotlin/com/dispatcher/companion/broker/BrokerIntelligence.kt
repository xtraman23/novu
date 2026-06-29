package com.dispatcher.companion.broker

import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.RateKind

enum class CallOutcome { BOOKED, REJECTED, FOLLOW_UP, UNKNOWN }

/** One historical call with a broker, as stored in the calls/rate_events tables. */
data class BrokerCallRecord(
    val lane: String,
    val outcome: CallOutcome,
    val agreedRateUsd: Double?,
    val loadedMiles: Double?,
    val rateEvents: List<RateEvent>,
)

enum class NegotiationStyle { AGGRESSIVE, FIRM, FLEXIBLE, UNKNOWN }

data class BrokerRollup(
    val totalCalls: Int,
    val bookedCalls: Int,
    val successRate: Double,        // booked / total
    val avgRatePerMile: Double?,    // over booked calls with miles
    val avgConcessionPct: Double?,  // (agreed - first offer) / first offer, booked calls
    val negotiationStyle: NegotiationStyle,
    val reliabilityScore: Double,   // 0..1
    val laneHistory: Map<String, Int>,
)

/**
 * Phase 9 broker memory analytics (FR-700): recomputed by a WorkManager job
 * after each call summary and denormalized onto the broker row.
 */
object BrokerIntelligence {

    fun rollup(calls: List<BrokerCallRecord>): BrokerRollup {
        val booked = calls.filter { it.outcome == CallOutcome.BOOKED }
        val successRate = if (calls.isEmpty()) 0.0 else booked.size.toDouble() / calls.size

        val rpms = booked.mapNotNull { c ->
            val rate = c.agreedRateUsd ?: return@mapNotNull null
            val miles = c.loadedMiles?.takeIf { it > 0 } ?: return@mapNotNull null
            rate / miles
        }
        val concessions = booked.mapNotNull { concessionPct(it) }
        val avgConcession = concessions.takeIf { it.isNotEmpty() }?.average()

        return BrokerRollup(
            totalCalls = calls.size,
            bookedCalls = booked.size,
            successRate = successRate,
            avgRatePerMile = rpms.takeIf { it.isNotEmpty() }?.average()?.round2(),
            avgConcessionPct = avgConcession?.round2(),
            negotiationStyle = style(avgConcession, calls.size),
            reliabilityScore = reliability(calls),
            laneHistory = calls.groupingBy { it.lane }.eachCount(),
        )
    }

    /** How far the broker moved off the first number before booking. */
    fun concessionPct(call: BrokerCallRecord): Double? {
        val first = call.rateEvents.firstOrNull { it.actor == RateActor.BROKER }?.amountUsd
            ?: return null
        val agreed = call.rateEvents.lastOrNull { it.kind == RateKind.AGREED }?.amountUsd
            ?: call.agreedRateUsd ?: return null
        if (first <= 0) return null
        return (agreed - first) / first
    }

    private fun style(avgConcession: Double?, sampleSize: Int): NegotiationStyle = when {
        sampleSize < 2 || avgConcession == null -> NegotiationStyle.UNKNOWN
        avgConcession >= 0.10 -> NegotiationStyle.FLEXIBLE
        avgConcession >= 0.03 -> NegotiationStyle.FIRM
        else -> NegotiationStyle.AGGRESSIVE
    }

    /** Bookings build trust; rejected/no-outcome calls erode it slightly. */
    private fun reliability(calls: List<BrokerCallRecord>): Double {
        if (calls.isEmpty()) return 0.0
        val score = calls.sumOf {
            when (it.outcome) {
                CallOutcome.BOOKED -> 1.0
                CallOutcome.FOLLOW_UP -> 0.5
                CallOutcome.UNKNOWN -> 0.3
                CallOutcome.REJECTED -> 0.1
            }
        } / calls.size
        // Confidence ramp: few calls → pulled toward neutral 0.5
        val confidence = (calls.size / 5.0).coerceAtMost(1.0)
        return (score * confidence + 0.5 * (1 - confidence)).round2()
    }

    private fun Double.round2() = Math.round(this * 100) / 100.0
}
