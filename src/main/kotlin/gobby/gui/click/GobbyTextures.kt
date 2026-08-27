package gobby.gui.click

import gobby.utils.render.TextureRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier as ResourceLocation

private const val KNOB_TEX = 128
private const val KNOB_HALF = KNOB_TEX / 2
private const val HALF = 0.5f

object GobbyTextures {

    private val knob = texture("toggle_knob")
    private val arrow = texture("triangle")
    private val resetIcon = texture("reset")
    private val gearIcon = texture("gear")
    private val trashIcon = texture("trash")
    private val checkOn = texture("check_on")
    private val checkOff = texture("check_off")
    private val addIcon = texture("add")
    private val searchIcon = texture("search")
    private val lockIcon = texture("lock")
    private val rulesIcon = texture("rules")

    private fun texture(name: String) = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/gui/$name")

    fun gear(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, gearIcon, x, y, size, size, color)

    fun trash(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, trashIcon, x, y, size, size, color)

    fun plus(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, addIcon, x, y, size, size, color)

    fun search(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, searchIcon, x, y, size, size, color)

    fun lock(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, lockIcon, x, y, size, size, color)

    fun rules(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, rulesIcon, x, y, size, size, color)

    fun checkbox(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, checked: Boolean, color: Int) =
        blit(ctx, if (checked) checkOn else checkOff, x, y, size, size, color)

    fun reset(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, resetIcon, x, y, size, size, color)

    fun disc(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) = blit(ctx, knob, x, y, size, size, color)

    fun capsule(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        when {
            w == h -> disc(ctx, x, y, w, color)
            w > h -> {
                val cap = h / 2
                part(ctx, x, y, cap, h, 0, 0, KNOB_HALF, KNOB_TEX, color)
                part(ctx, x + w - cap, y, cap, h, KNOB_HALF, 0, KNOB_HALF, KNOB_TEX, color)
                ctx.fill(x + cap, y, x + w - cap, y + h, color)
            }
            else -> {
                val cap = w / 2
                part(ctx, x, y, w, cap, 0, 0, KNOB_TEX, KNOB_HALF, color)
                part(ctx, x, y + h - cap, w, cap, 0, KNOB_HALF, KNOB_TEX, KNOB_HALF, color)
                ctx.fill(x, y + cap, x + w, y + h - cap, color)
            }
        }
    }

    fun roundedRect(
        ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int,
        topLeft: Boolean = true, topRight: Boolean = true, bottomLeft: Boolean = true, bottomRight: Boolean = true
    ) {
        val r = radius.coerceAtMost(minOf(w, h) / 2)
        if (r <= 0) return ctx.fill(x, y, x + w, y + h, color)
        corner(ctx, x, y, r, 0, 0, topLeft, color)
        corner(ctx, x + w - r, y, r, KNOB_HALF, 0, topRight, color)
        corner(ctx, x, y + h - r, r, 0, KNOB_HALF, bottomLeft, color)
        corner(ctx, x + w - r, y + h - r, r, KNOB_HALF, KNOB_HALF, bottomRight, color)
        ctx.fill(x, y + r, x + w, y + h - r, color)
        ctx.fill(x + r, y, x + w - r, y + r, color)
        ctx.fill(x + r, y + h - r, x + w - r, y + h, color)
    }

    private fun corner(ctx: GuiGraphicsExtractor, x: Int, y: Int, r: Int, u: Int, v: Int, rounded: Boolean, color: Int) {
        if (rounded) part(ctx, x, y, r, r, u, v, KNOB_HALF, KNOB_HALF, color)
        else ctx.fill(x, y, x + r, y + r, color)
    }

    fun triangle(ctx: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int, rotation: Float) {
        if (rotation == 0f) return blit(ctx, arrow, x, y, size, size, color)
        val cx = x + size * HALF
        val cy = y + size * HALF
        ctx.pose().pushMatrix()
        ctx.pose().translate(cx, cy)
        ctx.pose().rotate(rotation)
        ctx.pose().translate(-cx, -cy)
        blit(ctx, arrow, x, y, size, size, color)
        ctx.pose().popMatrix()
    }

    private fun part(
        ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int,
        u: Int, v: Int, regionW: Int, regionH: Int, color: Int
    ) {
        TextureRegistry.ensureRegistered(listOf(knob))
        ctx.blit(RenderPipelines.GUI_TEXTURED, knob, x, y, u.toFloat(), v.toFloat(), w, h, regionW, regionH, KNOB_TEX, KNOB_TEX, color)
    }

    private fun blit(ctx: GuiGraphicsExtractor, id: ResourceLocation, x: Int, y: Int, w: Int, h: Int, color: Int) {
        TextureRegistry.ensureRegistered(listOf(id))
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h, color)
    }
}
