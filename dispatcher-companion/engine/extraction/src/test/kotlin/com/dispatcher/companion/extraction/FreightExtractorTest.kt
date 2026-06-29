package com.dispatcher.companion.extraction

import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateKind
import com.dispatcher.companion.model.Speaker
import com.dispatcher.companion.model.TranscriptSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun seg(seq: Int, speaker: Speaker, text: String) =
    TranscriptSegment(seq, speaker, text, seq * 1_000L, seq * 1_000L + 900, 0.9)

class SpokenNumbersTest {

    @Test
    fun `parses freight-call dollar phrases`() {
        assertEquals(listOf(1900), SpokenNumbers.findAmounts("I only have nineteen hundred in it"))
        assertEquals(listOf(2100), SpokenNumbers.findAmounts("Can you do twenty one hundred?"))
        assertEquals(listOf(2000), SpokenNumbers.findAmounts("two grand and not a penny more"))
        assertEquals(listOf(2200), SpokenNumbers.findAmounts("two thousand two hundred all in"))
        assertEquals(listOf(1700), SpokenNumbers.findAmounts("seventeen hundred bucks"))
    }

    @Test
    fun `ignores non-amount words`() {
        assertTrue(SpokenNumbers.findAmounts("we deliver tomorrow morning").isEmpty())
    }
}

class FreightExtractorTest {

    private val x = FreightExtractor()

    @Test
    fun `extracts full PDWCR from a realistic broker exchange`() {
        val convo = listOf(
            seg(1, Speaker.BROKER, "I got a load picking up in Dallas, TX 75201 going to Atlanta, GA"),
            seg(2, Speaker.BROKER, "It's 42,000 lbs of dry goods on a 53' dry van"),
            seg(3, Speaker.BROKER, "I only have $1,900 in it"),
            seg(4, Speaker.DISPATCHER, "I can do it for $2,100 if it picks up at 8 am"),
        )
        val r = x.extractAll(convo)
        assertEquals("Dallas, TX", r.fields[FieldKey.PICKUP]?.text)
        assertEquals("75201", r.fields[FieldKey.PICKUP_ZIP]?.text)
        assertEquals("Atlanta, GA", r.fields[FieldKey.DELIVERY]?.text)
        assertEquals("42,000 lbs", r.fields[FieldKey.WEIGHT]?.text)
        assertEquals("Dry Goods", r.fields[FieldKey.COMMODITY]?.text)
        assertEquals("$2,100", r.fields[FieldKey.RATE]?.text) // latest rate wins
        assertEquals("DRY_VAN", r.fields[FieldKey.EQUIPMENT]?.text)
        assertEquals("53", r.fields[FieldKey.LENGTH_FT]?.text)
        assertEquals("8 am", r.fields[FieldKey.APPOINTMENT_PICKUP]?.text)
    }

    @Test
    fun `rate events carry the negotiation path with actors`() {
        val convo = listOf(
            seg(1, Speaker.BROKER, "Best I can do is $1,700"),
            seg(2, Speaker.DISPATCHER, "I need $2,100 to move it"),
            seg(3, Speaker.BROKER, "Meet me at nineteen hundred and book it"),
        )
        val r = x.extractAll(convo)
        assertEquals(listOf(1700.0, 2100.0, 1900.0), r.rateEvents.map { it.amountUsd })
        assertEquals(
            listOf(RateActor.BROKER, RateActor.DISPATCHER, RateActor.BROKER),
            r.rateEvents.map { it.actor },
        )
        assertEquals(RateKind.COUNTER, r.rateEvents[1].kind)
    }

