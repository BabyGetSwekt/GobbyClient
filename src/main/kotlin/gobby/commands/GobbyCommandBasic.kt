package gobby.commands

import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.gui.brush.BlockSelector
import gobby.gui.click.ClickGUI
import gobby.gui.screen.mobesp.openMobEspList
import gobby.gui.screen.modhider.openModIdList
import gobby.gui.screen.petrules.openPetRules
import gobby.gui.screen.pets.openPetsList

//import gobby.features.force.AutoUpdater
import gobby.pathfinder.PathExecutor
import gobby.pathfinder.TravelMode
import gobby.utils.Utils.executeLater
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.sendMessage
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import gobby.utils.MovementRecorder

internal object GobbyCommandBasic {

    private const val DUMP_FILE_NAME = "gobby-blockcache.dump"
    private const val DUMP_MIN_Y = 40
    private const val DUMP_MAX_Y = 140

    private fun openConfig(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal(name)
            .executes {
                mc.executeLater { mc.gui.setScreen(ClickGUI()) }
                Command.SINGLE_SUCCESS
            }
    }

    private fun sendCoordsCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("sendcoords")
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
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("blockselector")
                    .executes {
                        mc.executeLater { BlockSelector.open() }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun helpCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("help")
                    .executes {
                        modMessage("§b§m                              ")
                        modMessage("§e/gobby §7- Opens the settings menu")
                        modMessage("§e/gobby help §7- Shows this help menu")
                        modMessage("§e/gobby modid §7- Hide mod IDs from other mods")
                        modMessage("§e/gobby mobesp §7- Configure mob ESP highlights")
                        modMessage("§e/gobby blockselector §7- Pick a block for the brush")
                        modMessage("§e/gobby brush §7- Toggle brush mode")
                        modMessage("§e/gobby sendcoords §7- Send your coords in chat")
                        modMessage("§e/gobby path <x> <y> <z> §7- Walk-pathfind to coordinates (BETA + WIP)")
                        modMessage("§e/gobby flypath <x> <y> <z> §7- Fly-pathfind to coordinates (BETA + WIP)")
                        modMessage("§e/gobby pathstop §7- Stop following a path")
                        modMessage("§e/gobby update §7- Force check for updates")
                        modMessage("§b§m                              ")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun modIdCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("modid")
                    .executes {
                        mc.executeLater { openModIdList() }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun petsCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("pets")
                    .executes {
                        mc.executeLater { openPetsList() }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun petRulesCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("petrules")
                    .executes {
                        mc.executeLater { openPetRules() }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun mobEspCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("mobesp")
                    .executes {
                        mc.executeLater { openMobEspList() }
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun pathCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("path")
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

                                                modMessage("Planning route to $x $y $z...")
                                                PathExecutor.beginLongPath(start, goal, TravelMode.WALK)
                                                Command.SINGLE_SUCCESS
                                            }
                                    )
                            )
                    )
            )
    }

    private fun pathStopCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("pathstop")
                    .executes {
                        PathExecutor.stop()
                        modMessage("§cPath following stopped.")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    private fun recordCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("record")
                    .then(ClientCommands.literal("start")
                        .executes {
                            MovementRecorder.start()
                            Command.SINGLE_SUCCESS
                        }
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                    .executes { ctx ->
                                        val tx = IntegerArgumentType.getInteger(ctx, "x")
                                        val ty = IntegerArgumentType.getInteger(ctx, "y")
                                        val tz = IntegerArgumentType.getInteger(ctx, "z")
                                        MovementRecorder.start(BlockPos(tx, ty, tz))
                                        Command.SINGLE_SUCCESS
                                    })))
                    )
                    .then(ClientCommands.literal("stop").executes {
                        MovementRecorder.stop()
                        Command.SINGLE_SUCCESS
                    })
            )
    }

    fun register(event: CommandRegisterEvent) {
        event.register(openConfig("gobby"))
        event.register(openConfig("gobbyclient"))
        event.register(sendCoordsCommand())
        event.register(blockSelectorCommand())
        event.register(helpCommand())
        event.register(modIdCommand())
        event.register(mobEspCommand())
        event.register(petsCommand())
        event.register(petRulesCommand())
        event.register(pathCommand())
        event.register(pathStopCommand())
        event.register(recordCommand())
    }
}
