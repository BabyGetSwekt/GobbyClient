package gobby.pathfinder.etherwarp

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.DungeonMap
import gobby.features.dungeons.RoomPathfinder
import gobby.utils.ChatUtils.modMessage
import gobby.utils.findHotbarSlot
import gobby.utils.isHoldingSkyblockItem
import gobby.utils.skyblockID
import gobby.utils.Utils
import gobby.utils.managers.PacketOrderManager
import gobby.utils.managers.SwapManager
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.Locale

object EtherwarpPathExecutor {

    private val TELEPORT_ITEMS = arrayOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
    private const val TELEPORT_MIN_SQ = 1.0
    private const val LANDING_TOLERANCE_SQ = 2.25
    private const val LAND_TIMEOUT_TICKS = 15

    private var nodes: List<EtherwarpNode> = emptyList()
    private var index = 0
    private var kind = EtherwarpKind.ETHERWARP
    private var finishing = false
    private var predictedPosition: Vec3? = null
    private var pathGeneration = 0L
    private var requestedRoom: String? = null

    private class Hop(val label: Int, val expected: Vec3, val block: BlockPos, val aim: Aim, val from: Vec3, val firedTick: Int, val sneakSent: Boolean, val crouching: Boolean, val onGround: Boolean)
    private val inFlight = ArrayDeque<Hop>()
    private var tickCounter = 0
    private var lastPos: Vec3? = null
    private var clipboardSet = false

    val active: Boolean get() = index in nodes.indices
    val forceSneak: Boolean get() = nodes.isNotEmpty() && kind.sneak

    enum class Decision { WAIT, SWAP, CAST, CANCEL }

    internal fun decide(holdingItem: Boolean, swapPossible: Boolean, sneakReady: Boolean, aimValid: Boolean): Decision = when {
        !holdingItem && !swapPossible -> Decision.CANCEL
        !holdingItem -> Decision.SWAP
        !sneakReady -> Decision.WAIT
        !aimValid -> Decision.CANCEL
        else -> Decision.CAST
    }

    fun start(path: List<EtherwarpNode>, kind: EtherwarpKind, targetRoom: String? = null) {
        cancel()
        RoomPathfinder.missedNode = null
        if (path.size < 2) return
        nodes = path
        this.kind = kind
        index = 1
        predictedPosition = path.first().eye
        requestedRoom = targetRoom
        modMessage("§aExecuting ${path.size - 1} ${kind.name.lowercase()} teleports")
    }

    fun cancel() {
        nodes = emptyList()
        index = 0
        finishing = false
        inFlight.clear()
        lastPos = null
        clipboardSet = false
        predictedPosition = null
        pathGeneration++
        requestedRoom = null
    }

    fun tick() {
        if (nodes.isEmpty()) return
        val player = mc.player ?: return
        observeLandings(player)
        if (nodes.isEmpty()) return
        if (finishing) {
            if (inFlight.isEmpty()) finish()
            return
        }
        val holding = isHoldingSkyblockItem(*TELEPORT_ITEMS)
        val sneakReady = player.lastSentInput?.shift() == kind.sneak && (!kind.sneak || player.isCrouching)
        val aim = if (holding && sneakReady) liveAim() else null
        when (decide(holding, findHotbarSlot(*TELEPORT_ITEMS) >= 0, sneakReady, aim != null)) {
            Decision.SWAP -> SwapManager.swapToItem(*TELEPORT_ITEMS)
            Decision.CAST -> aim?.let { cast(player, it) }
            Decision.CANCEL -> abort(if (!holding) "No Aspect of the Void/End found" else "Next node not aimable live (occluded)")
            Decision.WAIT -> Unit
        }
    }

    private fun cast(player: LocalPlayer, aim: Aim) {
        if (RoomPathfinder.pathDebug) {
            val clientSlot = player.inventory.selectedSlot
            val srv = SwapManager.currentServerSlot
            if (srv in 0..8 && srv != clientSlot) println("[GobbyTP] ITEM DESYNC at cast: clientSlot=$clientSlot serverSlot=$srv held=${player.mainHandItem.skyblockID}")
        }
        val generation = pathGeneration
        PacketOrderManager.queueUseItem(aim.yaw, aim.pitch) {
            generation == pathGeneration && nodes.isNotEmpty()
        }
        val target = nodes[index]
        val origin = predictedPosition ?: nodes[index - 1].eye
        inFlight.addLast(Hop(index, target.eye, target.pos, aim, origin, tickCounter,
            player.lastSentInput?.shift() == true, player.isCrouching, player.onGround()))
        predictedPosition = target.eye
        index++
        if (index !in nodes.indices) finishing = true
    }

