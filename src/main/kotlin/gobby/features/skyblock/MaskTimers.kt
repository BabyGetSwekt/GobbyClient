package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.utils.render.TextureRegistry
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.gui.hud.HudSetting
import gobby.utils.LocationUtils
import gobby.utils.managers.InvincibilityManager
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier as ResourceLocation

object MaskTimers : Module("Mask Timers", "Shows Spirit and Bonzo Mask cooldowns on a movable HUD", Category.SKYBLOCK) {

    private val displayMode by SelectorSetting("Display Mode", 2, listOf("Image", "Text", "Both"), desc = "How each row is rendered")
    private val showSpirit by BooleanSetting("Show Spirit", true, desc = "Display Spirit Mask cooldown")
    private val showBonzo by BooleanSetting("Show Bonzo", true, desc = "Display Bonzo Mask cooldown")
    private val showPhoenix by BooleanSetting("Show Phoenix", true, desc = "Display Phoenix Pet cooldown")

    private const val ICON_SIZE = 12
    private const val ICON_GAP = 2
    private const val ROW_GAP = 2

    private const val MODE_IMAGE = 0
    private const val MODE_TEXT = 1

    private val showImages get() = displayMode != MODE_TEXT
    private val showText get() = displayMode != MODE_IMAGE

    private enum class Mask(
        val label: String,
        val sprite: ResourceLocation,
        val visible: () -> Boolean,
        val onCooldown: () -> Boolean,
        val seconds: () -> Double
    ) {
        SPIRIT(
            "Spirit",
            tex("spirit_mask"),
            { showSpirit },
            { InvincibilityManager.isSpiritOnCooldown },
            { InvincibilityManager.spiritCooldownSeconds }
        ),
        BONZO(
            "Bonzo",
            tex("bonzo_mask"),
            { showBonzo },
            { InvincibilityManager.isBonzoOnCooldown },
            { InvincibilityManager.bonzoCooldownSeconds }
        ),
        PHOENIX(
            "Phoenix",
            tex("phoenix"),
            { showPhoenix },
            { InvincibilityManager.isPhoenixOnCooldown },
            { InvincibilityManager.phoenixCooldownSeconds }
        )
    }

    private val GREEN_ID = tex("green_checkmark")
    private val FAILED_ID = tex("failed")
    private val maskHud by HudSetting("Mask Timers", "Movable mask cooldown display") { example ->
        ensureTextures()
        if (example) {
            renderMask(Mask.SPIRIT, ready = false, seconds = 12.3)
            renderMask(Mask.BONZO, ready = true, seconds = 0.0)
            return@HudSetting
        }
        if (!LocationUtils.onSkyblock) return@HudSetting
        Mask.entries
            .filter { it.visible() }
            .forEach { renderMask(it, ready = !it.onCooldown(), seconds = it.seconds()) }
    }

    private fun HudSetting.renderMask(mask: Mask, ready: Boolean, seconds: Double) {
        val ctx = drawContext ?: return
        val tr = mc.font
        var x = 0
        val y = getHeight()
        val rowTop = y

        if (showImages) {
            val statusIcon = if (ready) GREEN_ID else FAILED_ID
            ctx.blit(RenderPipelines.GUI_TEXTURED, statusIcon, x, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, -1)
            x += ICON_SIZE + ICON_GAP
            ctx.blit(RenderPipelines.GUI_TEXTURED, mask.sprite, x, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, -1)
            x += ICON_SIZE + ICON_GAP
        }

        if (showText || !ready) {
            val text = if (ready) "§a${mask.label}: §fReady" else "§f${mask.label}: ${formatSeconds(seconds)}"
            val textY = rowTop + (ICON_SIZE - tr.lineHeight) / 2
            ctx.text(tr, text, x, textY, -1, true)
            x += tr.width(text)
        }

        setSize(x, rowTop + ICON_SIZE + ROW_GAP)
    }

    private fun formatSeconds(seconds: Double): String = when {
        seconds >= 10.0 -> "§e${seconds.toInt()}s"
        seconds >= 1.0 -> "§6${"%.1f".format(seconds)}s"
        else -> "§c${"%.1f".format(seconds)}s"
    }

    private fun ensureTextures() = TextureRegistry.ensureRegistered(Mask.entries.map { it.sprite } + listOf(GREEN_ID, FAILED_ID))

}

private fun tex(name: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/$name")
