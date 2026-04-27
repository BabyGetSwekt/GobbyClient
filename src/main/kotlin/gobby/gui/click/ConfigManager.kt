package gobby.gui.click

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import gobby.Gobbyclient
import gobby.gui.hud.HudManager
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
                        is HudButton -> {}
                        is DropDownSetting -> {}
                    }
                }
                modulesJson.add(module.name, mj)
            }
            root.add("modules", modulesJson)

            val panelsJson = JsonObject()
            for ((cat, pos) in ClickGUI.panelPositions) {
                val pj = JsonObject()
                pj.addProperty("x", pos.first)
                pj.addProperty("y", pos.second)
                panelsJson.add(cat.name, pj)
            }
            root.add("panels", panelsJson)

            val hudsJson = JsonObject()
            for (hud in HudManager.getAll()) {
                val hj = JsonObject()
                hj.addProperty("x", hud.hudX)
                hj.addProperty("y", hud.hudY)
                hj.addProperty("scale", hud.hudScale)
                hudsJson.add(hud.name, hj)
            }
            root.add("huds", hudsJson)

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
                            is HudButton -> {}
                            is DropDownSetting -> {}
                        }
                    } catch (_: Exception) {}
                }
            }

            val panelsJson = root.getAsJsonObject("panels")
            if (panelsJson != null) {
                for (cat in Category.entries) {
                    val pj = panelsJson.getAsJsonObject(cat.name) ?: continue
                    val x = pj.get("x")?.asFloat ?: continue
                    val y = pj.get("y")?.asFloat ?: continue
                    ClickGUI.panelPositions[cat] = Pair(x, y)
                }
            }

            val hudsJson = root.getAsJsonObject("huds")
            if (hudsJson != null) {
                for (hud in HudManager.getAll()) {
                    val hj = hudsJson.getAsJsonObject(hud.name) ?: continue
                    hud.hudX = hj.get("x")?.asFloat ?: 0f
                    hud.hudY = hj.get("y")?.asFloat ?: 0f
                    hud.hudScale = hj.get("scale")?.asFloat ?: 1f
                }
            }
        } catch (e: Exception) {
            Gobbyclient.logger.error("Failed to load config", e)
        }
    }
}
