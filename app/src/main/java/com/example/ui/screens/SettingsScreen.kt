/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 * Any redistribution, modification, or store publication (including Google Play) must
 * retain visible attribution to Dotekomanie.cz in the app Settings UI and store description.
 */
package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ParkingStatusType
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.PinchZoomMap
import java.text.SimpleDateFormat
import java.util.*

/**
 * Holds runtime diagnostic information about the Android Auto service registration
 * and the package installer source on the current device.
 */
private data class AutoDiagnostics(
    val isCarServiceRegistered: Boolean,
    val installerPackage: String?,
    val isPlayStoreInstaller: Boolean,
    val isAndroidAutoInstalled: Boolean
)

/**
 * Queries the Android PackageManager to verify that [com.example.auto.ModreZonyCarAppService]
 * is registered and inspects the installer package name for Android Auto launcher visibility.
 */
private fun inspectAndroidAutoStatus(context: Context): AutoDiagnostics {
    val pm = context.packageManager
    val carIntent = Intent("androidx.car.app.CarAppService").apply {
        setPackage(context.packageName)
    }
    val services = try {
        pm.queryIntentServices(carIntent, PackageManager.GET_META_DATA)
    } catch (_: Exception) {
        emptyList()
    }
    val isRegistered = services.isNotEmpty()

    val installer = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    } catch (_: Exception) {
        null
    }

    val isPlayInstaller = installer == "com.android.vending"
    val isAutoInstalled = try {
        pm.getPackageInfo("com.google.android.projection.gearhead", 0)
        true
    } catch (_: Exception) {
        false
    }

    return AutoDiagnostics(
        isCarServiceRegistered = isRegistered,
        installerPackage = installer,
        isPlayStoreInstaller = isPlayInstaller,
        isAndroidAutoInstalled = isAutoInstalled
    )
}

/**
 * Launches the Android Auto companion settings screen on the phone, falling back
 * to the system App Info page if the companion activity is restricted.
 */
private fun openAndroidAutoSettings(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.google.android.projection.gearhead",
                "com.google.android.projection.pies.settings.SettingsActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    } catch (_: Exception) {
    }

    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.projection.gearhead")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }
    } catch (_: Exception) {
    }

    openAndroidAutoAppInfo(context)
}

/**
 * Opens the system Application Details screen for Android Auto so the user can
 * Force Stop or Clear Cache to refresh the Customize Launcher list.
 */
