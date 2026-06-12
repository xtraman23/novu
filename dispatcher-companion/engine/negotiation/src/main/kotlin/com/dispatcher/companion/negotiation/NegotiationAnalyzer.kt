package com.dispatcher.companion.negotiation

import com.dispatcher.companion.model.NegotiationAdvice
import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import kotlin.math.roundToInt

/** Conversational signals detected in the broker's speech (FR-501). */
data class NegotiationCues(
    val urgency: Double,      // 0..1 — broker needs it covered now
    val flexibility: Double,  // 0..1 — broker hints at room in the rate
    val firmness: Double,     // 0..1 — broker signals a hard limit
)

/** Dispatcher's economics, used to anchor the walk-away (FR-503). */
data class CostBasis(
    val totalMiles: Double,
    val minRatePerMile: Double,
) {
    val costFloorUsd: Double get() = totalMiles * minRatePerMile
}

/**
 * Deterministic tier of the negotiation copilot (FR-500). Always available
 * offline; the LLM tier refines the suggested reply when online.
 */
class NegotiationAnalyzer {

    private val urgencyCues = listOf(
        "today", "right now", "asap", "as soon as", "driver waiting",
        "need it covered", "needs to move", "hot load", "this morning",
        "by end of day", "falling off", "got to get this",
    )
    private val flexibilityCues = listOf(
        "let me see", "see what i can do", "talk to my", "maybe", "work with you",
        "wiggle", "might be able", "could go", "depends",
    )
    private val firmnessCues = listOf(
        "best i can do", "take it or leave it", "final", "only have", "max",
        "not a penny", "that's it", "can't go", "all i got", "tops",
    )

    fun detectCues(brokerText: String): NegotiationCues {
        val t = brokerText.lowercase()
        fun score(cues: List<String>) =
            (cues.count { t.contains(it) } / 2.0).coerceIn(0.0, 1.0)
        return NegotiationCues(score(urgencyCues), score(flexibilityCues), score(firmnessCues))
    }

    /**
     * @return advice, or null if the broker hasn't named a number yet.
     */
    fun advise(
        rateEvents: List<RateEvent>,
        cues: NegotiationCues,
        costBasis: CostBasis? = null,
    ): NegotiationAdvice? {
        val offer = rateEvents.lastOrNull { it.actor == RateActor.BROKER }?.amountUsd ?: return null

        // An urgent broker has more room above the stated number; a firm one has less.
        val floorMult = 1.06 + 0.06 * cues.urgency + 0.04 * cues.flexibility - 0.04 * cues.firmness
        val ceilMult = 1.25 + 0.10 * cues.urgency + 0.05 * cues.flexibility - 0.08 * cues.firmness
        val floor = offer * floorMult.coerceAtLeast(1.02)
        val ceiling = (offer * ceilMult).coerceAtLeast(floor + 100)

        val counter = round50((floor + ceiling) / 2)
        val walkAway = maxOf(costBasis?.costFloorUsd ?: 0.0, round50(floor).toDouble())
        val accept = (0.5 + 0.5 * (ceiling - counter) / (ceiling - floor)).coerceIn(0.05, 0.95)

        return NegotiationAdvice(
            suggestedReply = buildReply(counter, cues),
            counterUsd = counter.toDouble(),
            walkAwayUsd = walkAway,
            likelyFloorUsd = round50(floor).toDouble(),
            likelyCeilingUsd = round50(ceiling).toDouble(),
            acceptanceProbability = (accept * 100).roundToInt() / 100.0,
        )
    }

    private fun buildReply(counter: Int, cues: NegotiationCues): String {
        val amount = "$" + "%,d".format(counter)
        return when {
            cues.urgency > 0.4 ->
                "I can have a truck on it today for $amount — ready to book now."
            cues.firmness > 0.4 ->
                "I hear you. $amount and it's covered — otherwise I have to pass."
            else ->
                "I can move it for $amount. That's in line with this lane."
        }
    }

    private fun round50(v: Double): Int = ((v / 50).roundToInt() * 50)
}
