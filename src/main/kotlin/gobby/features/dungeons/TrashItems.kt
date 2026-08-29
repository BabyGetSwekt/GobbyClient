package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.ContainerClicks
import gobby.utils.Utils.getRandomInt
import gobby.utils.skyblockID
import gobby.utils.timer.Clock
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.awt.Color

private const val SLOT_SIZE = 16
private const val NO_CONTAINER = -1
private const val MAX_ATTEMPTS_PER_SLOT = 3
private const val SALE_MESSAGE_TIMEOUT_TICKS = 60

object TrashItems : Module("Trash Items", "Highlights and sells useless dungeon drops", Category.DUNGEONS) {

    private val highlight by BooleanSetting("Highlight Trash Item", true, desc = "Highlights useless item drops from dungeons")
    private val autoSell by BooleanSetting("Auto Sell", false, desc = "Automatically sells it in trade menus")

    private val TRASH_IDS = setOf("PREMIUM_FLESH", "EARTH_SHARD")
    private val SELL_MENU_TITLE = Regex("""^(Trades|Booster Cookie|\(\d/2\) Ophelia)$""")
    private val SOLD_MESSAGE = Regex("""^You sold (.+) (x\d+) for [\d,]+ Coins?!$""")
    private val HIGHLIGHT_COLOR = Color(0, 255, 255, 120).rgb

    private val sellClock = Clock()
    private val attemptsPerSlot = HashMap<Int, Int>()
    private var sellingContainer = NO_CONTAINER
    private var nextSellDelay = 0L
    private var pendingSales = 0
    private var pendingSaleTicks = 0

    fun onDrawSlotBackgrounds(screen: AbstractContainerScreen<*>, ctx: GuiGraphicsExtractor) {
        if (!enabled || !highlight) return
        ownInventorySlots(screen.menu).filter { it.item.isTrash }.forEach { slot ->
            ctx.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, HIGHLIGHT_COLOR)
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (pendingSales > 0 && --pendingSaleTicks <= 0) pendingSales = 0
        val menu = sellMenu() ?: return stopSelling()
        if (menu.containerId != sellingContainer) startSelling(menu.containerId)
        if (!sellClock.hasTimePassed(nextSellDelay)) return
        val slot = sellableSlots(menu).firstOrNull { it.item.isTrash && !it.isRefused } ?: return
        attemptsPerSlot.merge(slot.index, 1, Int::plus)
        ContainerClicks.quickMove(menu.containerId, slot.index)
        pendingSales++
        pendingSaleTicks = SALE_MESSAGE_TIMEOUT_TICKS
        sellClock.update()
        nextSellDelay = getRandomInt(100, 230).toLong()
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled || pendingSales <= 0) return
        val (name, amount) = SOLD_MESSAGE.find(event.message.trim())?.destructured ?: return
        pendingSales--
        event.cancel()
        modMessage("§aSold §6$name §a$amount")
    }

    private fun sellMenu(): AbstractContainerMenu? {
        if (!enabled || !autoSell) return null
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return null
        return screen.menu.takeIf { SELL_MENU_TITLE.matches(screen.title.string.noControlCodes.trim()) }
    }

    private fun startSelling(containerId: Int) {
        sellingContainer = containerId
        attemptsPerSlot.clear()
        sellClock.update()
        nextSellDelay = getRandomInt(340, 360).toLong()
    }

    private fun stopSelling() {
        sellingContainer = NO_CONTAINER
    }

    private fun ownInventorySlots(menu: AbstractContainerMenu): List<Slot> {
        val inventory = mc.player?.inventory ?: return emptyList()
        return menu.slots.filter { it.isActive && it.container === inventory }
    }

    private fun sellableSlots(menu: AbstractContainerMenu): List<Slot> = ownInventorySlots(menu).dropLast(1)

    private val Slot.isRefused: Boolean get() = (attemptsPerSlot[index] ?: 0) >= MAX_ATTEMPTS_PER_SLOT

    private val ItemStack.isTrash: Boolean get() = skyblockID in TRASH_IDS
}
