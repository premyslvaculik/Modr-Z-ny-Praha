package com.example

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.auto.CarMapSurfaceRenderer
import com.example.data.model.LatLngPoint
import com.example.data.model.ParkingStatusType
import com.example.data.model.UserSubscriptionEntity
import com.example.data.model.ZoneEvaluation
import com.example.data.seed.PragueZonesSeed
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.MapTileLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h860dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RealAppScaffold(
        uiState: MainUiState,
        viewModel: MainViewModel
    ) {
        MyApplicationTheme {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_parking_badge),
                                    contentDescription = "Logo",
                                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                                    modifier = Modifier.size(32.dp)
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
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "GPS",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = uiState.selectedTab == 0,
                            onClick = {},
                            icon = { Icon(Icons.Default.Map, contentDescription = null) },
                            label = { Text("Mapa & Stav") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 1,
                            onClick = {},
                            icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                            label = { Text("Moje zóny") }
                        )
                        NavigationBarItem(
                            selected = uiState.selectedTab == 2,
                            onClick = {},
                            icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                            label = { Text("Open Data") }
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
                }
            }
        }
    }

    private fun warmUpTiles(context: Context, z: Int, minX: Int, maxX: Int, minY: Int, maxY: Int) {
        for (tx in minX..maxX) {
            for (ty in minY..maxY) {
                MapTileLoader.getTile(context, z, tx, ty)
                MapTileLoader.getTileAndroidBitmap(context, z, tx, ty)
            }
        }
        // Wait up to 6 seconds for async tile downloads to populate LruCache
        val deadline = System.currentTimeMillis() + 6000L
        while (System.currentTimeMillis() < deadline) {
            var allLoaded = true
            for (tx in minX..maxX) {
                for (ty in minY..maxY) {
                    if (MapTileLoader.getCachedOnly(z, tx, ty) == null) {
                        allLoaded = false
                        break
                    }
                }
            }
            if (allLoaded) break
            Thread.sleep(150)
        }
    }

    private fun captureActivityBitmap(activity: Activity): Bitmap {
        val rootView: View = activity.window.decorView.rootView
        val width = rootView.width.coerceAtLeast(1080)
        val height = rootView.height.coerceAtLeast(2200)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)
        return bitmap
    }

    private fun saveBitmap(bmp: Bitmap, outFile: File) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    @Test
    fun `capture real application screenshots`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = MainViewModel(app)

        // Pre-warm Prague 8 & 9 tiles at zoom 15 (default PinchZoomMap zoomLevel = 15.3f -> tileZ = 15)
        warmUpTiles(app, 15, 17697, 17704, 11092, 11101)
        warmUpTiles(app, 14, 8848, 8852, 5546, 5551)

        val now = System.currentTimeMillis()
        val sampleSub = UserSubscriptionEntity(
            id = 1L,
            targetType = "SECTOR",
            targetCode = "8.3",
            displayName = "Sektor 8.3 (Kobylisy, Střížkov & Bohnice)",
            validFromTimestamp = now - 20L * 24 * 3600 * 1000,
            validToTimestamp = now + 345L * 24 * 3600 * 1000,
            carPlate = "8A4 5921"
        )
        val zone83 = PragueZonesSeed.zones.first { it.sectorCode == "8.3" }
        val zone81 = PragueZonesSeed.zones.first { it.sectorCode == "8.1" }

        val allowedState = MainUiState(
            userLocation = LatLngPoint(50.1319683, 14.4874056),
            isGpsActive = true,
            selectedSimLocationName = "Roudnická 453/22, Praha 8 (Sektor 8.3)",
            zones = PragueZonesSeed.zones,
            subscriptions = listOf(sampleSub),
            evaluation = ZoneEvaluation(
                status = ParkingStatusType.ALLOWED,
                currentZone = zone83,
                matchingSubscription = sampleSub,
                conflictingSubscription = null,
                title = "MŮŽETE PARKOVAT",
                message = "Zóna 8.3 (Kobylisy, Střížkov & Bohnice) je pokryta vaším předplatným (${sampleSub.displayName}). Platnost zbývá ještě 345 dní.",
                operatingHours = zone83.operatingHours
            ),
            lastSyncTime = now,
            selectedTab = 0
        )

        val currentState = androidx.compose.runtime.mutableStateOf(allowedState)

        composeRule.setContent {
            RealAppScaffold(uiState = currentState.value, viewModel = viewModel)
        }
        composeRule.waitForIdle()

        val outDir = File("../docs/screenshots").let {
            if (it.exists() || it.parentFile?.exists() == true) it else File("docs/screenshots")
        }

        // 1. Capture Real HomeScreen (ALLOWED in 8.3)
        val bmpAllowed = captureActivityBitmap(composeRule.activity)
        saveBitmap(bmpAllowed, File(outDir, "screen_map_allowed.png"))

        // 2. Capture Real HomeScreen (DIFFERENT_SUBZONE in 8.1 Karlín)
        val subzoneState = allowedState.copy(
            userLocation = LatLngPoint(50.0932, 14.4528),
            selectedSimLocationName = "Praha 8 – Karlín (Sektor 8.1)",
            evaluation = ZoneEvaluation(
                status = ParkingStatusType.DIFFERENT_SUBZONE,
                currentZone = zone81,
                matchingSubscription = null,
                conflictingSubscription = sampleSub,
                title = "JINÁ PODZÓNA",
                message = "Nacházíte se v podzóně 8.1 (Karlín), ale vaše předplatné je omezeno pouze na ${sampleSub.displayName}! Pro stání zde musíte uhradit parkovací tarif.",
                operatingHours = zone81.operatingHours
            )
        )
        composeRule.runOnUiThread {
            currentState.value = subzoneState
        }
        composeRule.waitForIdle()
        val bmpSubzone = captureActivityBitmap(composeRule.activity)
        saveBitmap(bmpSubzone, File(outDir, "screen_map_subzone.png"))

        // 3. Capture Real SubscriptionsScreen ("Moje zóny")
        composeRule.runOnUiThread {
            currentState.value = allowedState.copy(selectedTab = 1)
        }
        composeRule.waitForIdle()
        val bmpSubs = captureActivityBitmap(composeRule.activity)
        saveBitmap(bmpSubs, File(outDir, "screen_subscriptions.png"))

        // 4. Capture Real SettingsScreen ("Open Data & Android Auto")
        composeRule.runOnUiThread {
            currentState.value = allowedState.copy(selectedTab = 2)
        }
        composeRule.waitForIdle()
        val bmpSettings = captureActivityBitmap(composeRule.activity)
        saveBitmap(bmpSettings, File(outDir, "screen_android_auto.png"))

        assertEquals("Modré Zóny Praha", app.getString(R.string.app_name))
        assertTrue(File(outDir, "screen_map_allowed.png").exists())
    }
}
