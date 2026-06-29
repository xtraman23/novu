package com.dispatcher.companion.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PdwcrMergerTest {

    private val empty = emptyMap<FieldKey, FieldValue>()
    private fun fv(text: String, conf: Double, src: FieldSource) = FieldValue(text, conf, src)

    @Test
    fun `fills empty field`() {
        val out = PdwcrMerger.merge(empty, FieldKey.PICKUP, fv("Dallas, TX", 0.8, FieldSource.REGEX))
        assertEquals("Dallas, TX", out[FieldKey.PICKUP]?.text)
    }

    @Test
    fun `higher confidence replaces lower`() {
        var s = PdwcrMerger.merge(empty, FieldKey.RATE, fv("$1,700", 0.6, FieldSource.REGEX))
        s = PdwcrMerger.merge(s, FieldKey.RATE, fv("$1,900", 0.9, FieldSource.LLM))
        assertEquals("$1,900", s[FieldKey.RATE]?.text)
    }

    @Test
    fun `lower confidence never regresses field`() {
        var s = PdwcrMerger.merge(empty, FieldKey.WEIGHT, fv("42,000 lbs", 0.9, FieldSource.LLM))
        s = PdwcrMerger.merge(s, FieldKey.WEIGHT, fv("4,200 lbs", 0.5, FieldSource.REGEX))
        assertEquals("42,000 lbs", s[FieldKey.WEIGHT]?.text)
    }

    @Test
    fun `equal confidence prefers newest (rate renegotiation)`() {
        var s = PdwcrMerger.merge(empty, FieldKey.RATE, fv("$1,900", 0.9, FieldSource.REGEX))
        s = PdwcrMerger.merge(s, FieldKey.RATE, fv("$2,100", 0.9, FieldSource.REGEX))
        assertEquals("$2,100", s[FieldKey.RATE]?.text)
    }

    @Test
    fun `manual edit is sticky against automated sources`() {
        var s = PdwcrMerger.merge(empty, FieldKey.COMMODITY, fv("Dry Goods", 1.0, FieldSource.MANUAL))
        s = PdwcrMerger.merge(s, FieldKey.COMMODITY, fv("Produce", 0.99, FieldSource.LLM))
        assertEquals("Dry Goods", s[FieldKey.COMMODITY]?.text)
        assertEquals(FieldSource.MANUAL, s[FieldKey.COMMODITY]?.source)
    }

    @Test
    fun `manual edit overwrites manual edit`() {
        var s = PdwcrMerger.merge(empty, FieldKey.RATE, fv("$2,000", 1.0, FieldSource.MANUAL))
        s = PdwcrMerger.merge(s, FieldKey.RATE, fv("$2,050", 1.0, FieldSource.MANUAL))
        assertEquals("$2,050", s[FieldKey.RATE]?.text)
    }

    @Test
    fun `mergeAll applies per-field rules independently`() {
        val current = mapOf(
            FieldKey.PICKUP to fv("Dallas, TX", 0.95, FieldSource.MANUAL),
            FieldKey.RATE to fv("$1,700", 0.6, FieldSource.REGEX),
        )
        val incoming = mapOf(
            FieldKey.PICKUP to fv("Fort Worth, TX", 0.9, FieldSource.LLM),
            FieldKey.RATE to fv("$1,900", 0.9, FieldSource.LLM),
            FieldKey.DELIVERY to fv("Atlanta, GA", 0.9, FieldSource.LLM),
        )
        val out = PdwcrMerger.mergeAll(current, incoming)
        assertEquals("Dallas, TX", out[FieldKey.PICKUP]?.text)
        assertEquals("$1,900", out[FieldKey.RATE]?.text)
        assertEquals("Atlanta, GA", out[FieldKey.DELIVERY]?.text)
    }
}
