package gobby.features.dungeons.puzzles

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.RangeSetting
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.Utils.getRandomInt
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
import gobby.utils.getBowShootSpeedMs
import gobby.utils.isShortbow
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.timer.Clock
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.abs

object CreeperBeams : Module("Creeper Beams", "Draws and auto-solves the creeper beams puzzle", Category.DUNGEONS) {

    private val auto by BooleanSetting("Auto Solve", false, desc = "Etherwarp on top of the pillar/creeper and hold a shortbow to auto complete the puzzle")
    private val shootDelay by RangeSetting("Shoot Delay", 200, 300, 50, 600, 20, desc = "Random aim + shoot delay per shot in ms")

    private val COLORS = listOf(
        Color(0, 255, 255), Color(0, 255, 0), Color(255, 80, 80),
        Color(255, 165, 0), Color(190, 0, 255), Color(255, 255, 0)
    )

    private val CENTER = BlockPos(15, 74, 15)
    private val CHEST = BlockPos(15, 69, 15)
    private val SOLUTION = listOf(
        BlockPos(15, 84, 13) to CENTER,
        BlockPos(15, 78, 3) to BlockPos(15, 76, 27),
        BlockPos(5, 76, 24) to BlockPos(24, 77, 7),
        BlockPos(2, 75, 16) to BlockPos(27, 78, 14),
        BlockPos(4, 72, 8) to BlockPos(25, 79, 21),
        BlockPos(4, 75, 9) to BlockPos(25, 76, 23),
        BlockPos(22, 80, 22) to BlockPos(4, 72, 8),
        BlockPos(3, 76, 18) to BlockPos(26, 78, 12),
        BlockPos(9, 81, 20) to BlockPos(26, 70, 7),
        BlockPos(18, 81, 21) to BlockPos(9, 69, 3),
        BlockPos(18, 82, 8) to BlockPos(10, 69, 27),
        BlockPos(25, 76, 23) to BlockPos(6, 74, 5)
    )

    private const val STAND_Y = 75.0
    private const val STAND_Y_TOLERANCE = 0.1
    private const val STAND_XZ_TOLERANCE = 3.0
    private const val MAX_ATTEMPTS = 3
    private const val MISS_FALLBACK_MS = 3000L
    private const val HIT_SOUND = "minecraft:entity.experience_orb.pickup"
    private const val HIT_PITCH = 0.7936508f

    private class Beam(val a: BlockPos, val b: BlockPos) {
        var attempts = 0
        var unreachable = false
        fun lanterns() = listOf(a, b)
    }

    private var inRoom = false
    private var gaveUp = false
    private var completed = false
    private var beams = listOf<Beam>()
    private var middlePos: BlockPos? = null
    private var chestPos: BlockPos? = null
    private val queue = ArrayDeque<BlockPos>()
    private var currentBeam: Beam? = null

