package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.ContainerClicks
import gobby.utils.Utils.getRandomInt
import gobby.utils.getItemUUID
import gobby.utils.hasPotionEffect
import gobby.utils.isRecombobulated
import gobby.utils.isSplashPotion
import gobby.utils.itemQuality
import gobby.utils.skyblockID
import gobby.utils.starCount
import gobby.utils.timer.Clock
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.awt.Color

object TrashItems : Module("Trash Items", "Highlights and sells useless dungeon drops", Category.DUNGEONS) {

    private val highlight by BooleanSetting("Highlight Trash Item", true, desc = "Highlights useless item drops from dungeons")
    private val autoSell by BooleanSetting("Auto Sell", false, desc = "Automatically sells it in trade menus")
    private val ignoreRecomb by BooleanSetting("Ignore Recomb", true, desc = "Prevents selling recombobulated items")
        .withDependency { autoSell }
    private val ignoreStarred by BooleanSetting("Ignore Starred", true, desc = "Prevents selling starred items")
        .withDependency { autoSell }
    private val sellReviveStones by BooleanSetting("Sell Revive Stones", false, desc = "Also sells revive stones")
        .withDependency { autoSell }

    private val WEAPONS = setOf(
        "EARTH_SHARD", "CRYPT_BOW", "MACHINE_GUN_BOW", "ZOMBIE_SOLDIER_CUTLASS", "SILENT_DEATH",
        "CRYPT_DREADLORD_SWORD", "ZOMBIE_KNIGHT_SWORD", "ZOMBIE_COMMANDER_WHIP", "CONJURING", "CONJURING_SWORD"
    )
    private val ITEM_JUNK = setOf("PREMIUM_FLESH", "OPTICAL_LENS", "TRIPWIRE_HOOK", "STONE_BUTTON",
        "DUNGEON_LORE_PAPER", "DEFUSE_KIT", "BEATING_HEART", "TRAINING_WEIGHTS", "ICE_HUNK", "LEVER",
        "SIGN"
        )
    private val ARMOR_SETS = setOf(
        "ZOMBIE_KNIGHT", "ZOMBIE_SOLDIER", "BOUNCY", "SKELETON_MASTER", "SKELETON_SOLDIER", "ROTTEN",
        "SUPER_HEAVY", "SKELETON_LORD", "SKELETOR", "SNIPER_HELMET", "ZOMBIE_COMMANDER", "SKELETON_GRUNT",
        "HEAVY", "ZOMBIE_LORD"
    )
    private val ARMOR_PIECES = listOf("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS")
    private val JUNK_SPLASH_POTIONS = mapOf("healing" to 8)

    private val TRASH_IDS = WEAPONS + ITEM_JUNK + ARMOR_SETS.flatMap(::piecesOf)
    private val SELL_MENU_TITLE = Regex("""^(Trades|Booster Cookie|\(\d/2\) Ophelia)$""")
    private val SOLD_MESSAGE = Regex("""^You sold (.+) (x\d+) for [\d,]+ Coins?!$""")
    private val HIGHLIGHT_COLOR = Color(0, 255, 255, 255).rgb

    private val sellClock = Clock()
    private val attempts = HashMap<String, Int>()
    private var sellingContainer = -1
    private var nextSellDelay = 0L
    private var pendingSales = 0
    private var pendingSaleTicks = 0
    private var sellingStopped = false

    fun onDrawSlotBackgrounds(screen: AbstractContainerScreen<*>, ctx: GuiGraphicsExtractor) {
        if (!enabled || !highlight) return
        ownInventorySlots(screen.menu).filter { it.item.isTrash }.forEach { slot ->
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, HIGHLIGHT_COLOR)
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (pendingSales > 0 && --pendingSaleTicks <= 0) pendingSales = 0
        val menu = sellMenu() ?: return stopSelling()
        if (menu.containerId != sellingContainer) startSelling(menu.containerId)
        if (!sellClock.hasTimePassed(nextSellDelay)) return
        if (sellingStopped) return
        val slot = sellableSlots(menu).firstOrNull { it.item.isSellable } ?: return
        if (slot.isRefused) return giveUp()
        attempts.merge(slot.retryKey, 1, Int::plus)
        ContainerClicks.quickMove(menu.containerId, slot.index)
        pendingSales++
        pendingSaleTicks = 40
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

    private fun giveUp() {
        sellingStopped = true
        errorMessage("Either lag or no cookie detected, stopping")
    }

    private fun startSelling(containerId: Int) {
        sellingContainer = containerId
        attempts.clear()
        sellingStopped = false
        sellClock.update()
        nextSellDelay = getRandomInt(340, 360).toLong()
    }

    private fun stopSelling() {
        sellingContainer = -1
    }

    private fun ownInventorySlots(menu: AbstractContainerMenu): List<Slot> {
        val inventory = mc.player?.inventory ?: return emptyList()
        return menu.slots.filter { it.isActive && it.container === inventory }
    }

    private fun sellableSlots(menu: AbstractContainerMenu): List<Slot> = ownInventorySlots(menu).dropLast(1)

    private val Slot.retryKey: String get() = item.getItemUUID ?: "$index"

    private val Slot.isRefused: Boolean get() = (attempts[retryKey] ?: 0) >= 3

    private fun piecesOf(set: String): List<String> =
        if (ARMOR_PIECES.any(set::endsWith)) listOf(set) else ARMOR_PIECES.map { set + it }

    private val ItemStack.isTrash: Boolean
        get() = isJunkPotion || skyblockID.let { it in TRASH_IDS && !(it == "SKELETON_MASTER_CHESTPLATE" && itemQuality == 50) }

    private val ItemStack.isJunkPotion: Boolean
        get() = isSplashPotion && JUNK_SPLASH_POTIONS.any { hasPotionEffect(it.key, it.value) }

    private val ItemStack.isSellable: Boolean
        get() {
            if (!isTrash && !(sellReviveStones && skyblockID == "REVIVE_STONE")) return false
            return !(ignoreRecomb && isRecombobulated) && !(ignoreStarred && starCount > 0)
        }
}
