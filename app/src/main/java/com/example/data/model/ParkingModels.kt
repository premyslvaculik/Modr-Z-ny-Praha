/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing a Prague paid parking zone (ZPS) sector, its center coordinates,
 * multi-vertex boundary polygon JSON, operating hours, and visitor tariff.
 */
@Entity(tableName = "parking_zones")
data class ParkingZoneEntity(
    @PrimaryKey val id: String,
    val districtCode: String,      // e.g. "PRAHA_8"
    val districtName: String,      // e.g. "Praha 8"
    val sectorCode: String,        // e.g. "8.1"
    val sectorName: String,        // e.g. "Karlín"
    val centerLat: Double,
    val centerLng: Double,
    val polygonJson: String,       // JSON array of [lat, lng] pairs
    val operatingHours: String,    // e.g. "Po-Pá: 8:00 - 20:00"
    val visitorTariff: String      // e.g. "60 Kč/h (max 3h)"
)

/**
 * Room entity representing a user's resident parking subscription for a whole district
 * or a specific subzone sector, including validity timestamps and license plate.
 */
@Entity(tableName = "user_subscriptions")
data class UserSubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,        // "DISTRICT" or "SECTOR"
    val targetCode: String,        // e.g. "PRAHA_8" or "8.1"
    val displayName: String,       // e.g. "Praha 8 (Celá)" or "8.1 Karlín"
    val validFromTimestamp: Long,
    val validToTimestamp: Long,
    val carPlate: String = "",     // e.g. "1AB 2345"
    val remindDaysBefore: Int = 5,
    val isReminded: Boolean = false
)

/**
 * Geographic coordinate pair (WGS84 latitude and longitude).
 */
data class LatLngPoint(
    val lat: Double,
    val lng: Double
)

/**
 * High-level parking verdict for the user's current position.
 */
enum class ParkingStatusType {
    ALLOWED,              // In a paid/subscribed zone or subzone
    DIFFERENT_SUBZONE,    // In a subzone of the same district, but subscription is restricted to another subzone
    NOT_SUBSCRIBED,       // Inside a ZPS blue zone, but user hasn't paid for it
    OUTSIDE_ZPS           // Outside ZPS entirely (no restriction)
}

/**
 * Result of evaluating a coordinate against Prague ZPS polygons and active subscriptions.
 */
data class ZoneEvaluation(
    val status: ParkingStatusType,
    val currentZone: ParkingZoneEntity?,
    val matchingSubscription: UserSubscriptionEntity?,
    val conflictingSubscription: UserSubscriptionEntity?,
    val title: String,
    val message: String,
    val operatingHours: String,
    val distanceToNearestZoneMeters: Double = 0.0
)
