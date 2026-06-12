package com.dispatcher.companion.session

import com.dispatcher.companion.model.CallSummary
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.TranscriptSegment

/**
 * Offline summary generator (FR-801/901) — deterministic, always available.
 * The LLM tier replaces these texts when a provider is configured and online.
 */
object LocalSummaryGenerator {

    fun generate(
        fields: Map<FieldKey, FieldValue>,
        rateEvents: List<RateEvent>,
        transcript: List<TranscriptSegment>,
        outcome: String,
    ): CallSummary {
        fun f(k: FieldKey) = fields[k]?.text
        val lane = listOfNotNull(f(FieldKey.PICKUP), f(FieldKey.DELIVERY))
            .joinToString(" → ").ifEmpty { "Lane unknown" }
        val rate = f(FieldKey.RATE) ?: "rate not settled"
        val ratePath = rateEvents.joinToString(" → ") { "$" + "%,.0f".format(it.amountUsd) }

        val facts = buildList {
            f(FieldKey.WEIGHT)?.let { add("Weight: $it") }
            f(FieldKey.COMMODITY)?.let { add("Commodity: $it") }
            f(FieldKey.EQUIPMENT)?.let { add("Equipment: $it") }
            f(FieldKey.LENGTH_FT)?.let { add("Length: $it ft") }
            f(FieldKey.MC_NUMBER)?.let { add("MC: $it") }
            f(FieldKey.APPOINTMENT_PICKUP)?.let { add("Pickup appt: $it") }
            f(FieldKey.APPOINTMENT_DELIVERY)?.let { add("Delivery appt: $it") }
            f(FieldKey.DETENTION)?.let { add("Detention: $it") }
            f(FieldKey.LAYOVER)?.let { add("Layover: $it") }
            if (ratePath.isNotEmpty()) add("Rate path: $ratePath")
        }

        val short = "$lane at $rate — $outcome."
        val detailed = buildString {
            appendLine("Call result: $outcome")
            appendLine("Lane: $lane")
            appendLine("Rate: $rate")
            facts.forEach(::appendLine)
            append("Transcript segments: ${transcript.size}")
        }.trim()
        val bullets = (listOf("Lane: $lane", "Rate: $rate", "Outcome: $outcome") + facts)
            .joinToString("\n") { "- $it" }
        val crm = "Call with broker re: $lane. Discussed $rate. Outcome: $outcome." +
            (if (ratePath.isNotEmpty()) " Negotiation: $ratePath." else "")

        val followUps = when (outcome.uppercase()) {
            "BOOKED" -> "Send rate confirmation; dispatch driver; confirm pickup appointment."
            "FOLLOW_UP" -> "Call broker back; confirm whether load is still available."
            else -> "Log lane and rate for market reference."
        }
        return CallSummary(
            lane = lane,
            summaryShort = short,
            summaryDetailed = detailed,
            summaryBullets = bullets,
            summaryCrm = crm,
            keyDetails = facts.joinToString("; "),
            followUpActions = followUps,
            nextSteps = followUps.substringBefore(";"),
        )
    }
}
