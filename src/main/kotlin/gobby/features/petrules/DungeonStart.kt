package gobby.features.petrules

import gobby.events.DungeonStartEvent
import gobby.events.core.SubscribeEvent

private val ROMAN = listOf("Entrance", "I", "II", "III", "IV", "V", "VI", "VII")

data class CatacombsFloor(val floor: Int, val masterMode: Boolean) : TriggerOption {

    override val id: String get() = if (masterMode) "M$floor" else "F$floor"

    override val label: String
        get() = when {
            floor == 0 -> "You begin Catacombs Entrance"
            masterMode -> "You begin Catacombs Master Mode Floor ${ROMAN[floor]}"
            else -> "You begin Catacombs Floor ${ROMAN[floor]}"
        }
}

object DungeonStart : TriggerCategory {

    override val id = "dungeon_start"

    override val title = "You begin Catacombs [Floor]"

    override val options: List<TriggerOption> =
        (0..7).map { CatacombsFloor(it, false) } + (1..7).map { CatacombsFloor(it, true) }

    @SubscribeEvent
    fun onDungeonStart(event: DungeonStartEvent) {
        if (event.floor < 0) return
        PetRules.fire(this, CatacombsFloor(event.floor, event.masterMode).id)
    }
}
