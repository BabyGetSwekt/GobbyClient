package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.NbtOps
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.ChatFormatting
import java.io.File

object CopyGui : Module("Copy GUI", "Press the keybind in a GUI to dump its contents to /schematics", Category.DEVELOPER) {

    private val copyKey by KeybindSetting("Copy GUI", desc = "Press in any container GUI to copy its contents to a JSON file in /schematics")

    private val schematicsDir = File("./config/gobbyclientFabric/schematics").apply { mkdirs() }

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled) return
        if (copyKey == 0 || event.key != copyKey) return

        //? if >26.1.2
        val screen = mc.gui.screen() as? AbstractContainerScreen<*>
        //? if <=26.1.2
        /*val screen = mc.screen as? AbstractContainerScreen<*>*/
        if (screen == null) {
            errorMessage("Not in a container GUI")
            enabled = false
            return
        }

        copyScreen(screen)
        enabled = false
    }

    private fun copyScreen(screen: AbstractContainerScreen<*>) {
        val handler = screen.menu
        val title = screen.title.string
        val world = mc.level ?: return

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"title\": ${jsonString(title)},")
        sb.appendLine("  \"size\": ${handler.slots.size},")
        sb.appendLine("  \"slots\": [")

        val nonEmpty = handler.slots.filter { !it.item.isEmpty }
        for ((i, slot) in nonEmpty.withIndex()) {
            val stack = slot.item
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            val name = ChatFormatting.stripFormatting(stack.hoverName.string) ?: ""
            val lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines()
                .map { ChatFormatting.stripFormatting(it.string) ?: "" }
            val nbt = encodeStack(stack)
            val comma = if (i < nonEmpty.size - 1) "," else ""

            sb.appendLine("    {")
            sb.appendLine("      \"slot\": ${slot.index},")
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
        val registries = mc.level?.registryAccess() ?: return ""
        return try {
            ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
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
