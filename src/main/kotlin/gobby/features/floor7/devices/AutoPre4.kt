package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.events.BlockStateChangeEvent
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
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
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.getSection
import gobby.utils.skyblockID
import gobby.utils.timer.Clock
import net.minecraft.block.Blocks
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

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

    enum class State { IDLE, PREFIRING, ROTATING, SHOOTING, SWAPPING, LEAPING }

    private const val LEAP_DELAY_TICKS = 2
    private const val PREFIRE_GATE_MS = 150L
    private const val BONZO_SWAP_DELAY_MS = 1000L
    private val SHOOTING_STATES = setOf(State.PREFIRING, State.ROTATING, State.SHOOTING)
    private val LEAP_CLASSES = listOf(DungeonClass.Archer, DungeonClass.Berserk, DungeonClass.Mage, DungeonClass.Tank, DungeonClass.Healer)

    val shootPositions = listOf(68, 66, 64).flatMap { x -> listOf(130, 128, 126).map { y -> BlockPos(x, y, 50) } }
    private val offsetMap = mapOf(68 to -0.7, 66 to -0.5, 64 to 1.5)
    private val platePos = BlockPos(63, 127, 35)

    val shotAt = mutableListOf<BlockPos>()
    private val tempShot = mutableListOf<BlockPos>()
    private val shotClock = Clock()
    private val prefireGate = Clock()
    private val bonzoClock = Clock()
    private var bonzoScheduled = false
    private var currentEmerald: BlockPos? = null
    private var hasNewEmerald = false
    private var prefires = 0
    private var bowShootSpeedMs = 300L
    private var leapDelayTicks = 0
    private var lastWasTerminator = false
    private var bossSpoken = false

    var state: State = State.IDLE
        private set
    var deviceCompleted: Boolean = false
        private set
    var currentAimTarget: Vec3d? = null
        private set
    val isShootingPhase: Boolean get() = state in SHOOTING_STATES

    private fun isNearPlate() = posY == 127.0 && posX in 62.0..65.0 && posZ in 34.0..37.0

    fun isPlateDown(): Boolean = mc.world?.getBlockState(platePos)?.let {
        it.block == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE && it.get(Properties.POWER) > 0
    } ?: false

    private fun isHoldingTerminator(): Boolean {
        val mainHand = mc.player?.mainHandStack?.takeUnless { it.isEmpty } ?: return lastWasTerminator
        return (mainHand.skyblockID == "TERMINATOR").also { lastWasTerminator = it }
    }

    private fun aimCoords(pos: BlockPos): Vec3d {
        val xOffset = if (isHoldingTerminator()) offsetMap[pos.x] ?: 0.5 else 0.5
        return Vec3d(pos.x + xOffset, pos.y + 1.1, pos.z.toDouble())
    }

    private fun isAtPlateWithBow() =
        isNearPlate() && isPlateDown() && mc.player?.mainHandStack?.hasItemID("minecraft:bow") == true

    private fun setEmerald(pos: BlockPos) {
        currentEmerald = pos
        hasNewEmerald = true
        bossSpoken = true
    }

    private fun resetPrefire() {
        tempShot.clear()
        prefires = 0
        hasNewEmerald = false
    }

    private fun resetShooting() {
        shotAt.clear()
        resetPrefire()
        deviceCompleted = false
        state = State.IDLE
    }

    private fun smartPrefireTarget(): Vec3d? {
        val lastY = shotAt.lastOrNull()?.y ?: return null
        val remaining = shootPositions.filter { it.y == lastY && it !in shotAt }
        return when (remaining.size) {
            1 -> aimCoords(remaining[0])
            2 -> aimCoords(remaining[0]).add(aimCoords(remaining[1])).multiply(0.5)
            else -> null
        }
    }

    private fun shootCoord(): Vec3d? {
        currentEmerald?.takeIf { hasNewEmerald }?.let {
            resetPrefire()
            if (it !in shotAt) shotAt.add(it)
            return aimCoords(it)
        }
        if (prefires > 1 || shotAt.isEmpty()) return null
        smartPrefireTarget()?.let { return it }
        return shootPositions.filter { it !in shotAt && it !in tempShot }.randomOrNull()
            ?.also(tempShot::add)?.let(::aimCoords)
    }

    private fun scanExistingEmerald() {
        val world = mc.world ?: return
        shootPositions.firstOrNull { it !in shotAt && world.getBlockState(it).block == Blocks.EMERALD_BLOCK }
            ?.let(::setEmerald)
    }

    @SubscribeEvent
    fun onBlockChange(event: BlockStateChangeEvent) {
        if (mc.world == null || mc.player == null || dungeonFloor != 7 || !inBoss) return
        val pos = event.blockPos.takeIf { it in shootPositions } ?: return
        when {
            event.newState.block == Blocks.EMERALD_BLOCK -> setEmerald(pos)
            event.oldState?.block == Blocks.EMERALD_BLOCK && pos !in shotAt -> shotAt.add(pos)
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        mc.world ?: return
        mc.player ?: return

        if (bonzoScheduled && bonzoClock.hasTimePassed(BONZO_SWAP_DELAY_MS)) {
            bonzoScheduled = false
            if (InvincibilityManager.isWearingSpiritMask()) {
                state = State.SWAPPING
                EquipmentManager.swapHead(*BONZO_MASK_IDS.toTypedArray())
                modMessage("§aAuto Bonzo: §fSwapping to Bonzo Mask!")
            }
        }

        if (dungeonFloor != 7 || !inBoss || !enabled || DungeonUtils.isDead) return
        if (isAtPlateWithBow() && !hasNewEmerald) scanExistingEmerald()

        when (state) {
            State.IDLE -> tickIdle()
            State.PREFIRING -> tickPrefiring()
            State.ROTATING, State.SHOOTING -> if (!isAtPlateWithBow()) resetShooting() else if (state == State.SHOOTING) tickShooting()
            State.SWAPPING -> tickSwapping()
            State.LEAPING -> tickLeaping()
        }
    }

    private fun tickIdle() {
        if (deviceCompleted) { if (!isPlateDown()) resetShooting(); return }
        if (bossSpoken && isAtPlateWithBow()) { state = State.PREFIRING; prefireGate.update() }
    }

    private fun tickPrefiring() {
        if (!isAtPlateWithBow()) return resetShooting()
        if (!prefireGate.hasTimePassed(PREFIRE_GATE_MS)) return
        val player = mc.player ?: return
        bowShootSpeedMs = (player.mainHandStack.getShotCooldown()?.times(1000)?.toLong() ?: 250L).coerceIn(50L, 2000L)
        if (!shotClock.hasTimePassed(bowShootSpeedMs)) return

        val target = shootCoord() ?: return
        val (yaw, pitch) = calcAimAngles(target) ?: return
        currentAimTarget = target
        if (aimStyle == 1) {
            state = State.ROTATING
            RotationUtils.easeTo(yaw, pitch, 60) { state = State.SHOOTING }
        } else {
            RotationUtils.snapTo(yaw, pitch); state = State.SHOOTING
        }
    }

    private fun tickShooting() {
        rightClick()
        shotClock.update()
        prefires++
        currentAimTarget = null
        state = State.PREFIRING
    }

    private fun tickSwapping() {
        if (EquipmentManager.isSwapping) return
        state = when {
            deviceCompleted && autoLeap -> State.LEAPING.also { leapDelayTicks = LEAP_DELAY_TICKS }
            deviceCompleted -> State.IDLE
            else -> State.PREFIRING.also { prefireGate.update() }
        }
    }

    private fun tickLeaping() {
        if (EquipmentManager.isSwapping) return
        if (leapDelayTicks > 0) { leapDelayTicks--; return }
        LEAP_CLASSES.getOrNull(leapTo)?.let(LeapManager::scheduleLeap)
        state = State.IDLE
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        val name = mc.player?.gameProfile?.name ?: return
        val msg = event.message
        when {
            msg.startsWith("[BOSS] Goldor: Who dares trespass into my domain?") -> { bossSpoken = true; resetShooting() }
            msg == "Second Wind Activated! Your Spirit Mask saved your life!" -> tryScheduleBonzoSwap()
            msg.startsWith("$name completed a device!") && getSection() == 4 -> {
                deviceCompleted = true
                if (state != State.SWAPPING) {
                    if (autoLeap) { state = State.LEAPING; leapDelayTicks = LEAP_DELAY_TICKS } else state = State.IDLE
                }
            }
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        bossSpoken = false
        bonzoScheduled = false
        resetShooting()
    }

    private fun tryScheduleBonzoSwap() {
        if (!enabled || !autoBonzo || dungeonFloor != 7 || !inBoss
            || !InvincibilityManager.isWearingSpiritMask()
            || !isNearPlate() || !isPlateDown() || !isShootingPhase
            || bonzoScheduled || state == State.SWAPPING) return
        bonzoScheduled = true
        bonzoClock.update()
        modMessage("§aAuto Bonzo: §fSpirit Mask popped, swapping in §e1s")
    }
}
