package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.ParkingZoneEntity
import com.example.data.model.UserSubscriptionEntity
import org.json.JSONArray
import org.json.JSONObject

class MapWebInterface(
    private val onZoneSelected: (String) -> Unit,
    private val onReady: () -> Unit
) {
    @JavascriptInterface
    fun onZoneClick(zoneId: String) {
        onZoneSelected(zoneId)
    }

    @JavascriptInterface
    fun onMapReady() {
        onReady()
    }
}

object MapHtmlBuilder {
    fun buildHtml(
        context: Context,
        userLat: Double,
        userLng: Double,
        activeLayer: String,
        zonesJson: String
    ): String {
        return try {
            val baseHtml = context.assets.open("leaflet/map.html").bufferedReader().use { it.readText() }
            val css = context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
            val js = context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }

            val safeZones = if (zonesJson.isBlank()) "[]" else zonesJson
            val configScript = """
                <script>
                    window.INITIAL_CONFIG = {
                        lat: $userLat,
                        lng: $userLng,
                        layer: '$activeLayer',
                        zones: $safeZones
                    };
                </script>
            """.trimIndent()

            val inlinedCss = "<style>\n$css\n</style>"
            val inlinedJs = "<script>\n$js\nif (typeof L === 'undefined' && typeof leaflet !== 'undefined') { window.L = leaflet; }\n</script>\n$configScript"

            // Direct substring replacement WITHOUT regex backreference expansion (prevents Illegal group reference)
            var result = replaceTag(
                source = baseHtml,
                tagPattern = Regex("""<link\s+rel=["']stylesheet["']\s+href=["']leaflet\.css["']\s*/?>""", RegexOption.IGNORE_CASE),
                replacement = inlinedCss
            )
            result = replaceTag(
                source = result,
                tagPattern = Regex("""<script\s+src=["']leaflet\.js["']\s*></script>""", RegexOption.IGNORE_CASE),
                replacement = inlinedJs
            )
            result
        } catch (e: Exception) {
            Log.e("MapHtmlBuilder", "Failed to build map HTML: ${e.message}", e)
            context.assets.open("leaflet/map.html").bufferedReader().use { it.readText() }
        }
    }

    private fun replaceTag(source: String, tagPattern: Regex, replacement: String): String {
        val match = tagPattern.find(source) ?: return source
        return source.substring(0, match.range.first) + replacement + source.substring(match.range.last + 1)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HighDefMapView(
    userLat: Double,
    userLng: Double,
    zones: List<ParkingZoneEntity>,
    subscriptions: List<UserSubscriptionEntity>,
    onZoneClick: (ParkingZoneEntity) -> Unit,
    modifier: Modifier = Modifier,
    activeLayer: String = "google_roadmap",
    onLayerChanged: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var currentActiveLayer by remember { mutableStateOf(activeLayer) }
    var showLayerMenu by remember { mutableStateOf(false) }

    LaunchedEffect(activeLayer) {
        if (activeLayer != currentActiveLayer) {
            currentActiveLayer = activeLayer
            if (isMapReady && webViewRef != null) {
                webViewRef?.post {
                    webViewRef?.evaluateJavascript(
                        "if (window.setTileLayer) { window.setTileLayer('$activeLayer'); }",
                        null
                    )
                }
            }
        }
    }

    val now = remember { System.currentTimeMillis() }
    val activeSubCodes = remember(subscriptions) {
        subscriptions
            .filter { now in it.validFromTimestamp..it.validToTimestamp }
            .map { it.targetCode }
            .toSet()
    }

    // Build JSON representation of zones
    val zonesJson = remember(zones, activeSubCodes) {
        val arr = JSONArray()
        for (z in zones) {
            val isSubscribed = activeSubCodes.contains(z.sectorCode) || activeSubCodes.contains(z.districtCode)
            val obj = JSONObject()
            obj.put("id", z.id)
            obj.put("sectorCode", z.sectorCode)
            obj.put("sectorName", z.sectorName)
            obj.put("districtName", z.districtName)
            obj.put("centerLat", z.centerLat)
            obj.put("centerLng", z.centerLng)
            obj.put("polygon", JSONArray(z.polygonJson))
            obj.put("isSubscribed", isSubscribed)
            obj.put("visitorTariff", z.visitorTariff)
            arr.put(obj)
        }
        arr.toString()
    }

    // Push coordinates to map when ready
    LaunchedEffect(userLat, userLng, isMapReady) {
        if (isMapReady && webViewRef != null) {
            val locLabel = "Roudnická 453/22, Praha 8"
            webViewRef?.post {
                webViewRef?.evaluateJavascript(
                    "if (window.updateUserMarker) { window.updateUserMarker($userLat, $userLng, '$locLabel'); }",
                    null
                )
            }
        }
    }

    // Push zone overlays to map when ready
    LaunchedEffect(zonesJson, isMapReady) {
        if (isMapReady && webViewRef != null) {
            val quoted = JSONObject.quote(zonesJson)
            webViewRef?.post {
                webViewRef?.evaluateJavascript(
                    "if (window.renderZones) { window.renderZones($quoted); }",
                    null
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE2E8F0))
            .testTag("high_def_map_container")
    ) {
        AndroidView(
            factory = { ctx ->
                object : WebView(ctx) {
                    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
                        super.onSizeChanged(w, h, ow, oh)
                        if (w > 0 && h > 0) {
                            post {
                                evaluateJavascript("if (window.checkAndInvalidate) { window.checkAndInvalidate(); }", null)
                            }
                        }
                    }
                }.apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#E2E8F0"))

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowContentAccess = true
                    settings.allowFileAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("LeafletWebView", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                            return true
                        }
                    }

                    addJavascriptInterface(
                        MapWebInterface(
                            onZoneSelected = { zoneId ->
                                val zone = zones.firstOrNull { it.id == zoneId }
                                if (zone != null) {
                                    onZoneClick(zone)
                                }
                            },
                            onReady = {
                                isMapReady = true
                                post {
                                    val quoted = JSONObject.quote(zonesJson)
                                    evaluateJavascript("if (window.renderZones) { window.renderZones($quoted); }", null)
                                    evaluateJavascript("if (window.updateUserMarker) { window.updateUserMarker($userLat, $userLng, 'Roudnická 453/22, Praha 8'); }", null)
                                    evaluateJavascript("if (window.checkAndInvalidate) { window.checkAndInvalidate(); }", null)
                                }
                            }
                        ),
                        "Android"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isMapReady = true
                            val quoted = JSONObject.quote(zonesJson)
                            view?.evaluateJavascript("if (window.initMap) { window.initMap(); }", null)
                            view?.evaluateJavascript("if (window.renderZones) { window.renderZones($quoted); }", null)
                            view?.evaluateJavascript("if (window.updateUserMarker) { window.updateUserMarker($userLat, $userLng, 'Roudnická 453/22, Praha 8'); }", null)
                            view?.evaluateJavascript("if (window.checkAndInvalidate) { window.checkAndInvalidate(); }", null)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            Log.w("HighDefMapView", "Tile/resource error: ${request?.url} -> ${error?.description}")
                        }
                    }

                    val fullHtml = MapHtmlBuilder.buildHtml(ctx, userLat, userLng, currentActiveLayer, zonesJson)
                    val baseUrl = if (currentActiveLayer == "osm") "https://tile.openstreetmap.org/" else "https://mt0.google.com/"
                    loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { wv ->
                webViewRef = wv
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Map Controls (Layer Selector, Center GPS, Zoom In, Zoom Out)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Map Layer Switcher
            Box {
                FilledIconButton(
                    onClick = { showLayerMenu = true },
                    modifier = Modifier
                        .shadow(4.dp, CircleShape)
                        .size(46.dp)
                        .testTag("btn_switch_map_layer"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Změnit podkladovou mapu",
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showLayerMenu,
                    onDismissRequest = { showLayerMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Google Mapy (Ulice)",
                                fontWeight = if (currentActiveLayer == "google_roadmap") FontWeight.Bold else FontWeight.Normal,
                                color = if (currentActiveLayer == "google_roadmap") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            currentActiveLayer = "google_roadmap"
                            onLayerChanged?.invoke("google_roadmap")
                            webViewRef?.evaluateJavascript("if (window.setTileLayer) { window.setTileLayer('google_roadmap'); }", null)
                            showLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "OpenStreetMap (Standard)",
                                fontWeight = if (currentActiveLayer == "osm") FontWeight.Bold else FontWeight.Normal,
                                color = if (currentActiveLayer == "osm") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            currentActiveLayer = "osm"
                            onLayerChanged?.invoke("osm")
                            webViewRef?.evaluateJavascript("if (window.setTileLayer) { window.setTileLayer('osm'); }", null)
                            showLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Carto Voyager (OSM HD)",
                                fontWeight = if (currentActiveLayer == "carto_voyager") FontWeight.Bold else FontWeight.Normal,
                                color = if (currentActiveLayer == "carto_voyager") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            currentActiveLayer = "carto_voyager"
                            onLayerChanged?.invoke("carto_voyager")
                            webViewRef?.evaluateJavascript("if (window.setTileLayer) { window.setTileLayer('carto_voyager'); }", null)
                            showLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Google Satelit",
                                fontWeight = if (currentActiveLayer == "google_satellite") FontWeight.Bold else FontWeight.Normal,
                                color = if (currentActiveLayer == "google_satellite") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            currentActiveLayer = "google_satellite"
                            onLayerChanged?.invoke("google_satellite")
                            webViewRef?.evaluateJavascript("if (window.setTileLayer) { window.setTileLayer('google_satellite'); }", null)
                            showLayerMenu = false
                        }
                    )
                }
            }

            // Center on user position
            FilledIconButton(
                onClick = {
                    webViewRef?.evaluateJavascript("if (window.centerOnUser) { window.centerOnUser(); }", null)
                },
                modifier = Modifier
                    .shadow(4.dp, CircleShape)
                    .size(46.dp)
                    .testTag("btn_hd_center_gps"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Vycentrovat na moji polohu",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Zoom In button
            FilledIconButton(
                onClick = {
                    webViewRef?.evaluateJavascript("if (window.zoomIn) { window.zoomIn(); }", null)
                },
                modifier = Modifier
                    .shadow(4.dp, CircleShape)
                    .size(46.dp)
                    .testTag("btn_hd_zoom_in"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Přiblížit mapu",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Zoom Out button
            FilledIconButton(
                onClick = {
                    webViewRef?.evaluateJavascript("if (window.zoomOut) { window.zoomOut(); }", null)
                },
                modifier = Modifier
                    .shadow(4.dp, CircleShape)
                    .size(46.dp)
                    .testTag("btn_hd_zoom_out"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Oddálit mapu",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Map Legend Indicator (Top Left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF1A56DB), CircleShape)
                )
                Text(
                    text = "Vaše zóna (Povoleno)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFD97706), CircleShape)
                )
                Text(
                    text = "Ostatní ZPS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
