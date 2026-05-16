package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getPhase
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
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

    data class FrameData(val entity: ItemFrame, var rotation: Int)

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!inDungeons || !inBoss || dungeonFloor != 7 || getPhase() != 3) return
        if (!AlignHelper.enabled) return

        val player = mc.player ?: return
        if (player.distanceToSqr(deviceStandPos.x.toDouble(), deviceStandPos.y.toDouble(), deviceStandPos.z.toDouble()) > DEVICE_RANGE_SQ) {
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
    fun onWorldUnload(event: WorldLoadEvent) {
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

    private fun solveClosestFrame(player: LocalPlayer) {
        val solution = currentSolution ?: return
        val frames = currentFrames ?: return

        val sortedFrames = frames.mapIndexedNotNull { i, f -> f?.let { i to it } }
            .sortedBy { (_, f) -> player.distanceToSqr(f.entity.x, f.entity.y, f.entity.z) }

        for ((index, frameData) in sortedFrames) {
            val entity = frameData.entity
            if (player.distanceToSqr(entity.x, entity.y, entity.z) > MAX_INTERACT_RANGE_SQ) continue

            val target = solution[index] ?: continue
            var clicks = clicksNeeded(frameData.rotation, target)

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
        entity: ItemFrame,
        frameData: FrameData,
        clicks: Int,
        player: LocalPlayer
    ) {
        val networkHandler = player.connection
        repeat(clicks) {
            frameData.rotation = (frameData.rotation + 1) % 8
            networkHandler.send(
                ServerboundInteractPacket.createInteractionPacket(entity, false, InteractionHand.MAIN_HAND, Vec3(0.03125, 0.0, 0.0))
            )
            networkHandler.send(
                ServerboundInteractPacket.createInteractionPacket(entity, false, InteractionHand.MAIN_HAND)
            )
        }
    }

    private fun clicksNeeded(current: Int, target: Int): Int = (target - current + 8) % 8

    private fun unsolved(frames: List<FrameData?>, solution: List<Int?>): Int {
        return frames.withIndex().count { (i, f) ->
            val target = solution[i]
            f != null && target != null && clicksNeeded(f.rotation, target) > 0
        }
    }

    private fun scanFrames(): MutableList<FrameData?> {
        val world = mc.level ?: return mutableListOf()
        val frameMap = mutableMapOf<String, FrameData>()

        for (entity in world.entitiesForRendering().filterIsInstance<ItemFrame>()) {
            val stack = entity.item
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

                val cached = currentFrames
                if (cached != null && now - lastClick < CLICK_CACHE_DURATION) {
                    result.add(cached[index])
                } else {
                    val key = "${deviceCornerPos.x},${deviceCornerPos.y + dy},${deviceCornerPos.z + dz}"
                    result.add(frameMap[key])
                }
            }
        }

        return result
    }
}
