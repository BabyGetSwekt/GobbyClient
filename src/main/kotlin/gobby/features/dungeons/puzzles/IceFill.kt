package gobby.features.dungeons.puzzles

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.PacketSentEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.timer.Clock
import gobby.utils.PlayerUtils
import gobby.utils.isHoldingAOTV
import gobby.utils.managers.PacketOrderManager
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.rotation.AngleUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.tiles.Room
import net.minecraft.world.level.block.Blocks
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.concurrent.thread
import kotlin.math.sign

object IceFill : Module("Ice Fill", "Solves (and auto-completes) Ice Fill puzzle in F7", Category.DUNGEONS) {

    private val solver by BooleanSetting("Puzzle Solver", true, desc = "Draw the solution path")
    private val autoIceFill by BooleanSetting("Auto Ice Fill", false, desc = "Automatically teleports through the path. Start by Instant Transmissioning onto the first ice block of a floor.")
    private val rotateMode by SelectorSetting("Rotation", 0, listOf("No Rotate", "Rotate"), desc = "No Rotate: spoofs rotation server-side only (view stays put)\nRotate: also rotates the client view")
        .withDependency { autoIceFill }

    private enum class Floor(val y: Int, val xMin: Int, val xMax: Int, val zMin: Int, val zMax: Int, val start: BlockPos, val exit: BlockPos, val color: Color) {
        F1(70, 14, 16, 7, 10, BlockPos(15, 70, 7), BlockPos(15, 70, 10), Color(255, 50, 50)),
        F2(71, 13, 17, 12, 17, BlockPos(15, 71, 12), BlockPos(15, 71, 17), Color(50, 255, 50)),
        F3(72, 12, 18, 19, 26, BlockPos(15, 72, 19), BlockPos(15, 72, 26), Color(50, 100, 255));
        val width get() = xMax - xMin + 1
        fun bit(x: Int, z: Int) = 1L shl ((z - zMin) * width + (x - xMin))
    }

    private const val PITCH_UP = 14f
    private const val PITCH_DOWN = 48f
    private const val POSITION_MATCH_EPSILON_SQ = 1e-6

    private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private data class Move(val dir: Pair<Int, Int>, val bit: Long, val runLength: Int)

    private var path: List<Vec3>? = null
    private var lastFiredIndex = -1
    private var cancelNextMovement = false
    @Volatile private var solving = false

