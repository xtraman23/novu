package com.dispatcher.companion.desktop

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.Speaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DispatchCliParseTest {

    @Test
    fun `parses speaker prefixes including short forms`() {
        assertEquals(Speaker.BROKER to "hello", parseLine("BROKER: hello"))
        assertEquals(Speaker.BROKER to "hi", parseLine("b - hi"))
        assertEquals(Speaker.DISPATCHER to "rate?", parseLine("Dispatcher: rate?"))
        assertEquals(Speaker.DISPATCHER to "ok", parseLine("D: ok"))
    }

    @Test
    fun `unlabelled line defaults to broker`() {
        assertEquals(Speaker.BROKER to "Dallas to Atlanta", parseLine("Dallas to Atlanta"))
    }
}

class DesktopDispatchSessionTest {

    /**
     * The exact scenario the user described: a stilted Q&A call (not flowing
     * conversation). The app must still capture PDWCR on its own.
     */
    @Test
    fun `Q and A style call still fills PDWCR`() {
        val s = DesktopDispatchSession()
        s.onUtterance(Speaker.DISPATCHER, "Where's it pick up")
        s.onUtterance(Speaker.BROKER, "Dallas, TX")
        s.onUtterance(Speaker.DISPATCHER, "Going where")
        s.onUtterance(Speaker.BROKER, "Atlanta, GA")
        s.onUtterance(Speaker.DISPATCHER, "Weight and commodity")
        s.onUtterance(Speaker.BROKER, "It's 42,000 lbs of dry goods")
        s.onUtterance(Speaker.DISPATCHER, "What's it pay")
        s.onUtterance(Speaker.BROKER, "I have nineteen hundred on it")

        assertEquals("Dallas, TX", s.fields[FieldKey.PICKUP]?.text)
        assertEquals("Atlanta, GA", s.fields[FieldKey.DELIVERY]?.text)
        assertEquals("42,000 lbs", s.fields[FieldKey.WEIGHT]?.text)
        assertEquals("Dry Goods", s.fields[FieldKey.COMMODITY]?.text)
        assertEquals("$1,900", s.fields[FieldKey.RATE]?.text)
    }

    @Test
    fun `broker terminology is captured from a real-sounding call`() {
        val s = DesktopDispatchSession()
        s.onUtterance(Speaker.BROKER, "53 foot reefer, pickup is FCFS, it's a drop and hook")
        s.onUtterance(Speaker.BROKER, "There's a lumper fee and it's hazmat, MC is 322734")

        assertEquals("REEFER", s.fields[FieldKey.EQUIPMENT]?.text)
        assertEquals("FCFS", s.fields[FieldKey.APPOINTMENT_PICKUP]?.text)
        assertEquals("322734", s.fields[FieldKey.MC_NUMBER]?.text)
        val special = s.fields[FieldKey.SPECIAL_REQUIREMENTS]?.text ?: ""
        assertTrue("Drop & hook" in special, special)
        assertTrue("Lumper fee" in special, special)
        assertTrue("Hazmat" in special, special)
    }

    @Test
    fun `negotiation advice appears once the broker names a rate`() {
        val s = DesktopDispatchSession()
        assertEquals(null, s.advice)
        s.onUtterance(Speaker.BROKER, "Best I can do is seventeen hundred, need it today")
        val advice = s.advice
        assertNotNull(advice)
        assertTrue(advice.counterUsd > 1700.0)
        assertTrue(advice.acceptanceProbability in 0.0..1.0)
    }

    @Test
    fun `transcript preserves speaker labels and order`() {
        val s = DesktopDispatchSession()
        s.onUtterance(Speaker.BROKER, "one")
        s.onUtterance(Speaker.DISPATCHER, "two")
        assertEquals(2, s.transcript.size)
        assertEquals(Speaker.BROKER, s.transcript[0].speaker)
        assertEquals(Speaker.DISPATCHER, s.transcript[1].speaker)
        assertTrue(s.transcript[1].tStartMs > s.transcript[0].tStartMs)
    }
}
