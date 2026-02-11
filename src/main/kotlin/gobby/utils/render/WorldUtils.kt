package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import net.minecraft.block.entity.BlockEntity
import net.minecraft.item.EnderEyeItem
import net.minecraft.item.EnderPearlItem
import net.minecraft.item.FishingRodItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.LingeringPotionItem
import net.minecraft.item.SplashPotionItem
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.chunk.WorldChunk



object WorldUtils {

    fun isThrowable(stack: ItemStack): Boolean {
        val item = stack.item
        return item == Items.BOW ||
                item == Items.SNOWBALL ||
                item == Items.EGG ||
                item == Items.FIRE_CHARGE ||
                item == Items.TRIDENT ||
                item is EnderPearlItem ||
                item is SplashPotionItem ||
                item is LingeringPotionItem ||
                item is FishingRodItem ||
                item is EnderEyeItem
    }

    fun isPlantable(stack: ItemStack): Boolean {
        val item = stack.item
        return item == Items.WHEAT_SEEDS ||
                item == Items.CARROT ||
                item == Items.POTATO ||
                item == Items.BEETROOT_SEEDS ||
                item == Items.MELON_SEEDS ||
                item == Items.COCOA_BEANS ||
                item == Items.NETHER_WART
    }

    fun getTileEntities(): Sequence<BlockEntity> {
        return getLoadedChunks().flatMap { it.blockEntities.values.asSequence() }
    }

    fun getLoadedChunks(): Sequence<WorldChunk> {
        val player = mc.player ?: return emptySequence()
        val world = mc.world ?: return emptySequence()

        val radius = mc.options.clampedViewDistance.coerceAtLeast(2).coerceAtMost(8)
        val diameter = radius * 2 + 1

        val center = player.chunkPos
        val min = ChunkPos(center.x - radius, center.z - radius)
        val max = ChunkPos(center.x + radius, center.z + radius)

        return generateSequence(min) { pos ->
            var x = pos.x
            var z = pos.z
            x++
            if (x > max.x) {
                x = min.x
                z++
            }
            if (z > max.z) {
                null
            } else {
                ChunkPos(x, z)
            }
        }
            .take(diameter * diameter)
            .filter { world.isChunkLoaded(it.x, it.z) }
            .mapNotNull { world.getChunk(it.x, it.z) }
    }
}