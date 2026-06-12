package com.dispatcher.companion.extraction

/**
 * Parses the spoken dollar amounts heard in freight calls:
 * "nineteen hundred" → 1900, "twenty one hundred" → 2100,
 * "two grand" → 2000, "two thousand two hundred" → 2200.
 */
object SpokenNumbers {

    private val units = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
    )
    private val teens = mapOf(
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    private val word = "(?:${(units.keys + teens.keys + tens.keys).joinToString("|")})"
    private val multiWord = Regex("($word(?:[ -]$word)?)\\s+(hundred|thousand|grand)\\b" +
        "(?:\\s+(?:and\\s+)?($word(?:[ -]$word)?)\\s+hundred\\b)?", RegexOption.IGNORE_CASE)

    /** "seventeen" → 17, "twenty one" → 21, "two" → 2. */
    private fun wordsToInt(phrase: String): Int? {
        val parts = phrase.lowercase().split(' ', '-').filter { it.isNotBlank() }
        return when (parts.size) {
            1 -> units[parts[0]] ?: teens[parts[0]] ?: tens[parts[0]]
            2 -> {
                val t = tens[parts[0]] ?: return null
                val u = units[parts[1]] ?: return null
                t + u
            }
            else -> null
        }
    }

    /** Finds all spoken dollar amounts in [text], in utterance order. */
    fun findAmounts(text: String): List<Int> =
        multiWord.findAll(text).mapNotNull { m ->
            val head = wordsToInt(m.groupValues[1]) ?: return@mapNotNull null
            val scale = when (m.groupValues[2].lowercase()) {
                "hundred" -> 100
                else -> 1000 // thousand | grand
            }
            var amount = head * scale
            if (m.groupValues[3].isNotEmpty()) {
                val extra = wordsToInt(m.groupValues[3]) ?: 0
                amount += extra * 100
            }
            amount
        }.toList()
}
