package com.dispatcher.companion.extraction

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldValue

/**
 * Handles stilted Q&A calls where the value is the ANSWER to a question asked
 * in a PREVIOUS utterance — e.g. "Where's it pick up?" / "Dallas, TX". The
 * per-segment [FreightExtractor] alone misses these because the cue ("pick up")
 * and the value ("Dallas") are in different lines.
 *
 * Usage per utterance, in order:
 *   1. resolveAnswer(text, fields) — fills the field a prior question asked for
 *   2. run FreightExtractor as usual
 *   3. noteQuestion(text) — arms the expectation for the NEXT utterance
 *
 * Speaker-agnostic on purpose: it works even when diarization is unsure.
 * Pure and fully unit-tested.
 */
class ConversationContext {

    enum class Expecting { NONE, PICKUP, DELIVERY, WEIGHT, COMMODITY, RATE }

    var expecting: Expecting = Expecting.NONE
        private set

    // Stems (haul -> haul/hauling) so suffixes don't break matching. Checked in
    // order; weight before rate so "how much does it weigh" is weight not rate.
    private val qPickup = Regex("""(?i)(pick\s?up|picking up|where.*\b(from|loading|origin|ship\w*))""")
    private val qDelivery = Regex("""(?i)(deliver\w*|drop\s?off|going (to|where)|where.*\b(to|deliver\w*|going|destination|consignee))""")
    private val qWeight = Regex("""(?i)(weigh\w*|how heavy|how many (pounds|lbs))""")
    private val qCommodity = Regex("""(?i)(what.*\b(haul\w*|freight|commodit\w*|product\w*|carry\w*|pull\w*)|what's the (load|freight))""")
    private val qRate = Regex("""(?i)(what.*pay\w*|what's it pay|the rate|how much|paying|your rate|rate on)""")

    private val place = Regex("""^([A-Z][a-z]+(?: [A-Z][a-z]+)?)(?:,?\s+([A-Z]{2}))?""")
    private val zip = Regex("""\b(\d{5})\b""")
    private val weight = Regex("""\b(\d{1,3}(?:,\d{3})|\d{4,6})\s*(?:lbs?|pounds)?\b|\b(\d{1,3})k\b""", RegexOption.IGNORE_CASE)
    private val states = setOf(
        "AL","AK","AZ","AR","CA","CO","CT","DE","FL","GA","HI","ID","IL","IN","IA","KS","KY","LA","ME","MD",
        "MA","MI","MN","MS","MO","MT","NE","NV","NH","NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC",
        "SD","TN","TX","UT","VT","VA","WA","WV","WI","WY",
    )
    private val fillers = setOf(
        "Yeah","Yes","No","Okay","Ok","Well","So","Uh","Um","Let","It","The","I","We","They","Hold","Give","Sure",
    )

    /** Arm the expectation if [text] asks about a specific field. */
    fun noteQuestion(text: String) {
        val next = when {
            qPickup.containsMatchIn(text) -> Expecting.PICKUP
            qDelivery.containsMatchIn(text) -> Expecting.DELIVERY
            qWeight.containsMatchIn(text) -> Expecting.WEIGHT
            qCommodity.containsMatchIn(text) -> Expecting.COMMODITY
            qRate.containsMatchIn(text) -> Expecting.RATE
            else -> null
        }
        if (next != null) expecting = next
    }

    /**
     * If a question is pending and [text] parses as that kind of value (and the
     * field isn't already set), return the (field, value) to fill. Consumes the
     * expectation on success.
     */
    fun resolveAnswer(text: String, fields: Map<FieldKey, FieldValue>): Pair<FieldKey, String>? {
        if (expecting == Expecting.NONE) return null
        val t = text.trim()
        val result: Pair<FieldKey, String>? = when (expecting) {
            Expecting.PICKUP -> placeOrZip(t, FieldKey.PICKUP, FieldKey.PICKUP_ZIP, fields)
            Expecting.DELIVERY -> placeOrZip(t, FieldKey.DELIVERY, FieldKey.DELIVERY_ZIP, fields)
            Expecting.WEIGHT -> if (fields[FieldKey.WEIGHT] != null) null else parseWeight(t)?.let { FieldKey.WEIGHT to it }
            Expecting.COMMODITY -> if (fields[FieldKey.COMMODITY] != null) null else parseCommodity(t)?.let { FieldKey.COMMODITY to it }
            Expecting.RATE -> if (fields[FieldKey.RATE] != null) null else parseRate(t)?.let { FieldKey.RATE to it }
            Expecting.NONE -> null
        }
        if (result != null) expecting = Expecting.NONE
        return result
    }

    private fun placeOrZip(
        text: String,
        cityKey: FieldKey,
        zipKey: FieldKey,
        fields: Map<FieldKey, FieldValue>,
    ): Pair<FieldKey, String>? {
        zip.find(text)?.let { z ->
            if (fields[zipKey] == null) return zipKey to z.groupValues[1]
        }
        if (fields[cityKey] != null) return null
        val m = place.find(text) ?: return null
        val city = m.groupValues[1]
        if (city.split(" ").any { it in fillers }) return null
        val st = m.groupValues[2].takeIf { it in states }
        return cityKey to (if (st != null) "$city, $st" else city)
    }

    private fun parseWeight(text: String): String? {
        val m = weight.find(text) ?: return null
        val lbs = when {
            m.groupValues[1].isNotEmpty() -> m.groupValues[1].replace(",", "").toInt()
            m.groupValues[2].isNotEmpty() -> m.groupValues[2].toInt() * 1_000
            else -> return null
        }
        return if (lbs in 100..60_000) "%,d lbs".format(lbs) else null
    }

    private fun parseCommodity(text: String): String? {
        BrokerLexicon.commodities.firstOrNull { it.first.containsMatchIn(text) }?.let { return it.second }
        // A spoken/numeric amount is a rate, never a commodity.
        if (SpokenNumbers.findAmounts(text).isNotEmpty()) return null
        val words = text.trim().trimEnd('.', '!', '?').split(" ").filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 4) return null
        if (words.any { it.firstOrNull()?.isDigit() == true }) return null
        if (words.first() in fillers) return null
        return words.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun parseRate(text: String): String? {
        val spoken = SpokenNumbers.findAmounts(text).firstOrNull { it in 200..20_000 }
        if (spoken != null) return "$" + "%,d".format(spoken)
        val m = Regex("""\$?\s?(\d{1,2},\d{3}|\d{3,5})""").find(text) ?: return null
        val amount = m.groupValues[1].replace(",", "").toIntOrNull() ?: return null
        return if (amount in 200..20_000) "$" + "%,d".format(amount) else null
    }
}
