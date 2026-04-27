package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.events.BlockStateChangeEvent
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.utils.BONZO_MASK_IDS
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
import gobby.utils.getShotCooldown
import gobby.utils.hasItemID
import gobby.utils.rotation.AngleUtils.calcAimAngles
import gobby.utils.rotation.RotationUtils
import gobby.utils.managers.EquipmentManager
import gobby.utils.managers.InvincibilityManager
import gobby.utils.managers.LeapManager
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.getSection
import gobby.utils.skyblockID
import gobby.utils.timer.Clock
import net.minecraft.block.Blocks
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * This code was inspired by CyanAddons, but has been heavily modified.
 */
object AutoPre4 : Module(
    "Auto Pre 4", "Automatically completes the fourth device",
    Category.FLOOR7
) {

    val aimStyle by SelectorSetting("Aim Style", 1, listOf("Snap", "Ease"), desc = "How the aim rotates to the target")
    val shootingDeviceEsp by BooleanSetting("Shooting Device ESP", false, desc = "Highlights shot positions and shows aim target")
    private val autoLeap by BooleanSetting("Auto Leap", false, desc = "Automatically leaps after device completion")
    private val leapTo by SelectorSetting("Leap To", 3, listOf("Archer", "Berserk", "Mage", "Tank", "Healer"), desc = "Which class to leap to")
        .withDependency { autoLeap }
    private val autoBonzo by BooleanSetting("Auto Bonzo", false, desc = "Automatically swaps to Bonzo mask if you're about to die due to death ticks. Only works if you have Spirit Mask equipped.")

    enum class State { IDLE, PREFIRING, ROTATING, SHOOTING, LEAPING }

    private const val LEAP_DELAY_TICKS = 2
    private var bonzoSwapDelayTicks = 0

    val shootPositions = listOf(
        BlockPos(68, 130, 50),
        BlockPos(68, 128, 50),
        BlockPos(68, 126, 50),
        BlockPos(66, 130, 50),
        BlockPos(66, 128, 50),
        BlockPos(66, 126, 50),
        BlockPos(64, 130, 50),
        BlockPos(64, 128, 50),
        BlockPos(64, 126, 50)
    )

    private val offsetMap = mapOf(
        68 to -0.7,
        66 to -0.5,
        64 to 1.5
    )

    private val platePos = BlockPos(63, 127, 35)

    private var currentEmerald: BlockPos? = null
    private var hasNewEmerald = false
    val shotAt = mutableListOf<BlockPos>()
    private val tempShot = mutableListOf<BlockPos>()
    private var prefires = 0
    private val shotClock = Clock()
    private var bowShootSpeedMs = 300L
    private var leapDelayTicks = 0

    var state: State = State.IDLE
        private set
    var deviceCompleted: Boolean = false
        private set
    val isShootingPhase: Boolean get() = state == State.PREFIRING || state == State.ROTATING || state == State.SHOOTING
    var currentAimTarget: Vec3d? = null
        private set

    private fun isNearPlate(): Boolean {
        return posY == 127.0 && posX in 62.0..65.0 && posZ in 34.0..37.0
    }

    fun isPlateDown(): Boolean {
        val world = mc.world ?: return false
        val blockState = world.getBlockState(platePos)
        if (blockState.block != Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE) return false
        return blockState.get(Properties.POWER) > 0
    }

    private fun aimCoords(pos: BlockPos): Vec3d {
        val player = mc.player ?: return Vec3d(pos.x + 0.5, pos.y + 1.1, pos.z.toDouble())
        val heldItem = player.mainHandStack
        var xOffset = offsetMap[pos.x] ?: 0.5
        if (heldItem.skyblockID != "TERMINATOR") xOffset = 0.5
        return Vec3d(pos.x + xOffset, pos.y + 1.1, pos.z.toDouble())
    }

    private fun resetPrefire() {
        tempShot.clear()
        prefires = 0
        hasNewEmerald = false
    }

    private fun getSmartPrefireTarget(): Vec3d? {
        val lastShot = shotAt.lastOrNull() ?: return null
        val remaining = shootPositions.filter { it.y == lastShot.y && it !in shotAt }
        if (remaining.size != 2) return null
        val aim1 = aimCoords(remaining[0])
        val aim2 = aimCoords(remaining[1])
        return aim1.add(aim2).multiply(0.5)
    }

    private fun getShootCoord(): Vec3d? {
        currentEmerald.takeIf { hasNewEmerald }?.let { emerald ->
            resetPrefire()
            if (emerald !in shotAt) shotAt.add(emerald)
            return aimCoords(emerald)
        }

        if (prefires > 1) return null

        getSmartPrefireTarget()?.let { return it }

        val target = shootPositions
            .filter { it !in shotAt && it !in tempShot }
            .randomOrNull() ?: return null

        tempShot.add(target)
        return aimCoords(target)
    }

    private fun resetShooting() {
        shotAt.clear()
        resetPrefire()
        deviceCompleted = false
        state = State.IDLE
    }

    @SubscribeEvent
    fun onBlockChange(event: BlockStateChangeEvent) {
        if (mc.world == null || mc.player == null || dungeonFloor != 7 || !inBoss) return
        val pos = event.blockPos
        if (pos !in shootPositions) return

        if (event.newState.block == Blocks.EMERALD_BLOCK) {
            currentEmerald = pos
            hasNewEmerald = true
        } else if (event.oldState?.block == Blocks.EMERALD_BLOCK && pos !in shotAt) {
            shotAt.add(pos)
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (mc.world == null || mc.player == null) return

        if (bonzoSwapDelayTicks > 0 && --bonzoSwapDelayTicks == 0) {
            EquipmentManager.swapHead(*BONZO_MASK_IDS.toTypedArray())
            modMessage("§aAuto Bonzo: §fSwapping to Bonzo Mask!")
        }

        if (dungeonFloor != 7 || !inBoss || !enabled || DungeonUtils.isDead) return

        when (state) {
            State.IDLE -> tickIdle()
            State.PREFIRING -> tickPrefiring()
            State.ROTATING -> tickRotating()
            State.SHOOTING -> tickShooting()
            State.LEAPING -> tickLeaping()
        }
    }

    private fun isAtPlateWithBow(): Boolean {
        val player = mc.player ?: return false
        return isNearPlate() && isPlateDown() && player.mainHandStack.hasItemID("minecraft:bow")
    }

    private fun tickIdle() {
        if (deviceCompleted) {
            if (!isPlateDown()) resetShooting()
            return
        }
        if (isAtPlateWithBow()) state = State.PREFIRING
    }

    private fun tickPrefiring() {
        if (!isAtPlateWithBow()) { resetShooting(); return }

        val player = mc.player ?: return
        bowShootSpeedMs = player.mainHandStack.getShotCooldown()?.times(1000)?.toLong() ?: 300L

        if (!shotClock.hasTimePassed(bowShootSpeedMs)) return

        val target = getShootCoord() ?: return
        currentAimTarget = target
        val (yaw, pitch) = calcAimAngles(target) ?: return

        if (aimStyle == 1) {
            state = State.ROTATING
            RotationUtils.easeTo(yaw, pitch, 60) { state = State.SHOOTING }
        } else {
            RotationUtils.snapTo(yaw, pitch)
            state = State.SHOOTING
        }
    }

    private fun tickRotating() {
        if (!isAtPlateWithBow()) { resetShooting(); return }
    }

    private fun tickShooting() {
        if (!isAtPlateWithBow()) { resetShooting(); return }
        rightClick()
        shotClock.update()
        prefires++
        currentAimTarget = null
        state = State.PREFIRING
    }

    private fun tickLeaping() {
        if (leapDelayTicks > 0) {
            leapDelayTicks--
            return
        }
        val targetClass = LEAP_CLASSES.getOrNull(leapTo)
        if (targetClass != null) LeapManager.scheduleLeap(targetClass)
        state = State.IDLE
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (mc.world == null || mc.player == null) return
        if (event.message.startsWith("[BOSS] Goldor: Who dares trespass into my domain?")) {
            resetShooting()
            return
        }

        if (event.message == "Second Wind Activated! Your Spirit Mask saved your life!") {
            tryScheduleBonzoSwap()
            return
        }

        val name = mc.player?.gameProfile?.name ?: return
        if (event.message.startsWith("$name completed a device!") && getSection() == 4) {
            deviceCompleted = true
            if (autoLeap) {
                state = State.LEAPING
                leapDelayTicks = LEAP_DELAY_TICKS
            } else {
                state = State.IDLE
            }
        }
    }

    private fun tryScheduleBonzoSwap() {
        if (!enabled || !autoBonzo) return
        if (dungeonFloor != 7 || !inBoss) return
        if (!InvincibilityManager.isWearingSpiritMask()) return
        if (!isNearPlate() || !isPlateDown()) return
        if (!isShootingPhase) return
        if (bonzoSwapDelayTicks > 0) return
        bonzoSwapDelayTicks = (45..53).random()
        modMessage("§aAuto Bonzo: §fSpirit Mask popped, swapping in §e$bonzoSwapDelayTicks §fticks")
    }

    private val LEAP_CLASSES = arrayOf(
        DungeonUtils.DungeonClass.Archer,
        DungeonUtils.DungeonClass.Berserk,
        DungeonUtils.DungeonClass.Mage,
        DungeonUtils.DungeonClass.Tank,
        DungeonUtils.DungeonClass.Healer
    )
}
