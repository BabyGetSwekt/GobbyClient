package gobby.commands

import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.gui.ModIdHiderScreen
import gobby.gui.MobEspScreen
import gobby.gui.brush.BlockSelector
import gobby.gui.click.ClickGUI
import gobby.features.dungeons.DungeonMap
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapCheckmarks
import gobby.utils.skyblock.dungeon.map.MapScanner
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomType

//import gobby.features.force.AutoUpdater
import gobby.utils.skyblock.dungeon.DungeonMapSaver
import gobby.gui.hud.HudEditor
import gobby.utils.LocationUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getRelativeCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.pathfinder.PathExecutor
import gobby.pathfinder.PlanStats
import gobby.pathfinder.RouteEngine
import gobby.pathfinder.RoutePlan
import gobby.pathfinder.TravelMode
import gobby.utils.timer.Clock
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.Utils.executeLater
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.sendMessage
import gobby.utils.parseAbilities
import gobby.utils.skyblockID
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import gobby.pathfinder.navmesh.WalkMeshScanner
import gobby.pathfinder.navmesh.WalkPolygon
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.StructureCopier
import gobby.utils.MovementRecorder
import gobby.utils.skyblock.dungeon.RoomCopier
import com.mojang.brigadier.context.CommandContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

internal object GobbyCommandWorld {

    private const val DUMP_FILE_NAME = "gobby-blockcache.dump"
    private const val DUMP_MIN_Y = 40
    private const val DUMP_MAX_Y = 140

    private fun setRotationCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("setrotation")
                    .then(
                        ClientCommands.argument("yaw", FloatArgumentType.floatArg())
                            .then(
                                ClientCommands.argument("pitch", FloatArgumentType.floatArg())
                                    .executes { context ->
                                        val player = mc.player ?: return@executes 0
                                        val yaw = FloatArgumentType.getFloat(context, "yaw")
                                        val pitch = FloatArgumentType.getFloat(context, "pitch")
                                        player.yRotO = yaw
                                        player.xRotO = pitch
                                        player.yRot = yaw
                                        player.xRot = pitch
                                        modMessage("\u00A7aRotation set to yaw=\u00A7e$yaw \u00A7apitch=\u00A7e$pitch")
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )
    }

    private fun mapDebugCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("mapdebug")
                    .executes {
                        val player = mc.player ?: return@executes 0
                        val grid = DungeonMap.grid
                        val disc = DungeonMap.discoveredView
                        val cell = DungeonRooms.roomCellAt(grid, player.x, player.z)
                        val entrance = grid.indices.firstOrNull { (grid[it] as? MapTile.Room)?.data?.type == RoomType.ENTRANCE }
                        modMessage("\u00A7e[MapDebug] inDungeons=${LocationUtils.inDungeons} scanned=${DungeonMap.hasScanned}")
                        modMessage("\u00A7e pos=(${player.x.toInt()},${player.z.toInt()}) playerCell=$cell discovered=${cell?.let { disc.getOrElse(it) { false } }}")
                        modMessage("\u00A7e discoveredCount=${disc.count { it }} entranceCell=$entrance entranceDiscovered=${entrance?.let { disc.getOrElse(it) { false } }}")
                        modMessage("\u00A7e ${MapCheckmarks.debugInfo()}")
                        MapCheckmarks.dumpRooms(grid, disc).forEach { println("[GobbyMapDump] $it") }
                        MapCheckmarks.dumpMapGrid().forEach { println("[GobbyMapGrid] $it") }
                        modMessage("\u00A77 (per-room + map-grid dump written to log)")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun copyRoomCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("copyRoom").executes {
                RoomCopier.copyCurrentRoom()
                Command.SINGLE_SUCCESS
            })
    }

    fun register(event: CommandRegisterEvent) {
        event.register(setRotationCommand())
        event.register(mapDebugCommand())
        event.register(copyRoomCommand())
    }
}

