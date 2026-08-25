package gobby.pathfinder.etherwarp

import gobby.features.dungeons.DungeonMap
import gobby.features.dungeons.RoomPathfinder
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.abs

internal object EtherwarpExecutionReporter {
    fun isExpected(landing: Vec3, hop: EtherwarpExecutionHop): Boolean {
        return isExpected(landing, hop.expected)
    }

    fun isExpected(landing: Vec3, expected: Vec3): Boolean {
        val delta = landing.subtract(expected)
        return delta.x * delta.x + delta.z * delta.z < LANDING_TOLERANCE_SQ && abs(delta.y) <= LANDING_HEIGHT_TOLERANCE
    }

    fun logLanding(hop: EtherwarpExecutionHop, landing: Vec3): Boolean {
        if (RoomPathfinder.pathDebug) println("[GobbyTP] hop#${hop.label} landed OK expected=${format(hop.expected)} actual=${format(landing)} room='${roomNameAt(landing)}' localSneakSent=${hop.sneakSent} crouch=${hop.crouching}")
        return true
    }

    fun logMiss(hop: EtherwarpExecutionHop, landing: Vec3, kind: EtherwarpKind, clipboardSet: Boolean): Boolean =
        EtherwarpExecutionDiagnostics.logMiss(hop, landing, kind, clipboardSet)

    fun logDrop(hop: EtherwarpExecutionHop, kind: EtherwarpKind) = EtherwarpExecutionDiagnostics.logDrop(hop, kind)

    fun format(value: Vec3): String = "(${formatNumber(value.x)},${formatNumber(value.y)},${formatNumber(value.z)})"

    fun roomNameAt(pos: Vec3): String {
        val cell = DungeonRooms.roomCellAt(DungeonMap.grid, pos.x, pos.z) ?: return "unknown"
        return (DungeonMap.grid.getOrNull(cell) as? MapTile.Room)?.data?.name ?: "cell-$cell"
    }

    private fun formatNumber(value: Number): String = String.format(Locale.US, "%.2f", value.toDouble())

}
