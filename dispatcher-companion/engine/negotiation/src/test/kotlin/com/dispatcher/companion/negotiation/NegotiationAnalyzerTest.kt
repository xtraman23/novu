package com.dispatcher.companion.negotiation

import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.RateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun offer(amount: Double, actor: RateActor = RateActor.BROKER, t: Long = 0) =
    RateEvent(t, actor, amount, RateKind.OFFER)

class NegotiationAnalyzerTest {

    private val a = NegotiationAnalyzer()

    @Test
    fun `brief example - broker at 1700 with urgency yields ~1900 floor ~2100 counter`() {
        val cues = a.detectCues("I only have \$1,700 and I need it covered today, driver waiting")
        val advice = a.advise(listOf(offer(1700.0)), cues)!!
        assertTrue(advice.likelyFloorUsd in 1850.0..1975.0, "floor=${advice.likelyFloorUsd}")
        assertTrue(advice.counterUsd in 2000.0..2200.0, "counter=${advice.counterUsd}")
        assertTrue(advice.acceptanceProbability in 0.60..0.85, "p=${advice.acceptanceProbability}")
        assertTrue(advice.likelyCeilingUsd > advice.counterUsd)
        assertTrue(advice.counterUsd > advice.likelyFloorUsd)
    }

    @Test
    fun `no broker number yet - no advice`() {
        assertNull(a.advise(emptyList(), NegotiationCues(0.0, 0.0, 0.0)))
        assertNull(
            a.advise(
                listOf(offer(2100.0, RateActor.DISPATCHER)),
                NegotiationCues(0.0, 0.0, 0.0),
            )
        )
    }

    @Test
    fun `urgency raises the counter`() {
        val calm = a.advise(listOf(offer(1700.0)), NegotiationCues(0.0, 0.0, 0.0))!!
        val urgent = a.advise(listOf(offer(1700.0)), NegotiationCues(1.0, 0.0, 0.0))!!
        assertTrue(urgent.counterUsd > calm.counterUsd)
        assertTrue(urgent.likelyFloorUsd >= calm.likelyFloorUsd)
    }

    @Test
    fun `firmness lowers the counter but never below the offer`() {
        val firm = a.advise(listOf(offer(1700.0)), NegotiationCues(0.0, 0.0, 1.0))!!
        val calm = a.advise(listOf(offer(1700.0)), NegotiationCues(0.0, 0.0, 0.0))!!
        assertTrue(firm.counterUsd < calm.counterUsd)
        assertTrue(firm.counterUsd > 1700.0)
    }

    @Test
    fun `uses the latest broker offer in the path`() {
        val path = listOf(
            offer(1700.0, t = 0),
            RateEvent(5_000, RateActor.DISPATCHER, 2100.0, RateKind.COUNTER),
            offer(1900.0, t = 9_000),
        )
        val advice = a.advise(path, NegotiationCues(0.0, 0.0, 0.0))!!
        assertTrue(advice.likelyFloorUsd >= 1900.0 * 1.02)
    }

    @Test
    fun `cost basis anchors the walk-away`() {
        val basis = CostBasis(totalMiles = 815.0, minRatePerMile = 2.45) // floor $1,996.75
        val advice = a.advise(listOf(offer(1700.0)), NegotiationCues(0.0, 0.0, 0.0), basis)!!
        assertEquals(1996.75, advice.walkAwayUsd, 0.01)
    }

    @Test
    fun `cue detection scores the right dimensions`() {
        val c1 = a.detectCues("It needs to move today, driver waiting on it")
        assertTrue(c1.urgency > 0.5)
        val c2 = a.detectCues("Best I can do is seventeen, that's all I got")
        assertTrue(c2.firmness > 0.5)
        val c3 = a.detectCues("Let me see what I can do, I might be able to work with you")
        assertTrue(c3.flexibility > 0.5)
        val c4 = a.detectCues("Where's the truck right now?")
        assertTrue(c4.firmness < 0.5 && c4.flexibility < 0.5)
    }

    @Test
    fun `counters land on 50-dollar increments`() {
        val advice = a.advise(listOf(offer(1837.0)), NegotiationCues(0.3, 0.2, 0.1))!!
        assertEquals(0.0, advice.counterUsd % 50.0)
        assertEquals(0.0, advice.likelyFloorUsd % 50.0)
    }
}
