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

internal object GobbyCommandStorage {

    private const val DUMP_FILE_NAME = "gobby-blockcache.dump"
    private const val DUMP_MIN_Y = 40
    private const val DUMP_MAX_Y = 140

    private fun copyStructureCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("copyStructure")
                .then(ClientCommands.literal("1").executes {
                    StructureCopier.setPos1()
                    Command.SINGLE_SUCCESS
                })
                .then(ClientCommands.literal("2").executes {
                    StructureCopier.setPos2()
                    Command.SINGLE_SUCCESS
                })
                .then(ClientCommands.literal("stop").executes {
                    StructureCopier.stop()
                    Command.SINGLE_SUCCESS
                })
            )
    }

    private fun pasteStructureCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("pasteStructure")
                .executes {
                    if (LocationUtils.onHypixel) {
                        errorMessage("Cannot paste on Hypixel. Join orange0513.com:30030 (singleplayer world) first.")
                    } else {
                        StructureCopier.pasteLatest()
                    }
                    Command.SINGLE_SUCCESS
                }
            )
    }

    private fun saveMapCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("saveMap")
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
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("copyMap")
                .executes {
                    if (!mc.hasSingleplayerServer()) {
                        errorMessage("This command can only be used in singleplayer")
                    } else {
                        DungeonMapSaver.copyMap()
                    }
                    Command.SINGLE_SUCCESS
                }
            )
    }

    private fun printSlotCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("printSlot")
                .then(ClientCommands.argument("slot", IntegerArgumentType.integer())
                    .executes { ctx ->
                        val slot = IntegerArgumentType.getInteger(ctx, "slot")
                        val id = mc.player?.inventoryMenu?.slots?.getOrNull(slot)?.item?.skyblockID ?: "none"
                        modMessage("§eslot §f$slot §7→ §a${id.ifEmpty { "none" }}")
                        Command.SINGLE_SUCCESS
                    }
                )
            )
    }

    private fun getItemIDCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("getItemID")
                .executes {
                    val player = mc.player ?: return@executes 0
                    val stack = player.mainHandItem
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

    fun register(event: CommandRegisterEvent) {
        event.register(copyStructureCommand())
        event.register(pasteStructureCommand())
        event.register(saveMapCommand())
        event.register(copyMapCommand())
        event.register(printSlotCommand())
        event.register(getItemIDCommand())
    }
}
