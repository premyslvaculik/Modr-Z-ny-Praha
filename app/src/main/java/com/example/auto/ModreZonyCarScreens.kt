/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.data.model.LatLngPoint
import com.example.data.model.ParkingStatusType
import com.example.data.model.ZoneEvaluation
import com.example.data.repository.ParkingRepository
import com.example.util.GeoMath
import com.example.util.LocationHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Primary Android Auto Screen using NavigationTemplate + custom SurfaceCallback map rendering.
 * Shows live parking zone status ("MŮŽETE PARKOVAT" / "JINÁ PODZÓNA" / "ZÓNA NENÍ PŘEDPLACENA")
 * and allows interactive panning/zooming on the vehicle display.
 */
class ModreZonyCarMapScreen(
    carContext: CarContext
) : Screen(carContext), DefaultLifecycleObserver {

    private val repository = ParkingRepository(carContext.applicationContext)
    private val locationHelper = LocationHelper(carContext.applicationContext)
    private val surfaceRenderer = CarMapSurfaceRenderer(carContext, lifecycle)

    private val demoLocations = listOf(
        Pair("Roudnická 453/22, Praha 8 (8.3 Kobylisy)", LatLngPoint(50.1319683, 14.4874056)),
        Pair("Křižíkova, Praha 8 (8.1 Karlín)", LatLngPoint(50.0915, 14.4510)),
        Pair("Zenklova, Praha 8 (8.2 Libeň)", LatLngPoint(50.1085, 14.4725)),
        Pair("Sokolovská, Praha 9 (9.1 Vysočany)", LatLngPoint(50.1110, 14.5020)),
        Pair("Vinohradská, Praha 2 (2.1 Vinohrady)", LatLngPoint(50.0755, 14.4378))
    )
    private var currentDemoIndex = 0
    private var currentLocationName = demoLocations[0].first
    private var currentLocation = demoLocations[0].second
    private var currentEvaluation: ZoneEvaluation = ZoneEvaluation(
        status = ParkingStatusType.OUTSIDE_ZPS,
        currentZone = null,
        matchingSubscription = null,
        conflictingSubscription = null,
        title = "Načítám zóny...",
        message = "Kontroluji polohu vozu v pražských modrých zónách",
        operatingHours = ""
    )

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        lifecycleScope.launch {
            repository.ensureSeeded()
            combine(repository.allZones, repository.allSubscriptions) { zones, subs ->
                Pair(zones, subs)
            }.collect { (zones, subs) ->
                val eval = repository.evaluateLocation(currentLocation.lat, currentLocation.lng)
                currentEvaluation = eval
                surfaceRenderer.updateState(currentLocation, zones, subs, eval)
                invalidate()
            }
        }
        refreshGpsOrKeepPragueLocation()
    }

    private fun refreshGpsOrKeepPragueLocation() {
        locationHelper.fetchCurrentLocation(
            onLocationReceived = { pt ->
                val distFromPrague = GeoMath.distanceInMeters(pt.lat, pt.lng, 50.0880, 14.4350)
                if (distFromPrague <= 80000.0) {
                    currentLocation = pt
                    currentLocationName = "GPS poloha vozu"
                }
                reEvaluateAsync()
            },
            onFailed = {
                reEvaluateAsync()
            }
        )
    }

    fun cycleDemoLocation() {
        currentDemoIndex = (currentDemoIndex + 1) % demoLocations.size
        val next = demoLocations[currentDemoIndex]
        currentLocationName = next.first
        currentLocation = next.second
        surfaceRenderer.centerOnCar()
        reEvaluateAsync()
    }

    private fun reEvaluateAsync() {
        lifecycleScope.launch {
            val eval = repository.evaluateLocation(currentLocation.lat, currentLocation.lng)
            currentEvaluation = eval
            surfaceRenderer.carLocation = currentLocation
            surfaceRenderer.currentEvaluation = eval
            surfaceRenderer.renderFrame()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val eval = currentEvaluation
        val bgCarColor = when (eval.status) {
            ParkingStatusType.ALLOWED -> CarColor.GREEN
            ParkingStatusType.DIFFERENT_SUBZONE -> CarColor.YELLOW
            ParkingStatusType.NOT_SUBSCRIBED -> CarColor.RED
            ParkingStatusType.OUTSIDE_ZPS -> CarColor.BLUE
        }

        val zoneSummary = eval.currentZone?.let {
            "Sektor ${it.sectorCode} (${it.sectorName}) • ${it.visitorTariff}"
        } ?: currentLocationName

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Detail zóny")
                    .setOnClickListener {
                        screenManager.push(
                            ModreZonyCarDetailScreen(
                                carContext = carContext,
                                evaluation = eval,
                                locationName = currentLocationName,
                                onCycleLocation = { cycleDemoLocation() }
                            )
                        )
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Změnit bod")
                    .setOnClickListener {
                        cycleDemoLocation()
                    }
                    .build()
            )
            .build()

        val mapActionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_add)
                        ).build()
                    )
                    .setOnClickListener { surfaceRenderer.zoomIn() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_revert)
                        ).build()
                    )
                    .setOnClickListener { surfaceRenderer.zoomOut() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_mylocation)
                        ).build()
                    )
                    .setOnClickListener {
                        surfaceRenderer.centerOnCar()
                        refreshGpsOrKeepPragueLocation()
                    }
                    .build()
            )
            .build()

        val messageInfo = MessageInfo.Builder(eval.title)
            .setText(zoneSummary)
            .build()

        return NavigationTemplate.Builder()
            .setBackgroundColor(bgCarColor)
            .setNavigationInfo(messageInfo)
            .setActionStrip(actionStrip)
            .setMapActionStrip(mapActionStrip)
            .setPanModeListener { }
            .build()
    }
}

/**
 * Secondary Android Auto Detail Screen using PaneTemplate (parking-compliant)
 * showing full details of the active Prague ZPS sector, user subscription status,
 * operating hours, and visitor tariff.
 */
class ModreZonyCarDetailScreen(
    carContext: CarContext,
    private val evaluation: ZoneEvaluation,
    private val locationName: String,
    private val onCycleLocation: () -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val zone = evaluation.currentZone
        val paneBuilder = Pane.Builder()

        paneBuilder.addRow(
            Row.Builder()
                .setTitle(evaluation.title)
                .addText(evaluation.message)
                .build()
        )

        if (zone != null) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Aktuální sektor: ${zone.sectorCode} – ${zone.sectorName}")
                    .addText("${zone.districtName} • Provozní doba: ${zone.operatingHours}")
                    .build()
            )
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Návštěvnický tarif")
                    .addText(zone.visitorTariff)
                    .build()
            )
        } else {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Mimo zónu placeného stání (ZPS)")
                    .addText("Poloha: $locationName")
                    .build()
            )
        }

        val subText = evaluation.matchingSubscription?.let {
            "Pokryto oprávněním: ${it.displayName} (SPZ: ${it.carPlate.ifBlank { "neuvedena" }})"
        } ?: evaluation.conflictingSubscription?.let {
            "Pozor: Máte předplatné ${it.displayName}, které v této podzóně neplatí!"
        } ?: "Pro tuto zónu nemáte aktivní rezidentní předplatné."

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Stav vašeho předplatného")
                .addText(subText)
                .build()
        )

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("Přepnout testovací polohu v Praze")
                .setOnClickListener {
                    onCycleLocation()
                    screenManager.pop()
                }
                .build()
        )

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("Zpět na mapu")
                .setOnClickListener { screenManager.pop() }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Detail parkovací zóny")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
