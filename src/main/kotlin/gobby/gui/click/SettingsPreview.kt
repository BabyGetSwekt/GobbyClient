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
import kotlin.math.roundToInt

private const val PREVIEW_H = 96
private const val STAGE_PAD = 6
private const val BASE_SCALE_DIVISOR = 2.4f
private const val MIN_MODEL_SCALE = 20f
private const val PARTIAL_TICK = 1f
private const val BODY_ROT_BASE = 180f
private const val HEAD_ALIGNED = 0f
private const val PREVIEW_ENTITY_ID = -1

private const val DEFAULT_YAW = 0f
private const val DEFAULT_PITCH = 0f
private const val DEFAULT_ZOOM = 1f
private const val MIN_ZOOM = 0.55f
private const val MAX_ZOOM = 2.6f
private const val ZOOM_STEP = 0.12f
private const val YAW_PER_PIXEL = 2.2f
private const val PITCH_PER_PIXEL = 1.4f
private const val PITCH_LIMIT = 35f

private const val LIMB_POSITION = 1.0f
private const val LIMB_AMOUNT = 0.3f

private const val BOX_PAD = 2

private const val RESET_SIZE = 16
private const val RESET_ICON = 12
private const val RESET_MARGIN = 4
private const val RESET_RADIUS = 4
private const val BOX_RADIUS = 0

internal object SettingsPreview {

    const val SECTION_TITLE = "MODEL PREVIEW"

    private var subject: LivingEntity? = null
    private var yaw = DEFAULT_YAW
    private var pitch = DEFAULT_PITCH
    private var zoom = DEFAULT_ZOOM

    fun appliesTo(mod: Module): Boolean = mod is EntityHighlighter

    fun cardHeight(): Int = PREVIEW_H

    fun reset() {
        yaw = DEFAULT_YAW
        pitch = DEFAULT_PITCH
        zoom = DEFAULT_ZOOM
    }

    fun rotate(dx: Double, dy: Double) {
        yaw -= dx.toFloat() * YAW_PER_PIXEL
        pitch = (pitch - dy.toFloat() * PITCH_PER_PIXEL).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
    }

    fun zoom(amount: Double) {
        zoom = (zoom + amount.toFloat() * ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun resetRect(x: Int, y: Int, w: Int, h: Int) =
        Rect(x + w - RESET_MARGIN - RESET_SIZE, y + h - RESET_MARGIN - RESET_SIZE, RESET_SIZE, RESET_SIZE)

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

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        GobbyDraw.roundedRect(ctx, x, y, w, h, SETTINGS_CARD_RADIUS, cCard)
        GobbyDraw.roundedOutline(ctx, x, y, w, h, SETTINGS_CARD_RADIUS, cCardEdge)

        val entity = subject() ?: return drawUnavailable(ctx, x, y, w, h)
        val state = renderState(entity) ?: return drawUnavailable(ctx, x, y, w, h)
        val stageX = x + STAGE_PAD
        val stageY = y + STAGE_PAD
        val stageW = w - STAGE_PAD * 2
        val stageH = h - STAGE_PAD * 2
        val localScale = (stageH / BASE_SCALE_DIVISOR).coerceAtLeast(MIN_MODEL_SCALE) * zoom

        highlighter(gui)?.takeIf { it.espStyle() == EspStyle.MODEL }
            ?.let { PreviewModelTint.expect(state, entity, it.getColor()) } ?: PreviewModelTint.clear()
        drawModel(ctx, gui, state, stageX, stageY, stageW, stageH, localScale)
        drawHighlight(ctx, gui, state, stageX, stageY, stageW, stageH, localScale)
        drawReset(ctx, x, y, w, h, mx, my)
    }

    private fun renderState(entity: LivingEntity): LivingEntityRenderState? {
        val renderer = mc.entityRenderDispatcher.getRenderer(entity) ?: return null
        val state = renderer.createRenderState(entity, PARTIAL_TICK) as? LivingEntityRenderState ?: return null
        state.bodyRot = BODY_ROT_BASE + yaw
        state.yRot = HEAD_ALIGNED
        state.xRot = HEAD_ALIGNED
        state.boundingBoxWidth /= state.scale
        state.boundingBoxHeight /= state.scale
        state.scale = 1f
        return state
    }

    private fun drawModel(
        ctx: GuiGraphicsExtractor, gui: ClickGUI, state: LivingEntityRenderState,
        stageX: Int, stageY: Int, stageW: Int, stageH: Int, localScale: Float
    ) {
        val cameraAngle = Quaternionf().rotateX(Math.toRadians(pitch.toDouble()).toFloat())
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat()).mul(cameraAngle)
        val translation = Vector3f(0f, state.boundingBoxHeight / 2f, 0f)

        val absX = (gui.drawOffsetX + stageX * gui.guiScale).toInt()
        val absY = (gui.drawOffsetY + stageY * gui.guiScale).toInt()
        val absX1 = (gui.drawOffsetX + (stageX + stageW) * gui.guiScale).toInt()
        val absY1 = (gui.drawOffsetY + (stageY + stageH) * gui.guiScale).toInt()

        ctx.pose().pushMatrix()
        ctx.pose().identity()
        ctx.entity(state, localScale * gui.guiScale, translation, rotation, cameraAngle, absX, absY, absX1, absY1)
        ctx.pose().popMatrix()
    }

    private fun highlighter(gui: ClickGUI): EntityHighlighter? = gui.settingsModule as? EntityHighlighter

    private fun drawHighlight(
        ctx: GuiGraphicsExtractor, gui: ClickGUI, state: LivingEntityRenderState,
        stageX: Int, stageY: Int, stageW: Int, stageH: Int, localScale: Float
    ) {
        val mod = highlighter(gui) ?: return
        val color = mod.getColor().rgb
        val centerX = stageX + stageW / 2
        val centerY = stageY + stageH / 2
        val halfW = (state.boundingBoxWidth * localScale / 2f).roundToInt() + BOX_PAD
        val halfH = (state.boundingBoxHeight * localScale / 2f).roundToInt() + BOX_PAD

        val left = (centerX - halfW).coerceAtLeast(stageX)
        val right = (centerX + halfW).coerceAtMost(stageX + stageW)
        val top = (centerY - halfH).coerceAtLeast(stageY)
        val bottom = (centerY + halfH).coerceAtMost(stageY + stageH)

        when (mod.espStyle()) {
            EspStyle.MODEL -> return
            EspStyle.FILLED_BOX -> {
                ctx.fill(left, top, right, bottom, color)
                GobbyDraw.roundedOutline(ctx, left, top, right - left, bottom - top, BOX_RADIUS, color)
            }
            EspStyle.BOX -> GobbyDraw.roundedOutline(ctx, left, top, right - left, bottom - top, BOX_RADIUS, color)
        }

        if (mod.shouldDrawLines()) ctx.fill(centerX, bottom, centerX + 1, stageY + stageH, mod.getLineColor().rgb)
    }

    private fun drawReset(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        val r = resetRect(x, y, w, h)
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, RESET_RADIUS, if (hovered) cSidebarActive else cIconTile)
        GobbyTextures.reset(ctx, r.x + (r.w - RESET_ICON) / 2, r.y + (r.h - RESET_ICON) / 2, RESET_ICON, if (hovered) cInk else cInkSoft)
    }

    private fun drawUnavailable(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        val text = "Join a world to preview"
        val tw = textWScaled(text, SETTINGS_VALUE_SCALE)
        val th = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, x + (w - tw) / 2, y + (h - th) / 2, text, SETTINGS_VALUE_SCALE, cInkGhost, false)
    }
}
