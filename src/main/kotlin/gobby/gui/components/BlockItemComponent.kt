package gobby.gui.components

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.dsl.*
import net.minecraft.item.ItemStack
import java.awt.Color

class BlockItemComponent(
    val blockId: String,
    val itemStack: ItemStack
) : UIBlock(Color(0, 0, 0, 0)) {

    var mouseOver = false
        private set

    init {
        onMouseEnter { mouseOver = true }
        onMouseLeave { mouseOver = false }
    }

    companion object {
        const val BG_COLOR = 0xFF000000.toInt()
        const val HOVER_COLOR = 0xFF2A2A32.toInt()
        const val SELECTED_COLOR = 0xFF1B5E20.toInt()
        const val ETHERWARP_COLOR = 0xFFFFD700.toInt()
    }
}
