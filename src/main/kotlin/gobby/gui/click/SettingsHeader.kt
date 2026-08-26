package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import gobby.utils.render.CursorStyle
import gobby.utils.render.TextureRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier as ResourceLocation
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ICON_INSET = 5
private const val TITLE_GAP = 9
private const val PILL_PAD = 6
private const val PILL_H_HEADER = 26
private const val PILL_RADIUS = 7
private const val CHEVRON_GAP = 5
private const val PILL_GEAR = 11
private const val BACK_SIZE = 18
private const val BACK_ICON = 12
private const val BACK_GAP = 10
private const val LOGO_TEX_W = 256
private const val LOGO_TEX_H = 180
private const val SUBTITLE_GAP = 2
private const val FALLBACK_NAME = "Player"
private const val WORDMARK = "Gobby Client"
private const val HEADER_EDGE = 1
private const val SEARCH_HINT = "Click search to filter modules"
private const val RESULT_SUFFIX = " modules found"

internal object SettingsHeader {

    private val logo: ResourceLocation = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/gui/logo")
    private val backIcon: ResourceLocation = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/gui/back")
    private val cancelIcon: ResourceLocation = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/gui/cancel")
    private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun logoWidth(height: Int): Int = height * LOGO_TEX_W / LOGO_TEX_H

    fun playerName(): String = mc.player?.gameProfile?.name ?: FALLBACK_NAME

    fun localTime(): String = LocalTime.now().format(clockFormat)

    fun drawLogo(ctx: GuiGraphicsExtractor, x: Int, y: Int, height: Int) {
        TextureRegistry.ensureRegistered(listOf(logo))
        val w = logoWidth(height)
        ctx.blit(RenderPipelines.GUI_TEXTURED, logo, x, y, 0f, 0f, w, height, w, height, -1)
    }

    fun wordmarkWidth(scale: Float): Int = textWScaled(WORDMARK, scale)

    fun scaleWordmarkToFit(preferred: Float, available: Int): Float {
        val natural = wordmarkWidth(preferred)
        return if (natural <= available) preferred else preferred * available / natural
    }

