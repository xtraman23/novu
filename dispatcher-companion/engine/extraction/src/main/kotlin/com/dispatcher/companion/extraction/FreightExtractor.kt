package com.dispatcher.companion.extraction

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.FieldValue
import com.dispatcher.companion.model.PdwcrMerger
import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.RateKind
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment

data class ExtractionResult(
    val fields: Map<FieldKey, FieldValue>,
    val rateEvents: List<RateEvent>,
)

/**
 * Tier-1 deterministic PDWCR extractor (FR-401/402/403): regex + gazetteer,
 * pure Kotlin, <10 ms per segment. Tier-2 (LLM) refines the same fields with
 * higher confidence when online; both meet in [PdwcrMerger].
 */
class FreightExtractor {

    private val states = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN",
        "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV",
        "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN",
        "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    )

    // "Dallas", "Fort Worth", optionally followed by ", TX" / " TX" and/or a ZIP
    private val place = Regex(
        """([A-Z][a-z]+(?: [A-Z][a-z]+)?)(?:,? ([A-Z]{2}))?(?: (\d{5}))?"""
    )

    private val pickupCue = Regex(
        """(?i:pick(?:s|ing)? ?(?:up)? (?:in|at|out of|from)|pickup (?:is )?(?:in|at)|loading (?:in|at)|out of) (?=[A-Z])""",
    )
    private val deliveryCue = Regex(
        """(?i:deliver(?:s|ing|y)? (?:is )?(?:to|in|at)|drop(?:s|ping)? (?:in|at|off in)|going to|headed to|down to) (?=[A-Z])""",
    )
    private val laneCue = Regex("""([A-Z][a-z]+(?: [A-Z][a-z]+)?(?:,? [A-Z]{2})?) (?:to|over to) ([A-Z][a-z]+(?: [A-Z][a-z]+)?(?:,? [A-Z]{2})?)""")

    private val numericRate = Regex("""\$\s?(\d{1,2},\d{3}|\d{3,5})\b|\b(\d{1,2},\d{3}|\d{3,5})\s*(?:bucks|dollars)\b""")
    private val rateContext = Regex("""\b(rate|pay(?:s|ing)?|have|do it|give|offer|money|in it|all[- ]in|book it)\b""", RegexOption.IGNORE_CASE)

    private val weight = Regex("""\b(\d{1,3}(?:,\d{3})|\d{4,6})\s*(?:lbs?|pounds)\b|\b(\d{1,3})k\s*(?:lbs?|pounds)\b""", RegexOption.IGNORE_CASE)
    private val mcNumber = Regex("""\bMC\s*(?:number\s*)?(?:is\s*)?#?\s*(\d{4,8})\b""", RegexOption.IGNORE_CASE)
    private val zipCue = Regex("""\bzip(?: code)?\s*(?:is\s*)?(\d{5})\b""", RegexOption.IGNORE_CASE)
    private val lengthFt = Regex("""\b(\d{2})\s*(?:'|ft\b|foot\b|-foot\b)""")
    private val timeCue = Regex("""\b(?:at|by)\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.)|\d{4} hours)\b""", RegexOption.IGNORE_CASE)

    private val equipment = listOf(
        "dry van" to "DRY_VAN", "reefer" to "REEFER", "flatbed" to "FLATBED",
        "step deck" to "STEP_DECK", "power only" to "POWER_ONLY",
        "box truck" to "BOX_TRUCK", "hotshot" to "HOTSHOT", "conestoga" to "CONESTOGA",
        "van" to "DRY_VAN",
    )
    private val commodities = listOf(
        "dry goods", "paper products", "paper", "produce", "frozen food", "frozen",
        "steel", "lumber", "electronics", "beverages", "bottled water", "water",
        "furniture", "auto parts", "general freight", "food grade", "machinery",
    )

