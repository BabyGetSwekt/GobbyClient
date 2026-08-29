package gobby.features.petrules

import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils

private val BOSS_NAMES = listOf("Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Necron")

data class DungeonBoss(val floor: Int) : TriggerOption {

    override val id: String get() = if (floor == 0) "any" else "F$floor"

    override val label: String
        get() = if (floor == 0) "Any Dungeon Boss spawns" else "${BOSS_NAMES[floor - 1]} spawns"
}

object BossSpawn : TriggerCategory {

    override val id = "boss_spawn"

    override val title = "A [boss] spawns"

    override val options: List<TriggerOption> = listOf(DungeonBoss(0)) + BOSS_NAMES.indices.map { DungeonBoss(it + 1) }

    private var wasInBoss = false

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        val inBoss = LocationUtils.inDungeons && LocationUtils.inBoss
        if (inBoss == wasInBoss) return
        wasInBoss = inBoss
        val floor = LocationUtils.dungeonFloor
        if (!inBoss || floor !in BOSS_NAMES.indices.map { it + 1 }) return
        if (!PetRules.fire(this, DungeonBoss(floor).id)) PetRules.fire(this, DungeonBoss(0).id)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        wasInBoss = false
    }
}
