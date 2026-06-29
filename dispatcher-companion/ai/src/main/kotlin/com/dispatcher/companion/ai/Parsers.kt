package com.dispatcher.companion.ai

import com.dispatcher.companion.model.CallSummary
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.FieldValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tolerant parsers for LLM JSON output. Malformed output never throws — the
 * pipelines degrade to tier-1 results instead (Phase 2 §8 error handling).
 */
object Parsers {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Strips ```json fences some models wrap around output. */
    private fun unfence(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    fun parseExtraction(raw: String): Map<FieldKey, FieldValue> = runCatching {
        val root = json.parseToJsonElement(unfence(raw)).jsonObject
        val fields = root["fields"]?.jsonObject ?: return emptyMap()
        buildMap {
            for ((key, el) in fields) {
                val fieldKey = runCatching { FieldKey.valueOf(key) }.getOrNull() ?: continue
                val obj = el.jsonObject
                val value = obj["value"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: continue
                val conf = obj["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.7
                put(fieldKey, FieldValue(value, conf.coerceIn(0.0, 1.0), FieldSource.LLM))
            }
        }
    }.getOrElse { emptyMap() }

    fun parseSummary(raw: String): CallSummary? = runCatching {
        val o = json.parseToJsonElement(unfence(raw)).jsonObject
        fun s(k: String) = o[k]?.jsonPrimitive?.content ?: ""
        val summary = CallSummary(
            lane = s("lane"),
            summaryShort = s("summary_short"),
            summaryDetailed = s("summary_detailed"),
            summaryBullets = s("summary_bullets"),
            summaryCrm = s("summary_crm"),
            keyDetails = s("key_details"),
            followUpActions = s("follow_up_actions"),
            nextSteps = s("next_steps"),
        )
        if (summary.summaryShort.isBlank() && summary.summaryDetailed.isBlank()) null else summary
    }.getOrNull()

    fun parseSuggestedReply(raw: String): String? = runCatching {
        json.parseToJsonElement(unfence(raw)).jsonObject["suggested_reply"]
            ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
