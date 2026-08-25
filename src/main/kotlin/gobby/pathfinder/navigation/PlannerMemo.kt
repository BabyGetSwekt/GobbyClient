package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.world.BlockCache
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToLong
import gobby.utils.HashMix.mix
import gobby.utils.HashMix.mixRotated

internal class PlannerMemo(private val maxAimEntries: Int = DEFAULT_MAX_AIM_ENTRIES) {
    private val aims = HashMap<Long, Aim?>(INITIAL_CAPACITY)
    private val rays = Long2LongOpenHashMap().apply { defaultReturnValue(MISSING_RAY) }
    var aimHits: Long = 0
        private set
    var aimMisses: Long = 0
        private set

    fun exactAim(key: Long, compute: () -> Aim?): Aim? {
        if (aims.containsKey(key)) {
            aimHits++
            return aims[key]
        }
        aimMisses++
        val result = compute()
        if (aims.size < maxAimEntries) aims[key] = result
        return result
    }

    fun ray(key: Long, compute: () -> BlockPos?): BlockPos? {
        val cached = rays.get(key)
        if (cached != MISSING_RAY) return cached.takeUnless { it == NO_HIT }?.let(BlockPos::of)
        val result = compute()
        if (rays.size < MAX_RAY_ENTRIES) rays.put(key, result?.asLong() ?: NO_HIT)
        return result
    }

    companion object {
        fun exactAimKey(snapshot: BlockCache.SnapshotView, eye: Vec3, target: BlockPos, range: Double, kindOrdinal: Int): Long {
            val origin = quantizedOrigin(eye)
            return mix(snapshot.worldEpoch) xor
                mixRotated(snapshot.cacheVersion, CACHE_SHIFT) xor
                mixRotated(origin, ORIGIN_SHIFT) xor
                mixRotated(target.asLong(), TARGET_SHIFT) xor
                mixRotated(range.toRawBits(), RANGE_SHIFT) xor
                (kindOrdinal.toLong() shl KIND_SHIFT)
        }

        fun rayKey(snapshot: BlockCache.SnapshotView, source: Long, target: Long, rayIndex: Int, kindOrdinal: Int): Long =
            mix(snapshot.worldEpoch) xor
                mixRotated(snapshot.cacheVersion, CACHE_SHIFT) xor
                mixRotated(source, SOURCE_SHIFT) xor
                mixRotated(target, TARGET_SHIFT) xor
                mixRotated(rayIndex.toLong(), RAY_SHIFT) xor
                (kindOrdinal.toLong() shl KIND_SHIFT)

        private fun quantizedOrigin(eye: Vec3): Long {
            val x = (eye.x * POSITION_SCALE).roundToLong()
            val y = (eye.y * POSITION_SCALE).roundToLong()
            val z = (eye.z * POSITION_SCALE).roundToLong()
            return mix(x) xor mixRotated(y, Y_SHIFT) xor mixRotated(z, Z_SHIFT)
        }


        private const val INITIAL_CAPACITY = 128
        private const val DEFAULT_MAX_AIM_ENTRIES = 16_384
        private const val MAX_RAY_ENTRIES = 262_144
        private const val POSITION_SCALE = 256.0
        private const val CACHE_SHIFT = 11
        private const val ORIGIN_SHIFT = 23
        private const val TARGET_SHIFT = 37
        private const val SOURCE_SHIFT = 7
        private const val RAY_SHIFT = 43
        private const val RANGE_SHIFT = 47
        private const val KIND_SHIFT = 61
        private const val Y_SHIFT = 17
        private const val Z_SHIFT = 31
        private const val MISSING_RAY = Long.MIN_VALUE
        private const val NO_HIT = Long.MIN_VALUE + 1
    }
}

internal object PlannerMemoScope {
    private val current = ThreadLocal<PlannerMemo?>()

    fun current(): PlannerMemo? = current.get()

    fun <T> with(memo: PlannerMemo, block: () -> T): T {
        val previous = current.get()
        current.set(memo)
        return try {
            block()
        } finally {
            current.set(previous)
        }
    }
}
