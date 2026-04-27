package gobby.utils.copy

import gobby.Gobbyclient.Companion.mc
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.storage.NbtWriteView
import net.minecraft.util.ErrorReporter
import org.slf4j.Logger

object ArmorStandCodec {

    fun encode(stand: ArmorStandEntity, logger: Logger): String? {
        val world = mc.world ?: return null
        return try {
            val writeView = NbtWriteView.create(
                ErrorReporter.Logging(stand.errorReporterContext, logger),
                world.registryManager
            )
            stand.saveSelfData(writeView)
            val nbt = writeView.nbt
            nbt.putString("id", "minecraft:armor_stand")
            nbt.toString()
        } catch (_: Exception) {
            null
        }
    }
}
