/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.seed.PragueZonesSeed
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * First-launch onboarding wizard guiding the user through Open Data sync preferences
 * and initial Prague parking zone permit setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var selectedFrequency by remember { mutableStateOf("WEEKLY") }

    val districts = remember { PragueZonesSeed.getDistrictHierarchy() }
    var selectedDistrict by remember { mutableStateOf(districts.firstOrNull { it.code == "PRAHA_8" } ?: districts.first()) }
    var selectedSector by remember { mutableStateOf(selectedDistrict.sectors.first()) }
    var isEntireDistrict by remember { mutableStateOf(false) }

    val periods = remember {
        listOf(
            Pair("1 rok (365 dní) – Maximum", 365L),
            Pair("6 měsíců (180 dní)", 180L),
            Pair("3 měsíce (90 dní)", 90L),
            Pair("1 měsíc (30 dní)", 30L),
            Pair("1 týden (7 dní)", 7L)
        )
    }
    var selectedPeriodDays by remember { mutableLongStateOf(365L) }
    var periodDropdownExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("d. M. yyyy", Locale("cs", "CZ")) }
    var selectedStartDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var districtDropdownExpanded by remember { mutableStateOf(false) }
    var sectorDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedStartDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedUtc ->
                        val pickedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = pickedUtc
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, pickedCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, pickedCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, pickedCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedStartDateMillis = localCal.timeInMillis
                    }
                    showDatePickerDialog = false
                }) {
                    Text("Nastavit datum")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Zrušit")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (step == 1) Icons.Default.CloudDownload else Icons.Default.LocalParking,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (step == 1) "Vítejte v Modrých Zónách" else "Vaše předplacené zóny",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step == 1) {
                    Text(
                        text = "Aplikace má předinstalována data pražských zón placeného stání (ZPS). Může si také automaticky stahovat čerstvé aktualizace.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Jak často si přejete data aktualizovat?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val options = listOf(
                        Pair("ON_START", "Při každém spuštění aplikace"),
                        Pair("WEEKLY", "Jednou týdně (Doporučeno)"),
                        Pair("MANUAL", "Pouze ručně")
                    )

                    options.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedFrequency == key,
                                onClick = { selectedFrequency = key }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text(
                        text = "Vyberte městskou část, zónu a nastavte od kdy máte zaplaceno (vše lze kdykoliv upravit v záložce Moje zóny):",
                        style = MaterialTheme.typography.bodySmall
                    )

                    // District Dropdown
                    Text("Městská část:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = districtDropdownExpanded,
                        onExpandedChange = { districtDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedDistrict.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Vyberte městskou část") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = districtDropdownExpanded,
                            onDismissRequest = { districtDropdownExpanded = false }
                        ) {
                            districts.forEach { dist ->
                                DropdownMenuItem(
                                    text = { Text(dist.name, fontWeight = if (dist.code == selectedDistrict.code) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        selectedDistrict = dist
                                        selectedSector = dist.sectors.first()
                                        districtDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Scope: Entire district vs Specific Sector
                    Text("Rozsah oprávnění:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isEntireDistrict,
                            onClick = { isEntireDistrict = true }
                        )
                        Text("Celá ${selectedDistrict.name} (všechny podzóny)", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !isEntireDistrict,
                            onClick = { isEntireDistrict = false }
                        )
                        Text("Pouze konkrétní podzóna / sektor", style = MaterialTheme.typography.bodySmall)
                    }

                    if (!isEntireDistrict) {
                        ExposedDropdownMenuBox(
                            expanded = sectorDropdownExpanded,
                            onExpandedChange = { sectorDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = "${selectedSector.code} ${selectedSector.name}",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sektor / podzóna") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectorDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = sectorDropdownExpanded,
                                onDismissRequest = { sectorDropdownExpanded = false }
                            ) {
                                selectedDistrict.sectors.forEach { sec ->
                                    DropdownMenuItem(
                                        text = { Text("${sec.code} ${sec.name}") },
                                        onClick = {
                                            selectedSector = sec
                                            sectorDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Start Date ("Od kdy mám zaplaceno")
                    Text("Od kdy máte zaplaceno:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    OutlinedCard(
                        onClick = { showDatePickerDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditCalendar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = dateFormat.format(Date(selectedStartDateMillis)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(onClick = { showDatePickerDialog = true }) {
                                Text("Změnit datum")
                            }
                        }
                    }

                    // Duration Dropdown (max 1 year)
                    Text("Doba platnosti (max. 1 rok):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val currentPeriodLabel = periods.firstOrNull { it.second == selectedPeriodDays }?.first ?: "1 rok (365 dní)"
                    ExposedDropdownMenuBox(
                        expanded = periodDropdownExpanded,
                        onExpandedChange = { periodDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentPeriodLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Časový horizont") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = periodDropdownExpanded,
                            onDismissRequest = { periodDropdownExpanded = false }
                        ) {
                            periods.forEach { (label, days) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedPeriodDays = days
                                        periodDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        viewModel.updateSyncFrequency(selectedFrequency)
                        step = 2
                    } else {
                        val validFrom = selectedStartDateMillis
                        val validTo = validFrom + (selectedPeriodDays * 24 * 60 * 60 * 1000L)
                        if (isEntireDistrict) {
                            viewModel.addSubscription(
                                targetType = "DISTRICT",
                                targetCode = selectedDistrict.code,
                                displayName = "${selectedDistrict.name} (Celá)",
                                validFrom = validFrom,
                                validTo = validTo,
                                carPlate = ""
                            )
                        } else {
                            viewModel.addSubscription(
                                targetType = "SECTOR",
                                targetCode = selectedSector.code,
                                displayName = "${selectedSector.code} ${selectedSector.name}",
                                validFrom = validFrom,
                                validTo = validTo,
                                carPlate = ""
                            )
                        }
                        viewModel.dismissOnboarding()
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag(if (step == 1) "btn_onboarding_next" else "btn_onboarding_finish")
            ) {
                Text(if (step == 1) "Pokračovat" else "Začít používat")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.dismissOnboarding()
                    onDismiss()
                }
            ) {
                Text("Přeskočit")
            }
        }
    )
}
