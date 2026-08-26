package gobby.gui.click

import gobby.BuildConfig
import gobby.utils.Utils.openUrl
import gobby.utils.render.CursorStyle
import gobby.utils.render.TextureRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier as ResourceLocation

private const val DISCORD_URL = "https://discord.gg/4QACNrZD7E"
private const val BRAND_TOP = 13
private const val BRAND_SCALE = 0.86f
private const val BRAND_GAP = 6
private const val GROUP_TOP = 34
private const val GROUP_LABEL_H = 13
private const val ITEM_H = 17
private const val ITEM_ICON = 11
private const val ITEM_RADIUS = 4
private const val SIDE_INSET = 6
private const val ICON_GAP = 7
private const val ITEM_SCALE = 0.74f
private const val GROUP_SCALE = 0.62f
private const val FOOTER_H = 20
private const val FOOTER_BOTTOM = 9
private const val LOGO = 15
private const val EDGE = 1

private val GROUPS = listOf(
    "Gameplay" to listOf(Category.DUNGEONS, Category.FLOOR7, Category.SKYBLOCK, Category.MINING),
    "Client" to listOf(Category.RENDER, Category.COMMANDS, Category.DEVELOPER)
)

internal object SettingsSidebar {

    private val discord: ResourceLocation = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/gui/discord")

    fun width(): Int = SIDEBAR_W_SETTINGS

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        TextureRegistry.ensureRegistered(Category.entries.map { it.iconTexture } + discord)
        val w = width()
        GobbyTextures.roundedRect(
            ctx, gui.panelX + EDGE, gui.panelY + EDGE, w - EDGE, PANEL_H - EDGE * 2, SETTINGS_PANEL_RADIUS, cSidebarPanel,
            topRight = false, bottomRight = false
        )
        ctx.fill(gui.panelX + w, gui.panelY + EDGE, gui.panelX + w + EDGE, gui.panelY + PANEL_H - EDGE, cShellEdge)

        drawBrand(ctx, gui)
        forEachItem(gui) { cat, rect -> drawItem(ctx, gui, cat, rect, mx, my) }
        forEachGroupLabel(gui) { title, x, y ->
            drawTextScaled(ctx, x, y, title, GROUP_SCALE, cInkGhost, false)
        }
        drawFooter(ctx, gui, mx, my)
    }

    private fun drawBrand(ctx: GuiGraphicsExtractor, gui: ClickGUI) {
        val x = gui.panelX + SIDE_INSET
        val y = gui.panelY + BRAND_TOP
        SettingsHeader.drawLogo(ctx, x, y, LOGO)
        val textX = x + SettingsHeader.logoWidth(LOGO) + BRAND_GAP
        val scale = SettingsHeader.scaleWordmarkToFit(BRAND_SCALE, gui.panelX + width() - SIDE_INSET - textX)
        val textH = (tr.lineHeight * scale).toInt()
        SettingsHeader.drawWordmark(ctx, textX, y + (LOGO - textH) / 2, scale)
    }

    private inline fun forEachGroupLabel(gui: ClickGUI, action: (String, Int, Int) -> Unit) {
        var y = gui.panelY + GROUP_TOP
        GROUPS.forEach { (title, members) ->
            val labelH = (tr.lineHeight * GROUP_SCALE).toInt()
            action(title, gui.panelX + SIDE_INSET + 2, y + (GROUP_LABEL_H - labelH) / 2)
            y += GROUP_LABEL_H + members.size * ITEM_H + BRAND_GAP
        }
    }

    private inline fun forEachItem(gui: ClickGUI, action: (Category, Rect) -> Unit) {
        var y = gui.panelY + GROUP_TOP
        GROUPS.forEach { (_, members) ->
            y += GROUP_LABEL_H
            members.forEach { cat ->
                action(cat, Rect(gui.panelX + SIDE_INSET - 2, y, width() - (SIDE_INSET - 2) * 2, ITEM_H))
                y += ITEM_H
            }
            y += BRAND_GAP
        }
    }

    private fun drawItem(ctx: GuiGraphicsExtractor, gui: ClickGUI, cat: Category, r: Rect, mx: Int, my: Int) {
        val selected = cat == gui.currentCategory
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        if (selected) GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ITEM_RADIUS, cSidebarActive)
        else if (hovered) GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ITEM_RADIUS, cRowHover)

        val iconX = r.x + 5
        val iconY = r.y + (r.h - ITEM_ICON) / 2
        ctx.blit(RenderPipelines.GUI_TEXTURED, cat.iconTexture, iconX, iconY, 0f, 0f, ITEM_ICON, ITEM_ICON, ITEM_ICON, ITEM_ICON, -1)

        val textH = (tr.lineHeight * ITEM_SCALE).toInt()
        val tint = if (selected) cInk else cInkSoft
        drawTextScaled(ctx, iconX + ITEM_ICON + ICON_GAP, r.y + (r.h - textH) / 2, cat.displayName, ITEM_SCALE, tint, false)
    }

    fun footerRect(gui: ClickGUI) = Rect(
        gui.panelX + SIDE_INSET - 2,
        gui.panelY + PANEL_H - FOOTER_BOTTOM - FOOTER_H,
        width() - (SIDE_INSET - 2) * 2,
        FOOTER_H
    )

    private fun drawFooter(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val r = footerRect(gui)
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ITEM_RADIUS, if (hovered) cSidebarActive else cValueBox)
        val icon = ITEM_ICON
        ctx.blit(RenderPipelines.GUI_TEXTURED, discord, r.x + 5, r.y + (r.h - icon) / 2, 0f, 0f, icon, icon, icon, icon, -1)
        val textH = (tr.lineHeight * ITEM_SCALE).toInt()
        drawTextScaled(ctx, r.x + 5 + icon + ICON_GAP, r.y + (r.h - textH) / 2, "v" + BuildConfig.MOD_VERSION, ITEM_SCALE, cInkSoft, false)
    }

    fun handleClick(gui: ClickGUI, mx: Int, my: Int): Boolean {
        if ((mx to my) in footerRect(gui)) {
            openUrl(DISCORD_URL)
            return true
        }
        var handled = false
        forEachItem(gui) { cat, rect ->
            if (!handled && (mx to my) in rect) {
                gui.currentCategory = cat
                gui.closeSettings()
                handled = true
            }
        }
        if (handled) return true
        return mx in gui.panelX..(gui.panelX + width()) && my in gui.panelY..(gui.panelY + PANEL_H)
    }
}
