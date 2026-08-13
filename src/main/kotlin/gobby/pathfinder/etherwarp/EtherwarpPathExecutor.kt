package gobby.pathfinder.etherwarp

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.DungeonMap
import gobby.features.dungeons.RoomPathfinder
import gobby.utils.ChatUtils.modMessage
import gobby.utils.findHotbarSlot
import gobby.utils.isHoldingSkyblockItem
import gobby.utils.skyblockID
import gobby.utils.managers.PacketOrderManager
import gobby.utils.managers.SwapManager
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.abs

object EtherwarpPathExecutor {
    private val TELEPORT_ITEMS = arrayOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
    private const val TELEPORT_MIN_SQ = 1.0
    private const val LANDING_TOLERANCE_SQ = 2.25
    private const val LAND_TIMEOUT_TICKS = 15
    private const val MISS_MIN_SQ = 9.0
    private const val LANDING_HEIGHT_TOLERANCE = 1.5
    private const val MAX_REANCHORS = 4
    private const val MIN_SERVER_SLOT = 0
    private const val MAX_SERVER_SLOT = 8

    private var nodes: List<EtherwarpNode> = emptyList()
    private var index = 0
    private var kind = EtherwarpKind.ETHERWARP
    private var finishing = false
    private var predictedPosition: Vec3? = null
    private var pathGeneration = 0L
    private var requestedRoom: String? = null
    private var hopField: EtherwarpHopField.Handle? = null
    private val inFlight = ArrayDeque<EtherwarpExecutionHop>()
    private var tickCounter = 0
    private var lastPos: Vec3? = null
    private var clipboardSet = false
    private var reanchors = 0

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

    fun start(path: List<EtherwarpNode>, kind: EtherwarpKind, targetRoom: String? = null, field: EtherwarpHopField.Handle? = null) {
        cancel()
        RoomPathfinder.missedNode = null
        if (path.size < 2) return
        nodes = path
        this.kind = kind
        hopField = field
        index = 1
        predictedPosition = path.first().eye
        requestedRoom = targetRoom
        modMessage("\u00A7aExecuting ${path.size - 1} ${kind.name.lowercase()} teleports")
    }

    fun cancel() {
        nodes = emptyList()
        index = 0
        finishing = false
        inFlight.clear()
        lastPos = null
        clipboardSet = false
        reanchors = 0
        predictedPosition = null
        pathGeneration++
        requestedRoom = null
        hopField = null
    }

