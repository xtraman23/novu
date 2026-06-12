package com.dispatcher.companion.calculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test gazetteer with real coordinates for known lanes. */
private object TestGeo : GeoIndex {
    private val zips = mapOf(
        "75201" to GeoPoint(32.7876, -96.7994),  // Dallas, TX
        "30303" to GeoPoint(33.7525, -84.3922),  // Atlanta, GA
        "38103" to GeoPoint(35.1465, -90.0517),  // Memphis, TN
    )
    private val cities = mapOf(
        "dallas/TX" to GeoPoint(32.7767, -96.7970),
        "atlanta/GA" to GeoPoint(33.7490, -84.3880),
        "fort worth/TX" to GeoPoint(32.7555, -97.3308),
        "memphis/null" to GeoPoint(35.1495, -90.0490),
    )

    override fun byZip(zip: String) = zips[zip]
    override fun byCity(city: String, state: String?) =
        cities["${city.lowercase()}/${state ?: "null"}"]
}

class FreightCalculatorTest {

    private val calc = FreightCalculator(TestGeo)

    @Test
    fun `dallas to atlanta road miles are realistic (~780)`() {
        val r = calc.lane("Dallas, TX", "Atlanta, GA", rateUsd = 1900.0)!!
        assertTrue(r.loadedMiles in 740.0..860.0, "miles=${r.loadedMiles}")
    }

    @Test
    fun `all four lookup combinations work`() {
        assertNotNull(calc.lane("Dallas, TX", "Atlanta, GA", 1900.0)) // city-city
        assertNotNull(calc.lane("75201", "30303", 1900.0))            // zip-zip
        assertNotNull(calc.lane("75201", "Atlanta, GA", 1900.0))      // zip-city
        assertNotNull(calc.lane("Dallas, TX", "30303", 1900.0))       // city-zip
    }

    @Test
    fun `bare city without state resolves`() {
        assertNotNull(calc.resolve("Memphis"))
    }

    @Test
    fun `unknown place returns null instead of guessing`() {
        assertNull(calc.lane("Nowhereville, ZZ", "Atlanta, GA", 1900.0))
    }

    @Test
    fun `economics are internally consistent`() {
        val r = calc.compute(loadedMiles = 781.0, deadheadMiles = 34.0, rateUsd = 2100.0)
        assertEquals(815.0, r.totalMiles)
        assertEquals(2.69, r.ratePerMile, 0.01)         // 2100/781
        assertEquals(2.58, r.allInRatePerMile, 0.01)    // 2100/815
        assertEquals(514.08, r.fuelCostUsd, 0.5)        // 815/6.5*4.10
        assertEquals(r.revenueUsd - r.fuelCostUsd, r.profitUsd, 0.01)
        assertTrue(r.profitPerMile > 0)
    }

    @Test
    fun `deadhead drags the profitability score down`() {
        val clean = calc.compute(700.0, 0.0, 2000.0)
        val dirty = calc.compute(700.0, 200.0, 2000.0)
        assertTrue(dirty.profitabilityScore < clean.profitabilityScore)
    }

    @Test
    fun `score is bounded 0-100`() {
        assertEquals(0, calc.compute(1000.0, 500.0, 900.0).profitabilityScore)
        assertEquals(100, calc.compute(300.0, 0.0, 2500.0).profitabilityScore)
    }

    @Test
    fun `zero-mile input does not divide by zero`() {
        val r = calc.compute(0.0, 0.0, 500.0)
        assertEquals(0.0, r.ratePerMile)
        assertEquals(0.0, r.profitPerMile)
    }
}
