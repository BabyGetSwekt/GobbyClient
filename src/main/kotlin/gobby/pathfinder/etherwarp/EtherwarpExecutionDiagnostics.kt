package gobby.pathfinder.etherwarp

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.RoomPathfinder
import gobby.utils.ChatUtils.modMessage
import gobby.utils.Utils
import gobby.utils.skyblockID
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.Locale

internal object EtherwarpExecutionDiagnostics {
    private const val BASE_BLOCK_Y = 0
    private const val LAND_TIMEOUT_TICKS = 15
    private const val BLOCK_CENTER_OFFSET = 0.5

    fun logMiss(hop: EtherwarpExecutionHop, landing: Vec3, kind: EtherwarpKind, clipboardSet: Boolean): Boolean {
        val cmd = "/gobby setrotation ${f2(hop.aim.yaw)} ${f2(hop.aim.pitch)}"
        println("[GobbyTP] hop#${hop.label} MISS did not reach node")
        println("[GobbyTP]  node=${hop.block} expected=${fmt(hop.expected)} actualLanding=${fmt(landing)} movedFromOrigin=${f2(hop.from.distanceTo(landing))}")
        println("[GobbyTP]  fireOrigin=${fmt(hop.from)} attemptedAim=(${f2(hop.aim.yaw)},${f2(hop.aim.pitch)}) eyeHeight=${kind.eyeHeight()}")
        println("[GobbyTP]  sneakSent=${hop.sneakSent} crouch=${hop.crouching} onGround=${hop.onGround}")
        logLiveAim(hop, landing, kind)
        if (clipboardSet) {
            modMessage("\u00A7c[TP] hop#${hop.label} missed its node, see log")
            return true
        }
        Utils.setClipboard(cmd)
        RoomPathfinder.missedNode = hop.from
        modMessage("\u00A7c[TP] hop#${hop.label} missed, stand on the red node (fire origin) and run \u00A7e${cmd}\u00A7c\u00A7l (copied)")
        return true
    }

    fun logDrop(hop: EtherwarpExecutionHop, kind: EtherwarpKind) {
        val player = mc.player
        println("[GobbyTP] hop#${hop.label} DROP no teleport within ${LAND_TIMEOUT_TICKS}t")
        println("[GobbyTP]  node=${hop.block} fireOrigin=${fmt(hop.from)} aim=(${f2(hop.aim.yaw)},${f2(hop.aim.pitch)}) sneakSent=${hop.sneakSent} crouch=${hop.crouching} onGround=${hop.onGround}")
        println("[GobbyTP]  nowCrouch=${player?.isCrouching} nowShift=${player?.lastSentInput?.shift()} canUseAbility=${gobby.utils.managers.SwapManager.canUseAbility} clientSlot=${player?.inventory?.selectedSlot} serverSlot=${gobby.utils.managers.SwapManager.currentServerSlot} held=${player?.mainHandItem?.skyblockID}")
        logLiveAim(hop, null, kind)
        modMessage("\u00A7c[TP] hop#${hop.label} dropped (no teleport), see log")
    }

    private fun logLiveAim(hop: EtherwarpExecutionHop, landing: Vec3?, kind: EtherwarpKind) {
        if (kind != EtherwarpKind.ETHERWARP) return
        val firedEye = Vec3(hop.from.x, hop.from.y + kind.eyeHeight(), hop.from.z)
        val range = EtherwarpUtils.currentRange()
        val direction = EtherwarpUtils.directionFromAngles(hop.aim.yaw, hop.aim.pitch)
        val storedHit = EtherwarpUtils.etherwarpRaycast(firedEye, direction.scale(range))
        println("[GobbyTP]  aimFromOriginHits=${storedHit.pos} succeeded=${storedHit.succeeded}")
        landing?.let {
            val liveAim = EtherwarpUtils.aimForBlock(hop.block, firedEye)
            val struck = BlockPos.containing(it.x - BLOCK_CENTER_OFFSET, it.y - kind.landingY(BASE_BLOCK_Y), it.z - BLOCK_CENTER_OFFSET)
            println("[GobbyTP]  liveAimForBlock=$liveAim serverStruck=$struck ${EtherwarpUtils.stateComparison(struck)}")
        }
    }

    private fun f2(value: Number): String = String.format(Locale.US, "%.2f", value.toDouble())

    private fun fmt(value: Vec3): String = "(${f2(value.x)},${f2(value.y)},${f2(value.z)})"
}
