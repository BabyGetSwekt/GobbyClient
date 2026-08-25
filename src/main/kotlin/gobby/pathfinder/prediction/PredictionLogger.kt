package gobby.pathfinder.prediction

import net.minecraft.world.phys.Vec3
import gobby.utils.ConfigUtils

object PredictionLogger {

    private val file = ConfigUtils.file("debug.txt", "pathfinder")

    fun startRoute(goal: Vec3) {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText("=== route to ${fmt(goal)} ===\n")
        }
    }

    fun log(line: String) {
        runCatching { file.appendText(line + "\n") }
    }

    fun fmt(v: Vec3): String = "(%.2f, %.2f, %.2f)".format(v.x, v.y, v.z)
}
