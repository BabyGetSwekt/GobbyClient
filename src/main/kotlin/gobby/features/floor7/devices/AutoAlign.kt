package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldUnloadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getPhase
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.floor

object AutoAlign : Module(
    "Arrow Align", "Arrow align device helpers",
    Category.FLOOR7, hidden = true
) {

    private const val CLICK_CACHE_DURATION = 1000L
    private const val MAX_INTERACT_RANGE_SQ = 25.0
    private const val DEVICE_RANGE_SQ = 100.0
    private const val GRID_SIZE = 5

    private val solutions = listOf(
        listOf(7, 7, 7, 7, null, 1, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, null, 7, 7, 7, 1),
        listOf(null, null, null, null, null, 1, null, 1, null, 1, 1, null, 1, null, 1, 1, null, 1, null, 1, null, null, null, null, null),
        listOf(5, 3, 3, 3, null, 5, null, null, null, null, 7, 7, null, null, null, 1, null, null, null, null, 1, 3, 3, 3, null),
        listOf(null, null, null, null, null, null, 1, null, 1, null, 7, 1, 7, 1, 3, 1, null, 1, null, 1, null, null, null, null, null),
        listOf(null, null, 7, 7, 5, null, 7, 1, null, 5, null, null, null, null, null, null, 7, 5, null, 1, null, null, 7, 7, 1),
        listOf(7, 7, null, null, null, 1, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, null, null, null, 7, 1),
        listOf(5, 3, 3, 3, 3, 5, null, null, null, 1, 7, 7, null, null, 1, null, null, null, null, 1, null, 7, 7, 7, 1),
        listOf(7, 7, null, null, null, 1, null, null, null, null, 1, 3, null, 7, 5, null, null, null, null, 5, null, null, null, 3, 3),
        listOf(null, null, null, null, null, 1, 3, 3, 3, 3, null, null, null, null, 1, 7, 7, 7, 7, 1, null, null, null, null, null)
    )

    private val deviceStandPos = BlockPos(0, 120, 77)
    val deviceCornerPos = BlockPos(-2, 120, 75)

    val recentClicks = mutableMapOf<Int, Long>()
    val remainingClicks = mutableMapOf<Int, Int>()
    var currentFrames: MutableList<FrameData?>? = null
        private set
    var currentSolution: List<Int?>? = null
        private set
    val inP3 get() = DungeonUtils.inP3

    data class FrameData(val entity: ItemFrameEntity, var rotation: Int)

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!inDungeons || !inBoss || dungeonFloor != 7 || getPhase() != 3) return
        if (!AlignHelper.enabled) return

        val player = mc.player ?: return
        if (player.squaredDistanceTo(deviceStandPos.x.toDouble(), deviceStandPos.y.toDouble(), deviceStandPos.z.toDouble()) > DEVICE_RANGE_SQ) {
            reset()
            return
        }

        currentFrames = scanFrames()
        currentSolution = matchSolution()
        buildRemainingClicks()

        if (currentSolution == null || !AlignHelper.aura) return
        solveClosestFrame(player)
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldUnloadEvent) {
        reset()
    }

    private fun reset() {
        currentFrames = null
        currentSolution = null
        remainingClicks.clear()
        recentClicks.clear()
    }

    private fun matchSolution(): List<Int?>? {
        val rotations = currentFrames?.map { it?.rotation } ?: return null
        return solutions.find { sol ->
            sol.indices.none { i -> (sol[i] == null) xor (rotations[i] == null) }
        }
    }

    private fun buildRemainingClicks() {
        remainingClicks.clear()
        val solution = currentSolution ?: return
        val frames = currentFrames ?: return

        for (i in solution.indices) {
            val frame = frames[i] ?: continue
            val target = solution[i] ?: continue
            val clicks = clicksNeeded(frame.rotation, target)
            if (clicks > 0) remainingClicks[i] = clicks
        }
    }

    private fun solveClosestFrame(player: net.minecraft.client.network.ClientPlayerEntity) {
        val solution = currentSolution!!
        val frames = currentFrames!!

        val sortedFrames = frames.mapIndexed { i, f -> i to f }
            .filter { it.second != null }
            .sortedBy { (_, f) -> player.squaredDistanceTo(f!!.entity.x, f.entity.y, f.entity.z) }

        for ((index, frameData) in sortedFrames) {
            if (frameData == null) continue
            val entity = frameData.entity

            if (player.squaredDistanceTo(entity.x, entity.y, entity.z) > MAX_INTERACT_RANGE_SQ) continue

            var clicks = clicksNeeded(frameData.rotation, solution[index]!!)

            if (!inP3 && unsolved(frames, solution) <= 1) clicks--
            if (clicks <= 0) continue

            val lastClick = recentClicks[index] ?: 0
            if (System.currentTimeMillis() - lastClick < CLICK_CACHE_DURATION) continue

            recentClicks[index] = System.currentTimeMillis()
            sendClicks(entity, frameData, clicks, player)
            break
        }
    }

    private fun sendClicks(
        entity: ItemFrameEntity,
        frameData: FrameData,
        clicks: Int,
        player: net.minecraft.client.network.ClientPlayerEntity
    ) {
        val networkHandler = player.networkHandler
        repeat(clicks) {
            frameData.rotation = (frameData.rotation + 1) % 8
            networkHandler.sendPacket(
                PlayerInteractEntityC2SPacket.interactAt(entity, false, Hand.MAIN_HAND, Vec3d(0.03125, 0.0, 0.0))
            )
            networkHandler.sendPacket(
                PlayerInteractEntityC2SPacket.interact(entity, false, Hand.MAIN_HAND)
            )
        }
    }

    private fun clicksNeeded(current: Int, target: Int): Int = (target - current + 8) % 8

    private fun unsolved(frames: List<FrameData?>, solution: List<Int?>): Int {
        return frames.withIndex().count { (i, f) ->
            f != null && clicksNeeded(f.rotation, solution[i]!!) > 0
        }
    }

    private fun scanFrames(): MutableList<FrameData?> {
        val world = mc.world ?: return mutableListOf()
        val frameMap = mutableMapOf<String, FrameData>()

        for (entity in world.entities.filterIsInstance<ItemFrameEntity>()) {
            val stack = entity.heldItemStack ?: continue
            if (stack.item != Items.ARROW) continue
            val key = "${floor(entity.x).toInt()},${floor(entity.y).toInt()},${floor(entity.z).toInt()}"
            frameMap[key] = FrameData(entity, entity.rotation)
        }

        val result = mutableListOf<FrameData?>()
        val now = System.currentTimeMillis()

        for (dz in 0 until GRID_SIZE) {
            for (dy in 0 until GRID_SIZE) {
                val index = dy + dz * GRID_SIZE
                val lastClick = recentClicks[index] ?: 0

                if (currentFrames != null && now - lastClick < CLICK_CACHE_DURATION) {
                    result.add(currentFrames!![index])
                } else {
                    val key = "${deviceCornerPos.x},${deviceCornerPos.y + dy},${deviceCornerPos.z + dz}"
                    result.add(frameMap[key])
                }
            }
        }

        return result
    }
}
