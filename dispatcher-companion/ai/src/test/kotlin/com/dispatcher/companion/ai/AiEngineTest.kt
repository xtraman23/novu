package com.dispatcher.companion.ai

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun seg(seq: Int, sp: Speaker, text: String) =
    TranscriptSegment(seq, sp, text, seq * 1000L, seq * 1000L + 900, 0.9)

private class FakeProvider(
    override val name: String,
    override val worksOffline: Boolean,
    private val reply: String = "{}",
) : LlmProvider {
    var calls = 0
    override suspend fun complete(system: String, user: String): String {
        calls++
        return reply
    }
}

class ProviderRegistryTest {

    @Test
    fun `prefers first provider when online`() = runTest {
        val claude = FakeProvider("claude", worksOffline = false, reply = "A")
        val local = FakeProvider("local", worksOffline = true, reply = "B")
        val reg = ProviderRegistry(listOf(claude, local))
        assertEquals("A", reg.complete(online = true, system = "s", user = "u"))
        assertEquals(1, claude.calls)
    }

    @Test
    fun `falls back to offline-capable provider when offline`() = runTest {
        val claude = FakeProvider("claude", worksOffline = false)
        val local = FakeProvider("local", worksOffline = true, reply = "B")
        val reg = ProviderRegistry(listOf(claude, local))
        assertEquals("B", reg.complete(online = false, system = "s", user = "u"))
        assertEquals(0, claude.calls)
    }

    @Test
    fun `throws LlmUnavailable when nothing can serve - caller queues the job`() = runTest {
        val reg = ProviderRegistry(listOf(FakeProvider("claude", worksOffline = false)))
        assertFailsWith<LlmUnavailableException> {
            reg.complete(online = false, system = "s", user = "u")
        }
    }
}

class PromptsTest {

    private val convo = listOf(
        seg(1, Speaker.BROKER, "Picking up in Dallas, TX going to Atlanta for $1,900"),
        seg(2, Speaker.DISPATCHER, "I need $2,100"),
    )

    @Test
    fun `extraction prompt carries transcript with speaker labels`() {
        val user = Prompts.extractionUser(convo)
        assertTrue("Broker: Picking up in Dallas" in user)
        assertTrue("Dispatcher: I need" in user)
    }

    @Test
    fun `extraction system lists every field key`() {
        for (key in FieldKey.entries) {
            assertTrue(key.name in Prompts.EXTRACTION_SYSTEM, "missing ${key.name}")
        }
    }

    @Test
    fun `negotiation prompt carries the deterministic anchors`() {
        val user = Prompts.negotiationUser(convo, 2100.0, 1900.0, 2300.0)
        assertTrue("2100" in user && "1900" in user && "2300" in user)
    }
}

class ParsersTest {

    @Test
    fun `parses extraction payload`() {
        val raw = """{"fields": {"PICKUP": {"value": "Dallas, TX", "confidence": 0.97},
            "RATE": {"value": "${'$'}1,900", "confidence": 0.95},
            "BOGUS_KEY": {"value": "x", "confidence": 0.9}}}"""
        val fields = Parsers.parseExtraction(raw)
        assertEquals("Dallas, TX", fields[FieldKey.PICKUP]?.text)
        assertEquals(FieldSource.LLM, fields[FieldKey.RATE]?.source)
        assertEquals(2, fields.size) // unknown keys dropped
    }

    @Test
    fun `strips markdown fences`() {
        val raw = "```json\n{\"fields\": {\"COMMODITY\": {\"value\": \"Dry Goods\", \"confidence\": 0.8}}}\n```"
        assertEquals("Dry Goods", Parsers.parseExtraction(raw)[FieldKey.COMMODITY]?.text)
    }

    @Test
    fun `garbage output degrades to empty - never throws`() {
        assertTrue(Parsers.parseExtraction("Sorry, I can't help with that.").isEmpty())
        assertTrue(Parsers.parseExtraction("{\"fields\": 42}").isEmpty())
        assertNull(Parsers.parseSummary("not json"))
        assertNull(Parsers.parseSuggestedReply("not json"))
    }

    @Test
    fun `confidence is clamped to 0-1`() {
        val raw = """{"fields": {"RATE": {"value": "${'$'}2,000", "confidence": 7}}}"""
        assertEquals(1.0, Parsers.parseExtraction(raw)[FieldKey.RATE]?.confidence)
    }

    @Test
    fun `parses summary payload`() {
        val raw = """{"lane": "Dallas, TX → Atlanta, GA", "summary_short": "Booked at ${'$'}2,000",
            "summary_detailed": "d", "summary_bullets": "- a", "summary_crm": "c",
            "key_details": "k", "follow_up_actions": "f", "next_steps": "n"}"""
        val s = Parsers.parseSummary(raw)!!
        assertEquals("Dallas, TX → Atlanta, GA", s.lane)
        assertEquals("Booked at $2,000", s.summaryShort)
    }

    @Test
    fun `parses suggested reply`() {
        assertEquals(
            "I can do it for $2,100 today.",
            Parsers.parseSuggestedReply("""{"suggested_reply": "I can do it for ${'$'}2,100 today."}"""),
        )
    }
}
