@file:Suppress("DEPRECATION")

package gobby.utils

import net.minecraft.component.ComponentHolder
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
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

