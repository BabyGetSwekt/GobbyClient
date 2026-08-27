package gobby.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import gobby.Gobbyclient.Companion.MOD_ID
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Type

private const val CONFIG_ROOT = "./config/gobbyclientFabric"
private const val JSON_EXTENSION = "json"

private val logger = LoggerFactory.getLogger(MOD_ID)

object ConfigUtils {

    val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun directory(folder: String = ""): File =
        (if (folder.isEmpty()) File(CONFIG_ROOT) else File(CONFIG_ROOT, folder)).apply { mkdirs() }

    fun file(name: String, folder: String = ""): File =
        File(if (folder.isEmpty()) File(CONFIG_ROOT) else File(CONFIG_ROOT, folder), name)

    fun jsonFile(name: String, folder: String = ""): File = file("${name.substringBeforeLast('.')}.$JSON_EXTENSION", folder)

    inline fun <reified T : Any> makeConfig(name: String, folder: String = "", noinline default: () -> T): JsonConfig<T> =
        JsonConfig(jsonFile(name, folder), object : TypeToken<T>() {}.type, default)
}

/**
 * Reads a JSON object out of arbitrary text, giving null when the text is not one.
 */
fun parseJsonObject(raw: String): JsonObject? =
    runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()

/**
 * Reads a string field, giving null when it is absent, null or empty.
 */
fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.asString?.takeUnless { it.isEmpty() }

class JsonConfig<T : Any>(
    val file: File,
    private val type: Type,
    private val default: () -> T
) {
    var data: T = default()
        private set

    val isNew: Boolean = !file.exists()

    init {
        reload()
    }

    fun reload(): T {
        data = readFromDisk() ?: default()
        return data
    }

    fun save(value: T = data) {
        data = value
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ConfigUtils.gson.toJson(value))
        }.onFailure { logger.error("Failed to save config ${file.name}", it) }
    }

    fun edit(block: T.() -> Unit) {
        data.block()
        save()
    }

    private fun readFromDisk(): T? {
        if (!file.exists()) return null
        return runCatching { ConfigUtils.gson.fromJson<T>(file.readText(), type) }
            .onFailure { logger.error("Failed to load config ${file.name}", it) }
            .getOrNull()
    }
}
