package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.isDeveloper
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.MathHelper.hsvToRgb
import java.awt.Color

object ChatUtils {

    val kuudraTierRegex = Regex("Kuudra's Hollow \\(T(\\d+)\\)$")

    // Regex patterns for matching chat messages [all chat, party chat]
    val publicMessageRegex = Regex("""^\[\d+]\s+(\[[^]]+])?\s?(\w{1,16})(?: [ቾ⚒])?: (.+)$""")
    val partyMessageRegex = Regex("""^Party > (\[[^]]*])?\s?(\w{1,16})(?: [ቾ⚒])?: (.+)$""")

    val publicCoordRegex = Regex("""^\[\d+]\s+(?:\[[^]]+]\s+)?(\w{1,16})(?: [ቾ⚒])?: x: (-?\d+), y: (-?\d+), z: (-?\d+)$""")
    val partyCoordRegex = Regex("""^Party > (?:\[[^]]+]\s+)?(\w{1,16})(?: [ቾ⚒])?: x: (-?\d+), y: (-?\d+), z: (-?\d+)$""")



    private const val PREFIX = "§b[§3Gobby Client§b] §8»§r"
    private const val DEV_PREFIX = "§2[§aGobby Client§2] §8»§r"
    private val AQUA_PREFIX: MutableText
        get() = Text.empty()
            .append(Text.literal("[").formatted(Formatting.GRAY))
            .append(Text.literal("G").withColor(0x00FFAA))
            .append(Text.literal("o").withColor(0x00DDDD))
            .append(Text.literal("b").withColor(0x00BBBB))
            .append(Text.literal("b").withColor(0x009999))
            .append(Text.literal("y").withColor(0x007777))
            .append(Text.literal(" Client").formatted(Formatting.AQUA))
            .append(Text.literal("] ").formatted(Formatting.GRAY))
            .append(Text.literal("» ").formatted(Formatting.DARK_GRAY))

    private val RAINBOW_PREFIX_COLOR: MutableText
        get() {
            val prefix = Text.empty()
                .append(Text.literal("[").formatted(Formatting.GRAY))

            val text = "Gobby Client"
            val length = text.length

            text.forEachIndexed { index, char ->
                val hue = index.toFloat() / length
                val rgb = hsvToRgb(hue, 1f, 1f)
                prefix.append(Text.literal(char.toString()).withColor(rgb))
            }

            prefix.append(Text.literal("] ").formatted(Formatting.GRAY))
            prefix.append(Text.literal("» ").formatted(Formatting.DARK_GRAY))

            return prefix
        }

    @JvmStatic
    fun modMessage(message: Any, showPrefix: Boolean = true) {
        if (mc.player == null || mc.world == null || message == "") return
        val msg = if (showPrefix) "$PREFIX $message" else message.toString()
        mc.execute { mc.inGameHud?.chatHud?.addMessage(Text.literal(msg)) }
    }

    fun devMessage(message: Any, showPrefix: Boolean = true) {
        if (mc.player == null || mc.world == null || message == "" || !isDeveloper() || !gobby.features.developer.DevMode.enabled) return
        val msg = if (showPrefix) "$DEV_PREFIX $message" else message.toString()
        mc.execute { mc.inGameHud?.chatHud?.addMessage(Text.literal(msg)) }
    }

    fun coloredModMessage(message: String, showPrefix: Boolean = true) {
        if (mc.player == null || mc.world == null || message == "") return
        val text: MutableText = if (showPrefix) RAINBOW_PREFIX_COLOR.copy().append(Text.literal(message)) else Text.literal(message)
        mc.inGameHud?.chatHud?.addMessage(text)
    }

    fun errorMessage(message: Any) {
        if (mc.player == null || mc.world == null || message == "") return
        val text = Text.empty()
            .append(Text.literal("[").formatted(Formatting.DARK_RED))
            .append(Text.literal("Gobby Client").formatted(Formatting.RED))
            .append(Text.literal("] ").formatted(Formatting.DARK_RED))
            .append(Text.literal("» ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(message.toString()).formatted(Formatting.RED))
        mc.execute { mc.inGameHud?.chatHud?.addMessage(text) }
    }

    fun sendMessage(message: Any) {
        if (mc.player == null || mc.world == null || message == "") return
        mc.player?.networkHandler?.sendChatMessage(message.toString())
    }

    fun sendCommand(message: Any) {
        if (mc.player == null || mc.world == null || message == "") return
        mc.player?.networkHandler?.sendChatMessage("/${message.toString()}")
    }

    fun partyMessage(message: String) {
        if (mc.player == null || mc.world == null || message == "") return
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