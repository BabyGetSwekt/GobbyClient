package gobby.features.floor7

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.mixin.accessor.KeyMappingAccessor
import gobby.utils.render.BlockRenderUtils.drawConnectedBlocks
import gobby.utils.skyblockID
import gobby.utils.skyblock.dungeon.DungeonUtils.getPhase
import net.minecraft.client.KeyMapping
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.awt.Color

object NecronPlatform : Module("Necron Platform", "Helper too break the 3x3 area so that the platform stays in tact.", Category.FLOOR7) {

    private val mineTriggerbot by BooleanSetting("Mine Triggerbot", false, desc = "Automatically mines the block when looking at it while holding a Dungeon breaker")

    private var mining = false

    private val PLATFORM = BlockPos.betweenClosed(BlockPos(55, 63, 115), BlockPos(53, 63, 113)).map(BlockPos::immutable)

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !enabled || getPhase() != 4) return
        val world = mc.level ?: return
        val placed = PLATFORM.filter { !world.getBlockState(it).isAir }
        drawConnectedBlocks(event.matrixStack, event.camera, placed, Color(0, 255, 0, 80), Color(0, 255, 0, 255))
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) = holdAttack(shouldMine())

    private fun shouldMine(): Boolean {
        if (!enabled || !mineTriggerbot || getPhase() != 4 || mc.gui.screen() != null) return false
        if (mc.player?.mainHandItem?.skyblockID != "DUNGEONBREAKER") return false
        val hit = mc.hitResult as? BlockHitResult ?: return false
        return hit.type == HitResult.Type.BLOCK && hit.blockPos in PLATFORM
    }

    private fun holdAttack(down: Boolean) {
        if (down == mining) return
        mining = down
        KeyMapping.set((mc.options.keyAttack as KeyMappingAccessor).boundKey, down)
    }
}
