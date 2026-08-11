package gobby.pathfinder.world

import gobby.Gobbyclient.Companion.mc
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant

object CacheDiagnostics {

    private const val FILE_NAME = "gobby-cache-debug.log"
    private val lock = Any()

    var enabled = false

    fun log(message: String) {
        if (!enabled) return
        val line = "${Instant.now()} $message"
        println("[GobbyCache] $message")
        synchronized(lock) {
            runCatching {
                val directory = mc.gameDirectory.toPath().resolve("logs")
                Files.createDirectories(directory)
                Files.writeString(
                    directory.resolve(FILE_NAME),
                    "$line${System.lineSeparator()}",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                )
            }
        }
    }

    fun writeState(lines: List<String>) {
        synchronized(lock) {
            runCatching {
                val directory = mc.gameDirectory.toPath().resolve("logs")
                Files.createDirectories(directory)
                Files.write(
                    directory.resolve("gobby-cache-state.log"),
                    lines.map { "$it${System.lineSeparator()}" },
                    Charsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
            }
        }
    }
}
