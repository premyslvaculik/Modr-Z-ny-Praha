/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.auto

import android.graphics.*
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.data.model.LatLngPoint
import com.example.data.model.ParkingStatusType
import com.example.data.model.ParkingZoneEntity
import com.example.data.model.UserSubscriptionEntity
import com.example.data.model.ZoneEvaluation
import com.example.data.seed.PragueZonesSeed
import com.example.util.GeoMath
import com.example.util.MapTileLoader
import kotlin.math.*

/**
 * Renders the keyless Czech Street Map / OpenStreetMap tiles, exact Prague ZPS zone polygons
 * (Blue #1D4ED8 for paid zones, Orange #D97706 for other zones), the car GPS position,
 * and a high-contrast HUD banner directly onto the Android Auto car display surface.
 */
class CarMapSurfaceRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle
) : SurfaceCallback, DefaultLifecycleObserver {

    private var surfaceContainer: SurfaceContainer? = null
    private var visibleArea: Rect? = null

    @Volatile
    var zoomLevel: Double = 14.5
        private set

    @Volatile
    var panOffsetPxX: Float = 0f
        private set

    @Volatile
    var panOffsetPxY: Float = 0f
        private set

    @Volatile
    var carLocation: LatLngPoint = LatLngPoint(50.1319683, 14.4874056)

    @Volatile
    var zones: List<ParkingZoneEntity> = PragueZonesSeed.zones

    @Volatile
    var subscriptions: List<UserSubscriptionEntity> = emptyList()

    @Volatile
    var currentEvaluation: ZoneEvaluation? = null

    private val tileListener: () -> Unit = {
        renderFrame()
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        } catch (_: Exception) {
        }
        MapTileLoader.addTileListener(tileListener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        MapTileLoader.removeTileListener(tileListener)
        surfaceContainer = null
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        synchronized(this) {
            this.surfaceContainer = surfaceContainer
        }
        renderFrame()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        synchronized(this) {
            this.visibleArea = visibleArea
        }
        renderFrame()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        renderFrame()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        synchronized(this) {
            if (this.surfaceContainer == surfaceContainer) {
                this.surfaceContainer = null
            }
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        synchronized(this) {
            panOffsetPxX -= distanceX
            panOffsetPxY -= distanceY
        }
        renderFrame()
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        synchronized(this) {
            val delta = ln(scaleFactor.toDouble()) / ln(2.0)
            zoomLevel = (zoomLevel + delta).coerceIn(11.5, 17.5)
        }
        renderFrame()
    }

    fun zoomIn() {
        synchronized(this) {
            zoomLevel = (zoomLevel + 0.75).coerceAtMost(17.5)
        }
        renderFrame()
    }

    fun zoomOut() {
        synchronized(this) {
            zoomLevel = (zoomLevel - 0.75).coerceAtLeast(11.5)
        }
        renderFrame()
    }

    fun centerOnCar() {
        synchronized(this) {
            panOffsetPxX = 0f
            panOffsetPxY = 0f
            zoomLevel = 14.8
        }
        renderFrame()
    }

    fun updateState(
        location: LatLngPoint,
        zoneList: List<ParkingZoneEntity>,
        subList: List<UserSubscriptionEntity>,
        evaluation: ZoneEvaluation
    ) {
        this.carLocation = location
        this.zones = zoneList
        this.subscriptions = subList
        this.currentEvaluation = evaluation
        renderFrame()
    }

    private fun lonToTileX(lon: Double, z: Int): Double {
        return (lon + 180.0) / 360.0 * (1 shl z)
    }

    private fun latToTileY(lat: Double, z: Int): Double {
        val rad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * (1 shl z)
    }

    fun renderFrame() {
        val container = synchronized(this) { surfaceContainer } ?: return
        val surface = container.surface ?: return
        if (!surface.isValid || container.width <= 0 || container.height <= 0) return

        val canvas = try {
            surface.lockCanvas(null)
        } catch (_: Exception) {
            null
        } ?: return

        try {
            drawMapOntoCanvas(
                canvas = canvas,
                width = container.width.toFloat(),
                height = container.height.toFloat(),
                safeRect = visibleArea
            )
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    fun drawMapOntoCanvas(
        canvas: Canvas,
        width: Float,
        height: Float,
        safeRect: Rect? = null
    ) {
        // 1. Background fallback
        canvas.drawColor(Color.parseColor("#ECE8DF"))

        val centerX = if (safeRect != null && safeRect.width() > 0) {
            safeRect.exactCenterX() + panOffsetPxX
        } else {
            width / 2f + panOffsetPxX
        }
        val centerY = if (safeRect != null && safeRect.height() > 0) {
            safeRect.exactCenterY() + panOffsetPxY
        } else {
            height / 2f + panOffsetPxY
        }

        val tileZ = zoomLevel.roundToInt().coerceIn(11, 17)
        val scaleFromTileZ = 2.0.pow(zoomLevel - tileZ).toFloat()
        val tileDrawSizePx = 512f * scaleFromTileZ

        val anchorLoc = carLocation
        val anchorTileX = lonToTileX(anchorLoc.lng, tileZ)
        val anchorTileY = latToTileY(anchorLoc.lat, tileZ)

        fun latLngToScreen(lat: Double, lng: Double): PointF {
            val ptTileX = lonToTileX(lng, tileZ)
            val ptTileY = latToTileY(lat, tileZ)
            val dx = ((ptTileX - anchorTileX) * tileDrawSizePx).toFloat()
            val dy = ((ptTileY - anchorTileY) * tileDrawSizePx).toFloat()
            return PointF(centerX + dx, centerY + dy)
        }

        // 2. Render Street Map Tiles
        val minTileX = floor(anchorTileX - (centerX / tileDrawSizePx) - 1).toInt()
        val maxTileX = ceil(anchorTileX + ((width - centerX) / tileDrawSizePx) + 1).toInt()
        val minTileY = floor(anchorTileY - (centerY / tileDrawSizePx) - 1).toInt()
        val maxTileY = ceil(anchorTileY + ((height - centerY) / tileDrawSizePx) + 1).toInt()

        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                val left = centerX + ((tx - anchorTileX) * tileDrawSizePx).toFloat()
                val top = centerY + ((ty - anchorTileY) * tileDrawSizePx).toFloat()
                val dstRect = RectF(left, top, left + ceil(tileDrawSizePx) + 1f, top + ceil(tileDrawSizePx) + 1f)

                val bmp = MapTileLoader.getTileAndroidBitmap(carContext, tileZ, tx, ty)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, dstRect, tilePaint)
                } else if (tileZ > 11) {
                    val parentBmp = MapTileLoader.getCachedAndroidBitmapOnly(tileZ - 1, tx shr 1, ty shr 1)
                    if (parentBmp != null) {
                        val halfW = parentBmp.width / 2
                        val halfH = parentBmp.height / 2
                        val srcX = (tx and 1) * halfW
                        val srcY = (ty and 1) * halfH
                        val srcRect = Rect(srcX, srcY, srcX + halfW, srcY + halfH)
                        canvas.drawBitmap(parentBmp, srcRect, dstRect, tilePaint)
                    }
                }
            }
        }

        // 3. Render Prague ZPS Zone Polygons
        val now = System.currentTimeMillis()
        val activeSubs = subscriptions.filter { now in it.validFromTimestamp..it.validToTimestamp }
        val eval = currentEvaluation

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(190, 255, 255, 255)
            strokeWidth = 6f
            strokeJoin = Paint.Join.ROUND
        }

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 2.5f
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        for (zone in zones) {
            val points = GeoMath.parsePolygon(zone.polygonJson)
            if (points.size < 3) continue

            val isSubscribed = activeSubs.any {
                it.targetCode == zone.sectorCode || it.targetCode == zone.districtCode
            }
            val isCurrentZone = eval?.currentZone?.id == zone.id

            val path = Path()
            points.forEachIndexed { idx, pt ->
                val screenPt = latLngToScreen(pt.lat, pt.lng)
                if (idx == 0) path.moveTo(screenPt.x, screenPt.y)
                else path.lineTo(screenPt.x, screenPt.y)
            }
            path.close()

            if (isSubscribed) {
                fillPaint.color = Color.argb(if (isCurrentZone) 64 else 42, 29, 78, 216) // #1D4ED8
                strokePaint.color = Color.rgb(29, 78, 216)
                strokePaint.strokeWidth = if (isCurrentZone) 5.5f else 3.5f
                strokePaint.pathEffect = null
            } else {
                fillPaint.color = Color.argb(if (isCurrentZone) 50 else 28, 217, 119, 6) // #D97706
                strokePaint.color = Color.rgb(217, 119, 6)
                strokePaint.strokeWidth = if (isCurrentZone) 4.5f else 2.8f
                strokePaint.pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
            }

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, haloPaint)
            canvas.drawPath(path, strokePaint)

            // Draw Sector Pill Badge
            val centerPt = latLngToScreen(zone.centerLat, zone.centerLng)
            if (centerPt.x in 40f..(width - 40f) && centerPt.y in 40f..(height - 40f)) {
                val label = if (isSubscribed) "✓ ${zone.sectorCode}" else zone.sectorCode
                val textWidth = badgeTextPaint.measureText(label)
                val pillRect = RectF(
                    centerPt.x - textWidth / 2f - 14f,
                    centerPt.y - 18f,
                    centerPt.x + textWidth / 2f + 14f,
                    centerPt.y + 18f
                )
                badgeBgPaint.color = if (isSubscribed) Color.rgb(29, 78, 216) else Color.rgb(180, 83, 9)
                canvas.drawRoundRect(pillRect, 12f, 12f, badgeBgPaint)
                canvas.drawRoundRect(pillRect, 12f, 12f, badgeBorderPaint)
                canvas.drawText(label, centerPt.x, centerPt.y + 8f, badgeTextPaint)
            }
        }

        // 4. Draw Car GPS Location Beacon
        val carScreenPt = latLngToScreen(anchorLoc.lat, anchorLoc.lng)
        val statusColor = when (eval?.status) {
            ParkingStatusType.ALLOWED -> Color.rgb(22, 163, 74)        // Green
            ParkingStatusType.DIFFERENT_SUBZONE -> Color.rgb(217, 119, 6) // Amber
            ParkingStatusType.NOT_SUBSCRIBED -> Color.rgb(220, 38, 38) // Red
            else -> Color.rgb(37, 99, 235)                             // Blue
        }

        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(60, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor))
        }
        canvas.drawCircle(carScreenPt.x, carScreenPt.y, 34f, pulsePaint)

        val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(carScreenPt.x, carScreenPt.y, 16f, whiteRingPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = statusColor
        }
        canvas.drawCircle(carScreenPt.x, carScreenPt.y, 11f, dotPaint)

        // 5. Draw Bottom HUD Status Pill on Canvas
        if (eval != null) {
            val hudBottom = (safeRect?.bottom?.toFloat() ?: height) - 18f
            val hudLeft = (safeRect?.left?.toFloat() ?: 24f) + 18f
            val hudRight = (safeRect?.right?.toFloat() ?: (width - 24f)) - 18f
            val hudRect = RectF(hudLeft, hudBottom - 68f, hudRight.coerceAtMost(hudLeft + 560f), hudBottom)

            val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(235, 15, 23, 42)
            }
            val hudStripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = statusColor
            }
            canvas.drawRoundRect(hudRect, 16f, 16f, hudBgPaint)
            canvas.drawRoundRect(
                RectF(hudRect.left, hudRect.top, hudRect.left + 12f, hudRect.bottom),
                16f,
                16f,
                hudStripePaint
            )

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                textSize = 17f
            }

            val zoneLabel = eval.currentZone?.let { "${it.sectorCode} – ${it.sectorName}" } ?: "Mimo modrou zónu ZPS"
            canvas.drawText("${eval.title} • $zoneLabel", hudRect.left + 24f, hudRect.top + 28f, titlePaint)
            val tariffInfo = eval.currentZone?.let { "Provoz: ${it.operatingHours} | Návštěvníci: ${it.visitorTariff}" }
                ?: "Parkování bez omezení modré zóny"
            canvas.drawText(tariffInfo, hudRect.left + 24f, hudRect.top + 54f, subPaint)
        }
    }
}
