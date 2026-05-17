package gobby.features.skyblock

import gobby.Gobbyclient
import gobby.events.ClientTickEvent
import gobby.events.PacketSentEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.events.render.NewRender3DEvent
import gobby.features.Triggerbot
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.SelectorSetting
import gobby.utils.ChatUtils
import gobby.utils.LocationUtils
import gobby.utils.PlayerUtils
import gobby.utils.Utils
import gobby.utils.isEtherwarpable
import gobby.utils.render.BlockRenderUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.awt.Color

object Etherwarp : Triggerbot("Etherwarp", "Etherwarp triggerbot and helpers", Category.SKYBLOCK) {

    val mode by SelectorSetting(
        "Mode",
        0,
        listOf("Auto Sneak", "Manual Sneak"),
        desc = "Auto Sneak: Automatically sneaks and etherwarps\nManual Sneak: Only right-clicks when already sneaking"
    )
    val esp by BooleanSetting(
        "ESP",
        false,
        desc = "Highlights etherwarp target blocks in dungeons.\nThe only blocks that can be used to etherwarp to are:\n- PRISMARINE_BRICK_SLAB\n- PRISMARINE_BRICK_STAIRS\n- PRISMARINE_BRICKS\n- PRISMARINE_WALL\n\nTo place these blocks down use `/gobby brush`. Or use `/gobby help` for more info.\nEtherwarp blocks are marked as yellow."
    )
    val highlighter by BooleanSetting("Highlighter", false, desc = "Highlights the block you would etherwarp to")
    private val preventDeath by BooleanSetting(
        "Prevent Accidental Death",
        true,
        desc = "Cancels etherwarps that would land on the highest block of the current dungeon room (at death barrier)"
    )

    val TARGET_BLOCKS = setOf(
        Blocks.PRISMARINE_BRICK_SLAB,
        Blocks.PRISMARINE_BRICK_STAIRS,
        Blocks.PRISMARINE_BRICKS,
        Blocks.PRISMARINE_WALL
    )

    private val validColor = Color(0, 255, 0, 80)
    private val invalidColor = Color(255, 0, 0, 80)

    private var sneakDelay = 0
    private var wasSneaking = false
    private var currentHighestY: Int? = null
    private var isInEntrance = false

    override fun shouldActivate(): Boolean = enabled && !LocationUtils.inBoss && LocationUtils.dungeonFloor != -1 && Gobbyclient.mc.screen == null

    override fun isValidBlock(pos: BlockPos): Boolean =
        Gobbyclient.mc.level?.getBlockState(pos)?.block in TARGET_BLOCKS

    override fun getBlockCooldown(): Long = 3000L

    override fun getTargetPos(): BlockPos? {
        val player = Gobbyclient.mc.player ?: return null
        if (!player.mainHandItem.isEtherwarpable()) return null
        if (mode == 1 && !player.isShiftKeyDown) return null
        return EtherwarpUtils.getEtherPos().takeIf { it.succeeded }?.pos
    }

    override fun performAction() {
        val player = Gobbyclient.mc.player ?: return
        when (mode) {
            0 -> {
                if (player.isShiftKeyDown) {
                    PlayerUtils.rightClick()
                } else {
                    wasSneaking = false
                    Gobbyclient.mc.options.keyShift.isDown = true
                    sneakDelay = Utils.getRandomInt(3, 4)
                }
            }
            1 -> {
                if (player.isShiftKeyDown) PlayerUtils.rightClick()
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
        if (sneakDelay == 0 && !wasSneaking) Gobbyclient.mc.options.keyShift.isDown = false
        if (sneakDelay == 1) PlayerUtils.rightClick()
    }

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        currentHighestY = event.highestY
        isInEntrance = event.room?.data?.type == RoomType.ENTRANCE
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        if (!enabled || !preventDeath) return
        if (Gobbyclient.mc.screen != null) return
        if (event.packet !is ServerboundUseItemPacket) return
        if (isInEntrance) return
        val player = Gobbyclient.mc.player ?: return
        if (!player.mainHandItem.isEtherwarpable()) return
        val highestY = currentHighestY ?: return
        val hit = EtherwarpUtils.getEtherPos().takeIf { it.succeeded }?.pos ?: return
        if (hit.y >= highestY) {
            event.cancel()
            ChatUtils.errorMessage("Prevented you from going out of the dungeon")
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled || !highlighter) return
        val player = Gobbyclient.mc.player ?: return
        if (!player.isShiftKeyDown) return
        if (!player.mainHandItem.isEtherwarpable()) return

        val etherPos = EtherwarpUtils.getEtherPos()
        val pos = etherPos.pos ?: return
        val color = if (etherPos.succeeded) validColor else invalidColor

        val box = AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        )
        BlockRenderUtils.draw3DBox(event.matrixStack, event.camera, box, color)
    }
}