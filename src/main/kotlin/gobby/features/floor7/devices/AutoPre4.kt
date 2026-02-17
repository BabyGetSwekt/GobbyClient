package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.BlockStateChangeEvent
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
import gobby.utils.getShotCooldown
import gobby.utils.hasItemID
import gobby.utils.PacketUtils.getSequence
import gobby.utils.rotation.AngleUtils.calcAimAngles
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblockID
import gobby.utils.timer.Clock
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand
import net.minecraft.block.Blocks
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * This code was inspired by CyanAddons, but has been heavily modified.
 */
object AutoPre4 {

    // TODO: Auto leap + auto mask

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
    var deviceCompleted = false
        private set
    var currentAimTarget: Vec3d? = null
        private set

    private fun isNearPlate(): Boolean {
        return posY == 127.0 && posX in 62.0..65.0 && posZ in 34.0..37.0
    }

    fun isPlateDown(): Boolean {
        val world = mc.world ?: return false
        val state = world.getBlockState(platePos)
        if (state.block != Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE) return false
        return state.get(Properties.POWER) > 0
    }

    private fun aimCoords(pos: BlockPos): Vec3d {
        val player = mc.player ?: return Vec3d(pos.x + 0.5, pos.y + 1.1, pos.z.toDouble())
        val heldItem = player.mainHandStack
        var xOffset = offsetMap[pos.x] ?: 0.5
        modMessage("Held item skyblockID: '${heldItem.skyblockID}', xOffset: $xOffset")
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
        if (mc.world == null || mc.player == null || dungeonFloor != 7 || !inBoss || !GobbyConfig.autoPre4) return
        if (!isNearPlate()) return

        val player = mc.player ?: return
        val heldItem = player.mainHandStack
        if (!heldItem.hasItemID("minecraft:bow")) return

        bowShootSpeedMs = heldItem.getShotCooldown()?.times(1000)?.toLong() ?: 300L

        if (deviceCompleted) return
        if (!shotClock.hasTimePassed(bowShootSpeedMs)) return
        if (RotationUtils.isEasing) return

        if (!isPlateDown()) {
            shotAt.clear()
            deviceCompleted = false
            return
        }

        val target = getShootCoord() ?: return
        currentAimTarget = target
        val (yaw, pitch) = calcAimAngles(target) ?: return

        when (GobbyConfig.autoPre4AimStyle) {
            1 -> RotationUtils.easeTo(yaw, pitch, 100) { // Smooth rotation
                rightClick()
                shotClock.update()
                prefires++
                currentAimTarget = null
            }
            2 -> {
                val packet = PlayerInteractItemC2SPacket(Hand.MAIN_HAND, getSequence(), yaw, pitch) // Serversided rotation
                RotationUtils.snapTo(yaw, pitch, true)
                mc.networkHandler?.sendPacket(packet)
                shotClock.update()
                prefires++
                currentAimTarget = null
            }
            else -> { // snap rotation
                RotationUtils.snapTo(yaw, pitch)
                rightClick()
                shotClock.update()
                prefires++
                currentAimTarget = null
            }
        }
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (mc.world == null || mc.player == null) return
        if (event.message.startsWith("[BOSS] Goldor: Who dares trespass into my domain?")) deviceCompleted = false

        val name = mc.player?.gameProfile?.name ?: return
        if (event.message.startsWith("$name completed a device!")) {
            deviceCompleted = true
        }
    }
}
