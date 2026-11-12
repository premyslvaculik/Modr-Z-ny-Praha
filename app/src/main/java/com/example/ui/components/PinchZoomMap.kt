/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LatLngPoint
import com.example.data.model.ParkingZoneEntity
import com.example.data.model.UserSubscriptionEntity
import com.example.util.GeoMath
import com.example.util.MapTileLoader
import kotlin.math.*

private fun lngToTileX(lng: Double, n: Double): Double {
    return (lng + 180.0) / 360.0 * n
}

private fun latToTileY(lat: Double, n: Double): Double {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val rad = Math.toRadians(clamped)
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / Math.PI) / 2.0 * n
}

private fun tileXToLng(tileX: Double, n: Double): Double {
    return tileX / n * 360.0 - 180.0
}

private fun tileYToLat(tileY: Double, n: Double): Double {
    val k = Math.PI - 2.0 * Math.PI * tileY / n
    return Math.toDegrees(atan(0.5 * (exp(k) - exp(-k))))
}

/**
 * Interactive Web Mercator slippy map rendered on a Jetpack Compose Canvas.
 * Displays keyless street tiles, Prague ZPS zone polygons (Blue #1D4ED8 for user-subscribed
 * zones, Orange #D97706 for other zones), sector badges, and user GPS location.
 */
