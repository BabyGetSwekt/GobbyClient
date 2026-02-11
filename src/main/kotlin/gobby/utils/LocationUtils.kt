package gobby.utils

import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.network.ClientConnectedToServerEvent
import gobby.utils.ChatUtils.kuudraTierRegex
import gobby.utils.ChatUtils.modMessage
import gobby.utils.Utils.posX
import gobby.utils.Utils.posZ
import gobby.utils.timer.Executor
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.scoreboard.ScoreHolder
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.scoreboard.Team
import java.util.Collections

object LocationUtils {

    private val floorRegex = Regex("The Catacombs \\((\\w+)\\)\$")

    val TEXT_SCOREBOARD = ObjectArrayList<Text>()
    val STRING_SCOREBOARD = ObjectArrayList<String>()

    var onHypixel = false
    var onSkyblock = false
    var area = "Unknown"
    var location = "Unknown"
    var inDungeons = false
    var dungeonFloor = -1
    val inBoss: Boolean get() = inBoss()

    fun update() {
        val client = MinecraftClient.getInstance() ?: return
        updateScoreboard(client)
        updateTablist(client)
        updateFloor()
    }

    @SubscribeEvent
    fun onConnect(event: ClientConnectedToServerEvent) {
        val client = MinecraftClient.getInstance() ?: return
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

    fun updateScoreboard(client: MinecraftClient) {
        try {
            TEXT_SCOREBOARD.clear()
            STRING_SCOREBOARD.clear()

            val player = client.player ?: return

            val scoreboard: Scoreboard = player.networkHandler.scoreboard
            val objective: ScoreboardObjective? =
                scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.FROM_ID.apply(1))

            val textLines = ObjectArrayList<Text>()
            val stringLines = ObjectArrayList<String>()

            for (scoreHolder: ScoreHolder in scoreboard.knownScoreHolders) {
                val holderObjectives = scoreboard.getScoreHolderObjectives(scoreHolder)
                if (objective != null && holderObjectives.containsKey(objective)) {
                    val scObjName = Formatting.strip(objective.displayName.string)?.uppercase() ?: ""
                    onSkyblock = scObjName.contains("SKYBLOCK")
                    val team: Team? = scoreboard.getScoreHolderTeam(scoreHolder.nameForScoreboard)

                    if (team != null) {
                        val textLine = Text.empty()
                            .append(team.prefix.copy())
                            .append(team.suffix.copy())

                        val strLine = team.prefix.string + team.suffix.string

                        if (strLine.trim().isNotEmpty()) {
                            val formatted = Formatting.strip(strLine)
                            textLines.add(textLine)
                            stringLines.add(formatted)
                        }
                    }
                }
            }

            if (objective != null) {
                stringLines.add(objective.displayName.string)
                textLines.add(Text.empty().append(objective.displayName.copy()))

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

    fun updateTablist(client: MinecraftClient, debug: Boolean = false): List<String>? {
        val tabList = client.networkHandler?.playerList ?: return emptyList()
        val sortedTabList = tabList.sortedWith(
            compareBy<PlayerListEntry> { it.scoreboardTeam?.name ?: "" }
                .thenBy { it.profile.name.lowercase() }
        )
        for (entry in sortedTabList) {
            val line = entry.displayName?.string ?: continue
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


    private fun isConnectedToHypixel(client: MinecraftClient): Boolean {
        val serverAddress = client.currentServerEntry?.address?.lowercase() ?: ""
        val serverBrand = client.player?.networkHandler?.brand ?: ""
        return (serverAddress.isNotEmpty() && serverAddress.equals("ilovecatgirls.xyz", ignoreCase = true))
                || serverAddress.contains("hypixel.net")
                || serverAddress.contains("hypixel.io")
                || serverBrand.contains("Hypixel BungeeCord")
    }

    private fun updateFloor() {
        if (inDungeons) {
            floorRegex.find(area)?.groupValues?.get(1)?.let {
                if (inDungeons) {
                    floorRegex.find(area)?.groupValues?.get(1)?.let {
                        dungeonFloor = when (it) {
                            "Entrance" -> 0
                            else -> it.drop(1).toIntOrNull() ?: -1
                        }
                    }
                }
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
