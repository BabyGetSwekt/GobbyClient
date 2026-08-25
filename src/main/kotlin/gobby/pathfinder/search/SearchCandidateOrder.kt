package gobby.pathfinder.search

import gobby.pathfinder.etherwarp.RayCone
import gobby.pathfinder.etherwarp.Raycasts

internal object SearchCandidateOrder {
    inline fun visitLegacy(
        rays: Raycasts,
        goalYaw: Double,
        goalPitch: Double,
        cone: Double,
        backYaw: Double?,
        backPitch: Double?,
        backtrackCone: Double,
        visitor: (Int) -> Unit
    ) {
        for (band in rays.bandStart.indices) {
            visitLegacyBand(rays, band, goalYaw, goalPitch, cone, backYaw, backPitch, backtrackCone, visitor)
        }
    }

    inline fun visitFast(
        rays: Raycasts,
        goalYaw: Double,
        goalPitch: Double,
        cone: Double,
        seen: IntGenerationSet,
        visitor: (Int) -> Unit
    ) {
        seen.next(rays.size)
        for (band in rays.bandStart.indices) {
            visitBand(rays, band, goalYaw, goalPitch, cone) { index ->
                if (seen.add(index)) visitor(index)
            }
        }
    }

    fun ordered(rays: Raycasts, goalYaw: Double, goalPitch: Double, cone: Double): SearchCandidateSequence {
        return ordered(rays, goalYaw, goalPitch, cone, IntGenerationSet(rays.size))
    }

    fun ordered(rays: Raycasts, goalYaw: Double, goalPitch: Double, cone: Double, seen: IntGenerationSet): SearchCandidateSequence {
        val ordered = IntArray(rays.size)
        var size = 0
        visitFast(rays, goalYaw, goalPitch, cone, seen) { index -> ordered[size++] = index }
        for (index in 0 until rays.size) if (seen.add(index)) ordered[size++] = index
        return SearchCandidateSequence(ordered)
    }

    private inline fun visitBand(
        rays: Raycasts,
        band: Int,
        goalYaw: Double,
        goalPitch: Double,
        cone: Double,
        visitor: (Int) -> Unit
    ) {
        val slots = rays.bandSize[band]
        val step = rays.bandYawStep[band]
        val first = rays.bandStart[band]
        val center = RayCone.slotOf(goalYaw, step, slots)
        val span = span(rays, band, goalPitch, cone)
        if (span >= 0) {
            visitor(first + center)
            val limit = minOf(span, slots)
            var distance = 1
            while (distance <= limit) {
                visitor(first + wrap(center + distance, slots))
                visitor(first + wrap(center - distance, slots))
                distance++
            }
        }
        var slot = band % COARSE_STRIDE
        while (slot < slots) {
            if (!RayCone.contains(slot, center, span, slots)) visitor(first + slot)
            slot += COARSE_STRIDE
        }
    }

    private inline fun visitLegacyBand(
        rays: Raycasts,
        band: Int,
        goalYaw: Double,
        goalPitch: Double,
        cone: Double,
        backYaw: Double?,
        backPitch: Double?,
        backtrackCone: Double,
        visitor: (Int) -> Unit
    ) {
        val slots = rays.bandSize[band]
        val step = rays.bandYawStep[band]
        val first = rays.bandStart[band]
        val center = RayCone.slotOf(goalYaw, step, slots)
        val inner = span(rays, band, goalPitch, cone)
        val middle = span(rays, band, goalPitch, (cone * MID_CONE_FACTOR).coerceAtMost(RayCone.SPHERE_DEG))
        val backCenter = backYaw?.let { RayCone.slotOf(it, step, slots) } ?: 0
        val backSpan = if (backYaw == null || backPitch == null) -1 else span(rays, band, backPitch, backtrackCone)
        var offset = -inner
        while (offset <= inner) {
            val slot = wrap(center + offset, slots)
            if (!RayCone.contains(slot, backCenter, backSpan, slots)) visitor(first + slot)
            offset++
        }
        offset = -middle
        while (offset <= middle) {
            if (offset < -inner || offset > inner) {
                val slot = wrap(center + offset, slots)
                if (!RayCone.contains(slot, backCenter, backSpan, slots)) visitor(first + slot)
            }
            offset += MID_STRIDE
        }
        var slot = band % COARSE_STRIDE
        while (slot < slots) {
            if (!RayCone.contains(slot, center, middle, slots) && !RayCone.contains(slot, backCenter, backSpan, slots)) visitor(first + slot)
            slot += COARSE_STRIDE
        }
    }

    private fun span(rays: Raycasts, band: Int, targetPitch: Double, cone: Double): Int =
        RayCone.yawHalfWidth(rays.bandPitch[band], targetPitch, cone)
            .takeIf { it != RayCone.OUTSIDE }
            ?.let { RayCone.spanOf(it, rays.bandYawStep[band], rays.bandSize[band]) }
            ?: -1

    private fun wrap(value: Int, size: Int): Int = ((value % size) + size) % size

    private const val MID_CONE_FACTOR = 2.0
    private const val MID_STRIDE = 2
    private const val COARSE_STRIDE = 3
}
