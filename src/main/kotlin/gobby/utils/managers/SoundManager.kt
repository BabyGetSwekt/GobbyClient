package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier as ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

object SoundManager {
    fun play(path: String) = Unit

    fun soundExists(id: String): Boolean = parse(id) != null

    fun playCustomSound(id: String, pitch: Float, volume: Float) {
        val location = parse(id) ?: return
        mc.execute {
            val player = mc.player ?: return@execute
            mc.level?.playLocalSound(
                player.x, player.y, player.z,
                SoundEvent.createVariableRangeEvent(location), SoundSource.PLAYERS,
                volume, pitch, false
            )
        }
    }

    private fun parse(id: String): ResourceLocation? =
        runCatching { ResourceLocation.parse(id.trim().lowercase()) }.getOrNull()
            ?.takeIf { BuiltInRegistries.SOUND_EVENT.containsKey(it) }
}
