package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.LocationUtils
import gobby.utils.managers.EquipmentManager
import gobby.utils.render.TitleUtils
import java.awt.Color

object MaskSwapper : Module("Mask Swapper", "Swap masks with keybinds", Category.SKYBLOCK) {

    private val COL_BONZO = Color(85, 85, 255)
    private val COL_SPIRIT = Color(85, 255, 255)
    private val COL_COW = Color(170, 170, 170)

    val bonzoKey by KeybindSetting("Bonzo Mask", desc = "Keybind for Bonzo Mask")
    val spiritKey by KeybindSetting("Spirit Mask", desc = "Keybind for Spirit Mask")
    val cowKey by KeybindSetting("Cow Head", desc = "Keybind for Cow Head")

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled) return
        if (!LocationUtils.onSkyblock) return
        if (mc.currentScreen != null) return
        val key = event.key
        if (key == 0) return

        when (key) {
            bonzoKey -> swap("Bonzo Mask", COL_BONZO, "BONZO_MASK", "STARRED_BONZO_MASK")
            spiritKey -> swap("Spirit Mask", COL_SPIRIT, "SPIRIT_MASK", "STARRED_SPIRIT_MASK")
            cowKey -> swap("Cow Head", COL_COW, "COW_HEAD")
        }
    }

    private fun swap(name: String, color: Color, vararg ids: String) {
        EquipmentManager.swapHead(*ids)
        TitleUtils.displayStyledTitleTicks(name, 20, color)
    }
}
