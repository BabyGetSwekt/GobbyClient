package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.Registries
import net.minecraft.util.Formatting
import java.io.File

object CopyGui : Module("Copy GUI", "Press the keybind in a GUI to dump its contents to /schematics", Category.DEVELOPER) {

    private val copyKey by KeybindSetting("Copy GUI", desc = "Press in any container GUI to copy its contents to a JSON file in /schematics")

    private val schematicsDir = File("./config/gobbyclientFabric/schematics").apply { mkdirs() }

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled) return
        if (copyKey == 0 || event.key != copyKey) return

        val screen = mc.currentScreen as? HandledScreen<*>
        if (screen == null) {
            errorMessage("Not in a container GUI")
            enabled = false
            return
        }

        copyScreen(screen)
        enabled = false
    }

    private fun copyScreen(screen: HandledScreen<*>) {
        val handler = screen.screenHandler
        val title = screen.title.string
        val world = mc.world ?: return

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"title\": ${jsonString(title)},")
        sb.appendLine("  \"size\": ${handler.slots.size},")
        sb.appendLine("  \"slots\": [")

        val nonEmpty = handler.slots.filter { !it.stack.isEmpty }
        for ((i, slot) in nonEmpty.withIndex()) {
            val stack = slot.stack
            val itemId = Registries.ITEM.getId(stack.item).toString()
            val name = Formatting.strip(stack.name.string) ?: ""
            val lore = stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).styledLines()
                .map { Formatting.strip(it.string) ?: "" }
            val nbt = encodeStack(stack)
            val comma = if (i < nonEmpty.size - 1) "," else ""

            sb.appendLine("    {")
            sb.appendLine("      \"slot\": ${slot.id},")
            sb.appendLine("      \"item\": ${jsonString(itemId)},")
            sb.appendLine("      \"count\": ${stack.count},")
            sb.appendLine("      \"name\": ${jsonString(name)},")
            sb.appendLine("      \"lore\": [${lore.joinToString(",") { jsonString(it) }}],")
            sb.appendLine("      \"nbt\": ${jsonString(nbt)}")
            sb.appendLine("    }$comma")
        }

        sb.appendLine("  ]")
        sb.appendLine("}")

        val safeTitle = title.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "container" }
        val file = File(schematicsDir, "gui_${safeTitle}_${System.currentTimeMillis()}.json")
        file.writeText(sb.toString())
        modMessage("§aCopied GUI §f\"$title\" §a(${nonEmpty.size}/${handler.slots.size} slots) to §e${file.name}")
    }

    private fun encodeStack(stack: ItemStack): String {
        val registries = mc.world?.registryManager ?: return ""
        return try {
            ItemStack.CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), stack)
                .result().orElse(null)?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun jsonString(s: String): String {
        val out = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c.code < 0x20) out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }
}
