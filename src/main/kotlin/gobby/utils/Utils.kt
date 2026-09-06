package gobby.utils

import gobby.Gobbyclient.Companion.logger
import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.CameraAccessor
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.Block
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.BlockPos
import net.minecraft.util.Util
import net.minecraft.world.phys.Vec3
import java.util.Locale

object Utils {

    fun setClipboard(text: String) { mc.keyboardHandler.clipboard = text }

    fun getClipboard(): String = mc.keyboardHandler.clipboard

    fun openUrl(url: String) {
        runCatching { Util.getPlatform().openUri(url) }
            .onFailure { logger.error("could not open {}", url, it) }
    }

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
        return this?.getAttributeValue(Attributes.MAX_HEALTH)?.toFloat() ?: 0f
    }

    fun ClientLevel.getBlockAtPos(pos: BlockPos): Block = getBlockState(pos).block

    val Camera.cameraPos: Vec3
        get() = (this as CameraAccessor).`gobbyclient$getPos`()

    /**
     * Always defers to the next client tick, unlike `Minecraft.execute(Runnable)` which runs
     * inline when called from the game thread. Use when the caller's stack might mutate `mc.screen`
     * after our task runs (e.g. opening a screen from inside a chat command — `ChatScreen.keyPressed`
     * dispatches the command then calls `setScreen(null)` synchronously, wiping any screen we set).
     */

    fun Minecraft.executeLater(block: () -> Unit) = this.schedule(Runnable(block))

    fun ClientLevel.setBlockAtPos(pos: BlockPos, block: Block) = setBlock(pos, block.defaultBlockState(), 3)

    fun getBlockIdAt(blockPos: BlockPos): Int? {
        val blockState = mc.level?.getBlockState(blockPos) ?: return null
        return BuiltInRegistries.BLOCK.getId(blockState.block)
    }

    fun isDeveloper(): Boolean {
        val player = mc ?: return false
        if (mc.player == null) return false
        if (FabricLoader.getInstance().isDevelopmentEnvironment) return true
        val name = player.gameProfile.name
        return name.startsWith("Goblin")
    }

    fun getRandomInt(min: Int, max: Int): Int = (min..max).random()

    fun swapDelayTicks(): Int = getRandomInt(3, 7)

    inline val posX get() = mc.player?.x ?: 0.0
    inline val posY get() = mc.player?.y ?: 0.0
    inline val posZ get() = mc.player?.z ?: 0.0
    inline val yaw get() = mc.player?.yRot ?: 0f
    inline val pitch get() = mc.player?.xRot ?: 0f
    inline val eyePosX get() = mc.player?.eyePosition?.x ?: 0.0
    inline val eyePosY get() = mc.player?.eyePosition?.y ?: 0.0
    inline val eyePosZ get() = mc.player?.eyePosition?.z ?: 0.0

    fun Double.toRadians(): Double = Math.toRadians(this)

    fun Float.toRadians(): Double = Math.toRadians(this.toDouble())

}