    private fun liveAim(): Aim? {
        val target = nodes.getOrNull(index) ?: return null
        val source = nodes[index - 1]
        val origin = predictedPosition ?: source.eye
        val eye = Vec3(origin.x, origin.y + kind.eyeHeight(), origin.z)
        val range = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: kind.defaultRange
        if (kind == EtherwarpKind.ETHERWARP) {
            val storedAim = Pair(source.yaw, source.pitch)
            return EtherwarpUtils.validateAim(eye, target.pos, range, storedAim)
                .takeIf { it != EtherwarpUtils.EtherPos.NONE }
                ?.let { Aim(storedAim.first, storedAim.second) }
                ?: EtherwarpUtils.aimForBlock(target.pos, eye, range, cached = false)?.let { Aim(it.first, it.second) }
        }
        return kind.aimAt(eye, target.pos, range, cached = false)
    }

    private fun observeLandings(player: LocalPlayer) {
        tickCounter++
        val cur = Vec3(player.x, player.y, player.z)
        val prev = lastPos
        lastPos = cur
        if (inFlight.isEmpty()) return
        if (prev != null) {
            val dx = cur.x - prev.x
            val dy = cur.y - prev.y
            val dz = cur.z - prev.z
            if (dx * dx + dy * dy + dz * dz > TELEPORT_MIN_SQ) {
                val matched = inFlight.indexOfFirst { hop ->
                    val x = cur.x - hop.expected.x
                    val z = cur.z - hop.expected.z
                    x * x + z * z < LANDING_TOLERANCE_SQ
                }
                if (matched < 0) {
                    logMiss(inFlight.first(), cur)
                    cancel()
                    return
                }
                repeat(matched) { inFlight.removeFirst() }
                val hop = inFlight.removeFirst()
                if (!logLanding(hop, cur)) {
                    cancel()
                    return
                }
            }
        }
        if (inFlight.isNotEmpty() && tickCounter - inFlight.first().firedTick > LAND_TIMEOUT_TICKS) {
            logDrop(inFlight.removeFirst())
            cancel()
        }
    }

    private fun logLanding(hop: Hop, landing: Vec3): Boolean {
        val dx = landing.x - hop.expected.x
        val dz = landing.z - hop.expected.z
        if (dx * dx + dz * dz < LANDING_TOLERANCE_SQ)
            println("[GobbyTP] hop#${hop.label} landed OK expected=${fmt(hop.expected)} actual=${fmt(landing)} room='${roomNameAt(landing)}' localSneakSent=${hop.sneakSent} crouch=${hop.crouching}")
        else logMiss(hop, landing)
        return dx * dx + dz * dz < LANDING_TOLERANCE_SQ
    }

