@file:Suppress("DEPRECATION")

package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.equalsOneOf
import net.minecraft.component.ComponentHolder
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries


/**
 * Function to get the item data (NBT) from an item stack.
 */
@SuppressWarnings("deprecation")
val ComponentHolder.getItemData: NbtCompound
    get() = this.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt()

/**
 * Returns the skyblock ID of the item.
 */
val ComponentHolder.skyblockID: String
    get() = this.getItemData.getString("id", "")

/**
 * Returns the UUID of the item, if it exists.
 */
val ComponentHolder.getItemUUID: String?
    get() {
        val uuid = this.getItemData.getString("uuid", "")
        return uuid.ifEmpty { null }
    }

/**
 * Checks if the component holder is holding an item with the specified skyblock ID.
 */
fun ComponentHolder.isHolding(id: String): Boolean =
    this.skyblockID == id

fun ItemStack.getItemID(): String {
    return Registries.ITEM.getId(this.item).toString()
}
/**
 * Checks if the item stack’s Minecraft ID matches the given string.
 * Example: "minecraft:bow", "minecraft:blaze_rod"
 */
fun ItemStack.hasItemID(id: String): Boolean {
    val itemId = Registries.ITEM.getId(this.item).toString()
    return itemId == id
}

fun ItemStack.getLoreStrings(): List<String> {
    val lore = this.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).styledLines()
    return lore.map { it.string }
}

private fun ItemStack.findStatValue(statName: String): Double? {
    val regex = Regex("${Regex.escape(statName)}: \\+?([\\d,]+(?:\\.\\d+)?)")
    for (line in getLoreStrings()) {
        val match = regex.find(line) ?: continue
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }
    return null
}

data class Ability(
    val name: String,
    val abilityTrigger: String? = null,
    val manaCost: Int? = null,
    val soulflowCost: Int? = null,
    val cooldownSeconds: Int? = null
)

private val ABILITY_TRIGGERS = listOf(
    "LEFT CLICK",
    "RIGHT CLICK",
    "MIDDLE CLICK",
    "SNEAK LEFT CLICK",
    "SNEAK RIGHT CLICK",
    "SNEAK",
    "PASSIVE",
    "HOLD LEFT CLICK",
    "HOLD RIGHT CLICK",
    "ITEM ABILITY",
    "DROP"
).sortedByDescending { it.length }

private val ABILITY_HEADER_REGEX = Regex("^Ability:\\s+(.+?)\\s*$")
private val MANA_COST_REGEX = Regex("^Mana Cost:\\s+([\\d,]+)")
private val SOULFLOW_COST_REGEX = Regex("^Soulflow Cost:\\s+([\\d,]+)")
private val COOLDOWN_REGEX = Regex("^Cooldown:\\s+([\\d,]+)s")

private fun splitNameAndTrigger(raw: String): Pair<String, String?> {
    for (trigger in ABILITY_TRIGGERS) {
        if (raw.endsWith(trigger)) {
            val name = raw.removeSuffix(trigger).trimEnd()
            if (name.isNotEmpty() && name != raw) return name to trigger
        }
    }
    return raw to null
}

fun ItemStack.parseAbilities(): List<Ability> {
    val lines = getLoreStrings()
    val abilities = mutableListOf<Ability>()

    var currentName: String? = null
    var currentTrigger: String? = null
    var currentMana: Int? = null
    var currentSoulflow: Int? = null
    var currentCooldown: Int? = null

    fun flush() {
        if (currentName != null) {
            abilities.add(Ability(currentName!!, currentTrigger, currentMana, currentSoulflow, currentCooldown))
        }
        currentName = null
        currentTrigger = null
        currentMana = null
        currentSoulflow = null
        currentCooldown = null
    }

    for (raw in lines) {
        val line = raw.trim()
        val header = ABILITY_HEADER_REGEX.find(line)
        if (header != null) {
            flush()
            val (name, trigger) = splitNameAndTrigger(header.groupValues[1].trim())
            currentName = name
            currentTrigger = trigger
            continue
        }
        if (currentName == null) continue

        MANA_COST_REGEX.find(line)?.let {
            currentMana = it.groupValues[1].replace(",", "").toIntOrNull()
            return@let
        }
        SOULFLOW_COST_REGEX.find(line)?.let {
            currentSoulflow = it.groupValues[1].replace(",", "").toIntOrNull()
            return@let
        }
        COOLDOWN_REGEX.find(line)?.let {
            currentCooldown = it.groupValues[1].replace(",", "").toIntOrNull()
            return@let
        }
    }
    flush()
    return abilities
}

fun ItemStack.getDamage(): Double? = findStatValue("Damage")

fun ItemStack.getStrength(): Double? = findStatValue("Strength")

fun ItemStack.getCritChance(): Double? = findStatValue("Crit Chance")

fun ItemStack.getCritDamage(): Double? = findStatValue("Crit Damage")

fun ItemStack.getBonusAtkSpd(): Double? = findStatValue("Bonus Attack Speed")

fun ItemStack.getShotCooldown(): Double? = findStatValue("Shot Cooldown")

fun ItemStack.isEtherwarpable(): Boolean {
    if (!mc?.player?.mainHandStack?.skyblockID.equalsOneOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")) return false
    return this.getItemData.getBoolean("ethermerge").orElse(false)
}

fun ItemStack.getTunedTransmission(): Int {
    if (!mc?.player?.mainHandStack?.skyblockID.equalsOneOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")) return 0
    return this.getItemData.getInt("tuned_transmission").orElse(0)
}

val SPIRIT_MASK_IDS = setOf("SPIRIT_MASK", "STARRED_SPIRIT_MASK")
val BONZO_MASK_IDS = setOf("BONZO_MASK", "STARRED_BONZO_MASK")

fun getHelmetID(): String =
    mc.player?.inventory?.getStack(39)?.skyblockID ?: ""

fun hasHelmetWithID(id: String): Boolean =
    getHelmetID() == id

fun isHoldingSkyblockItem(vararg ids: String): Boolean {
    val player = mc.player ?: return false
    return player.mainHandStack.skyblockID in ids
}

fun swapToSkyblockItem(vararg ids: String): Boolean {
    val player = mc.player ?: return false
    if (player.mainHandStack.skyblockID in ids) return true

    val inventory = player.inventory
    for (i in 0..8) {
        val stack = inventory.getStack(i)
        if (stack.skyblockID in ids) {
            inventory.selectedSlot = i
            return true
        }
    }
    return false
}

fun countInHotbar(id: String): Int {
    val player = mc.player ?: return 0
    return (0..8).sumOf { i ->
        val stack = player.inventory.getStack(i)
        if (stack.skyblockID == id) stack.count else 0
    }
}

fun isHoldingAOTV(): Boolean =
    mc.player?.mainHandStack?.skyblockID == "ASPECT_OF_THE_VOID"

