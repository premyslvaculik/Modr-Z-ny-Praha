/*
 * Modré Zóny Praha – Prague Blue Parking Zones (Android & Android Auto)
 * Copyright (C) 2026 Dotekomanie.cz (https://dotekomanie.cz)
 *
 * Licensed under the GNU General Public License v3.0 with Section 7(b) Additional Terms.
 */
package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native keyless street tile loader and disk/memory cache shared between Jetpack Compose Canvas
 * and Android Auto SurfaceCallback (android.graphics.Canvas).
 */
object MapTileLoader {

    private val memoryCache = object : LruCache<String, ImageBitmap>(200) {}
    private val rawBitmapCache = object : LruCache<String, Bitmap>(200) {}
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val legacyCachePurged = AtomicBoolean(false)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    private val _tileVersion = MutableStateFlow(0)
    val tileVersion: StateFlow<Int> = _tileVersion.asStateFlow()

    @Volatile
    var preferredSource: String = "street_cz"
        private set

    fun addTileListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeTileListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyTileUpdated() {
        _tileVersion.value = _tileVersion.value + 1
        for (l in listeners) {
            try {
                l.invoke()
            } catch (_: Exception) {
            }
        }
    }

    fun switchSource(newSource: String) {
        if (preferredSource != newSource) {
            preferredSource = newSource
            notifyTileUpdated()
        }
    }

    fun getTile(context: Context, z: Int, x: Int, y: Int, source: String = preferredSource): ImageBitmap? {
        val maxIndex = (1 shl z) - 1
        if (z < 0 || y < 0 || y > maxIndex) return null
        val wrappedX = ((x % (1 shl z)) + (1 shl z)) % (1 shl z)
        val key = "${source}_$z/$wrappedX/$y"

        synchronized(memoryCache) {
            memoryCache.get(key)?.let { return it }
        }

        triggerAsyncLoad(context, source, z, wrappedX, y, key)
        return null
    }

    fun getTileAndroidBitmap(context: Context, z: Int, x: Int, y: Int, source: String = preferredSource): Bitmap? {
        val maxIndex = (1 shl z) - 1
        if (z < 0 || y < 0 || y > maxIndex) return null
        val wrappedX = ((x % (1 shl z)) + (1 shl z)) % (1 shl z)
        val key = "${source}_$z/$wrappedX/$y"

        synchronized(rawBitmapCache) {
            rawBitmapCache.get(key)?.let { return it }
        }

        triggerAsyncLoad(context, source, z, wrappedX, y, key)
        return null
    }

    private fun triggerAsyncLoad(context: Context, source: String, z: Int, wrappedX: Int, y: Int, key: String) {
        if (inFlight.add(key)) {
            val appContext = context.applicationContext
            scope.launch {
                try {
                    purgeLegacyWatermarkCacheIfNeeded(appContext)
                    val rawBmp = loadFromDiskOrNetwork(appContext, source, z, wrappedX, y)
                    if (rawBmp != null) {
                        val imgBmp = rawBmp.asImageBitmap()
                        synchronized(rawBitmapCache) {
                            rawBitmapCache.put(key, rawBmp)
                        }
                        synchronized(memoryCache) {
                            memoryCache.put(key, imgBmp)
                        }
                        notifyTileUpdated()
                    }
                } finally {
                    inFlight.remove(key)
                }
            }
        }
    }

    fun getCachedOnly(z: Int, x: Int, y: Int, source: String = preferredSource): ImageBitmap? {
        if (z < 0) return null
        val maxIndex = (1 shl z) - 1
        if (y < 0 || y > maxIndex) return null
        val wrappedX = ((x % (1 shl z)) + (1 shl z)) % (1 shl z)
        val key = "${source}_$z/$wrappedX/$y"
        synchronized(memoryCache) {
            return memoryCache.get(key)
        }
    }

    fun getCachedAndroidBitmapOnly(z: Int, x: Int, y: Int, source: String = preferredSource): Bitmap? {
        if (z < 0) return null
        val maxIndex = (1 shl z) - 1
        if (y < 0 || y > maxIndex) return null
        val wrappedX = ((x % (1 shl z)) + (1 shl z)) % (1 shl z)
        val key = "${source}_$z/$wrappedX/$y"
        synchronized(rawBitmapCache) {
            return rawBitmapCache.get(key)
        }
    }

    private fun purgeLegacyWatermarkCacheIfNeeded(context: Context) {
        if (legacyCachePurged.compareAndSet(false, true)) {
            try {
                listOf("osm_tiles", "osm_tiles_v2").forEach { dirName ->
                    val oldDir = File(context.cacheDir, dirName)
                    if (oldDir.exists()) {
                        oldDir.deleteRecursively()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadFromDiskOrNetwork(
        context: Context,
        source: String,
        z: Int,
        x: Int,
        y: Int
    ): Bitmap? {
        val cacheDir = File(context.cacheDir, "map_tiles_clean_v3")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val tileFile = File(cacheDir, "tile_${source}_${z}_${x}_${y}.png")

        if (tileFile.exists() && tileFile.length() > 7000L) {
            try {
                val decoded = BitmapFactory.decodeFile(tileFile.absolutePath)
                if (decoded != null && decoded.width >= 256 && decoded.height >= 256) {
                    return decoded
                }
            } catch (_: Exception) {
                tileFile.delete()
            }
        } else if (tileFile.exists()) {
            tileFile.delete()
        }

        val serverIdx = (x + y) and 3
        val google2xUrl = "https://mt$serverIdx.google.com/vt/lyrs=m&hl=cs&scale=2&x=$x&y=$y&z=$z"
        val osmUrl = "https://tile.openstreetmap.org/$z/$x/$y.png"
        val google1xUrl = "https://mt$serverIdx.google.com/vt/lyrs=m&hl=cs&x=$x&y=$y&z=$z"

        val candidates = if (source == "osm") {
            listOf(osmUrl, google2xUrl, google1xUrl)
        } else {
            listOf(google2xUrl, osmUrl, google1xUrl)
        }

        for (urlStr in candidates) {
            val bytes = downloadBytes(urlStr)
            if (bytes != null && bytes.size > 7000) {
                try {
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (decoded != null && decoded.width >= 256 && decoded.height >= 256) {
                        try {
                            tileFile.writeBytes(bytes)
                        } catch (_: Exception) {
                        }
                        return decoded
                    }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun downloadBytes(urlStr: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                val userAgent = if (urlStr.contains("openstreetmap.org")) {
                    "ModreZonyPrahaAndroid/1.3 (https://opendata.praha.eu; contact@praha.eu)"
                } else {
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "image/png,image/webp,image/*,*/*;q=0.8")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
