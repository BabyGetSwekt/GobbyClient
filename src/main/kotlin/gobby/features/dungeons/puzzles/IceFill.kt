package gobby.features.dungeons.puzzles

import gobby.Gobbyclient.Companion.mc
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
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
import net.minecraft.block.Blocks
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.sign

object IceFill : Module("Ice Fill", "Solves (and auto-completes) Ice Fill puzzle in F7", Category.DUNGEONS) {

    private val solver by BooleanSetting("Puzzle Solver", true, desc = "Draw the solution path")
    private val autoIceFill by BooleanSetting("Auto Ice Fill", false, desc = "Automatically teleports through the path")
    private val espLines by BooleanSetting("ESP Lines", true, desc = "Render lines through walls")

    private enum class Floor(val y: Int, val xMin: Int, val xMax: Int, val zMin: Int, val zMax: Int, val start: BlockPos, val exit: BlockPos, val color: Color) {
        F1(70, 14, 16, 7, 10, BlockPos(15, 70, 7), BlockPos(15, 70, 10), Color(255, 50, 50)),
        F2(71, 13, 17, 12, 17, BlockPos(15, 71, 12), BlockPos(15, 71, 17), Color(50, 255, 50)),
        F3(72, 12, 18, 19, 26, BlockPos(15, 72, 19), BlockPos(15, 72, 26), Color(50, 100, 255));
        val width get() = xMax - xMin + 1
        fun bit(x: Int, z: Int) = 1L shl ((z - zMin) * width + (x - xMin))
    }

    private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private data class Move(val dir: Pair<Int, Int>, val bit: Long, val runLength: Int)

    private var path: List<Vec3d>? = null

    private fun reset() { path = null }
    private fun colorAt(y: Double): Color = Floor.entries.first { y < it.y + 0.5 }.color
    private fun floorOf(y: Double): Floor? = Floor.entries.firstOrNull { it.y == y.toInt() }

    private fun isFloorDone(floor: Floor): Boolean {
        val world = mc.world ?: return false
        val cells = path?.filter { it.y.toInt() == floor.y }.orEmpty()
        return cells.isNotEmpty() && cells.all {
            world.getBlockAtPos(BlockPos(it.x.toInt(), it.y.toInt() - 1, it.z.toInt())) == Blocks.PACKED_ICE
        }
    }

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        val room = event.room ?: return
        if (room.data.name != "Ice Fill") { reset(); return }
        val timer = Clock()
        val combined = Floor.entries.flatMap { floor ->
            solveFloor(floor, room) ?: run {
                errorMessage("Ice Fill: no solution at Y=${floor.y}"); path = null; return
            }
        }.map { room.getRealCoords(it).let { r -> Vec3d(r.x + 0.5, r.y.toDouble(), r.z + 0.5) } }
        path = combined
        modMessage("§aIce Fill: dynamically solved in ${timer.getTime()}ms, ${combined.size} nodes")
    }

    private fun solveFloor(floor: Floor, room: Room): List<BlockPos>? {
        val world = mc.world ?: return null
        var iceMask = floor.bit(floor.start.x, floor.start.z) or floor.bit(floor.exit.x, floor.exit.z)
        for (x in floor.xMin..floor.xMax) for (z in floor.zMin..floor.zMax) {
            if (world.getBlockAtPos(room.getRealCoords(BlockPos(x, floor.y, z))) == Blocks.AIR) iceMask = iceMask or floor.bit(x, z)
        }
        val total = iceMask.countOneBits()
        val out = mutableListOf(BlockPos(floor.start.x, floor.y, floor.start.z))
        fun runLength(x: Int, z: Int, dx: Int, dz: Int, filled: Long): Int {
            var c = 0; var nx = x + dx; var nz = z + dz
            while (nx in floor.xMin..floor.xMax && nz in floor.zMin..floor.zMax) {
                val b = floor.bit(nx, nz)
                if (iceMask and b == 0L || filled and b != 0L) break
                c++; nx += dx; nz += dz
            }
            return c
        }
        fun dfs(x: Int, z: Int, filled: Long, lastDir: Pair<Int, Int>?): Boolean {
            if (filled.countOneBits() == total) return x == floor.exit.x && z == floor.exit.z
            val moves = DIRS.mapNotNull { (dx, dz) ->
                val nx = x + dx; val nz = z + dz
                if (nx !in floor.xMin..floor.xMax || nz !in floor.zMin..floor.zMax) return@mapNotNull null
                val b = floor.bit(nx, nz)
                if (iceMask and b == 0L || filled and b != 0L) return@mapNotNull null
                Move(dx to dz, b, runLength(x, z, dx, dz, filled))
            }.sortedWith(compareBy({ it.dir != lastDir }, { -it.runLength }))
            for (move in moves) {
                val nx = x + move.dir.first; val nz = z + move.dir.second
                out.add(BlockPos(nx, floor.y, nz))
                if (dfs(nx, nz, filled or move.bit, move.dir)) return true
                out.removeAt(out.size - 1)
            }
            return false
        }
        return if (dfs(floor.start.x, floor.start.z, floor.bit(floor.start.x, floor.start.z), null)) out else null
    }

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (!enabled || !inDungeons || !autoIceFill || !isHoldingAOTV()) return
        val packet = event.packet as? PlayerPositionLookS2CPacket ?: return
        val p = path ?: return
        val pos = packet.change().position()
        val i = p.indexOfFirst { it.squaredDistanceTo(pos) < 1e-6 }
        if (i !in 0 until p.size - 1) return
        floorOf(p[i].y)?.let { if (isFloorDone(it)) return }
        val d = p[i + 1].subtract(p[i])
        val (yaw, _) = AngleUtils.calcAimAnglesFromDelta(d.x, d.y, d.z)
        val pitch = if (d.y > 0) 14f else 48f
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            if (isHoldingAOTV()) PlayerUtils.useItem(yaw, pitch)
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled || !inDungeons || !solver) return
        val p = path ?: return
        val dt = !espLines
        val done = Floor.entries.associateWith { isFloorDone(it) }
        for (i in 0 until p.size - 1) {
            val from = p[i]; val to = p[i + 1]; val color = colorAt(from.y)
            if (done[floorOf(from.y)] == true) continue
            if (from.y == to.y) {
                drawLine3D(event.matrixStack, event.camera, from, to, color, depthTest = dt)
                continue
            }
            val dirX = (to.x - from.x).sign
            val dirZ = (to.z - from.z).sign
            val midY = (from.y + to.y) / 2.0
            val s0 = Vec3d(from.x + dirX * 0.5, from.y, from.z + dirZ * 0.5)
            val s1 = Vec3d(s0.x, midY, s0.z)
            val s2 = Vec3d(s0.x + dirX * 0.5, midY, s0.z + dirZ * 0.5)
            val s3 = Vec3d(s2.x, to.y, s2.z)
            listOf(from, s0, s1, s2, s3, to).zipWithNext { a, b ->
                drawLine3D(event.matrixStack, event.camera, a, b, if (b === to) colorAt(to.y) else color, depthTest = dt)
            }
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = reset()
}