    private var bowWarned = false
    private var rotating = false
    private var awaitingShot = false
    private var lastAimed: BlockPos? = null
    private var awaitingConfirm: BlockPos? = null
    private var confirmNeedsBlock = false
    private val shotClock = Clock()
    private val confirmClock = Clock()

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        reset()
        val room = event.room ?: return
        if (room.data.name != "Creeper Beams") return
        val used = mutableSetOf<BlockPos>()
        beams = SOLUTION.filter { (a, b) ->
            (a !in used && b !in used).also { keep -> if (keep) { used.add(a); used.add(b) } }
        }.shuffled().map { (a, b) -> Beam(room.getRealCoords(a), room.getRealCoords(b)) }
        middlePos = room.getRealCoords(CENTER)
        chestPos = room.getRealCoords(CHEST)
        inRoom = true
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!enabled || !auto || !inRoom) return
        val player = mc.player ?: return
        val world = mc.level ?: return
        if (puzzleDone(world)) { if (!completed) { completed = true; modMessage("Puzzle completed, GG!") }; pauseShooting(); return }
        if (!atMiddle()) { pauseShooting(); return }
        if (!holdingShortbow) { if (!bowWarned) { errorMessage("Hold a shortbow to start"); bowWarned = true }; return }
        bowWarned = false
        awaitingConfirm?.let { c ->
            if (!isLantern(world, c) || confirmClock.hasTimePassed(MISS_FALLBACK_MS)) awaitingConfirm = null else return
        }
        val cooldown = player.mainHandItem.getBowShootSpeedMs()
        if (awaitingShot && shotClock.hasTimePassed(cooldown)) { fire(world); return }
        if (rotating || awaitingShot || RotationUtils.isEasing) return
        aimNext(world)
    }

    private fun aimNext(world: ClientLevel) {
        if (queue.isEmpty() && !loadNextBeam(world)) {
            if (!gaveUp) gaveUp = true
            return
        }
        val target = queue.firstOrNull() ?: return
        rotating = true
        val ms = getRandomInt(shootDelay.start.toInt(), shootDelay.endInclusive.toInt()).toLong()
        RotationUtils.aimAtBlockShortbow(target, rotate = true, delayMs = ms) { awaitingShot = true }
        if (!RotationUtils.isEasing) { rotating = false; currentBeam?.unreachable = true; queue.clear() }
        else lastAimed = target
    }

    private fun loadNextBeam(world: ClientLevel): Boolean {
        val beam = beams.firstOrNull { ready(world, it) && canFire(it) } ?: return false
        beam.attempts++
        currentBeam = beam
        queue.clear()
        queue.addAll(beam.lanterns())
        return true
    }

    private fun canFire(beam: Beam): Boolean {
        if (beam.lanterns().all { RotationUtils.canAimShortbow(it) }) return true
        beam.unreachable = true
        return false
    }

    private fun ready(world: ClientLevel, beam: Beam): Boolean =
        !beam.unreachable && beam.attempts < MAX_ATTEMPTS && !beamDone(world, beam)

    private fun beamDone(world: ClientLevel, beam: Beam): Boolean =
        !isLantern(world, beam.a) || !isLantern(world, beam.b)

    private fun fire(world: ClientLevel) {
        val target = lastAimed
        if (target != null && isLantern(world, target)) {
            rightClick()
            awaitingConfirm = target
            confirmClock.update()
        }
        shotClock.update()
        awaitingShot = false
        rotating = false
        if (queue.isNotEmpty()) queue.removeFirst()
        confirmNeedsBlock = queue.isEmpty() && awaitingConfirm != null
        lastAimed = null
    }

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (!enabled || !auto || !inRoom) return
        val packet = event.packet as? ClientboundSoundPacket ?: return
        if (packet.sound.value().location().toString() != HIT_SOUND || packet.pitch != HIT_PITCH) return
        mc.execute { if (!confirmNeedsBlock) awaitingConfirm = null }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled || !inDungeons || !inRoom) return
        val world = mc.level ?: return
        beams.forEachIndexed { i, beam ->
            if (!isLantern(world, beam.a) || !isLantern(world, beam.b)) return@forEachIndexed
            val color = COLORS[i % COLORS.size]
            draw3DBox(event.matrixStack, event.camera, blockBox(beam.a), color, filled = false, depthTest = false)
            draw3DBox(event.matrixStack, event.camera, blockBox(beam.b), color, filled = false, depthTest = false)
            drawLine3D(event.matrixStack, event.camera, blockCenter(beam.a), blockCenter(beam.b), color, depthTest = false)
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = reset()

    private fun reset() {
        inRoom = false; gaveUp = false; completed = false
        beams = emptyList()
        middlePos = null; chestPos = null
        queue.clear()
        currentBeam = null
        pauseShooting()
    }

    private fun pauseShooting() {
        rotating = false; awaitingShot = false; bowWarned = false; lastAimed = null; awaitingConfirm = null; confirmNeedsBlock = false
    }

    private val holdingShortbow: Boolean
        get() = mc.player?.mainHandItem?.takeUnless { it.isEmpty }?.isShortbow() == true

    private fun atMiddle(): Boolean {
        val mid = middlePos ?: return false
        val player = mc.player ?: return false
        val c = blockCenter(mid)
        return player.onGround() && abs(posY - STAND_Y) < STAND_Y_TOLERANCE &&
            abs(posX - c.x) <= STAND_XZ_TOLERANCE && abs(posZ - c.z) <= STAND_XZ_TOLERANCE
    }

    private fun puzzleDone(world: ClientLevel): Boolean {
        val chest = chestPos ?: return false
        return world.getBlockState(chest).block == Blocks.CHEST
    }

    private fun isLantern(world: ClientLevel, pos: BlockPos): Boolean =
        world.getBlockState(pos).block == Blocks.SEA_LANTERN

    private fun blockCenter(pos: BlockPos): Vec3 = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)

    private fun blockBox(pos: BlockPos): AABB =
        AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
}
