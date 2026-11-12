/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserSubscriptionEntity
import com.example.data.seed.PragueZonesSeed
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Screen for managing the user's resident parking permits (subscriptions) across
 * Prague municipal districts and individual subzones/sectors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSubscription by remember { mutableStateOf<UserSubscriptionEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Přidat zónu") },
                modifier = Modifier.testTag("fab_add_subscription")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Info Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                    Column {
                        Text(
                            text = "Moje předplacená parkovací oprávnění",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Klepnutím na tlačítko Upravit u libovolné zóny snadno změníte datum od kdy máte zaplaceno i dobu platnosti.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Subscriptions List
            if (uiState.subscriptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Nemáte uložena žádná parkovací oprávnění",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Klepnutím na tlačítko níže přidejte městskou část nebo konkrétní podzónu a nastavte od kdy máte zaplaceno.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("btn_empty_add_subscription")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Přidat první zónu")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.subscriptions, key = { it.id }) { sub ->
                        SubscriptionItemCard(
                            subscription = sub,
                            onEdit = { editingSubscription = sub },
                            onDelete = { viewModel.deleteSubscription(sub.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SubscriptionFormDialog(
            initialSubscription = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { type, code, name, from, to, plate ->
                viewModel.addSubscription(type, code, name, from, to, plate)
                showAddDialog = false
            }
        )
    }

    editingSubscription?.let { subToEdit ->
        SubscriptionFormDialog(
            initialSubscription = subToEdit,
            onDismiss = { editingSubscription = null },
            onConfirm = { type, code, name, from, to, plate ->
                viewModel.updateSubscription(
                    id = subToEdit.id,
                    targetType = type,
                    targetCode = code,
                    displayName = name,
                    validFrom = from,
                    validTo = to,
                    carPlate = plate
                )
                editingSubscription = null
            }
        )
    }
}

@Composable
private fun SubscriptionItemCard(
    subscription: UserSubscriptionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isNotStartedYet = now < subscription.validFromTimestamp
    val isExpired = now > subscription.validToTimestamp
    val daysLeft = ((subscription.validToTimestamp - now) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    val dateFormat = remember { SimpleDateFormat("d. M. yyyy", Locale("cs", "CZ")) }

    val statusColor = when {
        isExpired -> Color(0xFFDC2626)
        isNotStartedYet -> Color(0xFF2563EB)
        daysLeft <= 7 -> Color(0xFFEA580C)
        else -> Color(0xFF16A34A)
    }

    val statusLabel = when {
        isExpired -> "Vypršelo"
        isNotStartedYet -> "Začne ${dateFormat.format(Date(subscription.validFromTimestamp))}"
        else -> "Zbývá $daysLeft dní"
    }

    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sub_card_${subscription.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (subscription.targetType == "DISTRICT") "Celá městská část" else "Samostatný sektor / podzóna",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("btn_edit_sub_${subscription.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Upravit oprávnění",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upravit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("btn_delete_sub_${subscription.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Smazat oprávnění",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (subscription.carPlate.isNotBlank()) {
                Text(
                    text = "SPZ: ${subscription.carPlate}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Clickable validity row with explicit edit prompt for "Od kdy mám zaplaceno"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEdit() }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditCalendar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Zaplaceno od: ${dateFormat.format(Date(subscription.validFromTimestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Platnost do: ${dateFormat.format(Date(subscription.validToTimestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionFormDialog(
    initialSubscription: UserSubscriptionEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (type: String, code: String, name: String, from: Long, to: Long, plate: String) -> Unit
) {
    val isEditing = initialSubscription != null
    val districts = remember { PragueZonesSeed.getDistrictHierarchy() }

    val initialDistrict = remember(initialSubscription) {
        if (initialSubscription == null) {
            districts.firstOrNull { it.code == "PRAHA_8" } ?: districts.first()
        } else if (initialSubscription.targetType == "DISTRICT") {
            districts.firstOrNull { it.code == initialSubscription.targetCode } ?: districts.first()
        } else {
            districts.firstOrNull { d -> d.sectors.any { it.code == initialSubscription.targetCode } }
                ?: districts.first()
        }
    }

    var selectedDistrict by remember { mutableStateOf(initialDistrict) }
    var isEntireDistrictSelected by remember {
        mutableStateOf(initialSubscription?.targetType == "DISTRICT")
    }
    var selectedSector by remember {
        mutableStateOf(
            if (initialSubscription != null && initialSubscription.targetType == "SECTOR") {
                initialDistrict.sectors.firstOrNull { it.code == initialSubscription.targetCode }
                    ?: initialDistrict.sectors.first()
            } else {
                initialDistrict.sectors.first()
            }
        )
    }

    // Dropdown periods with maximum 1 year
    val periods = remember {
        listOf(
            Pair("1 rok (365 dní) – Maximum", 365L),
            Pair("6 měsíců (180 dní)", 180L),
            Pair("3 měsíce (90 dní)", 90L),
            Pair("1 měsíc (30 dní)", 30L),
            Pair("1 týden (7 dní)", 7L)
        )
    }

    val initialDays = remember(initialSubscription) {
        if (initialSubscription != null) {
            val diffDays = ((initialSubscription.validToTimestamp - initialSubscription.validFromTimestamp) / (24 * 60 * 60 * 1000L)).coerceIn(1L, 365L)
            periods.minByOrNull { abs(it.second - diffDays) }?.second ?: 365L
        } else {
            365L
        }
    }

    var selectedPeriodDays by remember { mutableLongStateOf(initialDays) }
    var periodDropdownExpanded by remember { mutableStateOf(false) }

    // Start date setup ("Od kdy mám zaplaceno")
    val dateFormat = remember { SimpleDateFormat("d. M. yyyy", Locale("cs", "CZ")) }
    var selectedStartDateMillis by remember {
        mutableLongStateOf(initialSubscription?.validFromTimestamp ?: System.currentTimeMillis())
    }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var carPlate by remember { mutableStateOf(initialSubscription?.carPlate ?: "") }
    var districtDropdownExpanded by remember { mutableStateOf(false) }
    var sectorDropdownExpanded by remember { mutableStateOf(false) }

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
            Text(
                text = if (isEditing) "Upravit parkovací oprávnění" else "Přidat předplacené oprávnění",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Choose District via Dropdown
                Text("1. Městská část:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                ExposedDropdownMenuBox(
                    expanded = districtDropdownExpanded,
                    onExpandedChange = { districtDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedDistrict.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Městská část") },
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

                // 2. Choose Scope (Entire District vs Specific Subsector)
                Text("2. Rozsah oprávnění:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEntireDistrictSelected = true }
                ) {
                    RadioButton(
                        selected = isEntireDistrictSelected,
                        onClick = { isEntireDistrictSelected = true }
                    )
                    Text("Celá ${selectedDistrict.name} (všechny podzóny)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEntireDistrictSelected = false }
                ) {
                    RadioButton(
                        selected = !isEntireDistrictSelected,
                        onClick = { isEntireDistrictSelected = false }
                    )
                    Text("Pouze konkrétní podzóna / sektor", style = MaterialTheme.typography.bodyMedium)
                }

                if (!isEntireDistrictSelected) {
                    ExposedDropdownMenuBox(
                        expanded = sectorDropdownExpanded,
                        onExpandedChange = { sectorDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = "${selectedSector.code} ${selectedSector.name}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Vyberte sektor / podzónu") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectorDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sectorDropdownExpanded,
                            onDismissRequest = { sectorDropdownExpanded = false }
                        ) {
                            selectedDistrict.sectors.forEach { sector ->
                                DropdownMenuItem(
                                    text = { Text("${sector.code} ${sector.name}") },
                                    onClick = {
                                        selectedSector = sector
                                        sectorDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Always-visible Start Date ("Od kdy mám zaplaceno")
                Text("3. Od kdy máte zaplaceno (Začátek):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                OutlinedCard(
                    onClick = { showDatePickerDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_pick_start_date"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditCalendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Datum začátku platnosti",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dateFormat.format(Date(selectedStartDateMillis)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { showDatePickerDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Změnit datum", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 4. Dropdown for Subscription Horizon (max 1 year)
                Text("4. Časový horizont předplatného (max. 1 rok):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                        label = { Text("Doba platnosti") },
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
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selectedPeriodDays == days) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedPeriodDays == days) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    selectedPeriodDays = days
                                    periodDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Overview summary card
                val computedEndMillis = selectedStartDateMillis + (selectedPeriodDays * 24 * 60 * 60 * 1000L)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Zaplaceno od:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = dateFormat.format(Date(selectedStartDateMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Platnost do:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = dateFormat.format(Date(computedEndMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 5. Car Plate
                OutlinedTextField(
                    value = carPlate,
                    onValueChange = { carPlate = it.uppercase() },
                    label = { Text("SPZ vozidla (volitelné)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val from = selectedStartDateMillis
                    val to = from + (selectedPeriodDays * 24 * 60 * 60 * 1000L)
                    if (isEntireDistrictSelected) {
                        onConfirm(
                            "DISTRICT",
                            selectedDistrict.code,
                            "${selectedDistrict.name} (Celá)",
                            from,
                            to,
                            carPlate
                        )
                    } else {
                        onConfirm(
                            "SECTOR",
                            selectedSector.code,
                            "${selectedSector.code} ${selectedSector.name}",
                            from,
                            to,
                            carPlate
                        )
                    }
                },
                modifier = Modifier.testTag("btn_confirm_add_subscription")
            ) {
                Text(if (isEditing) "Uložit změny" else "Uložit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}