    /** Incrementally extract from one new segment, merging into [current]. */
    fun extractSegment(
        segment: TranscriptSegment,
        current: Map<FieldKey, FieldValue> = emptyMap(),
    ): ExtractionResult {
        val text = segment.text
        var fields = current
        val events = mutableListOf<RateEvent>()

        fun put(key: FieldKey, value: String, conf: Double) {
            fields = PdwcrMerger.merge(fields, key, FieldValue(value, conf, FieldSource.REGEX))
        }

        // --- Lane: "Dallas to Atlanta" / "Dallas, TX to Atlanta, GA"
        laneCue.find(text)?.let { m ->
            val from = m.groupValues[1].trim()
            val to = m.groupValues[2].trim()
            if (looksLikePlace(from) && looksLikePlace(to)) {
                put(FieldKey.PICKUP, normalizePlace(from), 0.75)
                put(FieldKey.DELIVERY, normalizePlace(to), 0.75)
            }
        }
        // --- Directional cues, higher confidence than bare lane
        extractPlaceAfter(pickupCue, text)?.let { (p, zip) ->
            put(FieldKey.PICKUP, p, 0.85)
            zip?.let { put(FieldKey.PICKUP_ZIP, it, 0.9) }
        }
        extractPlaceAfter(deliveryCue, text)?.let { (p, zip) ->
            put(FieldKey.DELIVERY, p, 0.85)
            zip?.let { put(FieldKey.DELIVERY_ZIP, it, 0.9) }
        }

        // --- Rate: numeric (with $ or context) and spoken
        val numeric = numericRate.findAll(text).mapNotNull { m ->
            val raw = (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace(",", "")
            val amount = raw.toIntOrNull() ?: return@mapNotNull null
            val explicit = m.value.startsWith("$")
            if (amount in 200..20_000 && (explicit || rateContext.containsMatchIn(text))) amount else null
        }.toList()
        val spoken = SpokenNumbers.findAmounts(text)
            .filter { it in 200..20_000 && rateContext.containsMatchIn(text) }
        for (amount in numeric + spoken) {
            val conf = if (numeric.contains(amount)) 0.9 else 0.8
            put(FieldKey.RATE, "$" + "%,d".format(amount), conf)
            events += RateEvent(
                tMs = segment.tStartMs,
                actor = when (segment.speaker) {
                    Speaker.BROKER -> RateActor.BROKER
                    Speaker.DISPATCHER -> RateActor.DISPATCHER
                    Speaker.UNKNOWN -> RateActor.BROKER
                },
                amountUsd = amount.toDouble(),
                kind = if (segment.speaker == Speaker.DISPATCHER) RateKind.COUNTER else RateKind.OFFER,
            )
        }

        // --- Weight
        weight.find(text)?.let { m ->
            val lbs = if (m.groupValues[1].isNotEmpty()) m.groupValues[1].replace(",", "").toInt()
            else m.groupValues[2].toInt() * 1_000
            if (lbs in 100..60_000) put(FieldKey.WEIGHT, "%,d lbs".format(lbs), 0.9)
        }

        // --- Commodity (gazetteer, longest match first)
        commodities.sortedByDescending { it.length }
            .firstOrNull { text.contains(it, ignoreCase = true) }
            ?.let { put(FieldKey.COMMODITY, it.split(' ').joinToString(" ") { w -> w.replaceFirstChar(Char::uppercase) }, 0.75) }

        // --- Equipment + length
        equipment.firstOrNull { text.contains(it.first, ignoreCase = true) }
            ?.let { put(FieldKey.EQUIPMENT, it.second, 0.9) }
        lengthFt.find(text)?.let { m ->
            val ft = m.groupValues[1].toInt()
            if (ft in 20..53) put(FieldKey.LENGTH_FT, ft.toString(), 0.85)
        }

        // --- MC, ZIP, appointment
        mcNumber.find(text)?.let { put(FieldKey.MC_NUMBER, it.groupValues[1], 0.95) }
        zipCue.find(text)?.let { m ->
            val key = if (fields[FieldKey.PICKUP_ZIP] == null) FieldKey.PICKUP_ZIP else FieldKey.DELIVERY_ZIP
            put(key, m.groupValues[1], 0.8)
        }
        timeCue.find(text)?.let { m ->
            val key = if (fields[FieldKey.APPOINTMENT_PICKUP] == null) FieldKey.APPOINTMENT_PICKUP
            else FieldKey.APPOINTMENT_DELIVERY
            put(key, m.groupValues[1], 0.7)
        }
        if (text.contains("detention", ignoreCase = true)) put(FieldKey.DETENTION, text, 0.7)
        if (text.contains("layover", ignoreCase = true)) put(FieldKey.LAYOVER, text, 0.7)

        return ExtractionResult(fields, events)
    }

    /** Run a whole transcript (recovery / tier-2 reconciliation). */
    fun extractAll(segments: List<TranscriptSegment>): ExtractionResult {
        var fields = emptyMap<FieldKey, FieldValue>()
        val events = mutableListOf<RateEvent>()
        for (s in segments) {
            val r = extractSegment(s, fields)
            fields = r.fields
            events += r.rateEvents
        }
        return ExtractionResult(fields, events)
    }

    private fun extractPlaceAfter(cue: Regex, text: String): Pair<String, String?>? {
        val m = cue.find(text) ?: return null
        val pm = place.find(text.substring(m.range.last + 1)) ?: return null
        val city = pm.groupValues[1]
        if (!looksLikePlace(city)) return null
        val state = pm.groupValues[2].takeIf { it in states }
        val zip = pm.groupValues[3].ifEmpty { null }
        return normalizePlace(city + (state?.let { ", $it" } ?: "")) to zip
    }

    private val nonPlaceWords = setOf(
        "I", "He", "She", "We", "They", "The", "That", "This", "It", "Monday", "Tuesday",
        "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Tomorrow", "Today",
        "Yeah", "Yes", "No", "Okay", "Ok", "Like", "Said", "Got", "Anything", "From",
        "Well", "So", "Listen", "Look",
    )

    private fun looksLikePlace(s: String): Boolean {
        val city = s.substringBefore(",").trim()
        return city.isNotEmpty() && city.split(" ").none { it in nonPlaceWords }
    }

    private fun normalizePlace(s: String): String {
        val m = place.find(s) ?: return s
        val city = m.groupValues[1]
        val st = m.groupValues[2].takeIf { it in states }
        return if (st != null) "$city, $st" else city
    }
}
