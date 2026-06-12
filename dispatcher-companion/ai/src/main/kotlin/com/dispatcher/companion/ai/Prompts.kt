package com.dispatcher.companion.ai

import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment

/** Prompt builders for the three LLM jobs. All demand strict JSON output. */
object Prompts {

    private fun renderTranscript(segments: List<TranscriptSegment>) =
        segments.joinToString("\n") { s ->
            val who = when (s.speaker) {
                Speaker.BROKER -> "Broker"
                Speaker.DISPATCHER -> "Dispatcher"
                Speaker.UNKNOWN -> "Unknown"
            }
            "$who: ${s.text}"
        }

    const val EXTRACTION_SYSTEM = """You extract freight load details from truck-dispatch call transcripts.
Respond with ONLY a JSON object, no prose, of the shape:
{"fields": {"<FIELD>": {"value": "<string>", "confidence": <0..1>}}}
Allowed FIELD keys: """ + "PICKUP, DELIVERY, WEIGHT, COMMODITY, RATE, PICKUP_ZIP, DELIVERY_ZIP, " +
        "EQUIPMENT, LENGTH_FT, BROKER_NAME, BROKER_COMPANY, MC_NUMBER, APPOINTMENT_PICKUP, " +
        "APPOINTMENT_DELIVERY, SPECIAL_REQUIREMENTS, DETENTION, LAYOVER, NOTES" + """
Only include fields actually mentioned. RATE is the latest rate under discussion in dollars like "$1,900".
WEIGHT like "42,000 lbs". PICKUP/DELIVERY like "Dallas, TX"."""

    fun extractionUser(segments: List<TranscriptSegment>) =
        "Transcript so far:\n" + renderTranscript(segments)

    const val SUMMARY_SYSTEM = """You write call summaries for truck dispatchers after broker calls.
Respond with ONLY a JSON object:
{"lane": "...", "summary_short": "...", "summary_detailed": "...",
 "summary_bullets": "...", "summary_crm": "...", "key_details": "...",
 "follow_up_actions": "...", "next_steps": "..."}
summary_bullets uses "- " lines. summary_crm is a CRM activity note. Be factual; never invent rates."""

    fun summaryUser(segments: List<TranscriptSegment>, outcome: String) =
        "Outcome: $outcome\nTranscript:\n" + renderTranscript(segments)

    const val NEGOTIATION_SYSTEM = """You are a freight negotiation copilot. Given the live transcript and the
deterministic analysis, refine the suggested reply the dispatcher should say next.
Respond with ONLY: {"suggested_reply": "..."} — one or two spoken sentences, confident, no fluff."""

    fun negotiationUser(
        segments: List<TranscriptSegment>,
        counterUsd: Double,
        floorUsd: Double,
        ceilingUsd: Double,
    ) = "Counter: $$counterUsd, est. floor: $$floorUsd, est. ceiling: $$ceilingUsd\n" +
        "Transcript:\n" + renderTranscript(segments)
}
