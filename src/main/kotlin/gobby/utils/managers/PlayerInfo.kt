package gobby.utils.managers

import gobby.utils.ConfigUtils

data class PlayerInfoData(
    var equippedWardrobeSlot: String = "",
    var equippedLoadoutSlot: String = "",
    var helmet: String = "",
    var chestplate: String = "",
    var leggings: String = "",
    var boots: String = ""
)

object PlayerInfo {
    private val config = ConfigUtils.makeConfig("playerInfo") { PlayerInfoData() }

    var equippedWardrobeSlot: String
        get() = config.data.equippedWardrobeSlot
        set(value) = config.edit { equippedWardrobeSlot = value }

    var equippedLoadoutSlot: String
        get() = config.data.equippedLoadoutSlot
        set(value) = config.edit { equippedLoadoutSlot = value }

    val helmet: String get() = config.data.helmet

    fun updateArmor(slot: String, uuid: String) = config.edit {
        when (slot) {
            "helmet" -> helmet = uuid
            "chestplate" -> chestplate = uuid
            "leggings" -> leggings = uuid
            else -> boots = uuid
        }
    }

    fun clearActiveSets() = config.edit {
        equippedWardrobeSlot = ""
        equippedLoadoutSlot = ""
    }
}
