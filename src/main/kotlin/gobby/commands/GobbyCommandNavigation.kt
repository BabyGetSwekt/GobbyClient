package gobby.commands

import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.features.dungeons.DungeonMap

//import gobby.features.force.AutoUpdater
import gobby.gui.hud.HudEditor
import gobby.utils.LocationUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getRelativeCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.pathfinder.PathExecutor
import gobby.pathfinder.TravelMode
import gobby.utils.Utils.executeLater
import gobby.utils.ChatUtils.modMessage
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.world.phys.Vec3

internal object GobbyCommandNavigation {

    private const val DUMP_FILE_NAME = "gobby-blockcache.dump"
    private const val TOPOLOGY_DUMP_FILE_NAME = "gobby-dungeon-topology.dump"
    private const val DUMP_MIN_Y = 40
    private const val DUMP_MAX_Y = 140

    private fun flyPathCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("flypath")
                    .then(
                        ClientCommands.argument("x", IntegerArgumentType.integer())
                            .then(
                                ClientCommands.argument("y", IntegerArgumentType.integer())
                                    .then(
                                        ClientCommands.argument("z", IntegerArgumentType.integer())
                                            .executes { context ->
                                                val x = IntegerArgumentType.getInteger(context, "x")
                                                val y = IntegerArgumentType.getInteger(context, "y")
                                                val z = IntegerArgumentType.getInteger(context, "z")
                                                val player = mc.player ?: return@executes 0
                                                val start = player.position()
                                                val goal = Vec3(x + 0.5, y.toDouble(), z + 0.5)

                                                modMessage("Planning flight to $x $y $z...")
                                                PathExecutor.beginLongPath(start, goal, TravelMode.FLY)
                                                Command.SINGLE_SUCCESS
                                            }
                                    )
                            )
                    )
            )
    }

    private fun lookingAtCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("lookingAt")
                    .executes {
                        val hit = mc.hitResult
                        if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
                            val pos = hit.blockPos
                            val block = mc.level?.getBlockState(pos)?.block ?: return@executes Command.SINGLE_SUCCESS
                            val blockName = BuiltInRegistries.BLOCK.getKey(block).path.uppercase()
                            val coords = "${pos.x}, ${pos.y}, ${pos.z}"
                            modMessage(Component.literal("§a$blockName §7$coords")
                                .setStyle(Style.EMPTY
                                    .withClickEvent(ClickEvent.CopyToClipboard(coords))
                                    .withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick to copy coordinates")))
                                ))

                            if (LocationUtils.inDungeons && !LocationUtils.inBoss) {
                                val room = ScanUtils.currentRoom
                                if (room != null) {
                                    val rel = room.getRelativeCoords(pos)
                                    val relCoords = "${rel.x}, ${rel.y}, ${rel.z}"
                                    modMessage(Component.literal("§bRelative: §7$relCoords")
                                        .setStyle(Style.EMPTY
                                            .withClickEvent(ClickEvent.CopyToClipboard(relCoords))
                                            .withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick to copy relative coordinates")))
                                        ))
                                }
                            }
                        } else {
                            modMessage("§cNot looking at a block.")
                        }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun mapCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("map")
                    .executes {
                        DungeonMap.printGrid()
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun getCoreCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("getcore")
                    .executes {
                        val player = mc.player ?: return@executes 0
                        val center = ScanUtils.getRoomCenter(player.blockPosition().x, player.blockPosition().z)
                        val core = ScanUtils.getCore(center)
                        val roomData = ScanUtils.coreToRoomData[core]
                        if (roomData != null) {
                            modMessage("§aRoom: §f${roomData.name} §7(${roomData.shape})")
                        } else {
                            modMessage("§cUnknown room")
                        }
                        modMessage("§aCore: §f$core")
                        modMessage("§aCenter: §f${center.x}, ${center.z}")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun hudCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("hud")
                    .executes {
                        mc.executeLater { mc.gui.setScreen(HudEditor()) }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    fun register(event: CommandRegisterEvent) {
        event.register(flyPathCommand())
        event.register(lookingAtCommand())
        event.register(mapCommand())
        event.register(getCoreCommand())
        event.register(hudCommand())
    }
}
