package gobby.pathfinder.etherwarp

import java.util.LinkedHashMap

internal object RaycastCache {
    private val cache = object : LinkedHashMap<RaycastKey, Raycasts>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RaycastKey, Raycasts>?): Boolean = size > MAX_TABLES
    }

    @Synchronized
    fun get(range: Double, pitchStep: Float, yawStep: Float): Raycasts =
        cache.getOrPut(RaycastKey(range, pitchStep, yawStep)) { Raycasts.generate(pitchStep, yawStep, range) }

    private data class RaycastKey(val range: Double, val pitchStep: Float, val yawStep: Float)

    private const val INITIAL_CAPACITY = 16
    private const val LOAD_FACTOR = 0.75f
    private const val MAX_TABLES = 32
}
