package com.dispatcher.companion.geo

import android.content.Context
import com.dispatcher.companion.calculator.GeoIndex
import com.dispatcher.companion.calculator.GeoPoint

/**
 * Offline gazetteer from assets/zip_geo.csv (zip,city,state,lat,lon).
 * Ships with major freight markets; the full 42k-row dataset drops in
 * without code changes (FR-602).
 */
class AssetGeoIndex(context: Context) : GeoIndex {

    private val byZip = HashMap<String, GeoPoint>()
    private val byCityState = HashMap<String, GeoPoint>()
    private val byCity = HashMap<String, GeoPoint>()

    init {
        context.assets.open("zip_geo.csv").bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val p = line.split(',')
                if (p.size < 5) return@forEach
                val point = GeoPoint(p[3].toDouble(), p[4].toDouble())
                byZip[p[0]] = point
                val city = p[1].lowercase()
                byCityState["$city/${p[2].uppercase()}"] = point
                byCity.putIfAbsent(city, point)
            }
        }
    }

    override fun byZip(zip: String) = byZip[zip]

    override fun byCity(city: String, state: String?): GeoPoint? =
        if (state != null) byCityState["${city.lowercase()}/${state.uppercase()}"]
        else byCity[city.lowercase()]
}
