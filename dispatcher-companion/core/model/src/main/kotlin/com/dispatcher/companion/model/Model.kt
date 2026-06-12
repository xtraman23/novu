package com.dispatcher.companion.model

enum class Speaker { BROKER, DISPATCHER, UNKNOWN }

data class TranscriptSegment(
    val seq: Int,
    val speaker: Speaker,
    val text: String,
    val tStartMs: Long,
    val tEndMs: Long,
    val confidence: Double,
)

/** All extractable load fields. The first five are the PDWCR core. */
enum class FieldKey(val isPdwcr: Boolean = false) {
    PICKUP(true), DELIVERY(true), WEIGHT(true), COMMODITY(true), RATE(true),
    PICKUP_ZIP, DELIVERY_ZIP, EQUIPMENT, LENGTH_FT,
    BROKER_NAME, BROKER_COMPANY, MC_NUMBER,
    APPOINTMENT_PICKUP, APPOINTMENT_DELIVERY,
    SPECIAL_REQUIREMENTS, DETENTION, LAYOVER, NOTES,
}

enum class FieldSource { REGEX, LLM, MANUAL }

data class FieldValue(
    val text: String,
    val confidence: Double,
    val source: FieldSource,
)

enum class RateActor { BROKER, DISPATCHER, AI_SUGGESTION }
enum class RateKind { OFFER, COUNTER, FLOOR_EST, CEILING_EST, AGREED }

data class RateEvent(
    val tMs: Long,
    val actor: RateActor,
    val amountUsd: Double,
    val kind: RateKind,
)

data class NegotiationAdvice(
    val suggestedReply: String,
    val counterUsd: Double,
    val walkAwayUsd: Double,
    val likelyFloorUsd: Double,
    val likelyCeilingUsd: Double,
    val acceptanceProbability: Double,
)

enum class CaptureQuality { EXCELLENT, GOOD, LIMITED, FALLBACK }

enum class CaptureMethodId { PLAYBACK_CAPTURE, VOIP_DIRECT, ACCESSIBILITY, MEDIA_PROJECTION, MICROPHONE }

data class BrokerProfile(
    val id: Long = 0,
    val name: String = "",
    val company: String = "",
    val mcNumber: String? = null,
    val negotiationStyle: String = "UNKNOWN",
    val reliabilityScore: Double = 0.0,
    val successRate: Double = 0.0,
    val avgRatePerMile: Double? = null,
    val notes: String = "",
)

data class CallSummary(
    val lane: String,
    val summaryShort: String,
    val summaryDetailed: String,
    val summaryBullets: String,
    val summaryCrm: String,
    val keyDetails: String,
    val followUpActions: String,
    val nextSteps: String,
)
