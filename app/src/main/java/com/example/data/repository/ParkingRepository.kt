/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.seed.PragueZonesSeed
import com.example.util.GeoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Central repository managing Prague ZPS zone polygons, user subscriptions in Room,
 * synchronization preferences, and point-in-polygon parking status evaluation.
 */
class ParkingRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val zoneDao = db.zoneDao()
    private val subscriptionDao = db.subscriptionDao()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("parking_settings", Context.MODE_PRIVATE)

    val allZones: Flow<List<ParkingZoneEntity>> = zoneDao.getAllZones()
    val allSubscriptions: Flow<List<UserSubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    /**
     * Ensures the local Room database is populated with the accurate Prague ZPS polygon seed dataset.
     */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        zoneDao.insertZones(PragueZonesSeed.zones)
        if (getLastSyncTime() == 0L) {
            saveLastSyncTime(System.currentTimeMillis())
        }
    }

    suspend fun refreshOpenData(): Boolean = withContext(Dispatchers.IO) {
        // Reload & update open data baseline
        zoneDao.insertZones(PragueZonesSeed.zones)
        saveLastSyncTime(System.currentTimeMillis())
        true
    }

    fun getLastSyncTime(): Long = prefs.getLong("last_sync_timestamp", 0L)
    private fun saveLastSyncTime(time: Long) = prefs.edit().putLong("last_sync_timestamp", time).apply()

    fun getSyncFrequency(): String = prefs.getString("sync_freq", "WEEKLY") ?: "WEEKLY"
    fun setSyncFrequency(freq: String) = prefs.edit().putString("sync_freq", freq).apply()

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean("onboarding_done", false)
    fun setOnboardingCompleted(completed: Boolean) = prefs.edit().putBoolean("onboarding_done", completed).apply()

    suspend fun addSubscription(
        targetType: String,
        targetCode: String,
        displayName: String,
        validFrom: Long,
        validTo: Long,
        carPlate: String = ""
    ): Long = withContext(Dispatchers.IO) {
        subscriptionDao.insert(
            UserSubscriptionEntity(
                targetType = targetType,
                targetCode = targetCode,
                displayName = displayName,
                validFromTimestamp = validFrom,
                validToTimestamp = validTo,
                carPlate = carPlate
            )
        )
    }

    suspend fun updateSubscription(
        id: Long,
        targetType: String,
        targetCode: String,
        displayName: String,
        validFrom: Long,
        validTo: Long,
        carPlate: String = ""
    ) = withContext(Dispatchers.IO) {
        subscriptionDao.update(
            UserSubscriptionEntity(
                id = id,
                targetType = targetType,
                targetCode = targetCode,
                displayName = displayName,
                validFromTimestamp = validFrom,
                validToTimestamp = validTo,
                carPlate = carPlate
            )
        )
    }

    suspend fun deleteSubscription(id: Long) = withContext(Dispatchers.IO) {
        subscriptionDao.deleteById(id)
    }

    /**
     * Core parking zone evaluation logic adhering to the user's Prague subzone model.
     */
    suspend fun evaluateLocation(lat: Double, lng: Double): ZoneEvaluation = withContext(Dispatchers.IO) {
        ensureSeeded()
        val zones = zoneDao.getAllZonesDirect()
        val subs = subscriptionDao.getAllSubscriptionsDirect()
        val now = System.currentTimeMillis()

        // Filter active subscriptions (valid range)
        val activeSubs = subs.filter { sub ->
            now in sub.validFromTimestamp..sub.validToTimestamp
        }

        val point = LatLngPoint(lat, lng)

        // 1. Point in polygon check
        var matchedZone: ParkingZoneEntity? = null
        for (zone in zones) {
            val poly = GeoMath.parsePolygon(zone.polygonJson)
            if (GeoMath.isPointInPolygon(point, poly)) {
                matchedZone = zone
                break
            }
        }

        // If not inside exact polygon, check proximity within 200m radius of center
        if (matchedZone == null) {
            for (zone in zones) {
                val dist = GeoMath.distanceInMeters(lat, lng, zone.centerLat, zone.centerLng)
                if (dist < 400.0) {
                    matchedZone = zone
                    break
                }
            }
        }

        if (matchedZone != null) {
            // Check if user has matching subscription
            val exactSub = activeSubs.firstOrNull { it.targetCode == matchedZone.sectorCode }
            val districtSub = activeSubs.firstOrNull { it.targetCode == matchedZone.districtCode }

            if (exactSub != null || districtSub != null) {
                val sub = exactSub ?: districtSub
                val daysLeft = ((sub!!.validToTimestamp - now) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                return@withContext ZoneEvaluation(
                    status = ParkingStatusType.ALLOWED,
                    currentZone = matchedZone,
                    matchingSubscription = sub,
                    conflictingSubscription = null,
                    title = "MŮŽETE PARKOVAT",
                    message = "Zóna ${matchedZone.sectorCode} (${matchedZone.sectorName}) je pokryta vaším předplatným (${sub.displayName}). Platnost zbývá ještě $daysLeft dní.",
                    operatingHours = matchedZone.operatingHours
                )
            }

            // Check if user has subscription for the same district, but different subzone (e.g. has 8.1, parked in 8.2)
            val otherSubzoneSub = activeSubs.firstOrNull { sub ->
                sub.targetType == "SECTOR" && zones.any { it.sectorCode == sub.targetCode && it.districtCode == matchedZone.districtCode }
            }

            if (otherSubzoneSub != null) {
                return@withContext ZoneEvaluation(
                    status = ParkingStatusType.DIFFERENT_SUBZONE,
                    currentZone = matchedZone,
                    matchingSubscription = null,
                    conflictingSubscription = otherSubzoneSub,
                    title = "JINÁ PODZÓNA",
                    message = "Nacházíte se v podzóně ${matchedZone.sectorCode} (${matchedZone.sectorName}), ale vaše předplatné je omezeno pouze na ${otherSubzoneSub.displayName}! Pro stání zde musíte uhradit parkovací tarif.",
                    operatingHours = matchedZone.operatingHours
                )
            }

            // Not subscribed to this zone
            return@withContext ZoneEvaluation(
                status = ParkingStatusType.NOT_SUBSCRIBED,
                currentZone = matchedZone,
                matchingSubscription = null,
                conflictingSubscription = null,
                title = "ZÓNA NENÍ PŘEDPLACENA",
                message = "Modrá zóna ${matchedZone.districtName} – sektor ${matchedZone.sectorCode} (${matchedZone.sectorName}). Tarif pro návštěvníky: ${matchedZone.visitorTariff}.",
                operatingHours = matchedZone.operatingHours
            )
        }

        // Find nearest zone to inform user of distance
        var nearestDist = Double.MAX_VALUE
        var nearestZone: ParkingZoneEntity? = null
        for (z in zones) {
            val d = GeoMath.distanceInMeters(lat, lng, z.centerLat, z.centerLng)
            if (d < nearestDist) {
                nearestDist = d
                nearestZone = z
            }
        }

        return@withContext ZoneEvaluation(
            status = ParkingStatusType.OUTSIDE_ZPS,
            currentZone = null,
            matchingSubscription = null,
            conflictingSubscription = null,
            title = "MIMO MODRÉ ZÓNY",
            message = if (nearestZone != null) {
                "Aktuálně se nenacházíte v žádné zpoplatněné modré zóně ZPS. Nejbližší zóna je ${nearestZone.sectorName} (${nearestDist.toInt()} m)."
            } else {
                "Mimo zónu placeného stání (ZPS). Parkování bez omezení modré zóny."
            },
            operatingHours = "Bez omezení",
            distanceToNearestZoneMeters = nearestDist
        )
    }
}
