package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.events.BlockStateChangeEvent
import gobby.events.ChatReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.features.render.BlockHighlighter
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.render.BlockRenderUtils.draw3DBox
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.awt.Color

object ShootingDeviceEsp : BlockHighlighter() {

    private val emeraldColor = Color(0, 255, 0, 60)
    private val hitColor = Color(255, 10, 0, 200)
    private val aimColor = Color(0, 255, 0, 200)
    private const val AIM_BOX_SIZE = 0.2

    private val completedShots = mutableSetOf<BlockPos>()

    override fun isEnabled(): Boolean = AutoPre4.enabled && AutoPre4.shootingDeviceEsp && dungeonFloor == 7

    override fun getStatePredicate(): (BlockState) -> Boolean = { it.block == Blocks.EMERALD_BLOCK }

    override fun isValidPosition(pos: BlockPos): Boolean = pos in AutoPre4.shootPositions

    override fun getColor(pos: BlockPos): Color = emeraldColor

    @SubscribeEvent
    fun onShootPositionChange(event: BlockStateChangeEvent) {
        if (!isEnabled()) return
        val pos = event.blockPos
        if (pos !in AutoPre4.shootPositions) return
        if (AutoPre4.deviceCompleted) return
        if (event.oldState?.block == Blocks.EMERALD_BLOCK && event.newState.block != Blocks.EMERALD_BLOCK) {
            completedShots.add(pos.toImmutable())
        }
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!isEnabled()) return
        val name = mc.player?.gameProfile?.name ?: return
        if (event.message.startsWith("$name completed a device!")) {
            completedShots.clear()
            highlightedBlocks.clear()
        }
    }

    @SubscribeEvent
    fun onRenderAimTarget(event: NewRender3DEvent) {
        if (!isEnabled()) return

        if (!AutoPre4.isPlateDown()) {
            completedShots.clear()
            highlightedBlocks.clear()
            return
        }

        for (pos in completedShots) {
            val box = Box(
                pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
                pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
            )
            draw3DBox(event.matrixStack, event.camera, box, hitColor)
        }

        if (!AutoPre4.enabled) return
        val aim = AutoPre4.currentAimTarget ?: return
        val half = AIM_BOX_SIZE / 2.0
        val aimBox = Box(
            aim.x - half, aim.y - half, aim.z - half,
            aim.x + half, aim.y + half, aim.z + half
        )
        draw3DBox(event.matrixStack, event.camera, aimBox, aimColor, filled = false)
    }
}
