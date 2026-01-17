package com.example.mobile.map

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ScreenUtils
import com.example.mobile.BuildConfig
import java.util.PriorityQueue
import kotlin.math.*

class MapGdxApp(
    private val onPickLocation: ((lat: Float, lon: Float) -> Unit)? = null,
    private val onDotClick: ((id: String) -> Unit)? = null,
    private val greenMarkerPng: ByteArray? = null,
    private val redMoveableMarkerPng: ByteArray? = null,
) : ApplicationAdapter(), GestureDetector.GestureListener {

    private lateinit var batch: SpriteBatch
    private lateinit var gestures: GestureDetector

    private val apiKey = BuildConfig.GEOAPIFY_KEY
    private val style = "osm-carto"

    private var zoom = 14
    private var centerLat = 46.056946f
    private var centerLon = 14.505751f

    private val tileSize = 256f

    private val prefetchBase = 3
    private val prefetchZooming = 6
    private var preloadBoostFrames = 0

    private val maxConcurrent = 12
    private val maxCachedTiles = 1400
    private val keepZoomLevelsForFallback = 2

    private data class Dot(val id: String, val lat: Float, val lon: Float)
    private val dots = LinkedHashMap<String, Dot>()

    private lateinit var greenTex: Texture
    private lateinit var redPickTex: Texture

    private val dotDrawSize = 44f
    private val dotHitRadius = 120f

    private data class TileKey(val z: Int, val x: Int, val y: Int)
    private data class TileRequest(val key: TileKey, val priority: Float)

    private inner class LruTextureCache(private val maxSize: Int) {
        private val map = object : LinkedHashMap<TileKey, Texture>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, Texture>?): Boolean {
                val shouldRemove = size > maxSize
                if (shouldRemove && eldest != null) eldest.value.dispose()
                return shouldRemove
            }
        }

        fun get(key: TileKey): Texture? = map[key]
        fun contains(key: TileKey): Boolean = map.containsKey(key)

        fun put(key: TileKey, tex: Texture) {
            val old = map.put(key, tex)
            if (old != null && old !== tex) old.dispose()
        }

        fun clear() {
            map.values.forEach { it.dispose() }
            map.clear()
        }
    }

    private val cache = LruTextureCache(maxCachedTiles)

    private val queuedSet = HashSet<TileKey>()
    private val requestPQ = PriorityQueue<TileRequest>(compareBy { it.priority })
    private val active = HashSet<TileKey>()

    private var pinchAccum = 1f
    private var lastPinchDistance = -1f
    private var zoomBurstFrames = 0

    private var hasPickMarker = false
    private var pickLat = 0f
    private var pickLon = 0f
    private var draggingPickMarker = false

    override fun create() {
        batch = SpriteBatch()
        gestures = GestureDetector(this)
        Gdx.input.inputProcessor = gestures

        greenTex = loadTextureFromPngBytesOrFallback(greenMarkerPng) { fallbackGreenDotTexture() }
        redPickTex = loadTextureFromPngBytesOrFallback(redMoveableMarkerPng) { fallbackRedDotTexture() }
    }

    override fun render() {
        ScreenUtils.clear(0.08f, 0.10f, 0.12f, 1f)
        if (apiKey.isBlank()) return

        batch.begin()
        drawVisibleTiles()
        drawDots()
        drawPickMarker()
        batch.end()

        pumpRequests()
    }

    fun clearPickDot() {
        hasPickMarker = false
        draggingPickMarker = false
    }

    fun setDots(newDots: List<Triple<String, Float, Float>>) {
        dots.clear()
        for ((id, lat, lon) in newDots) {
            dots[id] = Dot(id, lat, lon)
        }
    }

    fun addDot(id: String, lat: Float, lon: Float) {
        dots[id] = Dot(id, lat, lon)
    }

    fun removeDot(id: String) {
        dots.remove(id)
    }

    private fun drawDots() {
        if (dots.isEmpty()) return

        val (centerPx, centerPy) = latLonToPixel(centerLat.toDouble(), centerLon.toDouble(), zoom)
        val halfW = Gdx.graphics.width / 2f
        val halfH = Gdx.graphics.height / 2f

        for (d in dots.values) {
            val (pX, pY) = latLonToPixel(d.lat.toDouble(), d.lon.toDouble(), zoom)
            val screenX = (pX - centerPx) + halfW
            val screenY = halfH - (pY - centerPy)

            batch.draw(
                greenTex,
                screenX - dotDrawSize / 2f,
                screenY - dotDrawSize / 2f,
                dotDrawSize,
                dotDrawSize
            )
        }
    }

    private fun drawPickMarker() {
        if (!hasPickMarker) return

        val (mx, my) = pickMarkerScreenXY()
        val yFlipped = (Gdx.graphics.height - my)

        val size = 56f
        batch.draw(redPickTex, mx - size / 2f, yFlipped - size / 2f, size, size)
    }

    private fun pickMarkerScreenXY(): Pair<Float, Float> {
        val (centerPx, centerPy) = latLonToPixel(centerLat.toDouble(), centerLon.toDouble(), zoom)
        val (mPx, mPy) = latLonToPixel(pickLat.toDouble(), pickLon.toDouble(), zoom)

        val halfW = Gdx.graphics.width / 2f
        val halfH = Gdx.graphics.height / 2f

        val screenX = (mPx - centerPx) + halfW
        val screenY = (mPy - centerPy) + halfH
        return screenX to screenY
    }

    private fun drawVisibleTiles() {
        val z = zoom
        val n = 1 shl z

        val (centerPx, centerPy) = latLonToPixel(centerLat.toDouble(), centerLon.toDouble(), z)

        val halfW = Gdx.graphics.width / 2f
        val halfH = Gdx.graphics.height / 2f

        val leftPx = centerPx - halfW
        val rightPx = centerPx + halfW
        val topPy = centerPy - halfH
        val bottomPy = centerPy + halfH

        val effectivePrefetch = if (preloadBoostFrames > 0) prefetchZooming else prefetchBase
        if (preloadBoostFrames > 0) preloadBoostFrames--

        val minTileX = floor(leftPx / tileSize).toInt() - effectivePrefetch
        val maxTileX = floor(rightPx / tileSize).toInt() + effectivePrefetch
        val minTileY = floor(topPy / tileSize).toInt() - effectivePrefetch
        val maxTileY = floor(bottomPy / tileSize).toInt() + effectivePrefetch

        val centerTileX = floor(centerPx / tileSize).toInt()
        val centerTileY = floor(centerPy / tileSize).toInt()

        val drawnFallback = HashSet<TileKey>(512)

        for (ty in minTileY..maxTileY) {
            if (ty < 0 || ty >= n) continue

            for (tx in minTileX..maxTileX) {
                val wrappedX = ((tx % n) + n) % n
                val key = TileKey(z, wrappedX, ty)

                if (!cache.contains(key) && !queuedSet.contains(key) && !active.contains(key)) {
                    val dx = (tx - centerTileX).toFloat()
                    val dy = (ty - centerTileY).toFloat()
                    val dist2 = dx * dx + dy * dy

                    queuedSet.add(key)
                    requestPQ.add(TileRequest(key, dist2))
                }

                val tileOriginPx = tx * tileSize
                val tileTopPy = ty * tileSize

                val screenX = tileOriginPx - centerPx + halfW
                val screenY = halfH - ((tileTopPy + tileSize) - centerPy)

                val tex = cache.get(key)
                if (tex != null) {
                    batch.draw(tex, screenX, screenY, tileSize, tileSize)
                    continue
                }

                var drewSomething = false
                if (!drewSomething && z >= 1) {
                    val pZ = z - 1
                    val pN = 1 shl pZ
                    val pX = wrappedX / 2
                    val pY = ty / 2

                    if (pY in 0 until pN) {
                        val pKey = TileKey(pZ, pX, pY)
                        val pTex = cache.get(pKey)
                        if (pTex != null && drawnFallback.add(pKey)) {
                            val pTx = Math.floorDiv(tx, 2)
                            val pTy = Math.floorDiv(ty, 2)

                            val size = tileSize * 2f
                            val pOriginPx = pTx * size
                            val pTopPy = pTy * size

                            val pScreenX = pOriginPx - centerPx + halfW
                            val pScreenY = halfH - ((pTopPy + size) - centerPy)

                            batch.draw(pTex, pScreenX, pScreenY, size, size)
                            drewSomething = true
                        }
                    }
                }

                if (!drewSomething && z >= 2) {
                    val gZ = z - 2
                    val gN = 1 shl gZ
                    val gX = wrappedX / 4
                    val gY = ty / 4

                    if (gY in 0 until gN) {
                        val gKey = TileKey(gZ, gX, gY)
                        val gTex = cache.get(gKey)
                        if (gTex != null && drawnFallback.add(gKey)) {
                            val gTx = Math.floorDiv(tx, 4)
                            val gTy = Math.floorDiv(ty, 4)

                            val size = tileSize * 4f
                            val gOriginPx = gTx * size
                            val gTopPy = gTy * size

                            val gScreenX = gOriginPx - centerPx + halfW
                            val gScreenY = halfH - ((gTopPy + size) - centerPy)

                            batch.draw(gTex, gScreenX, gScreenY, size, size)
                            drewSomething = true
                        }
                    }
                }

                if (!drewSomething && z <= 18) {
                    val cZ = z + 1
                    val cN = 1 shl cZ

                    val cX0 = wrappedX * 2
                    val cY0 = ty * 2

                    if (cY0 >= 0 && cY0 + 1 < cN) {
                        val k00 = TileKey(cZ, (cX0) % cN, cY0)
                        val k10 = TileKey(cZ, (cX0 + 1) % cN, cY0)
                        val k01 = TileKey(cZ, (cX0) % cN, cY0 + 1)
                        val k11 = TileKey(cZ, (cX0 + 1) % cN, cY0 + 1)

                        val t00 = cache.get(k00)
                        val t10 = cache.get(k10)
                        val t01 = cache.get(k01)
                        val t11 = cache.get(k11)

                        if (t00 != null || t10 != null || t01 != null || t11 != null) {
                            val half = tileSize / 2f
                            if (t00 != null) batch.draw(t00, screenX, screenY + half, half, half)
                            if (t10 != null) batch.draw(t10, screenX + half, screenY + half, half, half)
                            if (t01 != null) batch.draw(t01, screenX, screenY, half, half)
                            if (t11 != null) batch.draw(t11, screenX + half, screenY, half, half)
                        }
                    }
                }
            }
        }
    }

    private fun pumpRequests() {
        val limit = if (zoomBurstFrames > 0) maxConcurrent + 6 else maxConcurrent
        if (zoomBurstFrames > 0) zoomBurstFrames--

        while (active.size < limit && requestPQ.isNotEmpty()) {
            val req = requestPQ.poll()
            val key = req.key
            queuedSet.remove(key)

            if (cache.contains(key) || active.contains(key)) continue

            val dz = abs(key.z - zoom)
            if (dz > keepZoomLevelsForFallback) continue

            active.add(key)
            fetchTile(key)
        }
    }

    private fun fetchTile(key: TileKey) {
        val url = "https://maps.geoapify.com/v1/tile/$style/${key.z}/${key.x}/${key.y}.png?apiKey=$apiKey"
        val request = Net.HttpRequest(Net.HttpMethods.GET).apply {
            this.url = url
            timeOut = 15000
        }

        Gdx.net.sendHttpRequest(request, object : Net.HttpResponseListener {
            override fun handleHttpResponse(httpResponse: Net.HttpResponse) {
                val bytes = httpResponse.result
                try {
                    val pixmap = Pixmap(bytes, 0, bytes.size)
                    Gdx.app.postRunnable {
                        try {
                            val tex = Texture(pixmap)
                            tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
                            cache.put(key, tex)
                        } catch (_: Exception) {
                        } finally {
                            pixmap.dispose()
                            active.remove(key)
                        }
                    }
                } catch (_: Exception) {
                    Gdx.app.postRunnable { active.remove(key) }
                }
            }

            override fun failed(t: Throwable) {
                Gdx.app.postRunnable { active.remove(key) }
            }

            override fun cancelled() {
                Gdx.app.postRunnable { active.remove(key) }
            }
        })
    }

    private fun latLonToPixel(lat: Double, lon: Double, z: Int): Pair<Float, Float> {
        val n = 2.0.pow(z.toDouble())
        val x = (lon + 180.0) / 360.0 * n * tileSize
        val latRad = Math.toRadians(lat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n * tileSize
        return x.toFloat() to y.toFloat()
    }

    private fun pixelToLatLon(px: Float, py: Float, z: Int): Pair<Float, Float> {
        val n = 2.0.pow(z.toDouble())
        val lon = (px / (n * tileSize)) * 360.0 - 180.0
        val y = 1.0 - (py / (n * tileSize)) * 2.0
        val lat = Math.toDegrees(atan(sinh(Math.PI * y)))
        return lat.toFloat() to lon.toFloat()
    }

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        if (hasPickMarker) {
            val (mx, my) = pickMarkerScreenXY()
            val dx = x - mx
            val dy = y - my
            if (dx * dx + dy * dy <= 60f * 60f) {
                draggingPickMarker = true
                return true
            }
        }
        draggingPickMarker = false
        return false
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        if (hasPickMarker && draggingPickMarker) {
            val (mPx, mPy) = latLonToPixel(pickLat.toDouble(), pickLon.toDouble(), zoom)
            val newPx = mPx + deltaX
            val newPy = mPy + deltaY
            val (newLat, newLon) = pixelToLatLon(newPx, newPy, zoom)
            pickLat = newLat
            pickLon = newLon
            onPickLocation?.invoke(pickLat, pickLon)
            return true
        }

        val (centerPx, centerPy) = latLonToPixel(centerLat.toDouble(), centerLon.toDouble(), zoom)
        val newPx = centerPx - deltaX
        val newPy = centerPy - deltaY
        val (newLat, newLon) = pixelToLatLon(newPx, newPy, zoom)
        centerLat = newLat
        centerLon = newLon
        return true
    }

    override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        draggingPickMarker = false
        return false
    }

    override fun longPress(x: Float, y: Float): Boolean {
        val (centerPx, centerPy) = latLonToPixel(centerLat.toDouble(), centerLon.toDouble(), zoom)
        val halfW = Gdx.graphics.width / 2f
        val halfH = Gdx.graphics.height / 2f

        val worldPx = centerPx + (x - halfW)
        val worldPy = centerPy + (y - halfH)

        val (lat, lon) = pixelToLatLon(worldPx, worldPy, zoom)

        hasPickMarker = true
        pickLat = lat
        pickLon = lon

        onPickLocation?.invoke(pickLat, pickLon)
        return true
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        val d = max(distance, 1f)

        if (lastPinchDistance < 0f) {
            lastPinchDistance = d
            pinchAccum = 1f
            return true
        }

        val scale = d / lastPinchDistance
        lastPinchDistance = d
        pinchAccum *= scale

        val zoomStepsFloat = ln(pinchAccum.toDouble()) / ln(2.0)
        val step = when {
            zoomStepsFloat >= 0.25 -> +1
            zoomStepsFloat <= -0.25 -> -1
            else -> 0
        }

        if (step != 0) {
            changeZoom(step)
            pinchAccum /= 2f.pow(step.toFloat())
        }

        return true
    }

    private fun changeZoom(delta: Int) {
        val newZoom = (zoom + delta).coerceIn(3, 19)
        if (newZoom == zoom) return
        zoom = newZoom
        preloadBoostFrames = 45
        requestPQ.clear()
        queuedSet.clear()
        zoomBurstFrames = 30
        pinchAccum = 1f
        lastPinchDistance = -1f
    }

    override fun pinchStop() {
        pinchAccum = 1f
        lastPinchDistance = -1f
    }

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        val yFixed = Gdx.graphics.height - y
        if (dots.isEmpty()) return false

        val (centerPx, centerPy) = latLonToPixel(centerLat.toDouble(), centerLon.toDouble(), zoom)
        val halfW = Gdx.graphics.width / 2f
        val halfH = Gdx.graphics.height / 2f

        var bestId: String? = null
        var bestDist2 = Float.MAX_VALUE

        for (d in dots.values) {
            val (pX, pY) = latLonToPixel(d.lat.toDouble(), d.lon.toDouble(), zoom)
            val screenX = (pX - centerPx) + halfW
            val screenY = halfH - (pY - centerPy)

            val dx = x - screenX
            val dy = yFixed - screenY
            val dist2 = dx * dx + dy * dy

            if (dist2 <= dotHitRadius * dotHitRadius && dist2 < bestDist2) {
                bestDist2 = dist2
                bestId = d.id
            }
        }

        if (bestId != null) {
            onDotClick?.invoke(bestId!!)
            return true
        }
        return false
    }

    override fun fling(velocityX: Float, velocityY: Float, button: Int) = false
    override fun pinch(initialPointer1: Vector2?, initialPointer2: Vector2?, pointer1: Vector2?, pointer2: Vector2?) = false

    private fun loadTextureFromPngBytesOrFallback(bytes: ByteArray?, fallback: () -> Texture): Texture {
        if (bytes == null || bytes.isEmpty()) return fallback()
        return try {
            val pm = Pixmap(bytes, 0, bytes.size)
            val tex = Texture(pm)
            pm.dispose()
            tex
        } catch (_: Exception) {
            fallback()
        }
    }

    private fun fallbackRedDotTexture(): Texture {
        val pm = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        pm.setColor(1f, 0f, 0f, 1f)
        pm.fillCircle(32, 32, 18)
        val t = Texture(pm)
        pm.dispose()
        return t
    }

    private fun fallbackGreenDotTexture(): Texture {
        val pm = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        pm.setColor(0f, 1f, 0f, 1f)
        pm.fillCircle(32, 32, 18)
        val t = Texture(pm)
        pm.dispose()
        return t
    }

    override fun dispose() {
        requestPQ.clear()
        queuedSet.clear()
        active.clear()
        cache.clear()
        batch.dispose()
        greenTex.dispose()
        redPickTex.dispose()
    }
}
