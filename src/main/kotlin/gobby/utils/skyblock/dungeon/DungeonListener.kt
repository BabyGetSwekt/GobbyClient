package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.Gobbyclient
import gobby.events.ChatReceivedEvent
import gobby.events.DungeonRunEndEvent
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonTeammate
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.ChatFormatting

object DungeonListener {

    private val teammateRegex = Regex(
        """(?:\[(?!\d+])[^]]{1,24}]\s+)*(?:\[(\d+)]\s+)?(?:\[[^]]{1,24}]\s+)*(\w{1,16})(?:\s+([^()]*?))?\s*\((Archer|Berserker|Berserk|Mage|Healer|Tank|DEAD)(?:\s+([IVXLCDM0]+))?\)""",
        RegexOption.IGNORE_CASE
    )

    private val RUN_END = Regex("""^\s*> EXTRA STATS <$""")

    fun teammateNameOf(entry: PlayerInfo): String? = teammateMatch(entry)?.groupValues?.get(2)

    private fun teammateMatch(entry: PlayerInfo): MatchResult? {
        val line = ChatFormatting.stripFormatting(entry.tabListDisplayName?.string ?: return null)?.trim() ?: return null
        return if (line.isEmpty()) null else teammateRegex.find(line)
    }

    val teammates = mutableMapOf<String, DungeonTeammate>()
    var doorOpener = ""
        private set
    var isBloodOpened = false
        private set
    var inP3 = false
        private set

    val endDialogues = mapOf(
        1 to listOf("[BOSS] Bonzo: Just you wait..."),
        2 to listOf("[BOSS] Scarf: His technique.. is too advanced.."),
        3 to listOf("[BOSS] The Professor: I can't let my Master hear about this.. he'll kill m-"),
        4 to listOf("[BOSS] Thorn: Congratulations humans, you may pass."),
        5 to listOf(
            "[BOSS] Vendetta Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Crossed Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Hockey Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Doctor Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Frog Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Smile Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Scream Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Purple Livid: My shadows are everywhere, THEY WILL FIND YOU!!",
            "[BOSS] Arcane Livid: My shadows are everywhere, THEY WILL FIND YOU!!"
        ),
        6 to listOf("[BOSS] Sadan: NOOOOOOOOO!!! THIS IS IMPOSSIBLE!!"),
        7 to listOf("[BOSS] The Wither King: Incredible. You did what I couldn't do myself.")
    )

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (!inDungeons) return
        if (event.packet !is ClientboundPlayerInfoUpdatePacket) return

        val tabEntries = mc.connection?.listedOnlinePlayers ?: return
        updateDungeonTeammates(tabEntries)
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inDungeons) return
        val message = event.message

        if (RUN_END.matches(message)) {
            Gobbyclient.EVENT_MANAGER.publish(DungeonRunEndEvent())
            return
        }

        if (message == "[BOSS] Goldor: Who dares trespass into my domain?") {
            inP3 = true
            return
        }
        if (message == "The Core entrance is opening!") {
            inP3 = false
            return
        }

        if (inBoss) return

        if (message == "The BLOOD DOOR has been opened!") {
            isBloodOpened = true
            doorOpener = ""
            return
        }

        val opener = message.substringBefore(" opened a WITHER door!")
        if ("$opener opened a WITHER door!" != message) return
        if (opener !in teammates) return
        doorOpener = opener
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (inBoss && doorOpener.isNotEmpty()) doorOpener = ""
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        teammates.clear()
        doorOpener = ""
        isBloodOpened = false
        inP3 = false
    }

    fun refreshTeammates() {
        val tabList = mc.connection?.listedOnlinePlayers ?: return
        updateDungeonTeammates(tabList)
    }

    private fun updateDungeonTeammates(tabList: Collection<PlayerInfo>) {
        for (entry in tabList.toList()) {
            val match = teammateMatch(entry) ?: continue
            val (levelStr, name, emblem, className, classLevel) = match.destructured
            val previous = teammates[name]
            val dead = className.equals("DEAD", ignoreCase = true)
            val dungeonClass = if (dead) previous?.dungeonClass ?: DungeonClass.Unknown
            else DungeonClass.entries.firstOrNull { className.startsWith(it.name, ignoreCase = true) } ?: DungeonClass.Unknown

            teammates[name] = DungeonTeammate(
                name = name,
                dungeonClass = dungeonClass,
                classLevel = if (dead) previous?.classLevel.orEmpty() else classLevel,
                playerLevel = levelStr.toIntOrNull() ?: previous?.playerLevel ?: 0,
                emblem = emblem.ifEmpty { null } ?: previous?.emblem
            )
        }
    }
}
