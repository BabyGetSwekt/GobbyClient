package gobby.gui.click

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import gobby.Gobbyclient
import java.awt.Color
import java.io.File

object ConfigManager {

    private val configFile = File("./config/gobbyclientFabric/config.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun save() {
        try {
            val root = JsonObject()

            val modulesJson = JsonObject()
            for (module in Module.modules) {
                val mj = JsonObject()
                if (module.toggled) mj.addProperty("enabled", module.enabled)
                module.keybindSetting?.let { mj.addProperty(it.name, it.value) }
                for (setting in module.settings) {
                    when (setting) {
                        is BooleanSetting -> mj.addProperty(setting.name, setting.value)
                        is NumberSetting -> mj.addProperty(setting.name, setting.value)
                        is SelectorSetting -> mj.addProperty(setting.name, setting.value)
                        is ColorSetting -> mj.addProperty(setting.name, setting.value.rgb)
                        is KeybindSetting -> mj.addProperty(setting.name, setting.value)
                        is ActionSetting -> {}
                    }
                }
                modulesJson.add(module.name, mj)
            }
            root.add("modules", modulesJson)

            // Panel positions
            val panelsJson = JsonObject()
            for ((cat, pos) in ClickGUI.panelPositions) {
                val pj = JsonObject()
                pj.addProperty("x", pos.first)
                pj.addProperty("y", pos.second)
                panelsJson.add(cat.name, pj)
            }
            root.add("panels", panelsJson)

            configFile.parentFile.mkdirs()
            configFile.writeText(gson.toJson(root))
        } catch (e: Exception) {
            Gobbyclient.logger.error("Failed to save config", e)
        }
    }

    fun load() {
        if (!configFile.exists()) return
        try {
            val root = gson.fromJson(configFile.readText(), JsonObject::class.java) ?: return

            val modulesJson = root.getAsJsonObject("modules") ?: JsonObject()
            for (module in Module.modules) {
                val mj = modulesJson.getAsJsonObject(module.name) ?: continue
                if (module.toggled && mj.has("enabled")) {
                    module.enabled = mj.get("enabled").asBoolean
                }
                module.keybindSetting?.let { kb ->
                    if (mj.has(kb.name)) try { kb.value = mj.get(kb.name).asInt } catch (_: Exception) {}
                }
                for (setting in module.settings) {
                    if (!mj.has(setting.name)) continue
                    try {
                        when (setting) {
                            is BooleanSetting -> setting.value = mj.get(setting.name).asBoolean
                            is NumberSetting -> setting.value = mj.get(setting.name).asInt
                            is SelectorSetting -> setting.value = mj.get(setting.name).asInt
                            is ColorSetting -> setting.value = Color(mj.get(setting.name).asInt, true)
                            is KeybindSetting -> setting.value = mj.get(setting.name).asInt
                            is ActionSetting -> {}
                        }
                    } catch (_: Exception) {}
                }
            }

            // Panel positions
            val panelsJson = root.getAsJsonObject("panels")
            if (panelsJson != null) {
                for (cat in Category.entries) {
                    val pj = panelsJson.getAsJsonObject(cat.name) ?: continue
                    val x = pj.get("x")?.asFloat ?: continue
                    val y = pj.get("y")?.asFloat ?: continue
                    ClickGUI.panelPositions[cat] = Pair(x, y)
                }
            }
        } catch (e: Exception) {
            Gobbyclient.logger.error("Failed to load config", e)
        }
    }
}