    fun tick() {
        if (nodes.isEmpty()) return
        val player = mc.player ?: return
        observeLandings(player)
        if (nodes.isEmpty()) return
        refreshHopField(player)
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
            val serverSlot = SwapManager.currentServerSlot
            if (serverSlot in MIN_SERVER_SLOT..MAX_SERVER_SLOT && serverSlot != clientSlot) println("[GobbyTP] ITEM DESYNC at cast: clientSlot=$clientSlot serverSlot=$serverSlot held=${player.mainHandItem.skyblockID}")
        }
        val generation = pathGeneration
        PacketOrderManager.queueUseItem(aim.yaw, aim.pitch) { generation == pathGeneration && nodes.isNotEmpty() }
        val target = nodes[index]
        val origin = (if (inFlight.isEmpty()) currentPosition() else null) ?: predictedPosition ?: nodes[index - 1].eye
        inFlight.addLast(EtherwarpExecutionHop(index, target.eye, target.pos, aim, origin, tickCounter, player.lastSentInput?.shift() == true, player.isCrouching, player.onGround()))
        predictedPosition = target.eye
        index++
        if (index !in nodes.indices) finishing = true
    }

    private fun currentPosition(): Vec3? = mc.player?.let { Vec3(it.x, it.y, it.z) }

    private fun reanchor(from: Vec3): Boolean {
        if (kind != EtherwarpKind.ETHERWARP || reanchors >= MAX_REANCHORS) return false
        val eye = Vec3(from.x, from.y + kind.eyeHeight(), from.z)
        val range = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: kind.defaultRange
        hopField?.query(from, range)?.takeIf { it.size >= 2 }?.let {
            inFlight.clear()
            reanchors++
            nodes = it
            index = 1
            predictedPosition = from
            finishing = false
            return true
        }
        val access = EtherwarpUtils.liveOrCachedAccess() ?: return false
        val target = (nodes.size - 1 downTo 1).firstOrNull { EtherwarpUtils.quickAim(nodes[it].pos, eye, range, access) != null } ?: return false
        if (RoomPathfinder.pathDebug) println("[GobbyTP] reanchored to node#$target from ${fmt(from)} (was #$index of ${nodes.size - 1})")
        inFlight.clear()
        reanchors++
        index = target
        predictedPosition = from
        finishing = false
        return true
    }

    private fun refreshHopField(player: LocalPlayer) {
        val field = hopField ?: return
        val refreshed = EtherwarpHopField.refresh(field)
        hopField = refreshed
        if (refreshed !== field) return
        if (inFlight.isNotEmpty() || finishing) return
        val range = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: kind.defaultRange
        field.query(Vec3(player.x, player.y, player.z), range)?.takeIf { it.size >= 2 }?.let {
            nodes = it
            index = 1
            predictedPosition = it.first().eye
            reanchors = 0
        }
    }

    private fun liveAim(): Aim? {
        val target = nodes.getOrNull(index) ?: return null
        val source = nodes[index - 1]
        val origin = (if (inFlight.isEmpty()) currentPosition() else null) ?: predictedPosition ?: source.eye
        val eye = Vec3(origin.x, origin.y + kind.eyeHeight(), origin.z)
        val range = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: kind.defaultRange
        val access = EtherwarpUtils.liveOrCachedAccess() ?: return null
        if (kind == EtherwarpKind.ETHERWARP) {
            val storedAim = Pair(source.yaw, source.pitch)
            return EtherwarpUtils.validateAim(eye, target.pos, range, storedAim, access)
                .takeIf { it != EtherwarpUtils.EtherPos.NONE }
                ?.let { Aim(storedAim.first, storedAim.second) }
                ?: EtherwarpUtils.aimForBlock(target.pos, eye, range, access)?.let { Aim(it.first, it.second) }
        }
        return kind.aimAt(eye, target.pos, range, cached = false)
    }

    private fun observeLandings(player: LocalPlayer) {
        tickCounter++
        val cur = Vec3(player.x, player.y, player.z)
        val previous = lastPos
        lastPos = cur
        if (inFlight.isEmpty()) return
        if (previous != null) {
            val delta = cur.subtract(previous)
            val movedSq = delta.lengthSqr()
            if (movedSq > TELEPORT_MIN_SQ) {
                val matched = inFlight.indexOfFirst { atExpected(cur, it) }
                if (matched >= 0) {
                    repeat(matched) { println("[GobbyTP] hop#${inFlight.removeFirst().label} UNOBSERVED") }
                    if (!logLanding(inFlight.removeFirst(), cur) && !reanchor(cur)) return cancel()
                } else if (movedSq >= MISS_MIN_SQ) {
                    logMiss(inFlight.first(), cur)
                    if (!reanchor(cur)) return cancel()
                }
            }
        }
        if (inFlight.isNotEmpty() && tickCounter - inFlight.first().firedTick > LAND_TIMEOUT_TICKS) {
            logDrop(inFlight.removeFirst())
            if (!reanchor(currentPosition() ?: cur)) cancel()
        }
    }

    private fun atExpected(landing: Vec3, hop: EtherwarpExecutionHop): Boolean {
        val delta = landing.subtract(hop.expected)
        return delta.x * delta.x + delta.z * delta.z < LANDING_TOLERANCE_SQ && abs(delta.y) < LANDING_HEIGHT_TOLERANCE
    }

    private fun logLanding(hop: EtherwarpExecutionHop, landing: Vec3): Boolean {
        if (!atExpected(landing, hop)) {
            logMiss(hop, landing)
            return false
        }
        println("[GobbyTP] hop#${hop.label} landed OK expected=${fmt(hop.expected)} actual=${fmt(landing)} room='${roomNameAt(landing)}' localSneakSent=${hop.sneakSent} crouch=${hop.crouching}")
        return true
    }

    private fun logMiss(hop: EtherwarpExecutionHop, landing: Vec3) {
        clipboardSet = EtherwarpExecutionDiagnostics.logMiss(hop, landing, kind, clipboardSet)
    }

    private fun logDrop(hop: EtherwarpExecutionHop) = EtherwarpExecutionDiagnostics.logDrop(hop, kind)

    private fun f2(value: Number): String = String.format(Locale.US, "%.2f", value.toDouble())

    private fun fmt(value: Vec3): String = "(${f2(value.x)},${f2(value.y)},${f2(value.z)})"

    private fun finish() {
        val actualRoom = mc.player?.let { roomNameAt(Vec3(it.x, it.y, it.z)) }
        println("[GobbyTP] arrived requested='$requestedRoom' actual='$actualRoom' pos=${mc.player?.position()}")
        modMessage("\u00A7aArrived")
        cancel()
    }

    private fun roomNameAt(pos: Vec3): String {
        val cell = DungeonRooms.roomCellAt(DungeonMap.grid, pos.x, pos.z) ?: return "unknown"
        return (DungeonMap.grid.getOrNull(cell) as? MapTile.Room)?.data?.name ?: "cell-$cell"
    }

    private fun abort(reason: String) {
        if (RoomPathfinder.pathDebug) {
            val player = mc.player
            println("[GobbyTP] abort ($reason): held=${player?.mainHandItem?.skyblockID} selectedSlot=${player?.inventory?.selectedSlot} idx=$index/${nodes.size}")
        }
        modMessage("\u00A7c$reason, stopping")
        cancel()
    }
}
