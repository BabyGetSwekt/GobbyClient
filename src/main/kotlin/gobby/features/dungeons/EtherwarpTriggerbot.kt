package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.features.Triggerbot
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils
import gobby.utils.Utils.getRandomInt
import gobby.utils.isEtherwarpable
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos

object EtherwarpTriggerbot : Triggerbot("Etherwarp", "Etherwarp triggerbot and helpers", Category.DUNGEONS) {

    val mode by SelectorSetting("Mode", 0, listOf("Auto Sneak", "Manual Sneak"), desc = "Auto Sneak: Automatically sneaks and etherwarps\nManual Sneak: Only right-clicks when already sneaking")
    val esp by BooleanSetting("ESP", false, desc = "Highlights etherwarp target blocks in dungeons.\nThe only blocks that can be used to etherwarp to are:\n- PRISMARINE_BRICK_SLAB\n- PRISMARINE_BRICK_STAIRS\n- PRISMARINE_BRICKS\n- PRISMARINE_WALL\n\nTo place these blocks down use `/gobby brush`. Or use `/gobby help` for more info.\nEtherwarp blocks are marked as yellow.")
    val highlighter by BooleanSetting("Highlighter", false, desc = "Highlights the block you would etherwarp to")

    private var sneakDelay = 0
    private var wasSneaking = false

    val TARGET_BLOCKS = setOf(
        Blocks.PRISMARINE_BRICK_SLAB,
        Blocks.PRISMARINE_BRICK_STAIRS,
        Blocks.PRISMARINE_BRICKS,
        Blocks.PRISMARINE_WALL
    )

    override fun shouldActivate(): Boolean = enabled && !inBoss && dungeonFloor != -1 && mc.currentScreen == null

    override fun isValidBlock(pos: BlockPos): Boolean =
        mc.world?.getBlockState(pos)?.block in TARGET_BLOCKS

    override fun getBlockCooldown(): Long = 3000L

    override fun getTargetPos(): BlockPos? {
        val player = mc.player ?: return null
        if (!player.mainHandStack.isEtherwarpable()) return null
        if (mode == 1 && !player.isSneaking) return null
        return EtherwarpUtils.getEtherPos().takeIf { it.succeeded }?.pos
    }

    override fun performAction() {
        val player = mc.player ?: return
        when (mode) {
            0 -> { /* Auto sneak mode */
                if (player.isSneaking) {
                    PlayerUtils.rightClick()
                } else {
                    wasSneaking = false
                    mc.options.sneakKey.isPressed = true
                    sneakDelay = getRandomInt(3, 4)
                }
            }
            1 -> { /* Manual sneak mode (you have to sneak urself) */
                if (player.isSneaking) PlayerUtils.rightClick()
            }
        }
    }

    @SubscribeEvent
    override fun onTick(event: ClientTickEvent.Pre) {
        if (sneakDelay > 0) {
            processSneakSequence()
            return
        }
        super.onTick(event)
    }

    private fun processSneakSequence() {
        sneakDelay--
        if (sneakDelay == 0 && !wasSneaking) mc.options.sneakKey.isPressed = false
        if (sneakDelay == 1) PlayerUtils.rightClick()
    }
}
