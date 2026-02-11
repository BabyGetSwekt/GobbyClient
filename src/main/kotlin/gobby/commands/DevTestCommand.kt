package gobby.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.coloredModMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils
import gobby.utils.skyblockID
import gobby.utils.timer.Executor
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object DevTestCommand {

    private fun setTask(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
            .executes {
                val task = Executor.schedule(60) {
                    modMessage("Printing this shit after 3 seconds")
                }
                Command.SINGLE_SUCCESS
            }
    }

    private fun getItemID(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
            .executes {
                modMessage(mc?.player?.mainHandStack?.skyblockID ?: "No item or no skyblock ID")
                Command.SINGLE_SUCCESS
            }
    }

    private fun getMessage(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
            .executes {
                coloredModMessage("Heya")
                //updateTablist()
                LocationUtils.updateScoreboard(mc)
                val strings = LocationUtils.STRING_SCOREBOARD
                val texts = LocationUtils.TEXT_SCOREBOARD
                modMessage("========== STRING ==========")
                modMessage(strings)
                modMessage("========== TEXTS ==========")
                modMessage(texts)
                Command.SINGLE_SUCCESS
            }
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(setTask("settask"))
        event.register(getItemID("getitemid"))
        event.register(getMessage("testmessage"))
    }

}