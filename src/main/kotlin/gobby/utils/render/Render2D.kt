package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.getColorAsInt
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color

object Render2D {

    // Minecraft color code mappings
    private val colorCodes = mapOf(
        '0' to Color(0, 0, 0),         // Black
        '1' to Color(0, 0, 170),       // Dark Blue
        '2' to Color(0, 170, 0),       // Dark Green
        '3' to Color(0, 170, 170),     // Dark Aqua
        '4' to Color(170, 0, 0),       // Dark Red
        '5' to Color(170, 0, 170),     // Dark Purple
        '6' to Color(255, 170, 0),     // Gold
        '7' to Color(170, 170, 170),   // Gray
        '8' to Color(85, 85, 85),      // Dark Gray
        '9' to Color(85, 85, 255),     // Blue
        'a' to Color(85, 255, 85),     // Green
        'b' to Color(85, 255, 255),    // Aqua
        'c' to Color(255, 85, 85),     // Red
        'd' to Color(255, 85, 255),    // Light Purple
        'e' to Color(255, 255, 85),    // Yellow
        'f' to Color(255, 255, 255),   // White
        'r' to Color(255, 255, 255)    // Reset
    )

    data class TextSegment(
        val text: String,
        val color: Color,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underlined: Boolean = false,
        val strikethrough: Boolean = false,
        val obfuscated: Boolean = false
    )

    fun drawString(
        text: String,
        x: Float,
        y: Float,
        color: Color,
        scale: Float = 1.0f,
        drawContext: GuiGraphicsExtractor
    ) {
        val segments = parseColorCodes(text, color)
        val matrixStack = drawContext.pose()
        matrixStack.pushMatrix()

        matrixStack.translate(x, y)
        matrixStack.scale(scale, scale)

        var currentX = 0
        for (segment in segments) {
            if (segment.text.isNotEmpty()) {
                drawContext.text(
                    mc.font,
                    segment.text,
                    currentX,
                    0,
                    segment.color.getColorAsInt(),
                    segment.bold
                )
                currentX += mc.font.width(segment.text)
            }
        }

        matrixStack.popMatrix()
    }

    private fun parseColorCodes(text: String, defaultColor: Color): List<TextSegment> =
        ColorCodeParser(text, defaultColor, colorCodes).parse()
}

private class ColorCodeParser(
    private val text: String,
    private val defaultColor: Color,
    private val colors: Map<Char, Color>
) {
    private var currentColor = defaultColor
    private var bold = false
    private var italic = false
    private var underlined = false
    private var strikethrough = false
    private var obfuscated = false

    fun parse(): List<Render2D.TextSegment> {
        val segments = mutableListOf<Render2D.TextSegment>()
        val currentText = StringBuilder()
        var index = 0
        while (index < text.length) {
            if (text[index] == '\u00A7' && index + 1 < text.length) {
                appendSegment(segments, currentText)
                applyCode(text[index + 1].lowercaseChar())
                index += 2
            } else {
                currentText.append(text[index++])
            }
        }
        appendSegment(segments, currentText)
        return segments
    }

    private fun appendSegment(segments: MutableList<Render2D.TextSegment>, text: StringBuilder) {
        if (text.isEmpty()) return
        segments += Render2D.TextSegment(text.toString(), currentColor, bold, italic, underlined, strikethrough, obfuscated)
        text.clear()
    }

    private fun applyCode(code: Char) {
        val color = colors[code]
        if (color != null) {
            currentColor = color
            resetFormatting()
            if (code == 'r') currentColor = defaultColor
            return
        }
        when (code) {
            'l' -> bold = true
            'o' -> italic = true
            'n' -> underlined = true
            'm' -> strikethrough = true
            'k' -> obfuscated = true
        }
    }

    private fun resetFormatting() {
        bold = false
        italic = false
        underlined = false
        strikethrough = false
        obfuscated = false
    }
}

