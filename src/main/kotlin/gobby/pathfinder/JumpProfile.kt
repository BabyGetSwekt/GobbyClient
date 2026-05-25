package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.world.BlockCache
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.floor
import kotlin.math.max

data class JumpProfile(
    val jumpVelocity: Double,
    val jumpHeight: Double,
    val stepHeight: Double,
    val maxClimb: Double,
    val maxHorizontalBlocks: Double,
    val maxSkipCells: Int
) {
    companion object {
        private const val DEFAULT_JUMP_VELOCITY = 0.42
        private const val GRAVITY = 0.08
        private const val DRAG = 0.98
        private const val MAX_SIM_TICKS = 80

        fun current(player: LocalPlayer? = mc.player): JumpProfile {
            val jumpVelocity = player?.getAttributeValue(Attributes.JUMP_STRENGTH) ?: DEFAULT_JUMP_VELOCITY
            val stepHeight = (player?.getAttributeValue(Attributes.STEP_HEIGHT) ?: BlockCache.STEP_HEIGHT)
                .coerceAtLeast(BlockCache.STEP_HEIGHT)
            val jumpHeight = simulateJumpHeight(jumpVelocity)
            val maxClimb = max(stepHeight, jumpHeight).coerceAtLeast(BlockCache.MAX_JUMP_RISE)
            val skyblockSpeed = (player?.abilities?.walkingSpeed ?: 0.1f) * 1000.0
            val horizontal = if (maxClimb <= BlockCache.MAX_JUMP_RISE + 0.1 && skyblockSpeed <= 150.0) {
                1.45
            } else {
                (1.2 + maxClimb * 0.9 + max(0.0, skyblockSpeed - 100.0) / 220.0).coerceIn(1.45, 4.0)
            }
            return JumpProfile(
                jumpVelocity = jumpVelocity,
                jumpHeight = jumpHeight,
                stepHeight = stepHeight,
                maxClimb = maxClimb,
                maxHorizontalBlocks = horizontal,
                maxSkipCells = floor(horizontal).toInt().coerceIn(1, 4)
            )
        }

        private fun simulateJumpHeight(initialVelocity: Double): Double {
            var velocity = initialVelocity
            var height = 0.0
            var best = 0.0
            var ticks = 0
            while (velocity > 0.0 && ticks++ < MAX_SIM_TICKS) {
                height += velocity
                if (height > best) best = height
                velocity = (velocity - GRAVITY) * DRAG
            }
            return best
        }
    }
}
