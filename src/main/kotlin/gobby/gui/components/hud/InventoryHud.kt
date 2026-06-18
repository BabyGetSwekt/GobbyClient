package gobby.gui.components.hud

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.LocalPlayerAccessor
import gobby.mixin.accessor.WalkAnimationStateAccessor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

object InventoryHud {

    @JvmField
    var suppressNameTag: Boolean = false

    private const val SLOT_SIZE = 16
    private const val SLOT_STRIDE = 18
    private const val COLUMN_GAP = 6
    private const val HOTBAR_GAP = 2
    private const val PANEL_PADDING = HudDrawing.PANEL_PADDING

    private const val PLAYER_PANEL_WIDTH = 50
    private const val PLAYER_PANEL_HEIGHT = 72

    private const val INVENTORY_COLUMNS = 9
    private const val INVENTORY_ROWS = 3
    private const val HOTBAR_FIRST_SLOT = 0
    private const val INVENTORY_FIRST_SLOT = 9

    private val ARMOR_SLOTS_TOP_TO_BOTTOM = listOf(
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    )

    data class Size(val width: Int, val height: Int)

    fun renderInventory(
        ctx: GuiGraphicsExtractor,
        player: LocalPlayer?,
        hudX: Float,
        hudY: Float,
        hudScale: Float,
        showArmor: Boolean,
        showPlayer: Boolean,
        freezePlayer: Boolean,
        highlightSelected: Boolean,
        exampleMode: Boolean
    ): Size {
        val layout = computeLayout(showArmor, showPlayer)

        drawBackground(ctx, layout)

        var nextColumnX = PANEL_PADDING
        val columnY = PANEL_PADDING

        if (showArmor) {
            drawArmorColumn(ctx, nextColumnX, columnY, player, exampleMode)
            nextColumnX += SLOT_SIZE + COLUMN_GAP
        }

        drawInventory(ctx, nextColumnX, columnY, player, exampleMode, highlightSelected)
        nextColumnX += layout.middleWidth + COLUMN_GAP

        if (showPlayer && player != null) {
            drawPlayerModel(ctx, nextColumnX, columnY, layout.contentHeight, hudX, hudY, hudScale, player, freezePlayer)
        }

        return Size(layout.totalWidth, layout.totalHeight)
    }

    private data class Layout(
        val middleWidth: Int,
        val middleHeight: Int,
        val contentHeight: Int,
        val totalWidth: Int,
        val totalHeight: Int
    )

    private fun computeLayout(showArmor: Boolean, showPlayer: Boolean): Layout {
        val armorWidth = if (showArmor) SLOT_SIZE + COLUMN_GAP else 0
        val playerWidth = if (showPlayer) PLAYER_PANEL_WIDTH + COLUMN_GAP else 0
        val middleWidth = SLOT_STRIDE * INVENTORY_COLUMNS - (SLOT_STRIDE - SLOT_SIZE)
        val middleHeight = SLOT_STRIDE * (INVENTORY_ROWS + 1) - (SLOT_STRIDE - SLOT_SIZE) + HOTBAR_GAP
        val contentHeight = maxOf(middleHeight, if (showPlayer) PLAYER_PANEL_HEIGHT else 0)
        val totalWidth = armorWidth + middleWidth + playerWidth + PANEL_PADDING * 2
        val totalHeight = contentHeight + PANEL_PADDING * 2
        return Layout(middleWidth, middleHeight, contentHeight, totalWidth, totalHeight)
    }

    private fun drawBackground(ctx: GuiGraphicsExtractor, layout: Layout) {
        HudDrawing.drawPanelBackground(ctx, layout.totalWidth, layout.totalHeight)
    }

    private fun drawArmorColumn(ctx: GuiGraphicsExtractor, x: Int, y: Int, player: LocalPlayer?, exampleMode: Boolean) {
        for ((index, armorSlot) in ARMOR_SLOTS_TOP_TO_BOTTOM.withIndex()) {
            val slotY = y + index * SLOT_STRIDE
            drawSlotBackground(ctx, x, slotY)
            val stack = resolveArmorStack(player, armorSlot, exampleMode)
            if (!stack.isEmpty) drawItemAtSlot(ctx, stack, x, slotY)
        }
    }

    private fun drawInventory(ctx: GuiGraphicsExtractor, originX: Int, originY: Int, player: LocalPlayer?, exampleMode: Boolean, highlightSelected: Boolean) {
        drawMainInventoryRows(ctx, originX, originY, player, exampleMode)

        val hotbarY = originY + INVENTORY_ROWS * SLOT_STRIDE + HOTBAR_GAP
        val selectedHotbarSlot = when {
            !highlightSelected -> -1
            exampleMode -> 0
            else -> player?.inventory?.selectedSlot ?: -1
        }
        drawHotbarRow(ctx, originX, hotbarY, selectedHotbarSlot, player, exampleMode)
    }

    private fun drawMainInventoryRows(ctx: GuiGraphicsExtractor, originX: Int, originY: Int, player: LocalPlayer?, exampleMode: Boolean) {
        for (row in 0 until INVENTORY_ROWS) {
            for (col in 0 until INVENTORY_COLUMNS) {
                val slotX = originX + col * SLOT_STRIDE
                val slotY = originY + row * SLOT_STRIDE
                val slotIndex = INVENTORY_FIRST_SLOT + row * INVENTORY_COLUMNS + col
                drawInventorySlot(ctx, slotX, slotY, slotIndex, isSelected = false, player, exampleMode)
            }
        }
    }