@Composable
fun PinchZoomMap(
    userLat: Double,
    userLng: Double,
    zones: List<ParkingZoneEntity>,
    subscriptions: List<UserSubscriptionEntity>,
    onZoneClick: (ParkingZoneEntity) -> Unit,
    modifier: Modifier = Modifier,
    userLocationName: String? = null
) {
    val context = LocalContext.current
    // Continuous Web Mercator zoom level (15.3f = detailed street level)
    var zoomLevel by remember { mutableFloatStateOf(15.3f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val textMeasurer = rememberTextMeasurer()

    // Subscribe to tile download completions so Canvas redraws as street tiles arrive
    val tileVersion by MapTileLoader.tileVersion.collectAsStateWithLifecycle()
    var mapSource by remember { mutableStateOf(MapTileLoader.preferredSource) }

    // Reset pan whenever target user coordinates change
    LaunchedEffect(userLat, userLng) {
        panOffset = Offset.Zero
    }

    // Determine subscribed codes for visual distinction
    val now = System.currentTimeMillis()
    val activeSubCodes = remember(subscriptions, now) {
        subscriptions
            .filter { now in it.validFromTimestamp..it.validToTimestamp }
            .map { it.targetCode }
            .toSet()
    }

    // Animated pulsing radar beacon
    val infiniteTransition = rememberInfiniteTransition(label = "vector_gps_radar")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 44f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.80f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val baseTilePx = 512f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEAECEF))
            .testTag("interactive_vector_map")
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    if (zoomChange != 1f) {
                        val oldZoom = zoomLevel
                        val newZoom = (oldZoom + log2(zoomChange)).coerceIn(12.2f, 18.2f)
                        val scaleFactor = 2f.pow(newZoom - oldZoom)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        panOffset = (panOffset + center - centroid) * scaleFactor + centroid - center + pan
                        zoomLevel = newZoom
                    } else {
                        panOffset += pan
                    }
                }
            }
            .pointerInput(zones, userLat, userLng, zoomLevel, panOffset, canvasSize) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val oldZoom = zoomLevel
                        val newZoom = (oldZoom + 0.85f).coerceAtMost(18.2f)
                        val scaleFactor = 2f.pow(newZoom - oldZoom)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        panOffset = (panOffset + center - tapOffset) * scaleFactor + tapOffset - center
                        zoomLevel = newZoom
                    },
                    onTap = { tapOffset ->
                        if (canvasSize.width > 0f && canvasSize.height > 0f) {
                            val z = zoomLevel.toInt().coerceIn(12, 18)
                            val n = (1 shl z).toDouble()
                            val tileSizePx = baseTilePx * 2f.pow(zoomLevel - z)
                            val centerScreen = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

                            val userTileX = lngToTileX(userLng, n)
                            val userTileY = latToTileY(userLat, n)

                            val tapTileX = userTileX + (tapOffset.x - centerScreen.x - panOffset.x) / tileSizePx
                            val tapTileY = userTileY + (tapOffset.y - centerScreen.y - panOffset.y) / tileSizePx

                            val tapLng = tileXToLng(tapTileX, n)
                            val tapLat = tileYToLat(tapTileY, n)
                            val tapPoint = LatLngPoint(tapLat, tapLng)

                            val clickedZone = zones.firstOrNull { zone ->
                                val polygon = GeoMath.parsePolygon(zone.polygonJson)
                                GeoMath.isPointInPolygon(tapPoint, polygon)
                            }
                            if (clickedZone != null) {
                                onZoneClick(clickedZone)
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read tileVersion so Compose invalidates Canvas when tiles finish downloading
            @Suppress("UNUSED_EXPRESSION")
            tileVersion

            if (canvasSize != size) {
                canvasSize = size
            }
            val centerScreen = Offset(size.width / 2f, size.height / 2f)
            val z = zoomLevel.toInt().coerceIn(12, 18)
            val n = (1 shl z).toDouble()
            val tileSizePx = baseTilePx * 2f.pow(zoomLevel - z)

            val userTileX = lngToTileX(userLng, n)
            val userTileY = latToTileY(userLat, n)

            fun projectToScreen(lat: Double, lng: Double): Offset {
                val tx = lngToTileX(lng, n)
                val ty = latToTileY(lat, n)
                return Offset(
                    x = centerScreen.x + panOffset.x + ((tx - userTileX) * tileSizePx).toFloat(),
                    y = centerScreen.y + panOffset.y + ((ty - userTileY) * tileSizePx).toFloat()
                )
            }

            // 1. Draw Real Street Map Raster Tiles across the visible viewport
            val minTileX = floor(userTileX - (centerScreen.x + panOffset.x) / tileSizePx).toInt()
            val maxTileX = floor(userTileX + (size.width - centerScreen.x - panOffset.x) / tileSizePx).toInt()
            val minTileY = floor(userTileY - (centerScreen.y + panOffset.y) / tileSizePx).toInt()
            val maxTileY = floor(userTileY + (size.height - centerScreen.y - panOffset.y) / tileSizePx).toInt()

            for (tx in minTileX..maxTileX) {
                for (ty in minTileY..maxTileY) {
                    val leftPx = (centerScreen.x + panOffset.x + (tx - userTileX) * tileSizePx).roundToInt()
                    val topPx = (centerScreen.y + panOffset.y + (ty - userTileY) * tileSizePx).roundToInt()
                    val rightPx = (centerScreen.x + panOffset.x + (tx + 1 - userTileX) * tileSizePx).roundToInt()
                    val bottomPx = (centerScreen.y + panOffset.y + (ty + 1 - userTileY) * tileSizePx).roundToInt()
                    val dstW = (rightPx - leftPx).coerceAtLeast(1)
                    val dstH = (bottomPx - topPx).coerceAtLeast(1)

                    val tileBitmap = MapTileLoader.getTile(context, z, tx, ty, mapSource)
                    if (tileBitmap != null) {
                        drawImage(
                            image = tileBitmap,
                            dstOffset = IntOffset(leftPx, topPx),
                            dstSize = IntSize(dstW, dstH)
                        )
                    } else if (z > 12) {
                        // Smooth parent tile fallback while high-res tile downloads
                        val parentX = floor(tx / 2.0).toInt()
                        val parentY = floor(ty / 2.0).toInt()
                        val parentTile = MapTileLoader.getCachedOnly(z - 1, parentX, parentY, mapSource)
                        if (parentTile != null) {
                            val subX = ((tx % 2) + 2) % 2
                            val subY = ((ty % 2) + 2) % 2
                            val halfW = parentTile.width / 2
                            val halfH = parentTile.height / 2
                            drawImage(
                                image = parentTile,
                                srcOffset = IntOffset(subX * halfW, subY * halfH),
                                srcSize = IntSize(halfW, halfH),
                                dstOffset = IntOffset(leftPx, topPx),
                                dstSize = IntSize(dstW, dstH)
                            )
                        }
                    }
                }
            }

            // 2. Draw All Parking Zones (Sectors) over the street map base layer
            for (zone in zones) {
                val isSubscribed = activeSubCodes.contains(zone.sectorCode) ||
                        activeSubCodes.contains(zone.districtCode)

                val polyPoints = GeoMath.parsePolygon(zone.polygonJson)
                if (polyPoints.size >= 3) {
                    val path = Path()
                    val firstOffset = projectToScreen(polyPoints[0].lat, polyPoints[0].lng)
                    path.moveTo(firstOffset.x, firstOffset.y)
                    for (i in 1 until polyPoints.size) {
                        val ptOffset = projectToScreen(polyPoints[i].lat, polyPoints[i].lng)
                        path.lineTo(ptOffset.x, ptOffset.y)
                    }
                    path.close()

                    // Light transparent zone fill so street names on OpenStreetMap remain 100% readable
                    val fillColor = if (isSubscribed) {
                        Color(0x2E1D4ED8) // 18% opacity Royal Blue for user's paid zone
                    } else {
                        Color(0x1FD97706) // 12% opacity Amber for other ZPS sectors
                    }
                    drawPath(path, color = fillColor, style = Fill)

                    // White outer halo for crisp contrast on top of OpenStreetMap streets
                    val strokeWidth = if (isSubscribed) 6.5f else 4.0f
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.85f),
                        style = Stroke(width = strokeWidth + 3.5f, join = StrokeJoin.Round, cap = StrokeCap.Round)
                    )

                    // Zone Boundary Stroke
                    val borderColor = if (isSubscribed) Color(0xFF1D4ED8) else Color(0xFFD97706)
                    val strokeStyle = if (isSubscribed) {
                        Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round)
                    } else {
                        Stroke(
                            width = strokeWidth,
                            join = StrokeJoin.Round,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    }
                    drawPath(path, color = borderColor, style = strokeStyle)

                    // Zone Label Badge
                    val centerOffset = projectToScreen(zone.centerLat, zone.centerLng)
                    if (centerOffset.x in -120f..(size.width + 120f) &&
                        centerOffset.y in -80f..(size.height + 80f)
                    ) {
                        val labelText = if (isSubscribed) {
                            "✓ ${zone.sectorCode} ${zone.sectorName}"
                        } else {
                            "${zone.sectorCode} ${zone.sectorName}"
                        }
                        val textLayout = textMeasurer.measure(
                            text = labelText,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSubscribed) Color(0xFF1E3A8A) else Color(0xFF7C2D12)
                            )
                        )
                        val badgeWidth = textLayout.size.width + 18f
                        val badgeHeight = textLayout.size.height + 8f
                        val badgeTopLeft = Offset(
                            centerOffset.x - badgeWidth / 2f,
                            centerOffset.y - badgeHeight / 2f
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.94f),
                            topLeft = badgeTopLeft,
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawRoundRect(
                            color = borderColor,
                            topLeft = badgeTopLeft,
                            size = Size(badgeWidth, badgeHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = if (isSubscribed) 2.2f else 1.5f)
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(
                                centerOffset.x - textLayout.size.width / 2f,
                                centerOffset.y - textLayout.size.height / 2f
                            )
                        )
                    }
                }
            }

            // 5. Draw Animated Blinking GPS Beacon at user location
            val userScreenPos = projectToScreen(userLat, userLng)

            drawCircle(
                color = Color(0xFF1A56DB).copy(alpha = pulseAlpha),
                radius = pulseRadius,
                center = userScreenPos
            )
            drawCircle(
                color = Color(0x331A56DB),
                radius = 20f,
                center = userScreenPos
            )
            drawCircle(
                color = Color.White,
                radius = 11f,
                center = userScreenPos
            )
            drawCircle(
                color = Color(0xFF1A56DB),
                radius = 7.5f,
                center = userScreenPos
            )

            // Pin label for user location
            val rawLabel = userLocationName ?: "Roudnická 453/22, Praha 8"
            val shortLabel = rawLabel.substringBefore(" (")
            val locTextLayout = textMeasurer.measure(
                text = shortLabel,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            val locBadgeW = locTextLayout.size.width + 18f
            val locBadgeH = locTextLayout.size.height + 8f
            val badgeTopLeft = Offset(userScreenPos.x - locBadgeW / 2f, userScreenPos.y - 40f)
            drawRoundRect(
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                topLeft = badgeTopLeft,
                size = Size(locBadgeW, locBadgeH),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFF38BDF8),
                topLeft = badgeTopLeft,
                size = Size(locBadgeW, locBadgeH),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 1.5f)
            )
            drawText(
                textLayoutResult = locTextLayout,
                topLeft = Offset(
                    userScreenPos.x - locTextLayout.size.width / 2f,
                    userScreenPos.y - 36f
                )
            )
        }

        // Single, clean, non-overlapping Map Legend in Top-Start
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 3.dp,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF1D4ED8), CircleShape)
                )
                Text(
                    text = "Modré zóny (povoleno)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Floating Map Controls (Center GPS, Zoom In, Zoom Out) at Bottom-End
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(
                onClick = {
                    panOffset = Offset.Zero
                    zoomLevel = 15.5f
                },
                modifier = Modifier
                    .shadow(4.dp, CircleShape)
                    .size(46.dp)
                    .testTag("btn_center_location"),
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

            FilledIconButton(
                onClick = { zoomLevel = (zoomLevel + 0.65f).coerceAtMost(18.2f) },
                modifier = Modifier
                    .shadow(4.dp, CircleShape)
                    .size(46.dp)
                    .testTag("btn_zoom_in"),
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

            FilledIconButton(
                onClick = { zoomLevel = (zoomLevel - 0.65f).coerceAtLeast(12.2f) },
                modifier = Modifier
                    .shadow(4.dp, CircleShape)
                    .size(46.dp)
                    .testTag("btn_zoom_out"),
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

        // Keyless Map Source Switcher Pill at Bottom-Start (Google Street CZ HD <-> OpenStreetMap)
        Surface(
            onClick = {
                val next = if (mapSource == "street_cz") "osm" else "street_cz"
                mapSource = next
                MapTileLoader.switchSource(next)
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .testTag("btn_toggle_map_source"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                text = if (mapSource == "street_cz") {
                    "Podklad: Mapa ulic CZ (přepnout na OSM)"
                } else {
                    "Podklad: OpenStreetMap (přepnout na Mapa CZ)"
                },
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
