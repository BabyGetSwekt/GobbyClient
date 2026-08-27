package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.mixin.accessor.AbstractContainerScreenAccessor
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.Utils.setClipboard
import gobby.utils.encodeJson
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack

object CopyItemNbt : Module("Copy Item NBT", "Press the keybind to copy the held or hovered item's NBT to your clipboard", Category.DEVELOPER) {

    private val copyKey by KeybindSetting("Copy NBT", desc = "Press while hovering a slot, or in the world to copy your held item")

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled || copyKey == 0 || event.key != copyKey) return
        val screen = mc.gui.screen()
        if (screen != null && screen !is AbstractContainerScreen<*>) return
        val stack = targetStack(screen) ?: return
        event.cancel()
        copy(stack)
    }

    private fun targetStack(screen: Screen?): ItemStack? {
        if (screen == null) return mc.player?.mainHandItem?.takeUnless { it.isEmpty }
        return (screen as AbstractContainerScreenAccessor).focusedSlot?.item?.takeUnless { it.isEmpty }
    }

    private fun copy(stack: ItemStack) {
        val json = stack.encodeJson() ?: return errorMessage("Weird, didn't detect an item, report this to Gobby ty bbg")
        setClipboard(json)
        modMessage("Successfully copied item NBT!")
    }
}
