package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.partyMessage
import gobby.utils.ChatUtils.partyMessageRegex
import gobby.utils.ChatUtils.sendCommand

object PartyCommands : Module("Party Commands", "Enables party commands (!help in party chat)", Category.SKYBLOCK) {

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (mc.player == null || mc.world == null || !enabled) return
        val rawMessage = event.message
        val match = partyMessageRegex.matchEntire(rawMessage) ?: return
        val (rank, username, message) = match.destructured


        if (!message.startsWith("!")) return
        val split = message.trim().split("\\s+".toRegex())
        val command = split.firstOrNull() ?: return
        val args = split.drop(1)

        //modMessage("Command: $command")
        //modMessage("Args: $args")

        when (command.lowercase()) {
            "!help" -> handleHelp(username, args)
            "!warp" -> sendCommand("p warp")
            "!pt" -> sendCommand("p transfer $username")
            "!fps" -> partyMessage("FPS: ${mc.currentFps}")
        }
    }

    private fun handleHelp(sender: String, args: List<String>) {
        modMessage("Existing commands: !warp, !pt, !fps")
    }

    // TODO: Make it so it so some commands only work if you're the party leader
}