private fun openAndroidAutoAppInfo(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:com.google.android.projection.gearhead")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Otevřete Nastavení -> Aplikace -> Android Auto",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Settings, Open Data synchronization, Android Auto diagnostics/simulator,
 * and mandatory Dotekomanie.cz open-source attribution screen.
 */
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("d. M. yyyy HH:mm", Locale.getDefault()) }
    var showCarPreviewDialog by remember { mutableStateOf(false) }
    val diagnostics = remember(context) { inspectAndroidAutoStatus(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Keyless Map & Open Data Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_keyless_status"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Bez API klíče",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Funguje ihned – bez API klíčů",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mapový podklad (Česká uliční mapa HD & OpenStreetMap) i přesné hranice městských částí a katastrů (Praha ZPS Open Data) jsou integrované přímo v aplikaci. Žádný uživatel si nemusí generovat ani zadávat vlastní API klíč.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Android Auto Integration & Troubleshooting Section
        Text(
            text = "Android Auto – Experiment (Palubní displej vozu)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_android_auto"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1D4ED8).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Android Auto",
                            tint = Color(0xFF1D4ED8)
                        )
                    }
                    Column {
                        Text(
                            text = "Vlastní mapa zón pro Android Auto (Experiment)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (diagnostics.isCarServiceRegistered) {
                                "Experimentální modul CarAppService (NAVIGATION) je aktivní"
                            } else {
                                "Experimentální služba CarAppService čeká na registraci"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Live Device Diagnostics Banner explaining why "Přizpůsobit spouštěč" hides sideloaded APKs
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (diagnostics.isPlayStoreInstaller) {
                        Color(0xFF16A34A).copy(alpha = 0.12f)
                    } else {
                        Color(0xFFD97706).copy(alpha = 0.14f)
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (diagnostics.isPlayStoreInstaller) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (diagnostics.isPlayStoreInstaller) Color(0xFF16A34A) else Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Proč aplikace chybí v „Přizpůsobit spouštěč“?",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val installerLabel = diagnostics.installerPackage ?: "Ruční instalace APK (mimo Google Play)"
                        Text(
                            text = "Zjištěný zdroj instalace v telefonu: $installerLabel",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Android Auto 11+ na Androidu 14/15 z bezpečnostních důvodů skryje každou navigační aplikaci nainstalovanou přímo ze souboru .apk, dokud neprovedete oba následující kroky:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Step-by-step fix for "Přizpůsobit spouštěč"
                Text(
                    text = "Řešení 1: Povolení vývojářského režimu + Vymazání mezipaměti Android Auto",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "1. Otevřete nastavení Android Auto → sjeďte dolů a 10× poklepejte na „Verze“, poté vpravo nahoře (⋮) otevřete „Nastavení pro vývojáře“.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. DŮLEŽITÉ: Hned první položku „Režim aplikace“ (Application mode) přepněte na „Vývojář“ (Developer) a dole zaškrtněte „Neznámé zdroje“ (Unknown sources). Samotné Neznámé zdroje bez režimu Vývojář u mapových aplikací nestačí!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "3. DŮLEŽITÉ: Android Auto si seznam aplikací ukládá do mezipaměti. Klepněte níže na „Vynutit ukončení / Smazat cache Android Auto“, dejte „Vynutit ukončení“ a v „Úložiště“ klepněte na „Vymazat mezipaměť“. Poté znovu otevřete „Přizpůsobit spouštěč“.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openAndroidAutoSettings(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_open_aa_settings")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nastavení Android Auto", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { openAndroidAutoAppInfo(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_open_aa_appinfo")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vynutit ukončení / Cache", fontSize = 12.sp)
                    }
                }

                HorizontalDivider()

                // Solution 2: Android 14+ com.android.vending installer check
                Text(
                    text = "Řešení 2 (100% jistota na Androidu 14/15): Instalace s příznakem Google Play",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Pokud váš telefon (např. Samsung / Pixel s Androidem 14+) blokuje v „Přizpůsobit spouštěč“ všechny APK instalované přes prohlížeč, nainstalujte APK přes bezplatnou utilitu KingInstaller (přímo v telefonu) nebo z PC přes ADB s příznakem obchodu Google Play:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val adbCommand = "adb install -i \"com.android.vending\" -r app-debug.apk"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = adbCommand,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCommand))
                                Toast.makeText(context, "ADB příkaz zkopírován do schránky", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Kopírovat příkaz",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Widescreen Embedded Preview of the Car Display
                Text(
                    text = "Náhled obrazovky v autě (NavigationTemplate + SurfaceCallback):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                AndroidAutoHeadUnitPreview(
                    uiState = uiState,
                    onOpenFullscreen = { showCarPreviewDialog = true }
                )

                OutlinedButton(
                    onClick = { showCarPreviewDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_auto_preview")
                ) {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Otevřít interaktivní simulátor displeje auta")
                }
            }
        }

        // Open Data Section
        Text(
            text = "Praha Open Data (ZPS)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Databáze parkovacích zón",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val syncText = if (uiState.lastSyncTime > 0) {
                            dateFormat.format(Date(uiState.lastSyncTime))
                        } else {
                            "Předinstalovaná data"
                        }
                        Text(
                            text = "Poslední aktualizace: $syncText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                Text(
                    text = "Aplikace obsahuje přesné polygonové hranice Zón placeného stání (ZPS) pro městské části Praha 1 až Praha 10 včetně přesného rozdělení Prahy 8 na sektory 8.1 Karlín, 8.2 Libeň a 8.3 Kobylisy, Střížkov a Bohnice bez přesahu do Prahy 9.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { viewModel.syncOpenData() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_sync_opendata"),
                    enabled = !uiState.isLoading
                ) {
                    Icon(imageVector = Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktualizovat data z Open Data")
                }
            }
        }

        // Sync Frequency Settings
        Text(
            text = "Frekvence aktualizací",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val options = listOf(
                    Pair("ON_START", "Při každém spuštění aplikace"),
                    Pair("WEEKLY", "Jednou týdně (doporučeno)"),
                    Pair("MANUAL", "Pouze ručně na vyžádání")
                )

                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.syncFrequency == key,
                            onClick = { viewModel.updateSyncFrequency(key) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Parking Guide Info
        Text(
            text = "Pravidla pro modré zóny v Praze",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "• Modrá výplň na mapě (#1D4ED8): Zobrazuje vaši zaplacenou rezidentní zónu nebo podzónu, kde můžete volně parkovat.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "• Oranžová výplň na mapě (#D97706): Zobrazuje ostatní zóny placeného stání (ZPS), kde vaše předplatné neplatí.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "• Podzóny (sektory): Některé městské části (např. Praha 8) umožňují zakoupit levnější parkovací kartu pouze pro konkrétní sektor (např. 8.3 Kobylisy). V ostatních sektorech (8.1 Karlín, 8.2 Libeň) tato karta neplatí!",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "• Víkendy a noci: Mimo provozní dobu zóny (obvykle v noci 20:00 - 8:00 a o víkendech mimo centrum) je parkování zdarma.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Mandatory Attribution & GNU GPLv3 Section 7(b) License Card (Dotekomanie.cz)
        Text(
            text = "O aplikaci & Licence",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_about_dotekomanie"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Dotekomanie.cz",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Projekt portálu Dotekomanie.cz",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Open-source licence GNU GPL v3.0 (Sekce 7b)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Tato aplikace vznikla jako open-source projekt pro portál Dotekomanie.cz. Celý projekt byl vytvořen v prostředí Google AI Studio za využití modelů Gemini 3.8 a Gemini 4 Argon. Zdrojový kód je šířen pod licencí GNU General Public License v3.0 s dodatečnou podmínkou povinné atribuce dle Sekce 7(b): každá upravená verze či distribuce musí zachovat uvedení Dotekomanie.cz v nastavení aplikace i v popisu v obchodě s aplikacemi (např. Google Play).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Ke stažení: Zkompilovaný instalační soubor APK (apk/ModreZonyPraha.apk) i kompletní zdrojový kód jsou volně ke stažení v oficiálním GitHub repozitáři projektu.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedButton(
                    onClick = {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dotekomanie.cz"))
                            context.startActivity(browserIntent)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_dotekomanie")
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Navštívit Dotekomanie.cz")
                }
            }
        }
    }

    if (showCarPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showCarPreviewDialog = false },
            confirmButton = {
                TextButton(onClick = { showCarPreviewDialog = false }) {
                    Text("Zavřít simulátor")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Android Auto – Palubní displej",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Náhled vykreslování NavigationTemplate + SurfaceCallback na širokoúhlé obrazovce vozu:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    AndroidAutoHeadUnitPreview(
                        uiState = uiState,
                        onOpenFullscreen = null
                    )
                }
            }
        )
    }
}

/**
 * Renders an interactive widescreen simulator preview of the Android Auto head unit
 * displaying the live Prague ZPS polygon map and NavigationTemplate status card.
 */
@Composable
private fun AndroidAutoHeadUnitPreview(
    uiState: MainUiState,
    onOpenFullscreen: (() -> Unit)?
) {
    val eval = uiState.evaluation
    val statusColor = when (eval.status) {
        ParkingStatusType.ALLOWED -> Color(0xFF16A34A)
        ParkingStatusType.DIFFERENT_SUBZONE -> Color(0xFFD97706)
        ParkingStatusType.NOT_SUBSCRIBED -> Color(0xFFDC2626)
        ParkingStatusType.OUTSIDE_ZPS -> Color(0xFF2563EB)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(3.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
            .background(Color(0xFF0F172A))
            .testTag("android_auto_headunit_preview")
    ) {
        // Left Android Auto System Rail + Right Map Surface
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Android Auto Dock
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0B1120))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "12:30",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Android Auto Menu",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Hlasový asistent",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Car Display Map Canvas + NavigationTemplate Overlay
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PinchZoomMap(
                    userLat = uiState.userLocation.lat,
                    userLng = uiState.userLocation.lng,
                    zones = uiState.zones,
                    subscriptions = uiState.subscriptions,
                    onZoneClick = {},
                    modifier = Modifier.fillMaxSize(),
                    userLocationName = uiState.selectedSimLocationName
                )

                // Top-left Android Auto NavigationTemplate MessageInfo Card
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .widthIn(max = 220.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Column {
                            Text(
                                text = eval.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = eval.currentZone?.let { "Sektor ${it.sectorCode} (${it.sectorName})" }
                                    ?: "Mimo modrou zónu ZPS",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Top-right Android Auto ActionStrip pill
                if (onOpenFullscreen != null) {
                    Surface(
                        onClick = onOpenFullscreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Zvětšit",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Detail zóny",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
