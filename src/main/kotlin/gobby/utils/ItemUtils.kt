@file:Suppress("DEPRECATION")

package gobby.utils

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

private fun ItemStack.getLoreStrings(): List<String> {
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

fun ItemStack.getDamage(): Double? = findStatValue("Damage")

fun ItemStack.getStrength(): Double? = findStatValue("Strength")

fun ItemStack.getCritChance(): Double? = findStatValue("Crit Chance")

fun ItemStack.getCritDamage(): Double? = findStatValue("Crit Damage")

fun ItemStack.getBonusAtkSpd(): Double? = findStatValue("Bonus Attack Speed")

fun ItemStack.getShotCooldown(): Double? = findStatValue("Shot Cooldown")

