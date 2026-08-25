package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import org.slf4j.LoggerFactory
import java.io.File
import gobby.utils.ConfigUtils

object RoomCopier {
    private val roomsDir = ConfigUtils.directory("rooms")
    private val logger = LoggerFactory.getLogger("RoomCopier")

    fun copyCurrentRoom() {
        val player = mc.player ?: return errorMessage("No player")
        val world = mc.level ?: return errorMessage("No world")
        val plan = RoomCopyCapture.resolvePlan(world, player).getOrElse { return errorMessage(errorMessageFor(it)) }
        val blocks = RoomCopyCapture.captureBlocks(world, plan)
        val entities = RoomCopyCapture.captureEntities(world, plan, logger)
        val file = File(roomsDir, sanitize(plan.name) + ".json")
        file.writeText(RoomCopyCapture.buildJson(plan, blocks, entities))
        modMessage("Saved " + file.name + " (" + plan.shape + " " + plan.type + ") | cells=" + plan.cells + " palette=" + blocks.palette.size + " runs=" + blocks.runs.size / 2 + " entities=" + entities.size + " " + file.length() / 1024 + "KB")
    }

    private fun errorMessageFor(error: Throwable): String = when (error.message) {
        "Not on dungeon grid" -> "Not on dungeon grid"
        "Could not resolve room cells" -> "Could not resolve room cells"
        "Chunks not loaded" -> "Chunks not loaded (walk closer)"
        "Empty column under room" -> "Empty column under room"
        else -> "Current tile is not a room (walk further into the room core and retry)"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "Room" }
}

