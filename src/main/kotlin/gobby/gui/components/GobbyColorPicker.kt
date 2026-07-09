package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.GradientComponent
import gg.essential.elementa.components.GradientComponent.GradientDirection
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import java.awt.Color

private const val SWATCH_CORNER = 2f
private const val POPUP_PAD = 6f
private const val POPUP_WIDTH = 152f
private const val AREA_WIDTH = POPUP_WIDTH - POPUP_PAD * 2
private const val SV_HEIGHT = 88f
private const val BAR_HEIGHT = 12f
private const val BAR_GAP = 7f
private const val HUE_Y = POPUP_PAD + SV_HEIGHT + BAR_GAP
private const val ALPHA_Y = HUE_Y + BAR_HEIGHT + BAR_GAP
private const val POPUP_HEIGHT = ALPHA_Y + BAR_HEIGHT + POPUP_PAD
private const val CURSOR_SIZE = 7f
private const val CURSOR_HALF = CURSOR_SIZE / 2f
private const val BAR_CURSOR_WIDTH = 3f
private const val BAR_CURSOR_HALF = BAR_CURSOR_WIDTH / 2f
private const val HUE_SEGMENTS = 6
private const val MAX_HUE = 0.9999f
private const val FULL_ALPHA = 255
private const val CURSOR_OUTLINE_ALPHA = 160

private val TRANSPARENT = Color(0, 0, 0, 0)
private val TRANSPARENT_WHITE = Color(255, 255, 255, 0)
private val HUE_STOPS = listOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)

private enum class DragTarget { NONE, SV, HUE, ALPHA }

