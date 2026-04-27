package gobby.commands

import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.gui.ModIdHiderScreen
import gobby.gui.brush.BlockSelector
import gobby.gui.click.ClickGUI
import gobby.features.dungeons.BloodBlink
import gobby.features.dungeons.DungeonMap
import gobby.features.force.AutoUpdater
import gobby.utils.skyblock.dungeon.DungeonMapSaver
import gobby.gui.hud.HudEditor
import gobby.utils.LocationUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getRelativeCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.pathfinder.PathExecutor
import gobby.pathfinder.core.PathFinder
import gobby.pathfinder.etherwarp.*
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.sendMessage
import gobby.utils.parseAbilities
import gobby.utils.skyblockID
import net.minecraft.registry.Registries
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.util.math.BlockPos

object GobbyCommand {

    private fun openConfig(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
            .executes {
                mc.send { mc.setScreen(ClickGUI()) }
                Command.SINGLE_SUCCESS
            }
    }

    private fun sendCoordsCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("sendcoords")
                    .executes { context ->
                        val player = mc.player ?: return@executes 0
                        val x = player.x.toInt()
                        val y = player.y.toInt()
                        val z = player.z.toInt()
                        sendMessage("x: $x, y: $y, z: $z")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun blockSelectorCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("blockselector")
                    .executes {
                        mc.send { BlockSelector.open() }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun helpCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("help")
                    .executes {
                        modMessage("§b§m                              ")
                        modMessage("§e/gobby §7- Opens the settings menu")
                        modMessage("§e/gobby help §7- Shows this help menu")
                        modMessage("§e/gobby modid §7- Hide mod IDs from other mods")
                        modMessage("§e/gobby blockselector §7- Pick a block for the brush")
                        modMessage("§e/gobby brush §7- Toggle brush mode")
                        modMessage("§e/gobby sendcoords §7- Send your coords in chat")
                        modMessage("§e/gobby path <x> <y> <z> §7- Pathfind to coordinates (BETA + WIP)")
                        modMessage("§e/gobby pathstop §7- Stop following a path")
                        modMessage("§e/gobby update §7- Force check for updates")
                        modMessage("§b§m                              ")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun modIdCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("modid")
                    .executes {
                        mc.send { ModIdHiderScreen.open() }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun pathCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("path")
                    .then(
                        ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(
                                ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(
                                        ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes { context ->
                                                val x = IntegerArgumentType.getInteger(context, "x")
                                                val y = IntegerArgumentType.getInteger(context, "y")
                                                val z = IntegerArgumentType.getInteger(context, "z")
                                                val player = mc.player ?: return@executes 0
                                                val start = player.blockPos
                                                val goal = BlockPos(x, y, z)
                                                val speed = player.movementSpeed.toDouble()

                                                modMessage("Pathfinding to $x $y $z (speed: ${"%.3f".format(speed)})...")
                                                val path = PathFinder.findPath(start, goal, speed)
                                                if (path != null) {
                                                    PathExecutor.start(path)
                                                    modMessage("§aPath found! ${path.size} nodes. Following path... Use §e/gobby pathstop §ato cancel.")
                                                } else {
                                                    modMessage("§cNo path found to $x $y $z")
                                                }
                                                Command.SINGLE_SUCCESS
                                            }
                                    )
                            )
                    )
            )
    }

    private fun pathStopCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("pathstop")
                    .executes {
                        PathExecutor.stop()
                        modMessage("§cPath following stopped.")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun updateCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("update")
                    .executes {
                        AutoUpdater.forceCheck()
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun lookingAtCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("lookingAt")
                    .executes {
                        val hit = mc.crosshairTarget
                        if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
                            val pos = hit.blockPos
                            val block = mc.world?.getBlockState(pos)?.block
                            val blockName = Registries.BLOCK.getId(block).path.uppercase()
                            val coords = "${pos.x}, ${pos.y}, ${pos.z}"
                            modMessage(Text.literal("§a$blockName §7$coords")
                                .setStyle(Style.EMPTY
                                    .withClickEvent(ClickEvent.CopyToClipboard(coords))
                                    .withHoverEvent(HoverEvent.ShowText(Text.literal("§eClick to copy coordinates")))
                                ))

                            if (LocationUtils.inDungeons && !LocationUtils.inBoss) {
                                val room = ScanUtils.currentRoom
                                if (room != null) {
                                    val rel = room.getRelativeCoords(pos)
                                    val relCoords = "${rel.x}, ${rel.y}, ${rel.z}"
                                    modMessage(Text.literal("§bRelative: §7$relCoords")
                                        .setStyle(Style.EMPTY
                                            .withClickEvent(ClickEvent.CopyToClipboard(relCoords))
                                            .withHoverEvent(HoverEvent.ShowText(Text.literal("§eClick to copy relative coordinates")))
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
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("map")
                    .executes {
                        DungeonMap.printGrid()
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun getCoreCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("getcore")
                    .executes {
                        val player = mc.player ?: return@executes 0
                        val center = ScanUtils.getRoomCenter(player.blockPos.x, player.blockPos.z)
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
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("hud")
                    .executes {
                        mc.send { mc.setScreen(HudEditor()) }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(openConfig("gobby"))
        event.register(openConfig("gobbyclient"))
        event.register(sendCoordsCommand())
        event.register(blockSelectorCommand())
        event.register(modIdCommand())
        event.register(helpCommand())
        event.register(pathCommand())
        event.register(pathStopCommand())
        event.register(updateCommand())
        event.register(hudCommand())
        event.register(lookingAtCommand())
        event.register(mapCommand())
        event.register(getCoreCommand())
        event.register(etherwarpCommand())
        event.register(bloodCommand())
        event.register(saveMapCommand())
        event.register(copyMapCommand())
        event.register(getItemIDCommand())
        event.register(copyStructureCommand())
        event.register(pasteStructureCommand())
        event.register(copyRoomCommand())
    }

    private fun copyRoomCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("copyRoom").executes {
                gobby.utils.skyblock.dungeon.RoomCopier.copyCurrentRoom()
                Command.SINGLE_SUCCESS
            })
    }

    private fun copyStructureCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("copyStructure")
                .then(ClientCommandManager.literal("1").executes {
                    gobby.utils.StructureCopier.setPos1()
                    Command.SINGLE_SUCCESS
                })
                .then(ClientCommandManager.literal("2").executes {
                    gobby.utils.StructureCopier.setPos2()
                    Command.SINGLE_SUCCESS
                })
                .then(ClientCommandManager.literal("stop").executes {
                    gobby.utils.StructureCopier.stop()
                    Command.SINGLE_SUCCESS
                })
            )
    }

    private fun pasteStructureCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("pasteStructure")
                .executes {
                    if (LocationUtils.onHypixel) {
                        errorMessage("Cannot paste on Hypixel. Join orange0513.com:30030 (singleplayer world) first.")
                    } else {
                        gobby.utils.StructureCopier.pasteLatest()
                    }
                    Command.SINGLE_SUCCESS
                }
            )
    }

    private fun saveMapCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("saveMap")
                .executes {
                    if (!LocationUtils.inDungeons) {
                        errorMessage("Must be in a dungeon")
                    } else if (DungeonMapSaver.isScanning) {
                        errorMessage("Already scanning")
                    } else {
                        DungeonMapSaver.startScan()
                    }
                    Command.SINGLE_SUCCESS
                }
            )
    }

    private fun copyMapCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("copyMap")
                .executes {
                    if (!mc.isInSingleplayer) {
                        errorMessage("This command can only be used in singleplayer")
                    } else {
                        DungeonMapSaver.copyMap()
                    }
                    Command.SINGLE_SUCCESS
                }
            )
    }

    private fun getItemIDCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("getItemID")
                .executes {
                    val player = mc.player ?: return@executes 0
                    val stack = player.mainHandStack
                    if (stack.isEmpty) {
                        errorMessage("You are not holding an item.")
                        return@executes Command.SINGLE_SUCCESS
                    }

                    val id = stack.skyblockID.ifEmpty { "§c(none)" }
                    modMessage("§b§m                              ")
                    modMessage("§eSkyblock ID: §f$id")

                    val abilities = stack.parseAbilities()
                    if (abilities.isEmpty()) {
                        modMessage("§7No abilities parsed from lore.")
                    } else {
                        abilities.forEach { ability ->
                            modMessage("§aAbility: §f${ability.name}")
                            ability.abilityTrigger?.let { modMessage("  §7- §6Trigger: §f$it") }
                            ability.manaCost?.let { modMessage("  §7- §bMana Cost: §f$it") }
                            ability.soulflowCost?.let { modMessage("  §7- §5Soulflow Cost: §f$it") }
                            ability.cooldownSeconds?.let { modMessage("  §7- §eCooldown: §f${it}s") }
                            if (ability.manaCost == null && ability.soulflowCost == null && ability.cooldownSeconds == null) {
                                modMessage("  §7- §8(no mana/soulflow/cooldown)")
                            }
                        }
                    }
                    modMessage("§b§m                              ")
                    Command.SINGLE_SUCCESS
                }
            )
    }

    private fun bloodCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("blood")
                .executes {
                    BloodBlink.retryBlink()
                    Command.SINGLE_SUCCESS
                }
            )
    }

    private fun etherwarpCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(ClientCommandManager.literal("etherwarp")
                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                    .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                            .executes { ctx ->
                                val x = IntegerArgumentType.getInteger(ctx, "x")
                                val y = IntegerArgumentType.getInteger(ctx, "y")
                                val z = IntegerArgumentType.getInteger(ctx, "z")
                                val player = mc.player ?: return@executes 0
                                val start = player.blockPos.down()
                                val goal = GoalBlockPos(BlockPos(x, y, z))
                                val config = EtherwarpConfig(startPos = start)

                                EtherwarpExecutor.pathAndExecute(start, goal, config)
                                Command.SINGLE_SUCCESS
                            }
                        )
                    )
                )
                .then(ClientCommandManager.literal("stop")
                    .executes {
                        EtherwarpExecutor.stop()
                        EtherwarpRenderer.clear()
                        Command.SINGLE_SUCCESS
                    }
                )
                .then(ClientCommandManager.literal("clear")
                    .executes {
                        EtherwarpRenderer.clear()
                        modMessage("§cCleared etherwarp path")
                        Command.SINGLE_SUCCESS
                    }
                )
            )
    }
}
