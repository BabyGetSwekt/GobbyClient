package gobby.pathfinder

import net.minecraft.world.phys.Vec3
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object PathBlacklist {

    private const val BLACKLIST_COST_MULTIPLIER = 10.0

    private val entries = CopyOnWriteArrayList<Entry>()
    private val tickCounter = AtomicLong(0L)

    private data class Entry(val center: Vec3, val radius: Double, val expiryTick: Long) {
        fun isExpired(now: Long): Boolean = now >= expiryTick
        fun contains(x: Double, z: Double): Boolean {
            val dx = x - center.x
            val dz = z - center.z
            return dx * dx + dz * dz <= radius * radius
        }
    }

    fun tick() {
        val now = tickCounter.incrementAndGet()
        entries.removeIf { it.isExpired(now) }
    }

    fun blacklistArea(center: Vec3, radius: Double, durationTicks: Long) {
        entries += Entry(center, radius, tickCounter.get() + durationTicks)
    }

    fun clear() = entries.clear()

    fun size(): Int = entries.size

    fun penaltyAt(x: Double, z: Double): Double {
        if (entries.isEmpty()) return 1.0
        val now = tickCounter.get()
        var multiplier = 1.0
        for (entry in entries) {
            if (entry.isExpired(now)) continue
            if (entry.contains(x, z)) multiplier *= BLACKLIST_COST_MULTIPLIER
        }
        return multiplier
    }
}
