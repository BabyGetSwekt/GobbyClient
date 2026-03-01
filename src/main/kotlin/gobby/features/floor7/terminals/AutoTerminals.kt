package gobby.features.floor7.terminals

import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.gui.click.SelectorSetting

object AutoTerminals : Module(
    "Auto Terminals", "Automatic terminal solvers in F7 P3",
    Category.FLOOR7
) {
    val firstDelay by NumberSetting("First Click Delay", 350, 200, 1000, 50, desc = "Delay in ms before the first click")
    val clickDelay by NumberSetting("Click Delay", 190, 100, 500, desc = "Delay in ms between each click")
    val breakThreshold by NumberSetting("Break Threshold", 500, 100, 1000, 100, desc = "Resync timeout in ms if a click gets no response. Recommended to keep at 500")
    val notP3 by BooleanSetting("Not P3", false, desc = "Allow terminals to work outside of P3 (for testing)")
    val melodySkip by SelectorSetting("Melody Skip", 1, listOf("None", "Edges", "All"), desc = "When to skip melody rows")
    val melodySkipDelay by NumberSetting("Skip Delay", 50, 0, 150, 50, desc = "Delay in ms between skip clicks")
        .withDependency { melodySkip != 0 }
}
