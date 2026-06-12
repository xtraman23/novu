package com.dispatcher.companion.broker

import com.dispatcher.companion.model.RateActor
import com.dispatcher.companion.model.RateEvent
import com.dispatcher.companion.model.RateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun path(first: Double, agreed: Double) = listOf(
    RateEvent(0, RateActor.BROKER, first, RateKind.OFFER),
    RateEvent(9_000, RateActor.DISPATCHER, agreed, RateKind.AGREED),
)

private fun call(
    lane: String = "Dallas, TX → Atlanta, GA",
    outcome: CallOutcome = CallOutcome.BOOKED,
    agreed: Double? = 2000.0,
    miles: Double? = 800.0,
    events: List<RateEvent> = path(1700.0, agreed ?: 2000.0),
) = BrokerCallRecord(lane, outcome, agreed, miles, events)

class BrokerIntelligenceTest {

    @Test
    fun `rollup aggregates rate and success metrics`() {
        val r = BrokerIntelligence.rollup(
            listOf(
                call(agreed = 2000.0, miles = 800.0),                       // 2.50 rpm
                call(agreed = 2400.0, miles = 1000.0),                      // 2.40 rpm
                call(outcome = CallOutcome.REJECTED, agreed = null, events = emptyList()),
            )
        )
        assertEquals(3, r.totalCalls)
        assertEquals(2, r.bookedCalls)
        assertEquals(0.67, r.successRate, 0.01)
        assertEquals(2.45, r.avgRatePerMile!!, 0.01)
        assertEquals(mapOf("Dallas, TX → Atlanta, GA" to 3), r.laneHistory)
    }

    @Test
    fun `flexible broker - concedes more than 10 percent`() {
        val r = BrokerIntelligence.rollup(
            listOf(
                call(events = path(1700.0, 2000.0)), // +17.6%
                call(events = path(1800.0, 2050.0)), // +13.9%
            )
        )
        assertEquals(NegotiationStyle.FLEXIBLE, r.negotiationStyle)
        assertTrue(r.avgConcessionPct!! > 0.10)
    }

    @Test
    fun `aggressive broker - barely moves`() {
        val r = BrokerIntelligence.rollup(
            listOf(
                call(events = path(2000.0, 2025.0)),
                call(events = path(1900.0, 1900.0)),
            )
        )
        assertEquals(NegotiationStyle.AGGRESSIVE, r.negotiationStyle)
    }

    @Test
    fun `single call is not enough to classify style`() {
        val r = BrokerIntelligence.rollup(listOf(call()))
        assertEquals(NegotiationStyle.UNKNOWN, r.negotiationStyle)
    }

    @Test
    fun `concession needs both a broker offer and an agreement`() {
        assertNull(BrokerIntelligence.concessionPct(call(events = emptyList(), agreed = null)))
        assertEquals(0.176, BrokerIntelligence.concessionPct(call(events = path(1700.0, 2000.0)))!!, 0.001)
    }

    @Test
    fun `reliability rewards booked history and ramps with sample size`() {
        val many = BrokerIntelligence.rollup(List(6) { call() })
        val few = BrokerIntelligence.rollup(listOf(call()))
        assertTrue(many.reliabilityScore > few.reliabilityScore)
        val bad = BrokerIntelligence.rollup(
            List(6) { call(outcome = CallOutcome.REJECTED, agreed = null, events = emptyList()) }
        )
        assertTrue(bad.reliabilityScore < 0.3)
    }

    @Test
    fun `empty history is neutral`() {
        val r = BrokerIntelligence.rollup(emptyList())
        assertEquals(0.0, r.successRate)
        assertNull(r.avgRatePerMile)
        assertEquals(NegotiationStyle.UNKNOWN, r.negotiationStyle)
    }
}
