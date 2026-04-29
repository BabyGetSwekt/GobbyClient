package gobby.utils.copy

import gobby.Gobbyclient.Companion.mc
import net.minecraft.entity.Entity
import net.minecraft.nbt.NbtDouble
import net.minecraft.nbt.NbtList
import net.minecraft.registry.Registries
import net.minecraft.storage.NbtWriteView
import net.minecraft.util.ErrorReporter
import org.slf4j.Logger

object EntityCodec {

    fun encode(entity: Entity, originX: Int, originY: Int, originZ: Int, logger: Logger): String? {
        val world = mc.world ?: return null
        return try {
            val writeView = NbtWriteView.create(
                ErrorReporter.Logging(entity.errorReporterContext, logger),
                world.registryManager
            )
            entity.saveSelfData(writeView)
            val nbt = writeView.nbt
            val id = Registries.ENTITY_TYPE.getId(entity.type).toString()
            nbt.putString("id", id)

            val rel = NbtList()
            rel.add(NbtDouble.of(entity.x - originX))
            rel.add(NbtDouble.of(entity.y - originY))
            rel.add(NbtDouble.of(entity.z - originZ))
            nbt.put("Pos", rel)

            nbt.toString()
        } catch (_: Exception) {
            null
        }
    }
}
