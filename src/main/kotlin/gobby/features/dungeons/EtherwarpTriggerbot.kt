package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.LeftClickEvent
import gobby.events.RightClickEvent
import gobby.events.core.SubscribeEvent
import gobby.features.Triggerbot
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.DropDownSetting
import gobby.gui.click.KeybindSetting
import gobby.gui.click.NumberSetting
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.Utils
import gobby.utils.isEtherwarpable
import gobby.utils.rotation.AngleUtils
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.acos

object EtherwarpTriggerbot : Triggerbot("Etherwarp Triggerbot", "Etherwarps to your saved spots. Manage them with /gobby etherwarp help", Category.DUNGEONS) {

    val mode by SelectorSetting(
        "Mode",
        0,
        listOf("Auto Sneak", "Manual Sneak"),
        desc = "Auto Sneak: Automatically sneaks and etherwarps\nManual Sneak: Only right-clicks when already sneaking"
    )
    val esp by BooleanSetting("ESP", false, desc = "Highlights your saved etherwarp spots in the current room")
    val espColor by ColorSetting("ESP Color", Color(255, 255, 0, 60), desc = "Highlight color for etherwarp spots").withDependency { esp }

    private val aimKey = KeybindSetting("Aim", desc = "Keybind for aim assist").also { settings.add(it) }
    private val aimGroup = DropDownSetting("Aim Assist", desc = "Smoothly rotate toward the nearest reachable etherwarp spot").also { settings.add(it) }
    private val aimFov by NumberSetting("FOV", 30, 1, 90, 1, desc = "Only assist toward spots within this many degrees of your aim").childOf(aimGroup)
    private val aimMode by SelectorSetting("Rotation Mode", 0, listOf("Smooth", "Linear", "Ease Out"), desc = "Rotation easing style").childOf(aimGroup)
    private val aimSpeed by NumberSetting("Rotation Speed", 200, 50, 600, 10, desc = "Rotation duration in ms").childOf(aimGroup)

    private const val SNEAK_MIN_TICKS = 3
    private const val SNEAK_MAX_TICKS = 4
    private const val AIM_COOLDOWN_MS = 500L

    private var sneakDelay = 0
    private var wasSneaking = false
    private var aimKeyWasDown = false
    private var lockedSpot: BlockPos? = null

    override fun shouldActivate(): Boolean = enabled && !inBoss && dungeonFloor != -1 && mc.gui.screen() == null

    override fun getBlockCooldown(): Long = 3000L

    override fun isValidBlock(pos: BlockPos): Boolean = EtherwarpRoutes.isSpot(pos)

    override fun getTargetPos(): BlockPos? {
        val player = mc.player ?: return null
        if (!player.mainHandItem.isEtherwarpable()) return null
        if (mode == 1 && !player.isShiftKeyDown) return null
        return EtherwarpUtils.getEtherPos(eyeHeight = PlayerUtils.SNEAK_EYE_HEIGHT).takeIf { it.succeeded }?.pos
    }

    override fun performAction() {
        val player = mc.player ?: return
        when (mode) {
            0 -> if (player.isShiftKeyDown) rightClick() else {
                wasSneaking = false
                mc.options.keyShift.isDown = true
                sneakDelay = Utils.getRandomInt(SNEAK_MIN_TICKS, SNEAK_MAX_TICKS)
            }
            1 -> if (player.isShiftKeyDown) rightClick()
        }
    }

    @SubscribeEvent
    override fun onTick(event: ClientTickEvent.Pre) {
        pollAimAssist()
        if (sneakDelay > 0) {
            processSneakSequence()
            return
        }
        super.onTick(event)
    }

    private fun processSneakSequence() {
        sneakDelay--
        if (sneakDelay == 0 && !wasSneaking) mc.options.keyShift.isDown = false
        if (sneakDelay == 1) rightClick()
    }

    @SubscribeEvent
    fun onLeftClick(event: LeftClickEvent) {
        if (!enabled || aimKey.value != KeybindSetting.MOUSE_OFFSET + GLFW.GLFW_MOUSE_BUTTON_LEFT) return
        val player = mc.player ?: return
        if (player.mainHandItem.isEtherwarpable() && nearestAimSpot(player) != null) event.cancel()
    }

    private fun pollAimAssist() {
        val active = enabled && mc.gui.screen() == null && aimKey.isPressed() &&
            mc.player?.mainHandItem?.isEtherwarpable() == true
        if (active && !aimKeyWasDown) {
            lockedSpot = mc.player?.let { nearestAimSpot(it) }
            lockedSpot?.let {
                capCooldown(it, AIM_COOLDOWN_MS)
                RotationUtils.startAngleLock(aimSpeed.toLong(), arrivalFor()) { aimAtLocked() }
            }
        } else if (!active && aimKeyWasDown) {
            lockedSpot = null
            RotationUtils.stopAngleLock()
        }
        aimKeyWasDown = active
    }

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (lockedSpot == null) return
        lockedSpot = null
        RotationUtils.stopAngleLock()
    }

    private fun aimAtLocked(): Pair<Float, Float>? {
        val spot = lockedSpot ?: return null
        return EtherwarpUtils.aimForBlock(spot, sneakEye(mc.player ?: return null))
    }

    private fun arrivalFor(): Float = when (aimMode) {
        1 -> 1.0f
        2 -> 0.5f
        else -> 0.3f
    }

    private fun sneakEye(player: LocalPlayer) = Vec3(player.x, player.y + PlayerUtils.SNEAK_EYE_HEIGHT, player.z)

    private fun nearestAimSpot(player: LocalPlayer): BlockPos? {
        val room = ScanUtils.currentRoom ?: return null
        val eye = sneakEye(player)
        val look = AngleUtils.directionFromAngles(player.yRot, player.xRot)
        return EtherwarpRoutes.spots(room.data.name)
            .mapNotNull { encoded ->
                val (x, y, z) = encoded.split(",").map { it.trim().toInt() }
                val pos = room.getRealCoords(BlockPos(x, y, z))
                val angles = EtherwarpUtils.aimForBlock(pos, eye) ?: return@mapNotNull null
                val aimDir = AngleUtils.directionFromAngles(angles.first, angles.second)
                if (Math.toDegrees(acos(look.dot(aimDir).coerceIn(-1.0, 1.0))) > aimFov) null
                else pos to Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5).distanceTo(eye)
            }
            .minByOrNull { it.second }?.first
    }
}
