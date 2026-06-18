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
import gobby.features.dungeons.DungeonMap
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
import gobby.utils.StructureCopier
import gobby.utils.MovementRecorder
import gobby.utils.skyblock.dungeon.RoomCopier
import com.mojang.brigadier.context.CommandContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

object GobbyCommand {

    private fun openConfig(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal(name)
            .executes {
                //? if >26.1.2
                mc.executeLater { mc.gui.setScreen(ClickGUI()) }
                //? if <=26.1.2
                /*mc.executeLater { mc.setScreen(ClickGUI()) }*/
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
                        mc.executeLater { ModIdHiderScreen.open() }
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

    private fun meshDumpCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("meshdump")
                    .then(
                        ClientCommands.argument("x", IntegerArgumentType.integer())
                            .then(
                                ClientCommands.argument("z", IntegerArgumentType.integer())
                                    .then(
                                        ClientCommands.argument("radius", IntegerArgumentType.integer(1, 256))
                                            .executes { context -> runMeshDump(context) }
                                    )
                            )
                    )
            )
    }

    private fun runMeshDump(context: CommandContext<FabricClientCommandSource?>): Int {
        val player = mc.player ?: return 0
        val cx = IntegerArgumentType.getInteger(context, "x")
        val cz = IntegerArgumentType.getInteger(context, "z")
        val radius = IntegerArgumentType.getInteger(context, "radius")
        val center = Vec3(cx + 0.5, player.y, cz + 0.5)
        val scanRange = (radius + 16).coerceAtMost(192)
        val mesh = WalkMeshScanner.scan(player.position(), center, scanRange)
        val polys = mesh.polygons.filter {
            val midX = (it.minX + it.maxX) / 2.0
            val midZ = (it.minZ + it.maxZ) / 2.0
            abs(midX - cx) <= radius && abs(midZ - cz) <= radius
        }
        val included = polys.toHashSet()
        val sb = StringBuilder()
        sb.append("{\n  \"center\": [$cx, $cz], \"radius\": $radius,\n")
        sb.append("  \"totalPolysInMesh\": ${mesh.polygons.size},\n")
        sb.append("  \"polysInRegion\": ${polys.size},\n")
        sb.append("  \"polygons\": [\n")
        for ((idx, p) in polys.withIndex()) {
            val portalsJson = p.portals.joinToString(",") { pt ->
                val opp = pt.opposite(p)
                val deltaY = opp.surfaceY - p.surfaceY
                val inRegion = if (opp in included) "true" else "false"
                "{\"to\":${opp.id},\"toY\":${"%.3f".format(Locale.US,opp.surfaceY)},\"dY\":${"%.3f".format(Locale.US,deltaY)},\"step\":${pt.isHeightStep},\"inRegion\":$inRegion," +
                    "\"l\":[${"%.2f".format(Locale.US, pt.left.x)},${"%.2f".format(Locale.US, pt.left.y)},${"%.2f".format(Locale.US, pt.left.z)}]," +
                    "\"r\":[${"%.2f".format(Locale.US, pt.right.x)},${"%.2f".format(Locale.US, pt.right.y)},${"%.2f".format(Locale.US, pt.right.z)}]}"
            }
            sb.append("    {\"id\":${p.id},\"x\":[${p.minX},${p.maxX}],\"z\":[${p.minZ},${p.maxZ}],")
            sb.append("\"y\":${"%.3f".format(Locale.US,p.surfaceY)},\"clear\":${p.wallClearance},")
            sb.append("\"portals\":[$portalsJson]}")
            if (idx < polys.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n}\n")
        val dir = File("./config/gobbyclientFabric/")
        dir.mkdirs()
        val file = File(dir, "meshdump_${cx}_${cz}_r${radius}.json")
        file.writeText(sb.toString())
        modMessage("§aMesh dump: §f${polys.size}§a polys in region (mesh total: §f${mesh.polygons.size}§a)")
        modMessage("§7Saved to §f${file.absolutePath}")
        return Command.SINGLE_SUCCESS
    }

    private fun pathDebugCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("pathdebug")
                    .then(
                        ClientCommands.argument("x", IntegerArgumentType.integer())
                            .then(
                                ClientCommands.argument("y", IntegerArgumentType.integer())
                                    .then(
                                        ClientCommands.argument("z", IntegerArgumentType.integer())
                                            .executes { context -> runPathDebug(context) }
                                    )
                            )
                    )
            )
    }

    private fun runPathDebug(context: CommandContext<FabricClientCommandSource?>): Int {
        val player = mc.player ?: return 0
        val x = IntegerArgumentType.getInteger(context, "x")
        val y = IntegerArgumentType.getInteger(context, "y")
        val z = IntegerArgumentType.getInteger(context, "z")
        val start = player.position()
        val goal = Vec3(x + 0.5, y.toDouble(), z + 0.5)
        val mesh = WalkMeshScanner.scan(start, goal, 128)
        val divider = "§b§m                              "
        val sb = StringBuilder()
        sb.append(divider).append('\n')
        sb.append("§ePathDebug §7start=§f(${"%.1f".format(start.x)},${"%.1f".format(start.y)},${"%.1f".format(start.z)}) §7goal=§f($x,$y,$z)").append('\n')
        sb.append("§ePolys in mesh: §a${mesh.polygons.size}").append('\n')
        if (mesh.polygons.isEmpty()) {
            sb.append("§cMesh is empty — no walkable surfaces.").append('\n')
            sb.append(divider)
            modMessage(sb.toString())
            return Command.SINGLE_SUCCESS
        }
        val startContain = mesh.polygonContaining(start)
        val startPoly = startContain ?: mesh.nearestPolygon(start)
        val goalContain = mesh.polygonContaining(goal)
        val goalPoly = goalContain ?: mesh.nearestPolygon(goal)
        val startTag = if (startContain != null) "§acontained" else "§enearest"
        val goalTag = if (goalContain != null) "§acontained" else "§enearest"
        sb.append("§eStart poly: §f#${startPoly?.id} §7($startTag, dist=${"%.1f".format(startPoly?.centerVec()?.distanceTo(start) ?: 0.0)})").append('\n')
        sb.append("§eGoal poly:  §f#${goalPoly?.id} §7($goalTag, dist=${"%.1f".format(goalPoly?.centerVec()?.distanceTo(goal) ?: 0.0)})").append('\n')
        if (startPoly == null || goalPoly == null) {
            sb.append("§cCannot identify both endpoints.").append('\n')
            sb.append(divider)
            modMessage(sb.toString())
            return Command.SINGLE_SUCCESS
        }
        val fullComponent = floodFillFromPoly(startPoly, respectClimbLimit = false)
        val astarComponent = floodFillFromPoly(startPoly, respectClimbLimit = true)
        sb.append("§eReachable (any portal):    §a${fullComponent.size}§e/§a${mesh.polygons.size} §7polys").append('\n')
        sb.append("§eReachable (A* climb ≤1.25): §a${astarComponent.size}§e/§a${mesh.polygons.size} §7polys").append('\n')
        val inFull = goalPoly in fullComponent
        val inAstar = goalPoly in astarComponent
        when {
            inAstar -> sb.append("§a✓ Goal is reachable by A* — should plan successfully.").append('\n')
            inFull && !inAstar -> {
                sb.append("§c✗ Goal is mesh-connected but A* can't reach it: needs a climb > 1.25 blocks.").append('\n')
                appendClimbBarrier(sb, astarComponent)
            }
            else -> sb.append("§c✗ Goal is in a DIFFERENT mesh component — missing portal.").append('\n')
        }
        sb.append(divider)
        modMessage(sb.toString())
        diagnoseVoxelSolverAsync(start, goal)
        return Command.SINGLE_SUCCESS
    }

    private fun diagnoseVoxelSolverAsync(start: Vec3, goal: Vec3) {
        val clock = Clock()
        RouteEngine.planAsync(start, goal, TravelMode.WALK).thenAccept { plan ->
            val ms = clock.getTime()
            mc.execute {
                val verdict = when {
                    plan is RoutePlan.Failed -> "§c✗ VoxelGroundSolver: FAILED in ${ms}ms (solve ${PlanStats.lastSolveMs}ms, ${PlanStats.lastWaypointCount} wps)"
                    plan is RoutePlan.Ground && plan.complete -> "§a✓ VoxelGroundSolver: COMPLETE path, ${plan.waypoints.size} waypoints in ${ms}ms"
                    plan is RoutePlan.Ground -> "§e◐ VoxelGroundSolver: PARTIAL path, ${plan.waypoints.size} waypoints in ${ms}ms (will replan at end)"
                    else -> "§7VoxelGroundSolver: ${plan::class.simpleName}, ${plan.waypoints.size} wps in ${ms}ms"
                }
                modMessage(verdict)
            }
        }
    }

    private fun appendClimbBarrier(sb: StringBuilder, astarComponent: Set<WalkPolygon>) {
        val maxClimb = BlockCache.MAX_JUMP_RISE
        val topReachable = astarComponent.maxByOrNull { it.surfaceY } ?: return
        val topCenter = topReachable.centerVec()
        sb.append("§eHighest A*-reachable poly: §f#${topReachable.id} y=${"%.2f".format(topReachable.surfaceY)} at §7(${topCenter.x.toInt()}, ${topCenter.z.toInt()})").append('\n')
        val blockedUp = topReachable.portals
            .map { it.opposite(topReachable) }
            .filter { it.surfaceY - topReachable.surfaceY > maxClimb }
            .sortedBy { it.surfaceY }
            .take(5)
        if (blockedUp.isEmpty()) {
            sb.append("§7  (no upward-blocked portals from top poly — barrier is deeper in the mesh)").append('\n')
            return
        }
        sb.append("§eBlocked upward portals from there:").append('\n')
        for (poly in blockedUp) {
            val delta = poly.surfaceY - topReachable.surfaceY
            val c = poly.centerVec()
            sb.append("§7  → poly#${poly.id} y=${"%.2f".format(poly.surfaceY)} (Δ=${"%.2f".format(delta)}) at (${c.x.toInt()}, ${c.z.toInt()})").append('\n')
        }
    }

    private fun floodFillFromPoly(
        start: WalkPolygon,
        respectClimbLimit: Boolean
    ): Set<WalkPolygon> {
        val maxClimb = BlockCache.MAX_JUMP_RISE
        val visited = HashSet<WalkPolygon>()
        val queue = ArrayDeque<WalkPolygon>()
        queue.add(start); visited.add(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (portal in cur.portals) {
                val nxt = portal.opposite(cur)
                if (respectClimbLimit && nxt.surfaceY - cur.surfaceY > maxClimb) continue
                if (visited.add(nxt)) queue.add(nxt)
            }
        }
        return visited
    }

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

