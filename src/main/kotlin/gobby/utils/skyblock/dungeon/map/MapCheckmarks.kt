package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.utils.skyblock.dungeon.map.MapConstants.CHECK_FAILED
import gobby.utils.skyblock.dungeon.map.MapConstants.CHECK_GREEN
import gobby.utils.skyblock.dungeon.map.MapConstants.CHECK_WHITE
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import net.minecraft.item.FilledMapItem
import net.minecraft.item.Items
import net.minecraft.item.map.MapState

object MapCheckmarks {

    private const val ROOM_ENTRANCE: Byte = 30
    private const val MAP_ROOM_SPACING = 4

    private var mapOffsetX = -1
    private var mapOffsetZ = -1
    private var roomPixelSize = -1
    private var roomGap = -1

    fun reset() {
        mapOffsetX = -1
        mapOffsetZ = -1
        roomPixelSize = -1
        roomGap = -1
    }

    fun update(grid: Array<MapTile>, checkmarks: Array<MapCheckmark>) {
        val player = mc.player ?: return
        val mapState = findMapState(player) ?: return
        val colors = mapState.colors

        if (mapOffsetX < 0 && !scanMapDimensions(colors)) return

        for (row in 0 until GRID_SIZE step 2) {
            for (col in 0 until GRID_SIZE step 2) {
                val index = row * GRID_SIZE + col
                if (grid[index] !is MapTile.Room) continue
                if (checkmarks[index] == MapCheckmark.GREEN) continue

                val roomCol = col / 2
                val roomRow = row / 2
                val mrx = mapOffsetX + roomCol * roomGap
                val mrz = mapOffsetZ + roomRow * roomGap
                if (mrx !in 0..127 || mrz !in 0..127) continue

                val cornerByte = colors[mrz * 128 + mrx]
                if (cornerByte.toInt() == 0) continue

                val halfRoom = roomPixelSize / 2
                val mcx = (mrx + halfRoom - 1).coerceIn(0, 127)
                val mcz = (mrz + halfRoom - 1 + 2).coerceIn(0, 127)
                val centerByte = colors[mcz * 128 + mcx]

                checkmarks[index] = if (cornerByte == centerByte) MapCheckmark.NONE
                else when (centerByte) {
                    CHECK_GREEN -> MapCheckmark.GREEN
                    CHECK_WHITE -> MapCheckmark.WHITE
                    CHECK_FAILED -> MapCheckmark.FAILED
                    else -> MapCheckmark.NONE
                }
            }
        }
    }

    private fun scanMapDimensions(colors: ByteArray): Boolean {
        var ex = -1; var ez = -1
        for (z in 0 until 128) {
            for (x in 0 until 128) {
                if (colors[z * 128 + x] == ROOM_ENTRANCE) { ex = x; ez = z; break }
            }
            if (ex >= 0) break
        }
        if (ex < 0) return false

        var left = ex
        while (left > 0 && colors[ez * 128 + left - 1] == ROOM_ENTRANCE) left--
        var right = ex
        while (right < 127 && colors[ez * 128 + right + 1] == ROOM_ENTRANCE) right++
        var top = ez
        while (top > 0 && colors[(top - 1) * 128 + ex] == ROOM_ENTRANCE) top--

        roomPixelSize = right - left + 1
        if (roomPixelSize < 3) return false
        roomGap = roomPixelSize + MAP_ROOM_SPACING
        mapOffsetX = left % roomGap
        mapOffsetZ = top % roomGap
        return true
    }

    private fun findMapState(player: net.minecraft.entity.player.PlayerEntity): MapState? {
        val world = mc.world ?: return null
        for (slot in 0..8) {
            val stack = player.inventory.getStack(slot)
            if (stack.item == Items.FILLED_MAP) {
                val state = FilledMapItem.getMapState(stack, world)
                if (state != null) return state
            }
        }
        return null
    }
}