    private fun drawHotbarRow(ctx: GuiGraphicsExtractor, originX: Int, hotbarY: Int, selectedSlot: Int, player: LocalPlayer?, exampleMode: Boolean) {
        for (col in 0 until INVENTORY_COLUMNS) {
            val slotX = originX + col * SLOT_STRIDE
            val slotIndex = HOTBAR_FIRST_SLOT + col
            drawInventorySlot(ctx, slotX, hotbarY, slotIndex, isSelected = col == selectedSlot, player, exampleMode)
        }
    }

    private fun drawInventorySlot(ctx: GuiGraphicsExtractor, slotX: Int, slotY: Int, slotIndex: Int, isSelected: Boolean, player: LocalPlayer?, exampleMode: Boolean) {
        drawSlotBackground(ctx, slotX, slotY)
        if (isSelected) drawSelectedSlotOutline(ctx, slotX, slotY)
        val stack = resolveInventoryStack(player, slotIndex, exampleMode)
        if (!stack.isEmpty) drawItemAtSlot(ctx, stack, slotX, slotY)
    }

    private fun drawPlayerModel(
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
        val scale = ((height / 2.4f).coerceAtLeast(20f) * hudScale).toInt()
        val anchorMouseX = absX + absW / 2f
        val anchorMouseY = absY + absH * 0.9f

        val savedPlayerState = if (freezeRotation) capturePlayerState(player) else null
        if (freezeRotation) freezePlayerToIdle(player)

        ctx.pose().pushMatrix()
        ctx.pose().identity()
        suppressNameTag = true
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                ctx,
                (absX + pad).toInt(),
                (absY + pad).toInt(),
                (absX + absW - pad).toInt(),
                (absY + absH - pad).toInt(),
                scale,
                0f,
                anchorMouseX,
                anchorMouseY,
                player
            )
        } finally {
            suppressNameTag = false
        }
        ctx.pose().popMatrix()

        if (savedPlayerState != null) restorePlayerState(player, savedPlayerState)
    }

    private data class PlayerFreezeState(
        val yRot: Float, val xRot: Float,
        val yBodyRot: Float, val yBodyRotO: Float,
        val yHeadRot: Float, val yHeadRotO: Float,
        val pose: Pose,
        val crouching: Boolean,
        val walkSpeed: Float, val walkSpeedOld: Float, val walkPosition: Float,
        val swingTime: Int, val attackAnim: Float, val oAttackAnim: Float,
        val swinging: Boolean,
        val deltaMovement: Vec3
    )

    private fun capturePlayerState(player: LocalPlayer): PlayerFreezeState {
        val walk = player.walkAnimation
        val walkAccessor = walk as WalkAnimationStateAccessor
        return PlayerFreezeState(
            player.yRot, player.xRot,
            player.yBodyRot, player.yBodyRotO,
            player.yHeadRot, player.yHeadRotO,
            player.pose,
            (player as LocalPlayerAccessor).crouching,
            walk.speed(), walkAccessor.lastSpeed, walk.position(),
            player.swingTime, player.attackAnim, player.oAttackAnim,
            player.swinging,
            player.deltaMovement
        )
    }

    private fun freezePlayerToIdle(player: LocalPlayer) {
        player.yRot = 0f
        player.xRot = 0f
        player.yBodyRot = 0f
        player.yBodyRotO = 0f
        player.yHeadRot = 0f
        player.yHeadRotO = 0f
        player.pose = Pose.STANDING
        (player as LocalPlayerAccessor).crouching = false
        val walk = player.walkAnimation
        walk.setSpeed(0f)
        (walk as WalkAnimationStateAccessor).setLastSpeed(0f)
        walk.setWalkPosition(0f)
        player.swingTime = 0
        player.attackAnim = 0f
        player.oAttackAnim = 0f
        player.swinging = false
        player.deltaMovement = Vec3.ZERO
    }

    private fun restorePlayerState(player: LocalPlayer, saved: PlayerFreezeState) {
        player.yRot = saved.yRot
        player.xRot = saved.xRot
        player.yBodyRot = saved.yBodyRot
        player.yBodyRotO = saved.yBodyRotO
        player.yHeadRot = saved.yHeadRot
        player.yHeadRotO = saved.yHeadRotO
        player.pose = saved.pose
        (player as LocalPlayerAccessor).crouching = saved.crouching
        val walk = player.walkAnimation
        walk.setSpeed(saved.walkSpeed)
        (walk as WalkAnimationStateAccessor).setLastSpeed(saved.walkSpeedOld)
        walk.setWalkPosition(saved.walkPosition)
        player.swingTime = saved.swingTime
        player.attackAnim = saved.attackAnim
        player.oAttackAnim = saved.oAttackAnim
        player.swinging = saved.swinging
        player.deltaMovement = saved.deltaMovement
    }

    private fun resolveArmorStack(player: LocalPlayer?, slot: EquipmentSlot, exampleMode: Boolean): ItemStack {
        if (exampleMode || player == null) return ItemStack.EMPTY
        return player.getItemBySlot(slot)
    }

    private fun resolveInventoryStack(player: LocalPlayer?, slotIndex: Int, exampleMode: Boolean): ItemStack {
        val inventory = player?.inventory ?: return ItemStack.EMPTY
        if (exampleMode) return ItemStack.EMPTY
        return inventory.getItem(slotIndex)
    }

    private fun drawSlotBackground(ctx: GuiGraphicsExtractor, x: Int, y: Int) {
        HudDrawing.drawBoxWithBorder(ctx, x, y, SLOT_SIZE, SLOT_SIZE)
    }

    private fun drawSelectedSlotOutline(ctx: GuiGraphicsExtractor, x: Int, y: Int) {
        HudDrawing.drawOutline(ctx, x, y, SLOT_SIZE, SLOT_SIZE, HudDrawing.ACCENT_GREEN)
    }

    private fun drawItemAtSlot(ctx: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        ctx.item(stack, x, y)
        ctx.itemDecorations(mc.font, stack, x, y)
    }
}
