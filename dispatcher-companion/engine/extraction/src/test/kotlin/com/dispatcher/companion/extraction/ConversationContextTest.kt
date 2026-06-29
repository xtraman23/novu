package com.dispatcher.companion.extraction

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.FieldSource
import com.dispatcher.companion.model.FieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationContextTest {

    private val empty = emptyMap<FieldKey, FieldValue>()

    @Test
    fun `pickup question then bare city answer`() {
        val c = ConversationContext()
        c.noteQuestion("Where's it pick up?")
        assertEquals(FieldKey.PICKUP to "Dallas, TX", c.resolveAnswer("Dallas, TX", empty))
    }

    @Test
    fun `delivery question then bare city answer`() {
        val c = ConversationContext()
        c.noteQuestion("And where's it going?")
        assertEquals(FieldKey.DELIVERY to "Atlanta", c.resolveAnswer("Atlanta", empty))
    }

    @Test
    fun `pickup question answered with a ZIP fills the zip field`() {
        val c = ConversationContext()
        c.noteQuestion("what's the pickup")
        assertEquals(FieldKey.PICKUP_ZIP to "75201", c.resolveAnswer("75201", empty))
    }

    @Test
    fun `weight question then numeric answer`() {
        val c = ConversationContext()
        c.noteQuestion("how much does it weigh")
        assertEquals(FieldKey.WEIGHT to "42,000 lbs", c.resolveAnswer("about 42,000 pounds", empty))
    }

    @Test
    fun `commodity question then short answer`() {
        val c = ConversationContext()
        c.noteQuestion("what are you hauling")
        assertEquals(FieldKey.COMMODITY to "Auto Parts", c.resolveAnswer("auto parts", empty))
    }

    @Test
    fun `rate question then spoken answer`() {
        val c = ConversationContext()
        c.noteQuestion("what's it pay")
        assertEquals(FieldKey.RATE to "$1,900", c.resolveAnswer("nineteen hundred", empty))
    }

    @Test
    fun `no answer is produced without a pending question`() {
        val c = ConversationContext()
        assertNull(c.resolveAnswer("Dallas, TX", empty))
    }

    @Test
    fun `does not overwrite an already-filled field`() {
        val c = ConversationContext()
        c.noteQuestion("where's it pick up")
        val filled = mapOf(FieldKey.PICKUP to FieldValue("Houston, TX", 0.9, FieldSource.MANUAL))
        assertNull(c.resolveAnswer("Dallas, TX", filled))
    }

    @Test
    fun `filler answers are ignored, not treated as a city`() {
        val c = ConversationContext()
        c.noteQuestion("where's it deliver")
        assertNull(c.resolveAnswer("Let me check", empty))
    }

    @Test
    fun `a new question overrides a stale expectation`() {
        val c = ConversationContext()
        c.noteQuestion("where's it pick up")
        c.noteQuestion("actually what's it pay")
        assertEquals(ConversationContext.Expecting.RATE, c.expecting)
    }
}
