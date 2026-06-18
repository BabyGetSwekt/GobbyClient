package gobby.utils

import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.features.developer.DevMode
import gobby.events.network.ClientConnectedToServerEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.Utils.posX
import gobby.utils.Utils.posZ
import gobby.utils.timer.Executor
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import java.util.Collections

enum class Island(val location: String) {
    BACKWATER_BAYOU("Backwater Bayou"),
    CRIMSON_ISLE("Crimson Isle"),
    CRYSTAL_HOLLOWS("Crystal Hollows"),
    DARK_AUCTION("Dark Auction"),
    DEEP_CAVERNS("Deep Caverns"),
    DUNGEON("Catacombs"),
    DUNGEON_HUB("Dungeon Hub"),
    DWARVEN_MINES("Dwarven Mines"),
    END("The End"),
    FARMING_ISLAND("The Farming Islands"),
    GALATEA("Galatea"),
    GARDEN("Garden"),
    GOLD_MINE("Gold Mine"),
    HUB("Hub"),
    JERRY_WORKSHOP("Jerry's Workshop"),
    KUUDRA("Kuudra"),
    MINESHAFT("Mineshaft"),
    PARK("The Park"),
    PRIVATE_ISLAND("Private Island"),
    RIFT("The Rift"),
    SINGLEPLAYER("Singleplayer"),
    SPIDERS_DEN("Spider's Den"),
}

object LocationUtils {

    private val floorRegex = Regex("The Catacombs \\((\\w+)\\)\$")

    fun isIn(island: Island): Boolean = location == island.location

    val TEXT_SCOREBOARD = ObjectArrayList<Component>()
    val STRING_SCOREBOARD = ObjectArrayList<String>()

    var onHypixel = false
    var onSkyblock = false
    var area = "Unknown"
    var location = "Unknown"
    private var _inDungeons = false
    var inDungeons: Boolean
        get() = _inDungeons || (DevMode.enabled && DevMode.forceDungeons)
        set(value) { _inDungeons = value }
    private var _dungeonFloor = -1
    var dungeonFloor: Int
        get() = if (DevMode.enabled && DevMode.forceDungeons && DevMode.forceFloor7) 7 else _dungeonFloor
        set(value) { _dungeonFloor = value }
    val inBoss: Boolean get() = inBoss()

    fun update() {
        val client = Minecraft.getInstance() ?: return
        if (client.hasSingleplayerServer()) {
            location = Island.SINGLEPLAYER.location
            area = Island.SINGLEPLAYER.location
            return
        }
        updateScoreboard(client)
        updateTablist(client)
        updateFloor()
    }

    @SubscribeEvent
    fun onConnect(event: ClientConnectedToServerEvent) {
        val client = Minecraft.getInstance() ?: return
        Executor.schedule(70) {
            onHypixel = isConnectedToHypixel(client)
        }
    }

    @SubscribeEvent
    fun onWorldJoin(event: WorldLoadEvent) {
        onSkyblock = false
        location = "Unknown"
        area = "Unknown"
        inDungeons = false
        dungeonFloor = -1
    }

    fun updateScoreboard(client: Minecraft) {
        try {
            TEXT_SCOREBOARD.clear()
            STRING_SCOREBOARD.clear()

            val player = client.player ?: return

            val scoreboard: Scoreboard = player.connection.scoreboard()
            val objective: Objective? =
                scoreboard.getDisplayObjective(DisplaySlot.BY_ID.apply(1))

            val textLines = ObjectArrayList<Component>()
            val stringLines = ObjectArrayList<String>()

            for (scoreHolder: ScoreHolder in scoreboard.trackedPlayers) {
                val holderObjectives = scoreboard.listPlayerScores(scoreHolder)
                if (objective != null && holderObjectives.containsKey(objective)) {
                    val scObjName = ChatFormatting.stripFormatting(objective.displayName.string)?.uppercase() ?: ""
                    onSkyblock = scObjName.contains("SKYBLOCK")
                    val team = scoreboard.getPlayersTeam(scoreHolder.scoreboardName)

                    if (team != null) {
                        val textLine = Component.empty()
                            .append(team.playerPrefix.copy())
                            .append(team.playerSuffix.copy())

                        val strLine = team.playerPrefix.string + team.playerSuffix.string

                        if (strLine.trim().isNotEmpty()) {
                            val formatted = ChatFormatting.stripFormatting(strLine)
                            textLines.add(textLine)
                            stringLines.add(formatted)
                        }
                    }
                }
            }

            if (objective != null) {
                stringLines.add(objective.displayName.string)
                textLines.add(Component.empty().append(objective.displayName.copy()))

                Collections.reverse(stringLines)
                Collections.reverse(textLines)
            }

            TEXT_SCOREBOARD.addAll(textLines)
            STRING_SCOREBOARD.addAll(stringLines)


            area = if (onSkyblock) getIslandArea() else "Unknown"

        } catch (e: NullPointerException) {
            modMessage(e)
        }
    }

    fun updateTablist(client: Minecraft, debug: Boolean = false): List<String>? {
        val tabList = client.connection?.listedOnlinePlayers ?: return emptyList()
        val sortedTabList = tabList.sortedWith(
            compareBy<PlayerInfo> { it.team?.name ?: "" }
                .thenBy { it.profile.name.lowercase() }
        )
        for (entry in sortedTabList) {
            val raw = entry.tabListDisplayName?.string ?: continue
            val line = ChatFormatting.stripFormatting(raw) ?: continue
            if (line.isEmpty()) continue
            if (debug) modMessage("Player in tab: $line")

            if (line.contains("Area: ")) {
                val index = line.indexOf("Area: ") + "Area: ".length
                val areaName = line.substring(index).trim()
                location = areaName
                if (debug) modMessage("Found area in tablist: $areaName")
            }

            if (line.contains("Dungeon: Catacombs")) {
                inDungeons = true
            }
        }

        return tabList.map { it.profile.name }
    }

    fun getIslandArea(): String {
        return STRING_SCOREBOARD.firstOrNull {
            it.contains("⏣") || it.contains("ф")
        }?.replace(Regex("[⏣ф]"), "")?.trim() ?: "Unknown"
    }


    private fun isConnectedToHypixel(client: Minecraft): Boolean {
        val serverAddress = client.currentServer?.ip?.lowercase() ?: ""
        val serverBrand = client.connection?.serverBrand() ?: ""
        return (serverAddress.isNotEmpty() && serverAddress.equals("ilovecatgirls.xyz", ignoreCase = true))
                || serverAddress.contains("hypixel.net")
                || serverAddress.contains("hypixel.io")
                || serverBrand.contains("Hypixel BungeeCord")
                || serverBrand.contains("hypixelp3sim.zapto.org")
    }

    private fun updateFloor() {
        if (!inDungeons) return

        val client = Minecraft.getInstance() ?: return
        val serverAddress = client.currentServer?.ip?.lowercase() ?: ""
        if (serverAddress.contains("hypixelp3sim.zapto.org")) {
            dungeonFloor = 7
            return
        }

        floorRegex.find(area)?.groupValues?.get(1)?.let {
            dungeonFloor = when (it) {
                "Entrance" -> 0
                else -> it.drop(1).toIntOrNull() ?: -1
            }
        }
    }

    private fun inBoss(): Boolean {
        if (dungeonFloor == -1) return false
        return when (dungeonFloor) {
            1 -> posX > -71 && posZ > -39
            in 2..4 -> posX > -39 && posZ > -39
            in 5..6 -> posX > -39 && posZ > -7
            7 -> posX > -7 && posZ > -7
            else -> false
        }
    }
}
