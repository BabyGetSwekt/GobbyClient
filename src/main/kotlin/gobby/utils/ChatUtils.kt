package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.isDeveloper
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import net.minecraft.util.Mth.hsvToRgb
import java.awt.Color

object ChatUtils {

    val kuudraTierRegex = Regex("Kuudra's Hollow \\(T(\\d+)\\)$")

    private val FORMATTING_CODE_PATTERN = Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE)
    val String.noControlCodes: String get() = FORMATTING_CODE_PATTERN.replace(this, "")

    // Regex patterns for matching chat messages [all chat, party chat]
    val publicMessageRegex = Regex("""^\[\d+]\s+(\[[^]]+])?\s?(\w{1,16})(?: [ቾ⚒])?: (.+)$""")
    val partyMessageRegex = Regex("""^Party > (\[[^]]*])?\s?(\w{1,16})(?: [ቾ⚒])?: (.+)$""")



    private const val PREFIX = "§b[§3Gobby Client§b] §8»§r"
    private const val DEV_PREFIX = "§2[§aGobby Client§2] §8»§r"
    private val AQUA_PREFIX: MutableComponent
        get() = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("G").withColor(0x00FFAA))
            .append(Component.literal("o").withColor(0x00DDDD))
            .append(Component.literal("b").withColor(0x00BBBB))
            .append(Component.literal("b").withColor(0x009999))
            .append(Component.literal("y").withColor(0x007777))
            .append(Component.literal(" Client").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("» ").withStyle(ChatFormatting.DARK_GRAY))

    private val RAINBOW_PREFIX_COLOR: MutableComponent
        get() {
            val prefix = Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.GRAY))

            val text = "Gobby Client"
            val length = text.length

            text.forEachIndexed { index, char ->
                val hue = index.toFloat() / length
                val rgb = hsvToRgb(hue, 1f, 1f)
                prefix.append(Component.literal(char.toString()).withColor(rgb))
            }

            prefix.append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
            prefix.append(Component.literal("» ").withStyle(ChatFormatting.DARK_GRAY))

            return prefix
        }

    @JvmStatic
    fun modMessage(message: Any, showPrefix: Boolean = true) {
        if (mc.player == null || mc.level == null || message == "") return
        val msg = if (showPrefix) "$PREFIX $message" else message.toString()
        mc.execute { mc.gui.hud.chat.addClientSystemMessage(Component.literal(msg)) }
    }

    fun modMessage(text: Component, showPrefix: Boolean = true) {
        if (mc.player == null || mc.level == null) return
        val msg = if (showPrefix) Component.literal("$PREFIX ").append(text) else text
        mc.execute { mc.gui.hud.chat.addClientSystemMessage(msg) }
    }

    fun devMessage(message: Any, showPrefix: Boolean = true) {
        if (mc.player == null || mc.level == null || message == "" || !isDeveloper() || !gobby.features.developer.DevMode.enabled || !gobby.features.developer.DevMode.enableDevMessages) return
        val msg = if (showPrefix) "$DEV_PREFIX $message" else message.toString()
        mc.execute { mc.gui.hud.chat.addClientSystemMessage(Component.literal(msg)) }
    }

    fun coloredModMessage(message: String, showPrefix: Boolean = true) {
        if (mc.player == null || mc.level == null || message == "") return
        val text: MutableComponent = if (showPrefix) RAINBOW_PREFIX_COLOR.copy().append(Component.literal(message)) else Component.literal(message)
        mc.gui.hud.chat.addClientSystemMessage(text)
    }

    fun errorMessage(message: Any) {
        if (mc.player == null || mc.level == null || message == "") return
        val text = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.DARK_RED))
            .append(Component.literal("Gobby Client").withStyle(ChatFormatting.RED))
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_RED))
            .append(Component.literal("» ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(message.toString()).withStyle(ChatFormatting.RED))
        mc.execute { mc.gui.hud.chat.addClientSystemMessage(text) }
    }

    fun sendMessage(message: Any) {
        if (mc.player == null || mc.level == null || message == "") return
        mc.player?.connection?.sendChat(message.toString())
    }

    fun sendCommand(message: Any) {
        if (mc.player == null || mc.level == null || message == "") return
        mc.player?.connection?.sendChat("/${message.toString()}")
    }

    fun partyMessage(message: String) {
        if (mc.player == null || mc.level == null || message == "") return
        sendCommand("pc $message")
    }

    fun Int.toColor(): Color {
        val red = (this shr 16) and 0xFF
        val green = (this shr 8) and 0xFF
        val blue = this and 0xFF
        return Color(red, green, blue)
    }

    fun Color.getColorAsInt(): Int {
        val a = (alpha shl 24) and 0xFF000000.toInt()
        val r = (red shl 16) and 0x00FF0000
        val g = (green shl 8) and 0x0000FF00
        val b = blue and 0x000000FF
        return a or r or g or b
    }
}