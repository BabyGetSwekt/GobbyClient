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
import gobby.utils.ChatUtils.modMessage
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.util.ErrorReporter
import org.slf4j.LoggerFactory
import java.io.File

object ArmorStandSaver : Module(
    "ArmorStand Saver",
    "Press the keybind to save every ArmorStand within the configured radius to a JSON file compatible with DungeonSimulationPlugin /structure paste",
    Category.DEVELOPER
) {

    private val radius by NumberSetting("Radius", default = 16, min = 1, max = 128, step = 1, desc = "Block radius around player to scan")
    private val saveKey by KeybindSetting("Save", desc = "Press to save nearby armor stands")

    private val outputDir = File("./config/gobbyclientFabric/armorstands").apply { mkdirs() }
    private val gson = Gson()
    private val logger = LoggerFactory.getLogger("ArmorStandSaver")

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled) return
        if (mc.currentScreen != null) return
        if (saveKey == 0 || event.key != saveKey) return
        saveNearbyArmorStands()
    }

    private fun saveNearbyArmorStands() {
        val player = mc.player ?: run { errorMessage("No player"); return }
        val world = mc.world ?: run { errorMessage("No world"); return }
        val origin = player.blockPos

        val stands = world.entities
            .filterIsInstance<ArmorStandEntity>()
            .filter { it.squaredDistanceTo(player) <= radius.toDouble() * radius.toDouble() }

        if (stands.isEmpty()) {
            errorMessage("No ArmorStands found within $radius blocks")
            return
        }

        val encoded = stands.mapNotNull { encodeArmorStand(it) }
        if (encoded.isEmpty()) {
            errorMessage("Failed to encode any armor stands")
            return
        }

        val file = File(outputDir, "ArmorStands_${System.currentTimeMillis()}.json")
        file.writeText(buildJson(intArrayOf(origin.x, origin.y, origin.z), encoded))
        modMessage("§aSaved §f${encoded.size}§a armor stands to §e${file.name}§a (radius $radius)")
    }

    private fun encodeArmorStand(stand: ArmorStandEntity): String? {
        val world = mc.world ?: return null
        return try {
            val writeView = net.minecraft.storage.NbtWriteView.create(
                ErrorReporter.Logging(stand.errorReporterContext, logger),
                world.registryManager
            )
            stand.saveSelfData(writeView)
            val nbt = writeView.nbt
            nbt.putString("id", "minecraft:armor_stand")
            nbt.toString()
        } catch (e: Exception) {
            logger.warn("Failed to encode armor stand: ${e.message}")
            null
        }
    }

    private fun buildJson(origin: IntArray, armorStandNbts: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"origin\": [${origin[0]}, ${origin[1]}, ${origin[2]}],")
        sb.appendLine("  \"blocks\": {},")
        sb.appendLine("  \"blockEntities\": [],")
        sb.appendLine("  \"armorStands\": [")
        armorStandNbts.forEachIndexed { i, nbt ->
            val comma = if (i < armorStandNbts.size - 1) "," else ""
            sb.appendLine("    {\"nbt\": ${gson.toJson(nbt)}}$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }
}