class GobbyColorPicker(
    private val window: UIComponent,
    initial: Color,
    private val onColorChange: (Color) -> Unit
) : UIRoundedRectangle(SWATCH_CORNER) {

    private var hue: Float
    private var saturation: Float
    private var brightness: Float
    private var alpha: Int
    private var current: Color = initial

    private var popup: FloatingPopup? = null
    private var dragTarget = DragTarget.NONE

    private var svBase: UIBlock? = null
    private var svCursor: UIComponent? = null
    private var hueCursor: UIComponent? = null
    private var alphaCursor: UIComponent? = null
    private var alphaGradient: GradientComponent? = null

    init {
        val hsb = Color.RGBtoHSB(initial.red, initial.green, initial.blue, null)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
        alpha = initial.alpha

        constrain { color = current.toConstraint() }
        enableEffect(OutlineEffect(ComponentTheme.BORDER, 1f))
        onMouseClick { event ->
            event.stopPropagation()
            if (popup == null) openPopup() else popup?.close()
        }
    }

    fun getColorValue(): Color = current

    private fun openPopup() {
        val newPopup = FloatingPopup(window, this, POPUP_WIDTH, POPUP_HEIGHT) {
            popup = null
            dragTarget = DragTarget.NONE
            svBase = null
            svCursor = null
            hueCursor = null
            alphaCursor = null
            alphaGradient = null
        }
        buildContent(newPopup.container)
        popup = newPopup
    }

    private fun buildContent(container: UIComponent) {
        val base = UIBlock(hueColor()).constrain {
            x = POPUP_PAD.pixels
            y = POPUP_PAD.pixels
            width = AREA_WIDTH.pixels
            height = SV_HEIGHT.pixels
        } childOf container
        svBase = base

        GradientComponent(Color.WHITE, TRANSPARENT_WHITE, GradientDirection.LEFT_TO_RIGHT).constrain {
            width = 100.percent
            height = 100.percent
        } childOf base

        GradientComponent(TRANSPARENT, Color.BLACK, GradientDirection.TOP_TO_BOTTOM).constrain {
            width = 100.percent
            height = 100.percent
        } childOf base

        svCursor = UIBlock(TRANSPARENT).constrain {
            width = CURSOR_SIZE.pixels
            height = CURSOR_SIZE.pixels
        }.also { it.enableEffect(OutlineEffect(Color.WHITE, 1f)) } childOf base

        UIBlock(TRANSPARENT).constrain {
            width = 100.percent
            height = 100.percent
        }.also {
            it childOf base
            it.onMouseClick { event ->
                event.stopPropagation()
                dragTarget = DragTarget.SV
                updateSv(event.relativeX, event.relativeY)
            }
            it.onMouseDrag { mouseX, mouseY, _ -> if (dragTarget == DragTarget.SV) updateSv(mouseX, mouseY) }
            it.onMouseRelease { dragTarget = DragTarget.NONE }
        }

        val hueBar = UIBlock(ComponentTheme.POPUP_BG).constrain {
            x = POPUP_PAD.pixels
            y = HUE_Y.pixels
            width = AREA_WIDTH.pixels
            height = BAR_HEIGHT.pixels
        } childOf container
        buildHueSegments(hueBar)
        hueCursor = barCursor(hueBar)
        attachBarInteraction(hueBar, DragTarget.HUE) { updateHue(it) }

        val alphaBar = UIBlock(ComponentTheme.POPUP_BG).constrain {
            x = POPUP_PAD.pixels
            y = ALPHA_Y.pixels
            width = AREA_WIDTH.pixels
            height = BAR_HEIGHT.pixels
        } childOf container
        alphaGradient = GradientComponent(alphaColor(0), alphaColor(FULL_ALPHA), GradientDirection.LEFT_TO_RIGHT).constrain {
            width = 100.percent
            height = 100.percent
        } childOf alphaBar
        alphaCursor = barCursor(alphaBar)
        attachBarInteraction(alphaBar, DragTarget.ALPHA) { updateAlpha(it) }

        syncVisuals()
    }

    private fun buildHueSegments(hueBar: UIComponent) {
        val segmentWidth = AREA_WIDTH / HUE_SEGMENTS
        (0 until HUE_SEGMENTS).forEach { index ->
            GradientComponent(HUE_STOPS[index], HUE_STOPS[index + 1], GradientDirection.LEFT_TO_RIGHT).constrain {
                x = (index * segmentWidth).pixels
                width = segmentWidth.pixels
                height = 100.percent
            } childOf hueBar
        }
    }

    private fun barCursor(parent: UIComponent): UIComponent = UIBlock(Color.WHITE).constrain {
        width = BAR_CURSOR_WIDTH.pixels
        height = 100.percent
    }.also { it.enableEffect(OutlineEffect(Color(0, 0, 0, CURSOR_OUTLINE_ALPHA), 1f)) } childOf parent

    private fun attachBarInteraction(bar: UIComponent, target: DragTarget, update: (Float) -> Unit) {
        UIBlock(TRANSPARENT).constrain {
            width = 100.percent
            height = 100.percent
        }.also {
            it childOf bar
            it.onMouseClick { event ->
                event.stopPropagation()
                dragTarget = target
                update(event.relativeX)
            }
            it.onMouseDrag { mouseX, _, _ -> if (dragTarget == target) update(mouseX) }
            it.onMouseRelease { dragTarget = DragTarget.NONE }
        }
    }

    private fun updateSv(relativeX: Float, relativeY: Float) {
        saturation = (relativeX / AREA_WIDTH).coerceIn(0f, 1f)
        brightness = (1f - relativeY / SV_HEIGHT).coerceIn(0f, 1f)
        applyChange()
    }

    private fun updateHue(relativeX: Float) {
        hue = (relativeX / AREA_WIDTH).coerceIn(0f, MAX_HUE)
        applyChange()
    }

    private fun updateAlpha(relativeX: Float) {
        alpha = ((relativeX / AREA_WIDTH).coerceIn(0f, 1f) * FULL_ALPHA).toInt()
        applyChange()
    }

    private fun applyChange() {
        syncVisuals()
        onColorChange(current)
    }

    private fun syncVisuals() {
        val opaque = Color(Color.HSBtoRGB(hue, saturation, brightness))
        current = Color(opaque.red, opaque.green, opaque.blue, alpha)
        setColor(current)
        svBase?.setColor(hueColor())
        alphaGradient?.setStartColor(Color(opaque.red, opaque.green, opaque.blue, 0))
        alphaGradient?.setEndColor(Color(opaque.red, opaque.green, opaque.blue, FULL_ALPHA))
        svCursor?.setX((saturation * AREA_WIDTH - CURSOR_HALF).pixels)
        svCursor?.setY(((1f - brightness) * SV_HEIGHT - CURSOR_HALF).pixels)
        hueCursor?.setX((hue * AREA_WIDTH - BAR_CURSOR_HALF).pixels)
        alphaCursor?.setX((alpha / FULL_ALPHA.toFloat() * AREA_WIDTH - BAR_CURSOR_HALF).pixels)
    }

    private fun hueColor(): Color = Color(Color.HSBtoRGB(hue, 1f, 1f))

    private fun alphaColor(value: Int): Color {
        val opaque = Color(Color.HSBtoRGB(hue, saturation, brightness))
        return Color(opaque.red, opaque.green, opaque.blue, value)
    }
}