    private fun logMiss(hop: Hop, landing: Vec3) {
        val cmd = "/gobby setrotation ${f2(hop.aim.yaw)} ${f2(hop.aim.pitch)}"
        val moved = hop.from.distanceTo(landing)
        println("[GobbyTP] hop#${hop.label} MISS did not reach node")
        println("[GobbyTP]  node=${hop.block} expected=${fmt(hop.expected)} actualLanding=${fmt(landing)} movedFromOrigin=${f2(moved)}")
        println("[GobbyTP]  fireOrigin=${fmt(hop.from)} attemptedAim=(${f2(hop.aim.yaw)},${f2(hop.aim.pitch)}) eyeHeight=${kind.eyeHeight()}")
        println("[GobbyTP]  sneakSent=${hop.sneakSent} crouching=${hop.crouching} onGround=${hop.onGround}")
        if (kind == EtherwarpKind.ETHERWARP) {
            val firedEye = Vec3(hop.from.x, hop.from.y + kind.eyeHeight(), hop.from.z)
            val range = EtherwarpUtils.currentRange()
            val dir = EtherwarpUtils.directionFromAngles(hop.aim.yaw, hop.aim.pitch)
            val storedHit = EtherwarpUtils.etherwarpRaycast(firedEye, dir.scale(range))
            val liveAim = EtherwarpUtils.aimForBlock(hop.block, firedEye)
            println("[GobbyTP]  attemptedAimHits=${storedHit.pos} succeeded=${storedHit.succeeded} liveAimForBlock=$liveAim")
            val struck = BlockPos.containing(landing.x - 0.5, landing.y - kind.landingY(0), landing.z - 0.5)
            println("[GobbyTP]  serverStruck=$struck ${EtherwarpUtils.stateComparison(struck)} (client aimed ${storedHit.pos})")
            println("[GobbyTP]  interpretation: ${if (storedHit.succeeded && storedHit.pos == hop.block) "aim is geometrically correct -> failure is sneak/cooldown (server did transmission or rejected)" else "aim does NOT hit target from fireOrigin -> aim/position computation is wrong"}")
        }
        if (!clipboardSet) {
            Utils.setClipboard(cmd)
            RoomPathfinder.missedNode = hop.from
            clipboardSet = true
            modMessage("§c[TP] hop#${hop.label} missed — stand on the red node (fire origin) and run §e$cmd§c§l (copied)")
        } else modMessage("§c[TP] hop#${hop.label} missed its node, see log")
    }

    private fun logDrop(hop: Hop) {
        val player = mc.player
        println("[GobbyTP] hop#${hop.label} DROP no teleport within ${LAND_TIMEOUT_TICKS}t")
        println("[GobbyTP]  node=${hop.block} fireOrigin=${fmt(hop.from)} aim=(${f2(hop.aim.yaw)},${f2(hop.aim.pitch)}) sneakSent=${hop.sneakSent} crouching=${hop.crouching} onGround=${hop.onGround}")
        println("[GobbyTP]  nowCrouch=${player?.isCrouching} nowShift=${player?.lastSentInput?.shift()} canUseAbility=${SwapManager.canUseAbility} clientSlot=${player?.inventory?.selectedSlot} serverSlot=${SwapManager.currentServerSlot} held=${player?.mainHandItem?.skyblockID}")
        if (kind == EtherwarpKind.ETHERWARP) {
            val firedEye = Vec3(hop.from.x, hop.from.y + kind.eyeHeight(), hop.from.z)
            val range = EtherwarpUtils.currentRange()
            val dir = EtherwarpUtils.directionFromAngles(hop.aim.yaw, hop.aim.pitch)
            val storedHit = EtherwarpUtils.etherwarpRaycast(firedEye, dir.scale(range))
            println("[GobbyTP]  aimFromOriginHits=${storedHit.pos} succeeded=${storedHit.succeeded} (true=aim valid live -> sneak/cooldown; false=position/prediction off)")
            storedHit.pos?.let { println("[GobbyTP]  occluder $it ${EtherwarpUtils.stateComparison(it)}") }
        }
        modMessage("§c[TP] hop#${hop.label} dropped (no teleport), see log")
    }

    private fun f2(v: Number): String = String.format(Locale.US, "%.2f", v.toDouble())

    private fun fmt(v: Vec3): String = "(${f2(v.x)},${f2(v.y)},${f2(v.z)})"

    private fun finish() {
        val actualRoom = mc.player?.let { roomNameAt(Vec3(it.x, it.y, it.z)) }
        println("[GobbyTP] arrived requested='$requestedRoom' actual='$actualRoom' pos=${mc.player?.position()}")
        modMessage("§aArrived")
        cancel()
    }

    private fun roomNameAt(pos: Vec3): String {
        val cell = DungeonRooms.roomCellAt(DungeonMap.grid, pos.x, pos.z) ?: return "unknown"
        return (DungeonMap.grid.getOrNull(cell) as? MapTile.Room)?.data?.name ?: "cell-$cell"
    }

    private fun abort(reason: String) {
        if (RoomPathfinder.pathDebug) {
            val p = mc.player
            println("[GobbyTP] abort ($reason): held=${p?.mainHandItem?.skyblockID} selectedSlot=${p?.inventory?.selectedSlot} idx=$index/${nodes.size}")
        }
        modMessage("§c$reason, stopping")
        cancel()
    }
}
