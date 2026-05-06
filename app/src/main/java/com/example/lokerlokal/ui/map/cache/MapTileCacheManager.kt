package com.example.lokerlokal.ui.map.cache

import android.util.Log
import com.example.lokerlokal.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * A camera viewport independent from GoogleMap classes so the cache manager stays testable.
 */
data class CameraViewport(
    val zoom: Float,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)

data class TileId(
    val zoomBucket: Int,
    val x: Int,
    val y: Int,
) {
    fun key(): String = "z$zoomBucket:x$x:y$y"
}

data class TileBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)

data class TileDiff(
    val visibleTiles: Set<TileId>,
    val freshTiles: Set<TileId>,
    val staleOrMissingTiles: Set<TileId>,
)

interface MapTileCacheManager<T> {
    fun zoomToBucket(zoom: Float): Int
    fun cameraToVisibleTiles(camera: CameraViewport): Set<TileId>
    fun tileToBounds(tileId: TileId): TileBounds
    fun diff(camera: CameraViewport, nowMs: Long = System.currentTimeMillis()): TileDiff

    suspend fun getFreshVisibleItems(
        camera: CameraViewport,
        nowMs: Long = System.currentTimeMillis(),
    ): List<T>

    suspend fun fetchMissingTiles(
        camera: CameraViewport,
        nowMs: Long = System.currentTimeMillis(),
    ): List<T>

    fun clearAll()
}

private data class CachedTile<T>(
    val tileId: TileId,
    val items: List<T>,
    val fetchedAtMs: Long,
    val ttlMs: Long,
) {
    fun isFresh(nowMs: Long): Boolean = nowMs - fetchedAtMs < ttlMs
}

class InMemoryTileCacheManager<T>(
    private val ttlMs: Long = 60_000L,
    private val itemKey: (T) -> String,
    private val remoteFetcher: suspend (TileId, TileBounds) -> List<T>,
) : MapTileCacheManager<T> {

    companion object {
        private const val TAG = "MapTileCache"
    }

    private val cache = LinkedHashMap<String, CachedTile<T>>()
    private val inFlight = LinkedHashMap<String, kotlinx.coroutines.Deferred<List<T>>>()
    private val mutex = Mutex()

    override fun zoomToBucket(zoom: Float): Int = when {
        zoom < 5f -> 4
        zoom < 9f -> 8
        zoom < 13f -> 12
        else -> 14
    }

    override fun cameraToVisibleTiles(camera: CameraViewport): Set<TileId> {
        val zb = zoomToBucket(camera.zoom)
        val nw = latLngToTileXY(camera.maxLat, camera.minLng, zb)
        val se = latLngToTileXY(camera.minLat, camera.maxLng, zb)

        val tiles = LinkedHashSet<TileId>()
        for (x in nw.x..se.x) {
            for (y in nw.y..se.y) {
                tiles += TileId(zb, x, y)
            }
        }
        return tiles
    }

    override fun tileToBounds(tileId: TileId): TileBounds {
        val nw = tileXYToLatLng(tileId.x, tileId.y, tileId.zoomBucket)
        val se = tileXYToLatLng(tileId.x + 1, tileId.y + 1, tileId.zoomBucket)
        return TileBounds(
            minLat = se.lat,
            minLng = nw.lng,
            maxLat = nw.lat,
            maxLng = se.lng,
        )
    }

    override fun diff(camera: CameraViewport, nowMs: Long): TileDiff {
        val visible = cameraToVisibleTiles(camera)
        val fresh = visible.filterTo(LinkedHashSet()) { tile ->
            cache[tile.key()]?.isFresh(nowMs) == true
        }
        val result = TileDiff(
            visibleTiles = visible,
            freshTiles = fresh,
            staleOrMissingTiles = visible - fresh,
        )
        logDebug(
            "diff zoom=${camera.zoom} bucket=${zoomToBucket(camera.zoom)} visible=${result.visibleTiles.size} fresh=${result.freshTiles.size} miss=${result.staleOrMissingTiles.size}"
        )
        return result
    }

    override suspend fun getFreshVisibleItems(camera: CameraViewport, nowMs: Long): List<T> {
        val tiles = cameraToVisibleTiles(camera)
        val items = tiles.asSequence()
            .mapNotNull { cache[it.key()]?.takeIf { entry -> entry.isFresh(nowMs) } }
            .flatMap { it.items.asSequence() }
            .distinctBy(itemKey)
            .toList()
        logDebug("freshItems tiles=${tiles.size} items=${items.size}")
        return items
    }

    override suspend fun fetchMissingTiles(camera: CameraViewport, nowMs: Long): List<T> {
        val missingTiles = diff(camera, nowMs).staleOrMissingTiles
        if (missingTiles.isEmpty()) {
            logDebug("fetchMissingTiles no-op; all visible tiles are fresh")
            return emptyList()
        }

        logDebug("fetchMissingTiles start count=${missingTiles.size} sample=${missingTiles.take(3).joinToString { it.key() }}")

        val fetchedItems = mutableListOf<T>()
        coroutineScope {
            val deferreds = missingTiles.map { tile ->
                acquireOrCreateFetch(tile)
            }
            deferreds.forEach { fetchedItems += it.await() }
        }

        val result = fetchedItems.distinctBy(itemKey)
        logDebug("fetchMissingTiles done tiles=${missingTiles.size} fetchedItems=${result.size}")
        return result
    }

    override fun clearAll() {
        cache.clear()
        inFlight.clear()
    }

    private suspend fun CoroutineScope.acquireOrCreateFetch(tileId: TileId): kotlinx.coroutines.Deferred<List<T>> {
        val key = tileId.key()
        mutex.withLock {
            inFlight[key]?.let {
                logDebug("inFlight reuse tile=$key")
                return it
            }
            val deferred = async(Dispatchers.IO) {
                val bounds = tileToBounds(tileId)
                logDebug("network fetch tile=$key")
                val items = remoteFetcher(tileId, bounds)
                mutex.withLock {
                    cache[key] = CachedTile(tileId, items, System.currentTimeMillis(), ttlMs)
                    inFlight.remove(key)
                }
                logDebug("network fetched tile=$key items=${items.size}")
                items
            }
            inFlight[key] = deferred
            return deferred
        }
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private data class TileXY(val x: Int, val y: Int)
    private data class LatLng(val lat: Double, val lng: Double)

    private fun latLngToTileXY(lat: Double, lng: Double, z: Int): TileXY {
        val n = (2.0).pow(z.toDouble())
        val x = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n.toInt() - 1)
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(clampedLat)
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n)
            .toInt()
            .coerceIn(0, n.toInt() - 1)
        return TileXY(x, y)
    }

    private fun tileXYToLatLng(x: Int, y: Int, z: Int): LatLng {
        val n = (2.0).pow(z.toDouble())
        val lng = x / n * 360.0 - 180.0
        val latRad = atan(sinh(Math.PI * (1 - 2 * y / n)))
        val lat = Math.toDegrees(latRad)
        return LatLng(lat, lng)
    }
}

