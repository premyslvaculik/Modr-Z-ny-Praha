/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto CarAppService entry point for "Modré Zóny Praha".
 * Uses HostValidator.ALLOW_ALL_HOSTS_VALIDATOR so the user can test and use
 * the app immediately in Android Auto Desktop Head Unit (DHU) and real vehicles
 * after enabling "Unknown sources" in Android Auto Developer Settings.
 */
class ModreZonyCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return ModreZonyCarSession()
    }
}

class ModreZonyCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return ModreZonyCarMapScreen(carContext)
    }
}
