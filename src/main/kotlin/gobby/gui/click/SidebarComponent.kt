package gobby.gui.click

import gobby.BuildConfig
import gobby.Gobbyclient.Companion.mc
import gobby.utils.render.TextureRegistry
import gobby.utils.Utils.openUrl
import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier as ResourceLocation

object SidebarComponent {

    private const val LIST_TOP_PAD = 10
    private const val LABEL_LEFT_PAD = 26
    private const val ICON_LEFT_PAD = 14
    private const val INDICATOR_W = 3
    private const val ICON_SIZE = 16
    private const val DISCORD_ICON_SIZE = 21

    // The main discord server url (for support/bugs/suggestions.
    // No rat i swear ;(
    private const val DISCORD_URL = "https://discord.gg/4QACNrZD7E"

    private val discordTexture: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/gui/discord")

    private fun ensureTextures() = TextureRegistry.ensureRegistered(Category.entries.map { it.iconTexture } + discordTexture)


    private const val BURGER_W = 14
    private const val BURGER_H = 10
    private const val BURGER_BAR_H = 2
    private const val BURGER_GAP = 2
    private const val BURGER_Y = 10

    private fun burgerRect(gui: ClickGUI) = Rect(
        gui.panelX + ICON_LEFT_PAD - BURGER_W / 2,
        gui.panelY + BURGER_Y,
        BURGER_W,
        BURGER_H
    )

    fun draw(ctx: GuiGraphics, gui: ClickGUI, mx: Int, my: Int) {
        ensureTextures()
        drawBackground(ctx, gui)
        drawBurger(ctx, gui, mx, my)
        drawBrand(ctx, gui)
        Category.entries.forEachIndexed { i, cat -> drawCategoryItem(ctx, gui, i, cat, mx, my) }
        drawVersionFooter(ctx, gui, mx, my)
    }

    private fun drawBackground(ctx: GuiGraphics, gui: ClickGUI) {
        val sbW = gui.sidebarWidth
        fill(ctx, gui.panelX + 1, gui.panelY + 1, sbW - 1, PANEL_H - 2, cSidebarBg)
        fill(ctx, gui.panelX + sbW, gui.panelY + 4, 1, PANEL_H - 8, cSeparator)
    }

    private fun fadeAlpha(progress: Float, start: Float, end: Float): Float =
        ((progress - start) / (end - start)).coerceIn(0f, 1f)

    private fun drawBrand(ctx: GuiGraphics, gui: ClickGUI) {
        if (gui.sidebarExpand <= 0.4f) return
        val alpha = fadeAlpha(gui.sidebarExpand, 0.4f, 1f)
        drawText(ctx, gui.panelX + LABEL_LEFT_PAD, gui.panelY + BURGER_Y, "Gobbyclient", withAlpha(cTextBright, alpha))
    }

    private fun categoryRect(gui: ClickGUI, index: Int): Rect = Rect(
        gui.panelX,
        gui.panelY + 28 + LIST_TOP_PAD + index * (SIDEBAR_ITEM_H + 2),
        gui.sidebarWidth,
        SIDEBAR_ITEM_H
    )

    private fun drawCategoryItem(ctx: GuiGraphics, gui: ClickGUI, index: Int, cat: Category, mx: Int, my: Int) {
        val r = categoryRect(gui, index)
        val selected = cat == gui.currentCategory && gui.settingsModule == null
        val hovered = (mx to my) in r

        when {
            selected -> {
                fill(ctx, r.x, r.y, r.w, r.h, cAccentDim)
                fill(ctx, r.x, r.y, INDICATOR_W, r.h, cAccent)
            }
            hovered -> fill(ctx, r.x, r.y, r.w, r.h, cHover)
        }

        val iconX = gui.panelX + ICON_LEFT_PAD - ICON_SIZE / 2
        val iconY = r.y + (r.h - ICON_SIZE) / 2
        ctx.blit(RenderPipelines.GUI_TEXTURED, cat.iconTexture, iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, -1)

        if (gui.sidebarExpand > 0.2f) {
            val alpha = fadeAlpha(gui.sidebarExpand, 0.2f, 1f)
            val textY = r.y + (r.h - tr.lineHeight) / 2
            drawText(ctx, gui.panelX + LABEL_LEFT_PAD, textY, cat.displayName, withAlpha(if (selected) cTextBright else cText, alpha))
        }
    }

    private fun discordRect(gui: ClickGUI) = Rect(
        gui.panelX + ICON_LEFT_PAD - DISCORD_ICON_SIZE / 2,
        gui.panelY + PANEL_H - DISCORD_ICON_SIZE - 6,
        DISCORD_ICON_SIZE,
        DISCORD_ICON_SIZE
    )

    private fun drawVersionFooter(ctx: GuiGraphics, gui: ClickGUI, mx: Int, my: Int) {
        val d = discordRect(gui)
        val hovered = (mx to my) in d
        val tint = if (hovered) -1 else withAlpha(-1, 0.75f)
        ctx.blit(RenderPipelines.GUI_TEXTURED, discordTexture, d.x, d.y, 0f, 0f, d.w, d.h, d.w, d.h, tint)

        if (gui.sidebarExpand <= 0.5f) return
        val alpha = fadeAlpha(gui.sidebarExpand, 0.5f, 1f) * 0.7f
        val textY = d.y + (d.h - tr.lineHeight) / 2 + 1
        drawText(ctx, d.x + d.w + 6, textY, "v" + BuildConfig.MOD_VERSION, withAlpha(cTextGray, alpha))
    }

    private fun drawBurger(ctx: GuiGraphics, gui: ClickGUI, mx: Int, my: Int) {
        val r = burgerRect(gui)
        val hovered = (mx to my) in r
        val color = if (hovered || gui.sidebarExpand > 0.5f) cAccent else cTextBright
        fill(ctx, r.x, r.y, r.w, BURGER_BAR_H, color)
        fill(ctx, r.x, r.y + BURGER_BAR_H + BURGER_GAP, r.w, BURGER_BAR_H, color)
        fill(ctx, r.x, r.y + 2 * (BURGER_BAR_H + BURGER_GAP), r.w, BURGER_BAR_H, color)
    }

    fun handleClick(gui: ClickGUI, mx: Int, my: Int): Boolean {
        if ((mx to my) in discordRect(gui)) {
            openUrl(DISCORD_URL)
            return true
        }
        if ((mx to my) in burgerRect(gui)) {
            ClickGUI.sidebarExpanded = !ClickGUI.sidebarExpanded
            return true
        }
        if (ClickGUI.sidebarExpanded &&
            mx > gui.panelX + gui.sidebarWidth && mx <= gui.panelX + PANEL_W &&
            my in gui.panelY..(gui.panelY + PANEL_H)) {
            ClickGUI.sidebarExpanded = false
            return true
        }
        if (mx !in gui.panelX..(gui.panelX + gui.sidebarWidth)) return false

        Category.entries.forEachIndexed { i, cat ->
            if ((mx to my) in categoryRect(gui, i)) {
                gui.currentCategory = cat
                gui.closeSettings()
                return true
            }
        }
        return my in gui.panelY..(gui.panelY + PANEL_H)
    }

    private fun withAlpha(rgb: Int, alpha: Float): Int {
        val a = ((rgb ushr 24) and 0xFF) / 255f
        val newA = (a * alpha * 255f).toInt().coerceIn(0, 255)
        return (newA shl 24) or (rgb and 0x00FFFFFF)
    }
}
