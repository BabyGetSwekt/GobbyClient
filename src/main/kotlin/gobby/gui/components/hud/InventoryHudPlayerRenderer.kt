package gobby.gui.components.hud

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.LocalPlayerAccessor
import gobby.mixin.accessor.WalkAnimationStateAccessor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3

internal object InventoryHudPlayerRenderer {
    fun draw(
        ctx: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        height: Int,
        hudX: Float,
        hudY: Float,
        hudScale: Float,
        player: LocalPlayer,
        freezeRotation: Boolean
    ) {
        ctx.fill(x, y, x + PLAYER_PANEL_WIDTH, y + height, HudDrawing.INNER_COLOR)
        val absX = hudX + x * hudScale
        val absY = hudY + y * hudScale
        val absW = PLAYER_PANEL_WIDTH * hudScale
        val absH = height * hudScale
        val pad = 2f * hudScale
        val scale = ((height / 2.4f).coerceAtLeast(MIN_MODEL_SCALE) * hudScale).toInt()
        val anchorX = absX + absW / 2f
        val anchorY = absY + absH * MODEL_ANCHOR_Y
        val saved = if (freezeRotation) capture(player) else null
        if (freezeRotation) freeze(player)
        ctx.pose().pushMatrix()
        ctx.pose().identity()
        InventoryHud.suppressNameTag = true
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, (absX + pad).toInt(), (absY + pad).toInt(), (absX + absW - pad).toInt(), (absY + absH - pad).toInt(), scale, 0f, anchorX, anchorY, player)
        } finally {
            InventoryHud.suppressNameTag = false
        }
        ctx.pose().popMatrix()
        saved?.let { restore(player, it) }
    }

    private data class FreezeState(
        val yRot: Float, val xRot: Float, val bodyRot: Float, val oldBodyRot: Float,
        val headRot: Float, val oldHeadRot: Float, val pose: Pose, val crouching: Boolean,
        val walkSpeed: Float, val oldWalkSpeed: Float, val walkPosition: Float,
        val swingTime: Int, val attackAnim: Float, val oldAttackAnim: Float,
        val swinging: Boolean, val deltaMovement: Vec3
    )

    private fun capture(player: LocalPlayer): FreezeState {
        val walk = player.walkAnimation
        val accessor = walk as WalkAnimationStateAccessor
        return FreezeState(player.yRot, player.xRot, player.yBodyRot, player.yBodyRotO, player.yHeadRot, player.yHeadRotO, player.pose, (player as LocalPlayerAccessor).crouching, walk.speed(), accessor.lastSpeed, walk.position(), player.swingTime, player.attackAnim, player.oAttackAnim, player.swinging, player.deltaMovement)
    }

    private fun freeze(player: LocalPlayer) {
        player.yRot = 0f; player.xRot = 0f; player.yBodyRot = 0f; player.yBodyRotO = 0f; player.yHeadRot = 0f; player.yHeadRotO = 0f
        player.pose = Pose.STANDING
        (player as LocalPlayerAccessor).crouching = false
        val walk = player.walkAnimation
        walk.setSpeed(0f); (walk as WalkAnimationStateAccessor).setLastSpeed(0f); walk.setWalkPosition(0f)
        player.swingTime = 0; player.attackAnim = 0f; player.oAttackAnim = 0f; player.swinging = false; player.deltaMovement = Vec3.ZERO
    }

    private fun restore(player: LocalPlayer, saved: FreezeState) {
        player.yRot = saved.yRot; player.xRot = saved.xRot; player.yBodyRot = saved.bodyRot; player.yBodyRotO = saved.oldBodyRot; player.yHeadRot = saved.headRot; player.yHeadRotO = saved.oldHeadRot
        player.pose = saved.pose; (player as LocalPlayerAccessor).crouching = saved.crouching
        val walk = player.walkAnimation
        walk.setSpeed(saved.walkSpeed); (walk as WalkAnimationStateAccessor).setLastSpeed(saved.oldWalkSpeed); walk.setWalkPosition(saved.walkPosition)
        player.swingTime = saved.swingTime; player.attackAnim = saved.attackAnim; player.oAttackAnim = saved.oldAttackAnim; player.swinging = saved.swinging; player.deltaMovement = saved.deltaMovement
    }

    private const val PLAYER_PANEL_WIDTH = 50
    private const val MIN_MODEL_SCALE = 20f
    private const val MODEL_ANCHOR_Y = 0.9f
}
