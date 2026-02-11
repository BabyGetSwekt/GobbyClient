package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.getColorAsInt
import net.minecraft.client.gui.DrawContext
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

    // Formatting codes
    private val formatCodes = setOf('k', 'l', 'm', 'n', 'o', 'r')

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
        drawContext: DrawContext
    ) {
        val segments = parseColorCodes(text, color)
        val matrixStack = drawContext.matrices
        matrixStack.pushMatrix()

        matrixStack.translate(x, y)
        matrixStack.scale(scale, scale)

        var currentX = 0
        for (segment in segments) {
            if (segment.text.isNotEmpty()) {
                drawContext.drawText(
                    mc.textRenderer,
                    segment.text,
                    currentX,
                    0,
                    segment.color.getColorAsInt(),
                    segment.bold
                )
                currentX += mc.textRenderer.getWidth(segment.text)
            }
        }

        matrixStack.popMatrix()
    }

    private fun parseColorCodes(text: String, defaultColor: Color): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        val chars = text.toCharArray()
        var i = 0

        var currentColor = defaultColor
        var bold = false
        var italic = false
        var underlined = false
        var strikethrough = false
        var obfuscated = false

        val currentText = StringBuilder()

        while (i < chars.size) {
            if (chars[i] == '§' && i + 1 < chars.size) {
                // Save current text segment if it exists
                if (currentText.isNotEmpty()) {
                    segments.add(TextSegment(
                        currentText.toString(),
                        currentColor,
                        bold,
                        italic,
                        underlined,
                        strikethrough,
                        obfuscated
                    ))
                    currentText.clear()
                }

                val code = chars[i + 1].lowercaseChar()

                when {
                    colorCodes.containsKey(code) -> {
                        currentColor = colorCodes[code]!!
                        // Reset formatting when color changes (except for 'r' which is handled separately)
                        if (code != 'r') {
                            bold = false
                            italic = false
                            underlined = false
                            strikethrough = false
                            obfuscated = false
                        } else {
                            // Reset everything including color
                            currentColor = defaultColor
                            bold = false
                            italic = false
                            underlined = false
                            strikethrough = false
                            obfuscated = false
                        }
                    }
                    code == 'l' -> bold = true
                    code == 'o' -> italic = true
                    code == 'n' -> underlined = true
                    code == 'm' -> strikethrough = true
                    code == 'k' -> obfuscated = true
                }

                i += 2 // Skip the § and the code character
            } else {
                currentText.append(chars[i])
                i++
            }
        }

        // Add remaining text
        if (currentText.isNotEmpty()) {
            segments.add(TextSegment(
                currentText.toString(),
                currentColor,
                bold,
                italic,
                underlined,
                strikethrough,
                obfuscated
            ))
        }

        return segments
    }
}