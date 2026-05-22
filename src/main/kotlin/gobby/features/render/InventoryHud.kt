package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.components.hud.InventoryHud as InventoryHudComponent
import gobby.gui.hud.HudSetting

object InventoryHud : Module("Inventory HUD", "Shows your armor, inventory, hotbar and player model", Category.RENDER) {

    private val showPlayer by BooleanSetting("Show Player Model", true, desc = "Render the player model")
    private val freezePlayer by BooleanSetting("Freeze Player Model", false, desc = "Lock the player model facing forward").withDependency { showPlayer }
    private val showArmor by BooleanSetting("Show Armor", true, desc = "Render the armor")
    private val highlightSelected by BooleanSetting("Highlight Selected Item", true, desc = "Outline the currently selected hotbar slot in green")

    private val invHud by HudSetting("Inventory HUD", "Movable inventory display") { example ->
        val ctx = drawContext ?: return@HudSetting
        val size = InventoryHudComponent.renderInventory(
            ctx,
            mc.player,
            hudX,
            hudY,
            hudScale,
            showArmor,
            showPlayer,
            freezePlayer,
            highlightSelected,
            example
        )
        setSize(size.width, size.height)
    }
}
