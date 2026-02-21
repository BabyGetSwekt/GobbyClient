package gobby.gui.components

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.vigilance.gui.settings.SettingComponent
import gobby.Gobbyclient.Companion.mc
import java.awt.Color

class InfoPropertyComponent(private val infoText: String) : SettingComponent() {

    private var popupVisible = false
    private var contentHeight = 80f

    private val iconCircle = UIRoundedRectangle(8f).constrain {
        width = 16.pixels
        height = 16.pixels
        color = Color(59, 130, 246).toConstraint()
    } childOf this

    private val questionMark = UIText("?").constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color.WHITE.toConstraint()
        textScale = 1.pixels
    } childOf iconCircle

    private val popup = UIRoundedRectangle(4f).constrain {
        width = 200.pixels
        height = basicHeightConstraint { contentHeight }
        color = Color(24, 24, 30, 240).toConstraint()
    }

    private val contentArea = UIBlock(Color(0, 0, 0, 0)).constrain {
        x = 8.pixels
        y = 8.pixels
        width = 184.pixels
        height = 100.percent - 16.pixels
    } childOf popup

    init {
        constrain {
            width = 16.pixels
            height = 16.pixels
        }

        buildFormattedContent()

        iconCircle.onMouseEnter {
            if (!popupVisible) {
                popupVisible = true
                val window = Window.of(this@InfoPropertyComponent)
                val iconLeft = iconCircle.getLeft()
                val iconCenterY = iconCircle.getTop() + iconCircle.getHeight() / 2f
                popup.constrain {
                    x = (iconLeft - 204f).pixels
                    y = (iconCenterY - contentHeight / 2f).pixels
                }
                window.addChild(popup)
            }
        }

        iconCircle.onMouseLeave {
            if (popupVisible) {
                popupVisible = false
                Window.of(this@InfoPropertyComponent).removeChild(popup)
            }
        }
    }

    private fun buildFormattedContent() {
        val textRenderer = mc?.textRenderer ?: return
        val segments = parseSegments(infoText)
        val maxWidth = 184f
        val lineHeight = 10f
        val spaceWidth = textRenderer.getWidth(" ").toFloat()
        var xPos = 0f
        var yPos = 0f

        for (segment in segments) {
            if (segment.isCode) {
                val textWidth = textRenderer.getWidth(segment.text).toFloat()
                val blockWidth = textWidth + 6f

                if (xPos + blockWidth > maxWidth && xPos > 0) {
                    xPos = 0f
                    yPos += lineHeight
                }

                val codeBg = UIRoundedRectangle(2f).constrain {
                    x = xPos.pixels
                    y = (yPos - 1f).pixels
                    width = blockWidth.pixels
                    height = (lineHeight + 2).pixels
                    color = CODE_BG_COLOR.toConstraint()
                } childOf contentArea

                UIText(segment.text).constrain {
                    x = 3.pixels
                    y = CenterConstraint()
                    color = CODE_TEXT_COLOR.toConstraint()
                    textScale = 1.pixels
                } childOf codeBg

                xPos += blockWidth + 2f
            } else {
                val lines = segment.text.split("\n")
                for ((lineIdx, line) in lines.withIndex()) {
                    if (lineIdx > 0) {
                        xPos = 0f
                        yPos += lineHeight
                    }

                    val words = line.split(" ")
                    for ((wordIdx, word) in words.withIndex()) {
                        if (word.isEmpty()) {
                            if (wordIdx < words.lastIndex) xPos += spaceWidth
                            continue
                        }

                        val wordWidth = textRenderer.getWidth(word).toFloat()

                        if (xPos + wordWidth > maxWidth && xPos > 0) {
                            xPos = 0f
                            yPos += lineHeight
                        }

                        UIText(word).constrain {
                            x = xPos.pixels
                            y = yPos.pixels
                            color = TEXT_COLOR.toConstraint()
                            textScale = 1.pixels
                        } childOf contentArea

                        xPos += wordWidth
                        if (wordIdx < words.lastIndex) xPos += spaceWidth
                    }
                }
            }
        }

        contentHeight = yPos + lineHeight + 16f
    }

    private data class Segment(val text: String, val isCode: Boolean)

    private fun parseSegments(text: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        var isCode = false
        val current = StringBuilder()

        for (char in text) {
            if (char == '`') {
                if (current.isNotEmpty()) {
                    segments.add(Segment(current.toString(), isCode))
                    current.clear()
                }
                isCode = !isCode
            } else {
                current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            segments.add(Segment(current.toString(), isCode))
        }
        return segments
    }

    companion object {
        private val TEXT_COLOR = Color(200, 200, 210)
        private val CODE_TEXT_COLOR = Color(230, 230, 240)
        private val CODE_BG_COLOR = Color(0, 0, 0, 180)
    }
}
