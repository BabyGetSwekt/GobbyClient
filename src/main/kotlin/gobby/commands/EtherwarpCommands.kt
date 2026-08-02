package gobby.commands

import com.mojang.brigadier.Command.SINGLE_SUCCESS
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import gobby.features.dungeons.EtherwarpEsp
import gobby.features.dungeons.EtherwarpRoutes
import gobby.features.dungeons.EtherwarpRoutes.Revert
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.tiles.Room
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object EtherwarpCommands {

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(
            ClientCommands.literal("gobby").then(
                ClientCommands.literal("etherwarp")
                    .then(ClientCommands.literal("add").executes { runGuarded(::add) })
                    .then(ClientCommands.literal("remove").executes { runGuarded(::remove) })
                    .then(ClientCommands.literal("clearroom").executes { runGuarded(::clearRoom) })
                    .then(ClientCommands.literal("revert").executes { runGuarded(::revert) })
                    .then(ClientCommands.literal("help").executes { help() })
            )
        )
    }

    private fun runGuarded(action: () -> Unit): Int {
        when {
            !LocationUtils.inDungeons -> errorMessage("You must be in a dungeon")
            LocationUtils.dungeonFloor == 7 && LocationUtils.inBoss -> errorMessage("You can't use this in the F7 boss")
            else -> { action(); EtherwarpEsp.refresh() }
        }
        return SINGLE_SUCCESS
    }

    private fun lookedAtBlock(): Pair<Room, BlockPos>? {
        val hit = mc.hitResult
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) {
            errorMessage("You're not looking at a block")
            return null
        }
        val room = ScanUtils.currentRoom ?: run { errorMessage("You must be in a scanned room"); return null }
        return room to hit.blockPos
    }

    private fun add() {
        val (room, pos) = lookedAtBlock() ?: return
        val blockName = BuiltInRegistries.BLOCK.getKey(mc.level?.getBlockState(pos)?.block ?: return).path.uppercase()
        val rel = EtherwarpRoutes.relativeStr(room, pos)
        if (EtherwarpRoutes.add(room.data.name, rel)) modMessage("§aAdded $blockName at $rel in ${room.data.name} to triggerbot!")
        else errorMessage("That block is already an etherwarp spot")
    }

    private fun remove() {
        val (room, pos) = lookedAtBlock() ?: return
        val rel = EtherwarpRoutes.relativeStr(room, pos)
        if (EtherwarpRoutes.remove(room.data.name, rel)) modMessage("Correctly removed etherwarp spot")
        else errorMessage("The block you're looking at is not an etherwarp spot")
    }

    private fun clearRoom() {
        val room = ScanUtils.currentRoom ?: run { errorMessage("You must be in a scanned room"); return }
        val count = EtherwarpRoutes.clear(room.data.name)
        if (count > 0) modMessage("Removed every etherwarp spot from ${room.data.name}")
        else errorMessage("No spots were found in ${room.data.name}")
    }

    private fun revert() {
        when (val result = EtherwarpRoutes.revert()) {
            is Revert.Removed -> modMessage("Etherwarp position at ${result.pos} in ${result.room} has been removed")
            is Revert.Added -> modMessage("Etherwarp position at ${result.pos} in ${result.room} has been added back")
            is Revert.Cleared -> modMessage("All ${result.count} etherwarp positions have been reverted")
            null -> errorMessage("Nothing to revert")
        }
    }

    private fun help(): Int {
        modMessage("§b§m                              ")
        modMessage("§eEtherwarp Triggerbot §7help guide")
        modMessage("§eadd §7- Adds the block you're looking at into triggerbot list")
        modMessage("§eremove §7- Removes the block you're looking at from the triggerbot list")
        modMessage("§eclearroom §7- Removes all etherwarp spots from the room you're in")
        modMessage("§erevert §7- Reverts either an add, remove or clearroom command")
        modMessage("§b§m                              ")
        return SINGLE_SUCCESS
    }
}
