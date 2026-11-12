/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.example.data.model.LatLngPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Wraps Google Play Services [FusedLocationProviderClient] to retrieve high-accuracy
 * device GPS coordinates and provides preset Prague locations for testing.
 */
class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Standard default location: Roudnická 453/22, Praha 8, 18200
    val defaultLocation = LatLngPoint(50.1319683, 14.4874056)

    val simulatedPoints = listOf(
        Pair("Roudnická 453/22, Praha 8 (Sektor 8.3)", LatLngPoint(50.1319683, 14.4874056)),
        Pair("Praha 8 – Karlín (Sektor 8.1)", LatLngPoint(50.0932, 14.4528)),
        Pair("Praha 8 – Libeň (Sektor 8.2)", LatLngPoint(50.1080, 14.4740)),
        Pair("Praha 8 – Kobylisy / Ládví (Sektor 8.3)", LatLngPoint(50.1265, 14.4560)),
        Pair("Praha 1 – Staré Město (Sektor 1.1)", LatLngPoint(50.0880, 14.4210)),
        Pair("Praha 2 – Vinohrady (Sektor 2.1)", LatLngPoint(50.0758, 14.4425)),
        Pair("Mimo zónu ZPS (Černý Most)", LatLngPoint(50.1080, 14.5800))
    )

    /**
     * Requests the current high-accuracy GPS fix, falling back to the last known location.
     */
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(
        onLocationReceived: (LatLngPoint) -> Unit,
        onFailed: () -> Unit
    ) {
        val cts = CancellationTokenSource()
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        onLocationReceived(LatLngPoint(loc.latitude, loc.longitude))
                    } else {
                        // Fallback to last known location
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                onLocationReceived(LatLngPoint(lastLoc.latitude, lastLoc.longitude))
                            } else {
                                onFailed()
                            }
                        }.addOnFailureListener { onFailed() }
                    }
                }
                .addOnFailureListener {
                    onFailed()
                }
        } catch (_: Exception) {
            onFailed()
        }
    }
}
