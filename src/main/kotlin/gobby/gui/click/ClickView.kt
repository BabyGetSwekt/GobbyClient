package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor

interface ClickView {

    val hidesSidebar: Boolean get() = true

    fun onOpened() {}

    fun onClosed() {}

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int)

    fun handleClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean

    fun handleScroll(gui: ClickGUI, mx: Int, my: Int, amount: Double): Boolean

    fun handleKey(gui: ClickGUI, key: Int): Boolean

    fun handleChar(chr: Char): Boolean
}
