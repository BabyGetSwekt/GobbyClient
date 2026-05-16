@file:Suppress("DEPRECATION")

package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.equalsOneOf
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.registries.BuiltInRegistries


/**
 * Function to get the item data (NBT) from an item stack.
 */
@SuppressWarnings("deprecation")
val DataComponentHolder.getItemData: CompoundTag
    get() = this.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

/**
 * Returns the skyblock ID of the item.
 */
val DataComponentHolder.skyblockID: String
    get() = this.getItemData.getStringOr("id", "")

/**
 * Returns the UUID of the item, if it exists.
 */
val DataComponentHolder.getItemUUID: String?
    get() {
        val uuid = this.getItemData.getStringOr("uuid", "")
        return uuid.ifEmpty { null }
    }

/**
 * Checks if the component holder is holding an item with the specified skyblock ID.
 */
fun DataComponentHolder.isHolding(id: String): Boolean =
    this.skyblockID == id

fun ItemStack.getItemID(): String {
    return BuiltInRegistries.ITEM.getKey(this.item).toString()
}
/**
 * Checks if the item stack’s Minecraft ID matches the given string.
 * Example: "minecraft:bow", "minecraft:blaze_rod"
 */
fun ItemStack.hasItemID(id: String): Boolean {
    val itemId = BuiltInRegistries.ITEM.getKey(this.item).toString()
    return itemId == id
}

fun ItemStack.getLoreStrings(): List<String> {
    val lore = this.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines()
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
private const val BASE_TRANSMISSION_RANGE = 8

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

fun ItemStack.getBowShootSpeedMs(): Long = (this.getShotCooldown()?.times(1000)?.toLong() ?: 250L).coerceIn(50L, 2000L)

fun ItemStack.isEtherwarpable(): Boolean {
    if (!mc?.player?.mainHandItem?.skyblockID.equalsOneOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")) return false
    return this.getItemData.getBoolean("ethermerge").orElse(false)
}

fun ItemStack.getTunedTransmission(): Int {
    if (!mc?.player?.mainHandItem?.skyblockID.equalsOneOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")) return 0
    return this.getItemData.getInt("tuned_transmission").orElse(0)
}

fun ItemStack.isShortbow(): Boolean = this.hoverName.string.contains("Shortbow") || this.skyblockID == "TERMINATOR"

fun ItemStack.getInstantTransmissionRange(): Int = BASE_TRANSMISSION_RANGE + getTunedTransmission()

val SPIRIT_MASK_IDS = setOf("SPIRIT_MASK", "STARRED_SPIRIT_MASK")
val BONZO_MASK_IDS = setOf("BONZO_MASK", "STARRED_BONZO_MASK")

fun getHelmetID(): String =
    mc.player?.inventoryMenu?.slots?.getOrNull(5)?.item?.skyblockID ?: ""

fun hasHelmetWithID(id: String): Boolean =
    getHelmetID() == id

fun isHoldingSkyblockItem(vararg ids: String): Boolean {
    val player = mc.player ?: return false
    return player.mainHandItem.skyblockID in ids
}

fun findHotbarSlot(vararg ids: String): Int {
    val player = mc.player ?: return -1
    for (i in 0..8) {
        if (player.inventory.getItem(i).skyblockID in ids) return i
    }
    return -1
}

fun swapToSkyblockItem(vararg ids: String): Boolean {
    val player = mc.player ?: return false
    if (player.mainHandItem.skyblockID in ids) return true
    val slot = findHotbarSlot(*ids)
    if (slot < 0) return false
    player.inventory.selectedSlot = slot
    return true
}

fun countInHotbar(id: String): Int {
    val player = mc.player ?: return 0
    return (0..8).sumOf { i ->
        val stack = player.inventory.getItem(i)
        if (stack.skyblockID == id) stack.count else 0
    }
}

fun isHoldingAOTV(): Boolean =
    mc.player?.mainHandItem?.skyblockID == "ASPECT_OF_THE_VOID"