//    private fun updateCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
//        return ClientCommands.literal("gobby")
//            .then(
//                ClientCommands.literal("update")
//                    .executes {
//                        AutoUpdater.forceCheck()
//                        Command.SINGLE_SUCCESS
//                    }
//            )
//    }

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
                        //? if >26.1.2
                        mc.executeLater { mc.gui.setScreen(HudEditor()) }
                        //? if <=26.1.2
                        /*mc.executeLater { mc.setScreen(HudEditor()) }*/
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
        event.register(flyPathCommand())
        event.register(pathStopCommand())
        event.register(pathDebugCommand())
        event.register(meshDumpCommand())
        event.register(recordCommand())
//        event.register(updateCommand())
        event.register(hudCommand())
        event.register(lookingAtCommand())
        event.register(mapCommand())
        event.register(getCoreCommand())
        event.register(saveMapCommand())
        event.register(copyMapCommand())
        event.register(getItemIDCommand())
        event.register(printSlotCommand())
        event.register(copyStructureCommand())
        event.register(pasteStructureCommand())
        event.register(copyRoomCommand())
    }

    private fun copyRoomCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(ClientCommands.literal("copyRoom").executes {
                RoomCopier.copyCurrentRoom()
                Command.SINGLE_SUCCESS
            })
    }

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

}
