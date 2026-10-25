package com.example.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Standard circuity factors:
     * Intercity bus/highway networks typically traverse ~1.20 - 1.30x great-circle distance due to terrain,
     * interchanges, and elevation contours. Rail trunk lines typically traverse ~1.12 - 1.22x.
     */
    const val DEFAULT_ROAD_CIRCUITY_FACTOR = 1.25
    const val DEFAULT_RAIL_CIRCUITY_FACTOR = 1.15

    /**
     * Geographic boundary and sanity validation rules for coordinates, speeds, and accuracies.
     */
    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return !lat.isNaN() && !lon.isNaN() && !lat.isInfinite() && !lon.isInfinite() &&
                lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0
    }

    fun isValidSpeed(speedKmh: Double): Boolean {
        return !speedKmh.isNaN() && !speedKmh.isInfinite() && speedKmh >= 0.0 && speedKmh <= 500.0
    }

    fun isValidAccuracy(accuracyMeters: Float): Boolean {
        return !accuracyMeters.isNaN() && !accuracyMeters.isInfinite() && accuracyMeters >= 0f && accuracyMeters <= 5000f
    }

    /**
     * Calculates great-circle (straight-line) distance between two coordinates in kilometers using Haversine formula.
     * Rejects invalid or out-of-range coordinates gracefully.
     */
    fun calculateDistanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        if (!isValidCoordinate(lat1, lon1) || !isValidCoordinate(lat2, lon2)) {
            return 0.0
        }
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculates route-aware distance in kilometers by applying corridor circuity modeling.
     */
    fun calculateRouteDistanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        circuityFactor: Double = DEFAULT_ROAD_CIRCUITY_FACTOR
    ): Double {
        val straightLine = calculateDistanceKm(lat1, lon1, lat2, lon2)
        val safeCircuity = if (circuityFactor.isNaN() || circuityFactor.isInfinite()) {
            DEFAULT_ROAD_CIRCUITY_FACTOR
        } else {
            circuityFactor.coerceIn(1.0, 3.0)
        }
        return straightLine * safeCircuity
    }

    /**
     * Calculates total distance along piecewise corridor waypoints when a mapped route is available.
     */
    fun calculatePolylineDistanceKm(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            total += calculateDistanceKm(p1.first, p1.second, p2.first, p2.second)
        }
        return total
    }

    /**
     * Calculates initial bearing from (lat1, lon1) to (lat2, lon2) in degrees [0, 360).
     */
    fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        if (!isValidCoordinate(lat1, lon1) || !isValidCoordinate(lat2, lon2)) {
            return 0.0
        }
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    /**
     * Calculates the minimum distance in kilometers from point P to line segment AB.
     */
    fun distanceToSegmentKm(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        val dAB = calculateDistanceKm(aLat, aLon, bLat, bLon)
        if (dAB < 0.001) return calculateDistanceKm(pLat, pLon, aLat, aLon)

        val cosLat = cos(Math.toRadians((aLat + bLat) / 2.0))
        val bx = (bLon - aLon) * (111.32 * cosLat)
        val by = (bLat - aLat) * 110.574
        val px = (pLon - aLon) * (111.32 * cosLat)
        val py = (pLat - aLat) * 110.574

        val ab2 = bx * bx + by * by
        if (ab2 < 1e-6) return calculateDistanceKm(pLat, pLon, aLat, aLon)

        val t = ((px * bx + py * by) / ab2).coerceIn(0.0, 1.0)
        val projX = t * bx
        val projY = t * by
        val dx = px - projX
        val dy = py - projY
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculates the minimum distance in kilometers from a coordinate to any segment in a polyline.
     */
    fun distanceToPolylineKm(
        pLat: Double, pLon: Double,
        polyline: List<Pair<Double, Double>>
    ): Double {
        if (polyline.isEmpty()) return Double.MAX_VALUE
        if (polyline.size == 1) return calculateDistanceKm(pLat, pLon, polyline[0].first, polyline[0].second)

        var minDistance = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val d = distanceToSegmentKm(pLat, pLon, a.first, a.second, b.first, b.second)
            if (d < minDistance) {
                minDistance = d
            }
        }
        return minDistance
    }

    /**
     * Decodes standard Google Encoded Polyline algorithm format string into coordinate pairs.
     */
    fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val poly = ArrayList<Pair<Double, Double>>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat / 1E5
            val pLng = lng / 1E5
            if (isValidCoordinate(pLat, pLng)) {
                poly.add(Pair(pLat, pLng))
            }
        }
        return poly
    }

    /**
     * Generates evenly spaced reference corridor milestones for transit route visualization.
     */
    fun generateCorridorMilestones(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        count: Int = 4
    ): List<Pair<Double, Double>> {
        if (!isValidCoordinate(originLat, originLon) || !isValidCoordinate(destLat, destLon)) {
            return emptyList()
        }
        val points = mutableListOf<Pair<Double, Double>>()
        for (i in 0..count) {
            val fraction = i.toDouble() / count
            val lat = originLat + (destLat - originLat) * fraction
            val lon = originLon + (destLon - originLon) * fraction
            points.add(Pair(lat, lon))
        }
        return points
    }
}
