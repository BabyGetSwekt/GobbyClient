package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import gobby.features.render.EntityHighlighter
import gobby.features.render.EspStyle
import gobby.mixin.accessor.WalkAnimationStateAccessor
import gobby.utils.render.CursorStyle
import gobby.utils.render.PreviewModelTint
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import org.joml.Quaternionf
import org.joml.Vector3f
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import java.awt.Color
import kotlin.math.roundToInt

private const val PREVIEW_H = 96
private const val STAGE_PAD = 6
private const val BASE_SCALE_DIVISOR = 2.4f
private const val MIN_MODEL_SCALE = 20f
private const val PARTIAL_TICK = 1f
private const val BODY_ROT_BASE = 180f
private const val HEAD_ALIGNED = 0f
private const val PREVIEW_ENTITY_ID = -1

private const val LIMB_POSITION = 1.0f
private const val LIMB_AMOUNT = 0.3f

private const val BOX_PAD = 2

private const val RESET_SIZE = 16
private const val RESET_ICON = 12
private const val RESET_MARGIN = 4
private const val RESET_RADIUS = 4
private const val BOX_RADIUS = 0

internal object SettingsPreview {

    private var subject: LivingEntity? = null

    fun cardHeight(): Int = PREVIEW_H

    fun resetRect(r: Rect) =
        Rect(r.x + r.w - RESET_MARGIN - RESET_SIZE, r.y + r.h - RESET_MARGIN - RESET_SIZE, RESET_SIZE, RESET_SIZE)

    private fun subject(): LivingEntity? {
        val level = mc.level ?: return null
        subject?.takeIf { it.level() === level }?.let { return it }
        return EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND)?.also {
            it.setId(PREVIEW_ENTITY_ID)
            it.walkAnimation.setSpeed(LIMB_AMOUNT)
            (it.walkAnimation as WalkAnimationStateAccessor).apply {
                setLastSpeed(LIMB_AMOUNT)
                setWalkPosition(LIMB_POSITION)
            }
            subject = it
        }
    }

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, setting: ModelPreviewSetting, r: Rect, mx: Int, my: Int) {
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, SETTINGS_CARD_RADIUS, cCard)
        GobbyDraw.roundedOutline(ctx, r.x, r.y, r.w, r.h, SETTINGS_CARD_RADIUS, cCardEdge)

        val entity = subject() ?: return drawUnavailable(ctx, r)
        val state = renderState(entity, setting) ?: return drawUnavailable(ctx, r)
        val stage = Rect(r.x + STAGE_PAD, r.y + STAGE_PAD, r.w - STAGE_PAD * 2, r.h - STAGE_PAD * 2)
        val localScale = (stage.h / BASE_SCALE_DIVISOR).coerceAtLeast(MIN_MODEL_SCALE) * setting.zoom
        val tint = setting.color.value

        if (style(gui) == EspStyle.MODEL) PreviewModelTint.expect(state, entity, tint) else PreviewModelTint.clear()
        drawModel(ctx, gui, state, stage, localScale, setting.pitch)
        drawHighlight(ctx, gui, state, stage, localScale, tint)
        drawReset(ctx, r, mx, my)
    }

    private fun renderState(entity: LivingEntity, setting: ModelPreviewSetting): LivingEntityRenderState? {
        val renderer = mc.entityRenderDispatcher.getRenderer(entity) ?: return null
        val state = renderer.createRenderState(entity, PARTIAL_TICK) as? LivingEntityRenderState ?: return null
        state.bodyRot = BODY_ROT_BASE + setting.yaw
        state.yRot = HEAD_ALIGNED
        state.xRot = HEAD_ALIGNED
        state.boundingBoxWidth /= state.scale
        state.boundingBoxHeight /= state.scale
        state.scale = 1f
        return state
    }

    private fun drawModel(
        ctx: GuiGraphicsExtractor, gui: ClickGUI, state: LivingEntityRenderState,
        stage: Rect, localScale: Float, pitch: Float
    ) {
        val cameraAngle = Quaternionf().rotateX(Math.toRadians(pitch.toDouble()).toFloat())
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat()).mul(cameraAngle)
        val translation = Vector3f(0f, state.boundingBoxHeight / 2f, 0f)

        val absX = (gui.drawOffsetX + stage.x * gui.guiScale).toInt()
        val absY = (gui.drawOffsetY + stage.y * gui.guiScale).toInt()
        val absX1 = (gui.drawOffsetX + (stage.x + stage.w) * gui.guiScale).toInt()
        val absY1 = (gui.drawOffsetY + (stage.y + stage.h) * gui.guiScale).toInt()

        ctx.pose().pushMatrix()
        ctx.pose().identity()
        ctx.entity(state, localScale * gui.guiScale, translation, rotation, cameraAngle, absX, absY, absX1, absY1)
        ctx.pose().popMatrix()
    }

    private fun highlighter(gui: ClickGUI): EntityHighlighter? = gui.settingsModule as? EntityHighlighter

    private fun style(gui: ClickGUI): EspStyle = highlighter(gui)?.espStyle() ?: EspStyle.MODEL

    private fun drawHighlight(
        ctx: GuiGraphicsExtractor, gui: ClickGUI, state: LivingEntityRenderState,
        stage: Rect, localScale: Float, tint: Color
    ) {
        val style = style(gui)
        if (style == EspStyle.MODEL) return
        val color = tint.rgb
        val centerX = stage.x + stage.w / 2
        val centerY = stage.y + stage.h / 2
        val halfW = (state.boundingBoxWidth * localScale / 2f).roundToInt() + BOX_PAD
        val halfH = (state.boundingBoxHeight * localScale / 2f).roundToInt() + BOX_PAD

        val left = (centerX - halfW).coerceAtLeast(stage.x)
        val right = (centerX + halfW).coerceAtMost(stage.x + stage.w)
        val top = (centerY - halfH).coerceAtLeast(stage.y)
        val bottom = (centerY + halfH).coerceAtMost(stage.y + stage.h)

        if (style == EspStyle.FILLED_BOX) ctx.fill(left, top, right, bottom, color)
        GobbyDraw.roundedOutline(ctx, left, top, right - left, bottom - top, BOX_RADIUS, color)

        val mod = highlighter(gui) ?: return
        if (mod.shouldDrawLines()) ctx.fill(centerX, bottom, centerX + 1, stage.y + stage.h, mod.getLineColor().rgb)
    }

    private fun drawReset(ctx: GuiGraphicsExtractor, card: Rect, mx: Int, my: Int) {
        val r = resetRect(card)
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, RESET_RADIUS, if (hovered) cSidebarActive else cIconTile)
        GobbyTextures.reset(ctx, r.x + (r.w - RESET_ICON) / 2, r.y + (r.h - RESET_ICON) / 2, RESET_ICON, if (hovered) cInk else cInkSoft)
    }

    private fun drawUnavailable(ctx: GuiGraphicsExtractor, r: Rect) {
        val text = "Join a world to preview"
        val tw = textWScaled(text, SETTINGS_VALUE_SCALE)
        val th = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, r.x + (r.w - tw) / 2, r.y + (r.h - th) / 2, text, SETTINGS_VALUE_SCALE, cInkGhost, false)
    }
}
