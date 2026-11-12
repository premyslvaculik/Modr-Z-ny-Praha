/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LatLngPoint
import com.example.data.model.ParkingStatusType
import com.example.data.model.ParkingZoneEntity
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.PinchZoomMap
import com.example.util.LocationHelper

/**
 * Primary dashboard screen combining the live parking verdict banner, simulated/GPS
 * location switcher, and interactive Prague ZPS polygon map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLocationSelector by remember { mutableStateOf(false) }
    var selectedZoneForDetails by remember { mutableStateOf<ParkingZoneEntity?>(null) }
    val locationHelper = remember { LocationHelper(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Parking Status Card (Status Banner)
        StatusCard(
            uiState = uiState,
            onSwitchSimLocation = { showLocationSelector = true },
            onOpenInGoogleMaps = {
                openGoogleMaps(context, uiState.userLocation.lat, uiState.userLocation.lng)
            }
        )

        // Single Unified OpenStreetMap + Blue Zones View (no duplicate top-left overlay)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            PinchZoomMap(
                userLat = uiState.userLocation.lat,
                userLng = uiState.userLocation.lng,
                zones = uiState.zones,
                subscriptions = uiState.subscriptions,
                onZoneClick = { zone ->
                    selectedZoneForDetails = zone
                },
                userLocationName = uiState.selectedSimLocationName,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Modal to choose test location in Prague (with dropdown)
    if (showLocationSelector) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        var selectedItemName by remember {
            mutableStateOf(uiState.selectedSimLocationName ?: locationHelper.simulatedPoints.first().first)
        }

        AlertDialog(
            onDismissRequest = { showLocationSelector = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Výběr polohy pro ověření zón", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Vyberte lokalitu z rozbalovacího seznamu pro okamžité otestování chování parkovacích zón a podzón v Praze:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedItemName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Městská část / adresa") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            locationHelper.simulatedPoints.forEach { (name, point) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = name,
                                            fontWeight = if (name == selectedItemName) FontWeight.Bold else FontWeight.Normal,
                                            color = if (name == selectedItemName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        selectedItemName = name
                                        dropdownExpanded = false
                                        viewModel.setSimulatedLocation(name, point)
                                        showLocationSelector = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Výchozí domácí adresa:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        onClick = {
                            val defaultPt = LatLngPoint(50.1319683, 14.4874056)
                            viewModel.setSimulatedLocation("Roudnická 453/22, Praha 8 (Sektor 8.3)", defaultPt)
                            showLocationSelector = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Roudnická 453/22, Praha 8",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "50.1319683N, 14.4874056E (Sektor 8.3)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationSelector = false }) {
                    Text("Zavřít")
                }
            }
        )
    }

    // Detail dialog when tapping on a zone
    selectedZoneForDetails?.let { zone ->
        AlertDialog(
            onDismissRequest = { selectedZoneForDetails = null },
            title = {
                Text("${zone.sectorCode} ${zone.sectorName} (${zone.districtName})", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Provozní doba: ${zone.operatingHours}", fontWeight = FontWeight.Medium)
                    Text("Tarif pro návštěvníky: ${zone.visitorTariff}", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedZoneForDetails = null
                        viewModel.selectTab(1)
                    }
                ) {
                    Text("Upravit moje zóny")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedZoneForDetails = null }) {
                    Text("Zavřít")
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    uiState: MainUiState,
    onSwitchSimLocation: () -> Unit,
    onOpenInGoogleMaps: () -> Unit
) {
    val eval = uiState.evaluation

    val (cardColor, contentColor, icon) = when (eval.status) {
        ParkingStatusType.ALLOWED -> Triple(
            Color(0xFF16A34A),
            Color.White,
            Icons.Default.CheckCircle
        )
        ParkingStatusType.DIFFERENT_SUBZONE -> Triple(
            Color(0xFFEA580C),
            Color.White,
            Icons.Default.Warning
        )
        ParkingStatusType.NOT_SUBSCRIBED -> Triple(
            Color(0xFFD97706),
            Color.White,
            Icons.Default.Info
        )
        ParkingStatusType.OUTSIDE_ZPS -> Triple(
            Color(0xFF2563EB),
            Color.White,
            Icons.Default.Place
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("status_banner_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = eval.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onSwitchSimLocation,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_switch_test_location")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Změnit polohu pro test",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = eval.message,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                color = contentColor.copy(alpha = 0.95f),
                fontWeight = FontWeight.Normal
            )

            if (eval.operatingHours.isNotEmpty()) {
                Text(
                    text = "Provoz: ${eval.operatingHours}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Bottom action row with balanced proportions (weight(1f) on left, fixed single-line pill button on right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable location badge with single-line ellipsis so it never squishes the right button
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.22f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSwitchSimLocation() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF38BDF8), CircleShape)
                        )
                        Text(
                            text = uiState.selectedSimLocationName ?: "Aktuální GPS poloha",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Well-proportioned single-line pill button
                Surface(
                    onClick = onOpenInGoogleMaps,
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    contentColor = cardColor,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("btn_open_google_maps")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Navigovat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

private fun openGoogleMaps(context: Context, lat: Double, lng: Double) {
    try {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Moje+Parkovaci+Misto)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        } catch (_: Exception) {
            Toast.makeText(context, "Nelze otevřít mapovou aplikaci", Toast.LENGTH_SHORT).show()
        }
    }
}
