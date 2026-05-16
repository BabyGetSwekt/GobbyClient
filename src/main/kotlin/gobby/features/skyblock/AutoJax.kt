package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.network.SystemChatReceivedEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.LocationUtils
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.Utils.getRandomInt
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
import gobby.utils.getBowShootSpeedMs
import gobby.utils.isShortbow
import gobby.utils.rotation.RotationUtils
import gobby.utils.timer.Clock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.core.BlockPos
import kotlin.math.abs

object AutoJax : Module("Auto Jax", "Automatically shoots at the redstone lamps at Jax. Hold a shortbow for it to work", Category.SKYBLOCK) {

    private const val START_X = -55.5
    private const val START_Y = 62.0
    private const val START_Z = -81.5
    private const val START_TOLERANCE = 0.9
    private const val ROTATION_JITTER_MS = 10

    private const val START_MSG = "Duration: 0s"
    private const val CANCEL_MSG = "Cancelled! You cannot leave the pressure plate!"
    private val DONE_PREFIXES = listOf("You completed Practice Mode in", "HIGH SCORE You completed Practice Mode in")

    private val LANTERNS = listOf(
        BlockPos(-52, 66, -86), BlockPos(-46, 64, -88), BlockPos(-44, 62, -87), BlockPos(-44, 63, -82),
        BlockPos(-46, 62, -80), BlockPos(-47, 64, -78), BlockPos(-46, 60, -75), BlockPos(-51, 63, -71),
        BlockPos(-55, 61, -71), BlockPos(-56, 63, -71), BlockPos(-61, 63, -73), BlockPos(-63, 61, -78),
        BlockPos(-63, 62, -82), BlockPos(-63, 63, -86)
    )

    private var active = false
    private var bowWarned = false
    private var rotating = false
    private var awaitingShot = false
    private var nextIdx = 0
    private val shotClock = Clock()

    private val atStart: Boolean
        get() = LocationUtils.location == "Hub"
            && abs(posY - START_Y) < 1e-3
            && abs(posX - START_X) <= START_TOLERANCE
            && abs(posZ - START_Z) <= START_TOLERANCE

    private val holdingShortbow: Boolean
        get() = mc.player?.mainHandItem?.takeUnless { it.isEmpty }?.isShortbow() == true

    private fun isLanternUnshot(pos: BlockPos): Boolean = mc.level?.getBlockState(pos)?.let {
        it.block == Blocks.REDSTONE_LAMP && !it.getValue(BlockStateProperties.LIT)
    } == true

    private fun pickNextTarget(): BlockPos? = LANTERNS.indices
        .map { (nextIdx + it) % LANTERNS.size }
        .firstOrNull { isLanternUnshot(LANTERNS[it]) }
        ?.also { nextIdx = it }
        ?.let { LANTERNS[it] }

    private fun warnNoBow() {
        if (bowWarned) return
        errorMessage("Hold your shortbow to start.")
        bowWarned = true
    }

    private fun startRound() {
        if (!atStart) return
        active = true
        if (!holdingShortbow) { warnNoBow(); return }
        bowWarned = false
        rotating = false
        awaitingShot = false
        nextIdx = getRandomInt(0, LANTERNS.size - 1)
        shotClock.update()
    }

    private fun reset() {
        active = false
        bowWarned = false
        rotating = false
        awaitingShot = false
    }

    private fun fireAndAdvance() {
        rightClick()
        shotClock.update()
        awaitingShot = false
        rotating = false
        nextIdx = (nextIdx + 1) % LANTERNS.size
    }

    private fun beginAimAt(target: BlockPos, cooldown: Long) {
        rotating = true
        val rotationMs = (cooldown + getRandomInt(-ROTATION_JITTER_MS, ROTATION_JITTER_MS)).coerceAtLeast(1L)
        RotationUtils.aimAtBlockShortbow(target, rotate = true, delayMs = rotationMs) { awaitingShot = true }
        if (!RotationUtils.isEasing) rotating = false
    }

    @SubscribeEvent
    fun onSystemChat(event: SystemChatReceivedEvent) {
        if (!enabled) return
        val msg = event.message
        when {
            msg.contains(START_MSG) -> startRound()
            msg == CANCEL_MSG -> reset()
            DONE_PREFIXES.any { msg.startsWith(it) } -> reset()
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!enabled || !active) return
        val player = mc.player ?: return
        mc.level ?: return
        if (!atStart) return
        if (!holdingShortbow) { warnNoBow(); return }
        bowWarned = false

        val cooldown = player.mainHandItem.getBowShootSpeedMs()

        if (awaitingShot && shotClock.hasTimePassed(cooldown)) { fireAndAdvance(); return }
        if (rotating || awaitingShot || RotationUtils.isEasing) return

        pickNextTarget()?.let { beginAimAt(it, cooldown) }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = reset()
}
