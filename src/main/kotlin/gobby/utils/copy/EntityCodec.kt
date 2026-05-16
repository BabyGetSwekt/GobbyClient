package gobby.utils.copy

import gobby.Gobbyclient.Companion.mc
import net.minecraft.world.entity.Entity
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.util.ProblemReporter
import org.slf4j.Logger

object EntityCodec {

    fun encode(entity: Entity, originX: Int, originY: Int, originZ: Int, logger: Logger): String? {
        val world = mc.level ?: return null
        return try {
            val writeView = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                world.registryAccess()
            )
            entity.saveWithoutId(writeView)
            val nbt = writeView.buildResult()
            val id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
            nbt.putString("id", id)

            val rel = ListTag()
            rel.add(DoubleTag.valueOf(entity.x - originX))
            rel.add(DoubleTag.valueOf(entity.y - originY))
            rel.add(DoubleTag.valueOf(entity.z - originZ))
            nbt.put("Pos", rel)

            nbt.toString()
        } catch (_: Exception) {
            null
        }
    }
}
