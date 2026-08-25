package gobby.features.dungeons

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.io.File

internal object BrushPersistence {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataType = object : TypeToken<MutableMap<String, MutableMap<String, MutableList<String>>>>() {}.type
    private val favoritesType = object : TypeToken<MutableSet<String>>() {}.type

    fun loadData(file: File, label: String): MutableMap<String, MutableMap<String, MutableList<String>>> {
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            gson.fromJson<MutableMap<String, MutableMap<String, MutableList<String>>>>(file.readText(), dataType)
                ?: mutableMapOf()
        }.getOrElse {
            println("[GobbyClient] Failed to load $label data: ${it.message}")
            mutableMapOf()
        }
    }

    fun saveData(file: File, label: String, data: MutableMap<String, MutableMap<String, MutableList<String>>>) =
        save(file, label, gson.toJson(data))

    fun loadFavorites(file: File): Pair<MutableSet<String>, Boolean> {
        if (!file.exists()) return mutableSetOf<String>() to false
        return runCatching {
            val json = gson.fromJson(file.readText(), JsonObject::class.java)
            val blocks = gson.fromJson<MutableSet<String>>(json.getAsJsonArray("blocks"), favoritesType) ?: mutableSetOf()
            blocks to (json.get("showFavorites")?.asBoolean ?: false)
        }.getOrElse {
            println("[GobbyClient] Failed to load favorites: ${it.message}")
            mutableSetOf<String>() to false
        }
    }

    fun saveFavorites(file: File, blocks: Set<String>, showFavorites: Boolean) {
        val json = JsonObject()
        json.add("blocks", gson.toJsonTree(blocks))
        json.addProperty("showFavorites", showFavorites)
        save(file, "favorites", gson.toJson(json))
    }

    private fun save(file: File, label: String, content: String) {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(content)
        }.onFailure { println("[GobbyClient] Failed to save $label data: ${it.message}") }
    }
}