    @Test
    fun `spoken rate fills the rate field`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "I only have nineteen hundred in it"))
        assertEquals("$1,900", r.fields[FieldKey.RATE]?.text)
    }

    @Test
    fun `phone numbers and MC numbers are not rates`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "Call me back at 555-0142, MC number is 322734"))
        assertNull(r.fields[FieldKey.RATE])
        assertEquals("322734", r.fields[FieldKey.MC_NUMBER]?.text)
    }

    @Test
    fun `bare numbers without context are not rates`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "It's about 760 miles"))
        assertNull(r.fields[FieldKey.RATE])
    }

    @Test
    fun `lane phrase extracts both ends`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "Got anything for Memphis to Nashville?"))
        assertEquals("Memphis", r.fields[FieldKey.PICKUP]?.text)
        assertEquals("Nashville", r.fields[FieldKey.DELIVERY]?.text)
    }

    @Test
    fun `weight in k-shorthand`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "It's 42k lbs, reefer at 34 degrees"))
        assertEquals("42,000 lbs", r.fields[FieldKey.WEIGHT]?.text)
        assertEquals("REEFER", r.fields[FieldKey.EQUIPMENT]?.text)
    }

    @Test
    fun `weekday after directional cue is not a city`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "It is going to Monday appointment"))
        assertNull(r.fields[FieldKey.DELIVERY])
    }

    @Test
    fun `detention note is captured`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "We pay detention after two hours"))
        assertTrue(r.fields[FieldKey.DETENTION] != null)
    }

    @Test
    fun `incremental extraction never regresses on later vague mention`() {
        var fields = x.extractSegment(
            seg(1, Speaker.BROKER, "Picking up in Dallas, TX going to Atlanta, GA")
        ).fields
        fields = x.extractSegment(seg(2, Speaker.BROKER, "Yeah Dallas to Atlanta like I said"), fields).fields
        assertEquals("Dallas, TX", fields[FieldKey.PICKUP]?.text) // 0.85 beats 0.75 re-mention
    }

    @Test
    fun `captures FCFS and assigns it to pickup`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "Pickup is FCFS, no appointment needed"))
        assertEquals("FCFS", r.fields[FieldKey.APPOINTMENT_PICKUP]?.text)
    }

    @Test
    fun `appointment at delivery goes to the delivery slot`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "Delivery is by appointment only"))
        assertEquals("By appointment", r.fields[FieldKey.APPOINTMENT_DELIVERY]?.text)
    }

    @Test
    fun `accumulates multiple special requirements across segments`() {
        var f = x.extractSegment(seg(1, Speaker.BROKER, "It's a drop and hook, no-touch freight")).fields
        f = x.extractSegment(seg(2, Speaker.BROKER, "Oh and it's hazmat, you'll need tarps"), f).fields
        val special = f[FieldKey.SPECIAL_REQUIREMENTS]?.text ?: ""
        assertTrue("Drop & hook" in special, special)
        assertTrue("No-touch freight" in special, special)
        assertTrue("Hazmat" in special, special)
        assertTrue("Tarps" in special, special)
    }

    @Test
    fun `recognizes broker equipment synonyms`() {
        assertEquals("REEFER", x.extractSegment(seg(1, Speaker.BROKER, "Need a temp controlled trailer")).fields[FieldKey.EQUIPMENT]?.text)
        assertEquals("POWER_ONLY", x.extractSegment(seg(1, Speaker.BROKER, "This is power only")).fields[FieldKey.EQUIPMENT]?.text)
        assertEquals("RGN", x.extractSegment(seg(1, Speaker.BROKER, "Goes on an RGN")).fields[FieldKey.EQUIPMENT]?.text)
    }

    @Test
    fun `word-boundary matching avoids false equipment hits`() {
        // "Sullivan" must not trigger DRY_VAN via "van"
        assertNull(x.extractSegment(seg(1, Speaker.BROKER, "Broker is Mike from Sullivan County")).fields[FieldKey.EQUIPMENT])
    }

    @Test
    fun `extracts pickup and delivery ZIPs from broker shorthand`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "Picking up 75201 and delivering to 30303"))
        assertEquals("75201", r.fields[FieldKey.PICKUP_ZIP]?.text)
        assertEquals("30303", r.fields[FieldKey.DELIVERY_ZIP]?.text)
    }

    @Test
    fun `lumper and team are captured as requirements`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "There's a lumper fee and it needs a team"))
        val special = r.fields[FieldKey.SPECIAL_REQUIREMENTS]?.text ?: ""
        assertTrue("Lumper fee" in special)
        assertTrue("Team" in special)
    }

    @Test
    fun `detention captures a short phrase not the whole sentence`() {
        val r = x.extractSegment(seg(1, Speaker.BROKER, "We pay detention after two hours at the dock, fifty an hour"))
        val d = r.fields[FieldKey.DETENTION]?.text ?: ""
        assertTrue(d.startsWith("detention", ignoreCase = true))
        assertTrue(d.length < 50)
    }

    @Test
    fun `per-segment extraction is fast enough for live use`() {
        val s = seg(1, Speaker.BROKER,
            "I got 42,000 lbs of produce picking up in Fresno, CA going to Denver, CO for $2,400 on a reefer")
        val started = System.nanoTime()
        repeat(1_000) { x.extractSegment(s) }
        val perCallMs = (System.nanoTime() - started) / 1_000_000.0 / 1_000
        assertTrue(perCallMs < 10, "tier-1 extraction took ${perCallMs}ms per segment (budget 10ms)")
    }
}
