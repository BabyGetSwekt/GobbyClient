package gobby.pathfinder.prediction

import net.minecraft.world.phys.Vec3
import java.io.File

object PredictionLogger {

    private val file = File("./config/gobbyclientFabric/pathfinder/debug.txt")

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
