package gobby.utils

import gobby.Gobbyclient.Companion.mc
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import java.util.Locale

object Utils {

    /**
     * Checks if the current object is equal to at least one of the specified objects.
     *
     * @param options List of other objects to check.
     * @return `true` if the object is equal to one of the specified objects.
     */
    fun Any?.equalsOneOf(vararg options: Any?): Boolean =
        options.any { this == it }

    fun Any?.equalsOneOf(options: Collection<Any?>): Boolean =
        options.any { this == it }

    fun Number.toFixed(decimals: Int = 2): String =
        "%.${decimals}f".format(Locale.US, this)

    fun LivingEntity.getSBMaxHealth(): Float {
        return this?.getAttributeValue(EntityAttributes.MAX_HEALTH)?.toFloat() ?: 0f
    }

    fun ClientWorld.getBlockAtPos(pos: BlockPos): Block = getBlockState(pos).block

    fun getBlockIdAt(blockPos: BlockPos): Int? {
        val blockState = mc.world?.getBlockState(blockPos) ?: return null
        return Registries.BLOCK.getRawId(blockState.block)
    }

    fun isDeveloper(): Boolean {
        val player = mc ?: return false
        if (mc.player == null) return false
        if (FabricLoader.getInstance().isDevelopmentEnvironment) return true
        val name = player.name
        return name.startsWith("Goblin")
    }

    fun getRandomInt(min: Int, max: Int): Int = (min..max).random()

    inline val posX get() = mc.player!!.x
    inline val posY get() = mc.player!!.y
    inline val posZ get() = mc.player!!.z
}