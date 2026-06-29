package com.dispatcher.companion.report

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment

/**
 * Pure builders for the two separate call artifacts the dispatcher wants kept
 * apart (FR-800): the FULL verbatim transcript, and the EXTRACTED INFO only.
 *
 * Lives in a platform-agnostic kotlin("jvm") module so BOTH the Android app
 * and the Windows desktop build share one source of truth — identical output
 * on phone and PC.
 */
object CallReport {

    /** Whole conversation, speaker-labelled, with mm:ss timestamps. */
    fun transcriptText(segments: List<TranscriptSegment>): String = buildString {
        appendLine("FULL CALL TRANSCRIPT")
        appendLine("Segments: ${segments.size}")
        appendLine("=".repeat(40))
        for (s in segments) {
            val who = when (s.speaker) {
                Speaker.BROKER -> "BROKER"
                Speaker.DISPATCHER -> "DISPATCHER"
                Speaker.UNKNOWN -> "UNKNOWN"
            }
            appendLine("[${stamp(s.tStartMs)}] $who: ${s.text}")
        }
    }.trimEnd()

    /** Structured load info only — no conversational text. */
    fun infoText(fields: Map<FieldKey, FieldValue>, rateEvents: List<RateEvent>): String = buildString {
        appendLine("LOAD INFORMATION")
        appendLine("=".repeat(40))
        fun line(label: String, key: FieldKey) {
            fields[key]?.text?.takeIf { it.isNotBlank() }?.let { appendLine("$label: $it") }
        }
        appendLine("-- PDWCR --")
        line("Pickup", FieldKey.PICKUP)
        line("Delivery", FieldKey.DELIVERY)
        line("Weight", FieldKey.WEIGHT)
        line("Commodity", FieldKey.COMMODITY)
        line("Rate", FieldKey.RATE)
        appendLine("-- Details --")
        line("Pickup ZIP", FieldKey.PICKUP_ZIP)
        line("Delivery ZIP", FieldKey.DELIVERY_ZIP)
        line("Equipment", FieldKey.EQUIPMENT)
        line("Length (ft)", FieldKey.LENGTH_FT)
        line("Broker", FieldKey.BROKER_NAME)
        line("Company", FieldKey.BROKER_COMPANY)
        line("MC number", FieldKey.MC_NUMBER)
        line("Pickup appt", FieldKey.APPOINTMENT_PICKUP)
        line("Delivery appt", FieldKey.APPOINTMENT_DELIVERY)
        line("Special requirements", FieldKey.SPECIAL_REQUIREMENTS)
        line("Detention", FieldKey.DETENTION)
        line("Layover", FieldKey.LAYOVER)
        line("Notes", FieldKey.NOTES)
        if (rateEvents.isNotEmpty()) {
            appendLine("-- Rate negotiation --")
            for (e in rateEvents) {
                val who = when (e.actor) {
                    RateActor.BROKER -> "Broker"
                    RateActor.DISPATCHER -> "You"
                    RateActor.AI_SUGGESTION -> "AI"
                }
                appendLine("[${stamp(e.tMs)}] $who: $%,.0f (${e.kind})".format(e.amountUsd))
            }
        }
    }.trimEnd()

    private fun stamp(ms: Long): String {
        val totalSec = (ms / 1000)
        return "%02d:%02d".format(totalSec / 60 % 60, totalSec % 60)
    }
}
