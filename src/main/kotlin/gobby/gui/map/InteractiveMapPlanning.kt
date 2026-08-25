package gobby.gui.map

import gobby.pathfinder.etherwarp.EtherwarpPathExecutor
import gobby.utils.managers.SwapManager

internal fun beginInteractiveRoutePlanning() {
    EtherwarpPathExecutor.beginPlanningSneak()
    SwapManager.swapToEtherwarpableItem()
}
