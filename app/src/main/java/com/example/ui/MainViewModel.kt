/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ParkingRepository
import com.example.util.LocationHelper
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state representing the user's current coordinates, loaded Prague ZPS zones,
 * active parking subscriptions, real-time parking evaluation, and navigation state.
 */
data class MainUiState(
    val userLocation: LatLngPoint = LatLngPoint(50.1319683, 14.4874056), // Roudnická 453/22, Praha 8
    val isGpsActive: Boolean = false,
    val selectedSimLocationName: String? = "Roudnická 453/22, Praha 8 (Sektor 8.3)",
    val zones: List<ParkingZoneEntity> = com.example.data.seed.PragueZonesSeed.zones,
    val subscriptions: List<UserSubscriptionEntity> = emptyList(),
    val evaluation: ZoneEvaluation = ZoneEvaluation(
        status = ParkingStatusType.OUTSIDE_ZPS,
        currentZone = null,
        matchingSubscription = null,
        conflictingSubscription = null,
        title = "Zjišťuji polohu...",
        message = "Načítám data parkovacích zón",
        operatingHours = ""
    ),
    val isLoading: Boolean = false,
    val lastSyncTime: Long = 0L,
    val syncFrequency: String = "WEEKLY",
    val showOnboarding: Boolean = false,
    val selectedTab: Int = 0 // 0 = Mapa & Status, 1 = Moje Zóny, 2 = Nastavení & OpenData
)

/**
 * Primary ViewModel coordinating location updates, zone polygon evaluation,
 * subscription CRUD operations, and expiration reminders.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ParkingRepository(application.applicationContext)
    private val locationHelper = LocationHelper(application.applicationContext)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        NotificationHelper.createNotificationChannel(application.applicationContext)

        viewModelScope.launch {
            repository.ensureSeeded()

            val lastSync = repository.getLastSyncTime()
            val syncFreq = repository.getSyncFrequency()
            val onboardingDone = repository.isOnboardingCompleted()

            _uiState.update {
                it.copy(
                    lastSyncTime = lastSync,
                    syncFrequency = syncFreq,
                    showOnboarding = !onboardingDone
                )
            }

            // Observe zones
            launch {
                repository.allZones.collect { zoneList ->
                    _uiState.update { it.copy(zones = zoneList) }
                    reEvaluate()
                }
            }

            // Observe subscriptions
            launch {
                repository.allSubscriptions.collect { subList ->
                    _uiState.update { it.copy(subscriptions = subList) }
                    reEvaluate()
                    checkExpirations(subList)
                }
            }
        }
    }

    private fun checkExpirations(subs: List<UserSubscriptionEntity>) {
        val now = System.currentTimeMillis()
        for (sub in subs) {
            if (sub.validToTimestamp > now) {
                val daysLeft = (sub.validToTimestamp - now) / (1000 * 60 * 60 * 24)
                if (daysLeft <= sub.remindDaysBefore) {
                    NotificationHelper.notifyExpiration(
                        getApplication(),
                        sub,
                        daysLeft
                    )
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun dismissOnboarding() {
        repository.setOnboardingCompleted(true)
        _uiState.update { it.copy(showOnboarding = false) }
    }

    fun setSimulatedLocation(name: String, point: LatLngPoint) {
        _uiState.update {
            it.copy(
                userLocation = point,
                selectedSimLocationName = name,
                isGpsActive = false
            )
        }
        reEvaluate()
    }

    fun refreshRealGpsLocation() {
        _uiState.update { it.copy(isLoading = true) }
        locationHelper.fetchCurrentLocation(
            onLocationReceived = { pt ->
                // Check if GPS is within Prague region (< 80 km from center)
                val distFromPrague = com.example.util.GeoMath.distanceInMeters(pt.lat, pt.lng, 50.0880, 14.4350)
                if (distFromPrague > 80000.0) {
                    // Running in cloud emulator (e.g. Mountain View, California)
                    // Keep Roudnická 453/22, Praha 8 (Sektor 8.3)
                    val roudnicka = com.example.data.model.LatLngPoint(50.1319683, 14.4874056)
                    _uiState.update {
                        it.copy(
                            userLocation = roudnicka,
                            isGpsActive = false,
                            selectedSimLocationName = "Roudnická 453/22, Praha 8 (Sektor 8.3)",
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            userLocation = pt,
                            isGpsActive = true,
                            selectedSimLocationName = "Aktuální GPS (Praha)",
                            isLoading = false
                        )
                    }
                }
                reEvaluate()
            },
            onFailed = {
                _uiState.update { it.copy(isLoading = false) }
                reEvaluate()
            }
        )
    }

    private fun reEvaluate() {
        val currentLoc = _uiState.value.userLocation
        viewModelScope.launch {
            val eval = repository.evaluateLocation(currentLoc.lat, currentLoc.lng)
            _uiState.update { it.copy(evaluation = eval) }
        }
    }

    fun addSubscription(
        targetType: String,
        targetCode: String,
        displayName: String,
        validFrom: Long,
        validTo: Long,
        carPlate: String
    ) {
        viewModelScope.launch {
            repository.addSubscription(
                targetType = targetType,
                targetCode = targetCode,
                displayName = displayName,
                validFrom = validFrom,
                validTo = validTo,
                carPlate = carPlate
            )
        }
    }

    fun updateSubscription(
        id: Long,
        targetType: String,
        targetCode: String,
        displayName: String,
        validFrom: Long,
        validTo: Long,
        carPlate: String
    ) {
        viewModelScope.launch {
            repository.updateSubscription(
                id = id,
                targetType = targetType,
                targetCode = targetCode,
                displayName = displayName,
                validFrom = validFrom,
                validTo = validTo,
                carPlate = carPlate
            )
        }
    }

    fun deleteSubscription(id: Long) {
        viewModelScope.launch {
            repository.deleteSubscription(id)
        }
    }

    fun syncOpenData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.refreshOpenData()
            val newSync = repository.getLastSyncTime()
            _uiState.update {
                it.copy(
                    lastSyncTime = newSync,
                    isLoading = false
                )
            }
            reEvaluate()
        }
    }

    fun updateSyncFrequency(freq: String) {
        repository.setSyncFrequency(freq)
        _uiState.update { it.copy(syncFrequency = freq) }
    }
}
