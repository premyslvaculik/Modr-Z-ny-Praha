/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.util

import com.example.data.model.LatLngPoint
import org.json.JSONArray
import kotlin.math.*

/**
 * Geographic geometry helper providing polygon JSON parsing, ray-casting point-in-polygon
 * containment checks, and Haversine great-circle distance calculation.
 */
object GeoMath {

    /**
     * Parses a JSON coordinate array (`[[lat, lng], ...]`) into a list of [LatLngPoint].
     */
    fun parsePolygon(jsonStr: String): List<LatLngPoint> {
        val points = mutableListOf<LatLngPoint>()
        try {
            val regex = Regex("""\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\]""")
            val matches = regex.findAll(jsonStr)
            for (match in matches) {
                val lat = match.groupValues[1].toDoubleOrNull()
                val lng = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null) {
                    points.add(LatLngPoint(lat, lng))
                }
            }
        } catch (_: Exception) {
            // fallback empty
        }
        return points
    }

    /**
     * Ray-casting algorithm to test if (lat, lng) is strictly inside a polygon.
     */
    fun isPointInPolygon(point: LatLngPoint, polygon: List<LatLngPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]

            val intersect = ((pi.lat > point.lat) != (pj.lat > point.lat)) &&
                    (point.lng < (pj.lng - pi.lng) * (point.lat - pi.lat) / (pj.lat - pi.lat) + pi.lng)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Calculates distance between two coordinates in meters using Haversine formula.
     */
    fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
