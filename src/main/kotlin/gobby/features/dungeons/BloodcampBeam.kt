package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.etherwarp.EtherwarpRaycaster
import gobby.utils.render.Interpolate
import gobby.utils.rotation.AngleUtils.calcAimAngles
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

private const val AIM_TOLERANCE_DEGREES = 14.0f

internal object BloodcampBeam {

    fun onTarget(point: Vec3): Boolean {
        val player = mc.player ?: return false
        val (yaw, pitch) = calcAimAngles(point) ?: return false
        return abs(Mth.wrapDegrees(player.yRot - yaw)) <= AIM_TOLERANCE_DEGREES &&
            abs(player.xRot - pitch) <= AIM_TOLERANCE_DEGREES &&
            reaches(point)
    }

    fun describe(point: Vec3): String {
        val player = mc.player ?: return "no player"
        val (yaw, pitch) = calcAimAngles(point) ?: return "no angles"
        val dYaw = abs(Mth.wrapDegrees(player.yRot - yaw))
        val dPitch = abs(player.xRot - pitch)
        return "dYaw=%.1f dPitch=%.1f los=%s".format(dYaw, dPitch, reaches(point))
    }

    private fun reaches(point: Vec3): Boolean {
        val eye = Interpolate.interpolatedEyePos()
        return EtherwarpRaycaster.transmission(eye, point.subtract(eye))?.equals(BlockPos.containing(point)) != false
    }
}
