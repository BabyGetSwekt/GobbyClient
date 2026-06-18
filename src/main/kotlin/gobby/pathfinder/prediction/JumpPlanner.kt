package gobby.pathfinder.prediction

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.JumpProfile
import gobby.pathfinder.world.BlockCache
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

enum class JumpDecision { JUMP, WAIT, BRAKE }

data class JumpPlan(val decision: JumpDecision, val simulation: JumpSimulation?)

object JumpPlanner {

    private const val BLOCK_HALF = 0.5
    private const val LATERAL_MOTION_LIMIT = 0.12

    private val CELL_REACH = BLOCK_HALF + BlockCache.PLAYER_HALF_WIDTH

    fun decide(player: LocalPlayer, target: Vec3, jumpProfile: JumpProfile, strict: Boolean): JumpPlan {
        val pos = player.position()
        val dx = target.x - pos.x
        val dz = target.z - pos.z
        val planar = sqrt(dx * dx + dz * dz)
        if (planar < 1.0E-4) return JumpPlan(JumpDecision.JUMP, null)

        val sim = simulate(player, dx, dz, jumpProfile)
        if (planar <= CELL_REACH) return JumpPlan(JumpDecision.JUMP, sim)

        val landing = sim.landing ?: return JumpPlan(JumpDecision.WAIT, sim)
        val reachesHeight = landing.y >= target.y - jumpProfile.stepHeight

        if (!strict) {
            return if (reachesHeight) JumpPlan(JumpDecision.JUMP, sim) else JumpPlan(JumpDecision.WAIT, sim)
        }

        val vel = player.deltaMovement
        val dirX = dx / planar
        val dirZ = dz / planar
        val lateralMotion = abs(vel.x * -dirZ + vel.z * dirX)
        val landDx = landing.x - target.x
        val landDz = landing.z - target.z
        val landPlanar = sqrt(landDx * landDx + landDz * landDz)
        val alongLanding = (landing.x - pos.x) * dirX + (landing.z - pos.z) * dirZ
        val overshoots = alongLanding > planar + CELL_REACH

        return when {
            reachesHeight && landPlanar <= CELL_REACH && lateralMotion <= LATERAL_MOTION_LIMIT -> JumpPlan(JumpDecision.JUMP, sim)
            overshoots -> JumpPlan(JumpDecision.BRAKE, sim)
            else -> JumpPlan(JumpDecision.WAIT, sim)
        }
    }

    fun diagnostics(player: LocalPlayer, target: Vec3, jumpProfile: JumpProfile, strict: Boolean): String {
        val pos = player.position()
        val dx = target.x - pos.x
        val dz = target.z - pos.z
        val planar = sqrt(dx * dx + dz * dz)
        if (planar < 1.0E-4) return "planar=0"
        val sim = simulate(player, dx, dz, jumpProfile)
        val vel = player.deltaMovement
        val dirX = dx / planar
        val dirZ = dz / planar
        val forwardMotion = vel.x * dirX + vel.z * dirZ
        val lateralMotion = abs(vel.x * -dirZ + vel.z * dirX)
        return "strict=$strict planar=%.2f fwd=%.3f lat=%.3f apex=%.2f targetY=%.2f chained=%s landing=%s"
            .format(
                planar, forwardMotion, lateralMotion, sim.apexY, target.y, sim.autoJumpChained,
                sim.landing?.let { PredictionLogger.fmt(it) } ?: "none"
            )
    }

    private fun simulate(player: LocalPlayer, headingX: Double, headingZ: Double, jumpProfile: JumpProfile): JumpSimulation =
        MovementSimulator.simulateJump(
            player.position(),
            player.deltaMovement,
            headingX,
            headingZ,
            player.isSprinting,
            jumpProfile.jumpVelocity,
            player.getAttributeValue(Attributes.MOVEMENT_SPEED),
            mc.options.autoJump().get(),
            jumpProfile.maxClimb
        )
}
