/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.theme.MyApplicationTheme

/**
 * Main entry activity hosting the Jetpack Compose UI, edge-to-edge layout,
 * and runtime permission requests for GPS location and expiration notifications.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

/**
 * Root composable providing the top app bar, bottom navigation bar, and tab switching
 * between Map & Status, My Subscriptions, and Open Data / Android Auto Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Location & Notification permission request flow
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineLocationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.refreshRealGpsLocation()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_parking_badge),
                            contentDescription = "Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("top_bar_logo")
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Modré Zóny ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Praha",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            permissionLauncher.launch(permissionsToRequest)
                            viewModel.refreshRealGpsLocation()
                        },
                        modifier = Modifier.testTag("btn_top_refresh_gps")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Získat aktuální GPS polohu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Mapa & Stav") },
                    modifier = Modifier.testTag("nav_tab_map")
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                    label = { Text("Moje zóny") },
                    modifier = Modifier.testTag("nav_tab_subscriptions")
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text("Open Data") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                0 -> HomeScreen(uiState = uiState, viewModel = viewModel)
                1 -> SubscriptionsScreen(uiState = uiState, viewModel = viewModel)
                2 -> SettingsScreen(uiState = uiState, viewModel = viewModel)
            }

            if (uiState.showOnboarding) {
                OnboardingDialog(
                    viewModel = viewModel,
                    onDismiss = { viewModel.dismissOnboarding() }
                )
            }
        }
    }
}
