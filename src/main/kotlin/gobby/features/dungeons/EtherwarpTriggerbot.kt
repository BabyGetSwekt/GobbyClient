package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.features.Triggerbot
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils
import gobby.utils.Utils.getRandomInt
import gobby.utils.isEtherwarpable
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos

object EtherwarpTriggerbot : Triggerbot() {

    private var sneakDelay = 0
    private var wasSneaking = false

    val TARGET_BLOCKS = setOf(
        Blocks.PRISMARINE_BRICK_SLAB,
        Blocks.PRISMARINE_BRICK_STAIRS,
        Blocks.PRISMARINE_BRICKS,
        Blocks.PRISMARINE_WALL
    )

    override fun shouldActivate(): Boolean = GobbyConfig.etherwarpTriggerbot != 0 || !inBoss || dungeonFloor != -1

    override fun isValidBlock(pos: BlockPos): Boolean =
        mc.world?.getBlockState(pos)?.block in TARGET_BLOCKS

    override fun getTargetPos(): BlockPos? {
        val player = mc.player ?: return null
        if (!player.mainHandStack.isEtherwarpable()) return null
        return EtherwarpUtils.getEtherPos().takeIf { it.succeeded }?.pos
    }

    override fun performAction() {
        val player = mc.player ?: return
        when (GobbyConfig.etherwarpTriggerbot) {
            1 -> { /* Auto sneak mode */
                wasSneaking = player.isSneaking
                if (player.isSneaking) {
                    PlayerUtils.rightClick()
                } else {
                    mc.options.sneakKey.isPressed = true
                    sneakDelay = getRandomInt(3, 4)
                }
            }
            2 -> { /* Manual sneak mode (you have to sneak urself) */
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