    private fun reset() { path = null; lastFiredIndex = -1; cancelNextMovement = false; solving = false }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (enabled && autoIceFill && path != null && event.message == "There are blocks in the way!") event.cancel()
    }

    private fun colorAt(y: Double): Color = Floor.entries.first { y < it.y + 0.5 }.color
    private fun floorOf(y: Double): Floor? = Floor.entries.firstOrNull { it.y == y.toInt() }

    private fun isFloorDone(floor: Floor): Boolean {
        val world = mc.level ?: return false
        val cells = path?.filter { it.y.toInt() == floor.y }.orEmpty()
        return cells.isNotEmpty() && cells.all {
            world.getBlockAtPos(BlockPos(it.x.toInt(), it.y.toInt() - 1, it.z.toInt())) == Blocks.PACKED_ICE
        }
    }

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        val room = event.room ?: return
        if (room.data.name != "Ice Fill") { reset(); return }
        if (solving) return

        val world = mc.level ?: return
        val masks = Floor.entries.map { it to buildIceMask(it, room, world) }
        solving = true
        val timer = Clock()
        thread(name = "GobbyClient-IceFill", isDaemon = true) {
            try {
                val solved = masks.map { (floor, mask) -> floor to solveFloor(floor, mask) }
                val combined = solved.flatMap { (floor, nodes) ->
                    nodes ?: run {
                        mc.execute { errorMessage("Ice Fill: no solution at Y=${floor.y}"); path = null }
                        return@thread
                    }
                }.map { room.getRealCoords(it).let { r -> Vec3(r.x + 0.5, r.y.toDouble(), r.z + 0.5) } }
                mc.execute {
                    path = combined
                    modMessage("§aIce Fill: dynamically solved in ${timer.getTime()}ms, ${combined.size} nodes")
                }
            } finally {
                solving = false
            }
        }
    }

    private fun buildIceMask(floor: Floor, room: Room, world: ClientLevel): Long {
        var mask = floor.bit(floor.start.x, floor.start.z) or floor.bit(floor.exit.x, floor.exit.z)
        for (x in floor.xMin..floor.xMax) for (z in floor.zMin..floor.zMax) {
            if (world.getBlockAtPos(room.getRealCoords(BlockPos(x, floor.y, z))) == Blocks.AIR) mask = mask or floor.bit(x, z)
        }
        return mask
    }

    private fun solveFloor(floor: Floor, iceMask: Long): List<BlockPos>? {
        val total = iceMask.countOneBits()
        val exitBit = floor.bit(floor.exit.x, floor.exit.z)
        val out = mutableListOf(BlockPos(floor.start.x, floor.y, floor.start.z))

        fun bitAt(x: Int, z: Int): Long =
            if (x in floor.xMin..floor.xMax && z in floor.zMin..floor.zMax) floor.bit(x, z) else 0L

        fun runLength(x: Int, z: Int, dx: Int, dz: Int, filled: Long): Int {
            var len = 0
            var nx = x + dx; var nz = z + dz
            while (true) {
                val b = bitAt(nx, nz)
                if (b == 0L || iceMask and b == 0L || filled and b != 0L) return len
                len++; nx += dx; nz += dz
            }
        }

        fun reachableFrom(startX: Int, startZ: Int, filled: Long): Long {
            val unfilled = iceMask and filled.inv()
            if (unfilled == 0L) return 0L
            var visited = 0L
            val stack = ArrayDeque<Int>()
            DIRS.forEach { (dx, dz) ->
                val nx = startX + dx; val nz = startZ + dz
                val b = bitAt(nx, nz)
                if (b != 0L && unfilled and b != 0L) {
                    visited = visited or b
                    stack.addLast(packCoord(nx, nz))
                }
            }
            while (stack.isNotEmpty()) {
                val packed = stack.removeLast()
                val cx = unpackX(packed); val cz = unpackZ(packed)
                DIRS.forEach { (dx, dz) ->
                    val nx = cx + dx; val nz = cz + dz
                    val b = bitAt(nx, nz)
                    if (b != 0L && unfilled and b != 0L && visited and b == 0L) {
                        visited = visited or b
                        stack.addLast(packCoord(nx, nz))
                    }
                }
            }
            return visited
        }

        fun dfs(x: Int, z: Int, filled: Long, lastDir: Pair<Int, Int>?): Boolean {
            if (filled.countOneBits() == total) return x == floor.exit.x && z == floor.exit.z
            val moves = DIRS.mapNotNull { (dx, dz) ->
                val nx = x + dx; val nz = z + dz
                val b = bitAt(nx, nz)
                if (b == 0L || iceMask and b == 0L || filled and b != 0L) return@mapNotNull null
                Move(dx to dz, b, runLength(x, z, dx, dz, filled))
            }.sortedWith(compareBy({ it.dir != lastDir }, { -it.runLength }))

            for (move in moves) {
                val newFilled = filled or move.bit
                if (move.bit and exitBit != 0L && newFilled.countOneBits() < total) continue
                val nx = x + move.dir.first; val nz = z + move.dir.second
                val unfilled = iceMask and newFilled.inv()
                if (unfilled != 0L && reachableFrom(nx, nz, newFilled) != unfilled) continue
                out.add(BlockPos(nx, floor.y, nz))
                if (dfs(nx, nz, newFilled, move.dir)) return true
                out.removeAt(out.size - 1)
            }
            return false
        }

        return if (dfs(floor.start.x, floor.start.z, floor.bit(floor.start.x, floor.start.z), null)) out else null
    }

    private inline fun packCoord(x: Int, z: Int): Int = (x shl 16) or (z and 0xFFFF)
    private inline fun unpackX(packed: Int): Int = packed shr 16
    private inline fun unpackZ(packed: Int): Int = packed.toShort().toInt()

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (!enabled || !inDungeons || !autoIceFill || !isHoldingAOTV()) return
        val packet = event.packet as? ClientboundPlayerPositionPacket ?: return
        val nodes = path ?: return
        val pos = packet.change().position()
        val index = nodes.indexOfFirst { it.distanceToSqr(pos) < POSITION_MATCH_EPSILON_SQ }
        if (index !in 0 until nodes.size - 1 || index == lastFiredIndex) return
        floorOf(nodes[index].y)?.takeIf(::isFloorDone)?.let { return }
        if (lastFiredIndex == -1) modMessage("§aStarting Auto Ice Fill")
        lastFiredIndex = index

        val delta = nodes[index + 1].subtract(nodes[index])
        val (yaw, _) = AngleUtils.calcAimAnglesFromDelta(delta.x, delta.y, delta.z)
        val pitch = if (delta.y > 0) PITCH_UP else PITCH_DOWN
        scheduleStep(yaw, pitch)
    }

    private fun scheduleStep(yaw: Float, pitch: Float) {
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            val player = mc.player ?: return@register
            if (!isHoldingAOTV()) return@register
            mc.connection?.send(ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision))
            PlayerUtils.useItem(yaw, pitch)
            if (rotateMode == 1) player.applySpoofedRotation(yaw, pitch)
            cancelNextMovement = true
        }
    }

    private fun Player.applySpoofedRotation(newYaw: Float, newPitch: Float) {
        yRot = newYaw
        xRot = newPitch
        yRotO = newYaw
        xRotO = newPitch
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        if (!cancelNextMovement) return
        if (event.packet !is ServerboundMovePlayerPacket) return
        cancelNextMovement = false
        event.cancel()
    }

    @SubscribeEvent
    fun onTickPost(event: ClientTickEvent.Post) {
        cancelNextMovement = false
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled || !inDungeons || !solver) return
        val nodes = path ?: return
        val done = Floor.entries.associateWith(::isFloorDone)
        nodes.zipWithNext { from, to ->
            if (done[floorOf(from.y)] == true) return@zipWithNext
            renderSegment(event, from, to)
        }
    }

    private fun renderSegment(event: NewRender3DEvent, from: Vec3, to: Vec3) {
        val color = colorAt(from.y)
        if (from.y == to.y) {
            drawLine3D(event.matrixStack, event.camera, from, to, color, depthTest = true)
            return
        }
        val dirX = (to.x - from.x).sign
        val dirZ = (to.z - from.z).sign
        val midY = (from.y + to.y) / 2.0
        val s0 = Vec3(from.x + dirX * 0.5, from.y, from.z + dirZ * 0.5)
        val s1 = Vec3(s0.x, midY, s0.z)
        val s2 = Vec3(s0.x + dirX * 0.5, midY, s0.z + dirZ * 0.5)
        val s3 = Vec3(s2.x, to.y, s2.z)
        val toColor = colorAt(to.y)
        listOf(from, s0, s1, s2, s3, to).zipWithNext { a, b ->
            drawLine3D(event.matrixStack, event.camera, a, b, if (b === to) toColor else color, depthTest = true)
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = reset()
}
