package com.dispatcher.companion.calculator

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

/** Offline ZIP/city gazetteer backed by the bundled zip_geo dataset (FR-602). */
interface GeoIndex {
    fun byZip(zip: String): GeoPoint?
    fun byCity(city: String, state: String?): GeoPoint?
}

data class LaneResult(
    val loadedMiles: Double,
    val deadheadMiles: Double,
    val totalMiles: Double,
    val revenueUsd: Double,
    val ratePerMile: Double,        // revenue / loaded miles
    val allInRatePerMile: Double,   // revenue / total miles
    val fuelCostUsd: Double,
    val profitUsd: Double,
    val profitPerMile: Double,
    val profitabilityScore: Int,    // 0..100
)

/** FR-601: lane economics. Road miles estimated as great-circle * 1.17. */
class FreightCalculator(
    private val geo: GeoIndex,
    private val mpg: Double = 6.5,
    private val fuelPricePerGallon: Double = 4.10,
    private val roadFactor: Double = 1.17,
) {

    fun milesBetween(a: GeoPoint, b: GeoPoint): Double = haversineMiles(a, b) * roadFactor

    /** Accepts a 5-digit ZIP or "City, ST" / "City ST" / "City" (FR-602). */
    fun resolve(place: String): GeoPoint? {
        val p = place.trim()
        if (Regex("""^\d{5}$""").matches(p)) return geo.byZip(p)
        val m = Regex("""^(.+?)[,\s]+([A-Za-z]{2})$""").find(p)
        return if (m != null) geo.byCity(m.groupValues[1].trim(), m.groupValues[2].uppercase())
        else geo.byCity(p, null)
    }

    fun lane(
        origin: String,
        destination: String,
        rateUsd: Double,
        deadheadFrom: String? = null,
    ): LaneResult? {
        val o = resolve(origin) ?: return null
        val d = resolve(destination) ?: return null
        val loaded = milesBetween(o, d)
        val deadhead = deadheadFrom?.let { resolve(it) }?.let { milesBetween(it, o) } ?: 0.0
        return compute(loaded, deadhead, rateUsd)
    }

    fun compute(loadedMiles: Double, deadheadMiles: Double, rateUsd: Double): LaneResult {
        val total = loadedMiles + deadheadMiles
        val fuel = total / mpg * fuelPricePerGallon
        val profit = rateUsd - fuel
        val rpm = if (loadedMiles > 0) rateUsd / loadedMiles else 0.0
        val allIn = if (total > 0) rateUsd / total else 0.0
        return LaneResult(
            loadedMiles = loadedMiles.round1(),
            deadheadMiles = deadheadMiles.round1(),
            totalMiles = total.round1(),
            revenueUsd = rateUsd,
            ratePerMile = rpm.round2(),
            allInRatePerMile = allIn.round2(),
            fuelCostUsd = fuel.round2(),
            profitUsd = profit.round2(),
            profitPerMile = (if (total > 0) profit / total else 0.0).round2(),
            profitabilityScore = score(allIn, deadhead = deadheadMiles, total = total),
        )
    }

    /** 0..100: anchored so $1.20/mi all-in ≈ 0 and $3.20/mi ≈ 100, minus deadhead drag. */
    private fun score(allInRpm: Double, deadhead: Double, total: Double): Int {
        val rpmScore = ((allInRpm - 1.20) / 2.0 * 100)
        val deadheadPenalty = if (total > 0) (deadhead / total) * 40 else 0.0
        return (rpmScore - deadheadPenalty).coerceIn(0.0, 100.0).toInt()
    }

    private fun haversineMiles(a: GeoPoint, b: GeoPoint): Double {
        val r = 3958.8
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }

    private fun Double.round1() = Math.round(this * 10) / 10.0
    private fun Double.round2() = Math.round(this * 100) / 100.0
}
