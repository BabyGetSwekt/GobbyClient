package gobby.utils.copy

import gobby.Gobbyclient.Companion.mc
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.util.ProblemReporter
import org.slf4j.Logger

object ArmorStandCodec {

    fun encode(stand: ArmorStand, logger: Logger): String? {
        val world = mc.level ?: return null
        return try {
            val writeView = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                world.registryAccess()
            )
            stand.saveWithoutId(writeView)
            val nbt = writeView.buildResult()
            nbt.putString("id", "minecraft:armor_stand")
            nbt.toString()
        } catch (_: Exception) {
            null
        }
    }
}
