package gobby.features.dungeons

import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.skyblock.dungeon.map.MapConstants
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.tiles.Room
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.world.phys.AABB

internal object BloodBlinkSupport {
    fun findBloodRoom(grid: Array<MapTile>): BloodTarget? =
        (0 until MapConstants.GRID_SIZE step 2).asSequence()
            .flatMap { row -> (0 until MapConstants.GRID_SIZE step 2).asSequence().map { col -> row to col } }
            .mapNotNull { (row, col) -> bloodTarget(grid, row, col) }
            .firstOrNull()

    fun renderSlabs(event: Render3DEvent, room: Room) {
        BloodBlink.Slab.entries.forEach { slab ->
            val position = room.getRealCoords(slab.offset)
            val box = AABB(
                position.x.toInt().toDouble(), position.y.toInt().toDouble(), position.z.toInt().toDouble(),
                position.x.toInt() + 1.0, position.y.toInt() + 1.0, position.z.toInt() + 1.0
            )
            draw3DBox(event.matrixStack, event.camera, box, slab.color, depthTest = false)
        }
    }

    private fun bloodTarget(grid: Array<MapTile>, row: Int, col: Int): BloodTarget? {
        val tile = grid[row * MapConstants.GRID_SIZE + col] as? MapTile.Room ?: return null
        if (tile.data.type != RoomType.BLOOD) return null
        return BloodTarget(MapConstants.START_X + col * MapConstants.HALF_ROOM, MapConstants.START_Z + row * MapConstants.HALF_ROOM)
    }
}

internal data class BloodTarget(val x: Int, val z: Int)
