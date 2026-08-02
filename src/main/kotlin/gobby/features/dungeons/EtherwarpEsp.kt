package gobby.features.dungeons

import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.tiles.Room
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

object EtherwarpEsp {

    private val positions = mutableListOf<BlockPos>()

    private fun isEnabled(): Boolean = EtherwarpTriggerbot.enabled && EtherwarpTriggerbot.esp && inDungeons && !inBoss

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) = load(event.room)

    fun refresh() = load(ScanUtils.currentRoom)

    private fun load(room: Room?) {
        positions.clear()
        if (room == null) return
        EtherwarpRoutes.spots(room.data.name).forEach { encoded ->
            val (x, y, z) = encoded.split(",").map { it.trim().toInt() }
            positions.add(room.getRealCoords(BlockPos(x, y, z)))
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!isEnabled() || positions.isEmpty()) return
        val color = EtherwarpTriggerbot.espColor
        positions.forEach { pos ->
            val box = AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
            draw3DBox(event.matrixStack, event.camera, box, color)
        }
    }
}
