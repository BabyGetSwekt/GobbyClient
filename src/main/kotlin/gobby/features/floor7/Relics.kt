package gobby.features.floor7

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.RightClickEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.PlayerUtils
import gobby.utils.skyblockID
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.Relic
import gobby.utils.timer.Clock
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult

object Relics : Module(
    "Relics", "Quality of life features for relics on F7/M7.",
    Category.FLOOR7
) {

    val cancelWrongClicks by BooleanSetting("Cancel Wrong Clicks", false, desc = "Cancels right clicks on wrong cauldrons while holding a relic")
    val relicTriggerbot by BooleanSetting("Relic Triggerbot", false, desc = "Automatically picks up relics and places them on the correct cauldron")

    private val clock = Clock()
    private val clickedBlocks = mutableMapOf<BlockPos, Clock>()

    private fun getHeldRelic(): Relic =
        Relic.fromItemID(mc.player?.mainHandStack?.skyblockID)

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (!enabled || !cancelWrongClicks || DungeonUtils.getPhase() != 5) return
        val relic = getHeldRelic()
        if (relic == Relic.None) return

        val hit = mc.crosshairTarget
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return
        if (hit.blockPos != relic.cauldronPos) event.cancel()
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!enabled || !relicTriggerbot || mc.player == null || mc.world == null) return
        if (DungeonUtils.getPhase() != 5 || mc.currentScreen != null) return
        if (!clock.hasTimePassed(100)) return

        if (clickedBlocks.isNotEmpty()) clickedBlocks.entries.removeIf { it.value.hasTimePassed(5000) }

        val relic = getHeldRelic()

        if (relic == Relic.None) {
            val hit = mc.crosshairTarget ?: return
            if (hit !is EntityHitResult || hit.type != HitResult.Type.ENTITY) return
            val entity = hit.entity
            if (entity !is ArmorStandEntity) return
            if (!entity.getEquippedStack(EquipmentSlot.HEAD).name.string.contains("Relic")) return

            PlayerUtils.rightClick()
            clock.update()
            return
        }

        val hit = mc.crosshairTarget
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return
        val pos = hit.blockPos
        if (pos in clickedBlocks) return
        if (pos != relic.cauldronPos) return

        PlayerUtils.rightClick()
        clock.update()
        clickedBlocks[pos] = Clock()
    }
}