    fun drawWordmark(ctx: GuiGraphicsExtractor, x: Int, y: Int, scale: Float) {
        val text = WORDMARK
        var offset = 0
        text.forEachIndexed { index, char ->
            val progress = index.toFloat() / (text.length - 1).coerceAtLeast(1)
            val glyph = char.toString()
            drawTextScaled(ctx, x + offset, y, glyph, scale, GobbyDraw.mix(cBrandDeep, cBrandLight, progress), false)
            offset += textWScaled(glyph, scale)
        }
    }

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mod: Module, mx: Int, my: Int) =
        draw(ctx, gui, mod.category.iconTexture, mod.name, mod.description, mx, my)

    fun draw(
        ctx: GuiGraphicsExtractor, gui: ClickGUI, icon: ResourceLocation,
        title: String, subtitle: String, mx: Int, my: Int, leading: ResourceLocation = backIcon
    ) {
        val left = drawShell(ctx, gui)
        drawBack(ctx, gui, mx, my, leading)
        drawIdentity(ctx, gui, icon, title, subtitle, left + SETTINGS_SIDE_PAD + BACK_SIZE + BACK_GAP, accountPillRect(gui).x)
        drawAccount(ctx, gui, mx, my)
    }

    fun drawGrid(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val left = drawShell(ctx, gui)
        val category = gui.currentCategory ?: Category.entries.first()
        val subtitle = if (SearchBar.query.isEmpty()) SEARCH_HINT else gui.visibleModules().size.toString() + RESULT_SUFFIX
        drawIdentity(ctx, gui, category.iconTexture, category.displayName, subtitle,
            left + SETTINGS_SIDE_PAD, SearchBar.rect(gui).x)
        drawAccount(ctx, gui, mx, my)
        SearchBar.draw(ctx, gui, mx, my)
    }

    private fun drawShell(ctx: GuiGraphicsExtractor, gui: ClickGUI): Int {
        val standalone = gui.sidebarWidth == 0
        val left = gui.panelX + gui.sidebarWidth + if (standalone) HEADER_EDGE else 0
        val width = gui.panelX + PANEL_W - HEADER_EDGE - left
        GobbyTextures.roundedRect(
            ctx, left, gui.panelY + HEADER_EDGE, width, SETTINGS_HEADER_H - HEADER_EDGE, SETTINGS_PANEL_RADIUS, cHeadBg,
            topLeft = standalone, bottomLeft = false, bottomRight = false
        )
        ctx.fill(left, gui.panelY + SETTINGS_HEADER_H, left + width, gui.panelY + SETTINGS_HEADER_H + 1, cShellEdge)
        return gui.panelX + gui.sidebarWidth
    }

    fun backRect(gui: ClickGUI): Rect = Rect(
        gui.panelX + gui.sidebarWidth + SETTINGS_SIDE_PAD,
        gui.panelY + (SETTINGS_HEADER_H - BACK_SIZE) / 2,
        BACK_SIZE, BACK_SIZE
    )

    fun cancelIcon(): ResourceLocation = cancelIcon

    private fun drawBack(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int, icon: ResourceLocation) {
        TextureRegistry.ensureRegistered(listOf(icon))
        val r = backRect(gui)
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, PILL_RADIUS, if (hovered) cSidebarActive else cIconTile)
        ctx.blit(
            RenderPipelines.GUI_TEXTURED, icon,
            r.x + (r.w - BACK_ICON) / 2, r.y + (r.h - BACK_ICON) / 2, 0f, 0f,
            BACK_ICON, BACK_ICON, BACK_ICON, BACK_ICON, if (hovered) cInk else cInkSoft
        )
    }

    private fun drawIdentity(
        ctx: GuiGraphicsExtractor, gui: ClickGUI, icon: ResourceLocation,
        title: String, subtitle: String, tileX: Int, rightBound: Int
    ) {
        val tileY = gui.panelY + (SETTINGS_HEADER_H - SETTINGS_ICON) / 2
        GobbyDraw.roundedRect(ctx, tileX, tileY, SETTINGS_ICON, SETTINGS_ICON, PILL_RADIUS, cIconTile)
        val inner = SETTINGS_ICON - ICON_INSET * 2
        ctx.blit(
            RenderPipelines.GUI_TEXTURED, icon,
            tileX + ICON_INSET, tileY + ICON_INSET, 0f, 0f, inner, inner, inner, inner, -1
        )

        val textX = tileX + SETTINGS_ICON + TITLE_GAP
        val titleH = (tr.lineHeight * SETTINGS_TITLE_SCALE).toInt()
        val subH = (tr.lineHeight * SETTINGS_SUBTITLE_SCALE).toInt()
        val block = titleH + SUBTITLE_GAP + subH
        val top = gui.panelY + (SETTINGS_HEADER_H - block) / 2
        val available = (rightBound - textX - TITLE_GAP).coerceAtLeast(0)
        drawTextScaled(ctx, textX, top, fit(title, available, SETTINGS_TITLE_SCALE), SETTINGS_TITLE_SCALE, cInk, false)
        if (subtitle.isNotEmpty()) {
            drawTextScaled(ctx, textX, top + titleH + SUBTITLE_GAP, fit(subtitle, available, SETTINGS_SUBTITLE_SCALE), SETTINGS_SUBTITLE_SCALE, cInkFaint, false)
        }
    }

    private fun fit(text: String, maxWidth: Int, scale: Float): String =
        if (textWScaled(text, scale) <= maxWidth) text else TextWrap.truncateToFit(text, maxWidth, scale)

    fun accountPillRect(gui: ClickGUI): Rect {
        val textW = maxOf(
            textWScaled(playerName(), SETTINGS_VALUE_SCALE),
            textWScaled(localTime(), SETTINGS_SUBTITLE_SCALE)
        )
        val w = PILL_PAD + logoWidth(LOGO_SIZE) + PILL_PAD + textW + CHEVRON_GAP + PILL_GEAR + PILL_PAD
        return Rect(
            gui.panelX + PANEL_W - SETTINGS_SIDE_PAD - w,
            gui.panelY + (SETTINGS_HEADER_H - PILL_H_HEADER) / 2,
            w, PILL_H_HEADER
        )
    }

    private fun drawAccount(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val r = accountPillRect(gui)
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, PILL_RADIUS, if (hovered) cIconTile else cCard)

        drawLogo(ctx, r.x + PILL_PAD, r.y + (r.h - LOGO_SIZE) / 2, LOGO_SIZE)

        val textX = r.x + PILL_PAD + logoWidth(LOGO_SIZE) + PILL_PAD
        val nameH = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        val subH = (tr.lineHeight * SETTINGS_SUBTITLE_SCALE).toInt()
        val block = nameH + SUBTITLE_GAP + subH
        val top = r.y + (r.h - block) / 2
        drawTextScaled(ctx, textX, top, playerName(), SETTINGS_VALUE_SCALE, cInk, false)
        drawTextScaled(ctx, textX, top + nameH + SUBTITLE_GAP, localTime(), SETTINGS_SUBTITLE_SCALE, cInkFaint, false)

        GobbyTextures.gear(ctx, r.x + r.w - PILL_PAD - PILL_GEAR, r.y + (r.h - PILL_GEAR) / 2, PILL_GEAR, if (hovered) cInkSoft else cInkGhost)
    }
}
