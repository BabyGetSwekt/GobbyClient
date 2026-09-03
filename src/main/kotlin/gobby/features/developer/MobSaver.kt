package gobby.features.developer

import com.google.gson.Gson
import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.fileLink
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ConfigUtils
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.storage.TagValueOutput
import java.io.File

object MobSaver : Module(
    "Mob Saver",
    "Press the keybind to save nearby mobs to a JSON file",
    Category.DEVELOPER
) {
    private val radius by NumberSetting("Radius", 16, 1, 128, 1, desc = "Block radius around player to scan")
    private val saveKey by KeybindSetting("Save", desc = "Press to save nearby mobs")
    private val outputDir = ConfigUtils.directory("mobs")
    private val gson = Gson()

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled || mc.gui.screen() != null || saveKey == 0 || event.key != saveKey) return
        saveNearbyMobs()
    }

    private fun saveNearbyMobs() {
        val player = mc.player ?: run { errorMessage("No player"); return }
        val world = mc.level ?: run { errorMessage("No world"); return }
        val maxDistance = radius.toDouble() * radius.toDouble()
        val mobs = world.entitiesForRendering()
            .filterIsInstance<Mob>()
            .filter { it.distanceToSqr(player) <= maxDistance }
        val encoded = mobs.mapNotNull { encodeMob(it, world) }
        if (encoded.isEmpty()) {
            errorMessage("No mobs found within $radius blocks")
            return
        }
        val file = File(outputDir, "Mobs_${System.currentTimeMillis()}.json")
        file.writeText(buildJson(player.blockPosition().let { intArrayOf(it.x, it.y, it.z) }, encoded))
        val message = ComponentParts.savedMessage(encoded.size, file, radius)
        modMessage(message)
    }

    private fun encodeMob(mob: Mob, world: net.minecraft.client.multiplayer.ClientLevel): String? = try {
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, world.registryAccess())
        mob.saveWithoutId(output)
        output.buildResult().apply {
            putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(mob.type).toString())
        }.toString()
    } catch (_: Exception) {
        null
    }

    private fun buildJson(origin: IntArray, mobNbts: List<String>): String = buildString {
        appendLine("{")
        appendLine("  \"origin\": [${origin[0]}, ${origin[1]}, ${origin[2]}],")
        appendLine("  \"mobs\": [")
        mobNbts.forEachIndexed { index, nbt ->
            val comma = if (index < mobNbts.lastIndex) "," else ""
            appendLine("    {\"nbt\": ${gson.toJson(nbt)}}$comma")
        }
        appendLine("  ]")
        appendLine("}")
    }
}

private object ComponentParts {
    fun savedMessage(count: Int, file: File, radius: Int) =
        Component.literal("§aSaved §f$count§a mobs to ")
            .append(fileLink(file.name, file))
            .append(Component.literal("§a (radius $radius)"))
}
