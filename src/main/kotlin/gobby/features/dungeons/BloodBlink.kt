package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.*
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.*
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.Utils.getRandomInt
import gobby.utils.PlayerUtils
import gobby.utils.findHotbarSlot
import gobby.utils.getInstantTransmissionRange
import gobby.utils.managers.PacketOrderManager
import gobby.utils.managers.SwapManager
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.rotation.AngleUtils
import gobby.utils.rotation.AngleUtils.horizontalDegrees
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.DungeonUtils.isDead
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.map.MapConstants
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.Room
import gobby.utils.skyblock.dungeon.tiles.RoomType
import gobby.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.block.Blocks
import net.minecraft.client.option.KeyBinding
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket
import net.minecraft.util.math.*
import java.awt.Color
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Contents of this file are inspired on RSA by rdbtCVS, licensed under MIT.
 * But most of the code has been rewritten by me.
 * Original: https://github.com/rdbtCVS/rsa
 */
object BloodBlink : Module("Blood Blink", "Auto navigates to the Blood Room", Category.DUNGEONS) {

    private const val PEARL_SUCCESS_Y = 68.0
    private const val MAX_PEARL_RETRIES = 6
    private const val SNEAK_EYE_HEIGHT = 1.54
    private const val EXPLORE_EXIT = 25
    private const val MAP_LOAD_TIME = 10
    private val MAP_CENTER = Vec3d(-104.5, 0.0, -104.5)

    private enum class Slab(val offset: Vec3d, val color: Color) {
        FIRST(Vec3d(5.0, 82.0, 2.0), Color(255, 50, 50, 120)),
        SECOND(Vec3d(9.0, 82.0, 2.0), Color(255, 165, 0, 120)),
        THIRD(Vec3d(21.0, 82.0, 2.0), Color(50, 255, 50, 120)),
        FOURTH(Vec3d(25.0, 82.0, 2.0), Color(50, 100, 255, 120))
    }

    private enum class State {
        IDLE, INIT, AWAIT_SLAB1_LAND, PEARL_UP_1, AWAIT_PEARL_UP_1_LAND,
        EXPLORE, AWAIT_EXPLORE_LAND, ETHERWARP_SLAB2, AWAIT_SLAB2_LAND,
        PEARL_UP_2, AWAIT_PEARL_UP_2_LAND, BLOOD_RUSH, FORWARD_PEARL,
        PEARL_DOWN, AWAIT_PEARL_DOWN_LAND, DONE
    }

    private val onlyOnGround by BooleanSetting("Only on Ground", false, desc = "Only start bloodrushing when you're on the ground")
    private val autoBlink by BooleanSetting("Auto Blink", true, desc = "Automatically bloodrushes on dungeon load")
    private var state = State.IDLE
    private var targetX = 0
    private var targetZ = 0
    private var targetBottom = 63
    private var bloodFound = false
    private var startRoom: Room? = null
    private var serverTick = -1
    private var tickCount = 0
    private var startCountdown = -67
    private var lowSlab = false
    private var forceSneak = false
    private var explored = false
    private var pearlDelay = 0
    private var pearlAttempts = 0
    private var pearlLandWait = 0
    private var pearlSwapped = false
    private var forwardPearlDelay = 0

    fun isBlinking(): Boolean = state != State.IDLE && state != State.DONE
    fun getForceSneak(): Boolean = forceSneak
    fun consumeForceSneak() { forceSneak = false }
    fun cancelBlink() { resetState(); state = State.DONE; modMessage("§cBlood Blink cancelled") }
    fun doBlink() { resetState(); state = State.INIT; KeyBinding.unpressAll() }

    private fun resetState() {
        state = State.IDLE
        lowSlab = false; forceSneak = false; explored = false
        pearlDelay = 0; pearlAttempts = 0; pearlLandWait = 0
        pearlSwapped = false; forwardPearlDelay = 0
    }

    private fun slabWorldPos(): Vec3d? = startRoom?.getRealCoords(Slab.entries[getRandomInt(0, 3)].offset)

    private fun voidDirection(roomX: Int, roomZ: Int): Direction {
        val x = (roomX - MapConstants.START_X) / (MapConstants.HALF_ROOM * 2)
        val z = (roomZ - MapConstants.START_Z) / (MapConstants.HALF_ROOM * 2)
        return when {
            x == 0 -> Direction.WEST
            z == 0 -> Direction.NORTH
            x > z -> Direction.EAST
            else -> Direction.SOUTH
        }
    }

    private fun pearlUpComplete(y: Double): Boolean = y > if (lowSlab) 97.0 else 98.0
    private fun sendEtherwarps(count: Int, yaw: Float, pitch: Float) = repeat(count) { PlayerUtils.useItem(yaw, pitch) }

    private fun lookAndEtherwarp(p: PlayerEntity, yaw: Float, pitch: Float, count: Int) {
        mc.networkHandler?.sendPacket(PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, p.isOnGround, p.horizontalCollision))
        sendEtherwarps(count, yaw, pitch)
    }

    private fun etherwarpToSlab(nextState: State) {
        val world = mc.world ?: return
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            val p = mc.player ?: return@register
            if (SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") < 0) { errorMessage("No AOTV/AOTE found in hotbar"); state = State.DONE; return@register }
            if (!p.lastPlayerInput.sneak()) return@register
            val slab = slabWorldPos() ?: return@register
            if (world.getBlockAtPos(BlockPos(MathHelper.floor(slab.x), MathHelper.floor(slab.y), MathHelper.floor(slab.z))) == Blocks.AIR) lowSlab = true
            val targetY = if (lowSlab) slab.y - 1.0 else slab.y
            val target = Vec3d(MathHelper.floor(slab.x) + 0.5, targetY, MathHelper.floor(slab.z) + 0.5)
            val (yaw, pitch) = AngleUtils.calcAimAnglesBetween(Vec3d(p.x, p.y + SNEAK_EYE_HEIGHT, p.z), target)
            PlayerUtils.useItem(yaw, pitch)
            state = nextState
        }
    }

    private fun pearl(yaw: Float, pitch: Float, onSuccess: () -> Unit) {
        if (!pearlSwapped) {
            val slot = findHotbarSlot("ENDER_PEARL")
            if (slot < 0 || !SwapManager.swapSlot(slot)) return
            pearlSwapped = true; return
        }
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            pearlSwapped = false
            if (PlayerUtils.useItem(yaw, pitch)) onSuccess()
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!enabled || !inDungeons || inBoss) return
        val player = mc.player ?: return
        if (tickCount <= 2) return
        if (isBlinking() && isDead) { errorMessage("§cBlood Blink stopped. You died lol"); resetState(); state = State.DONE; return }
        when (state) {
            State.IDLE -> if (autoBlink && ScanUtils.currentRoom?.data?.type == RoomType.ENTRANCE) { KeyBinding.unpressAll(); state = State.INIT; initSequence(player) }
            State.INIT -> initSequence(player)
            State.PEARL_UP_1 -> pearl(player.yaw, -90f) { state = State.AWAIT_PEARL_UP_1_LAND }
            State.EXPLORE -> exploreForBlood()
            State.ETHERWARP_SLAB2 -> slabToBlood(player)
            State.PEARL_UP_2 -> pearl(player.yaw, -90f) { state = State.AWAIT_PEARL_UP_2_LAND }
            State.BLOOD_RUSH -> bloodRush()
            State.FORWARD_PEARL -> pearlForward(player)
            State.PEARL_DOWN -> pearlDown(player)
            State.AWAIT_PEARL_DOWN_LAND -> awaitPearlDownTimeout()
            else -> {}
        }
    }

    private fun initSequence(player: PlayerEntity) {
        if (!bloodFound && DungeonListener.isBloodOpened) { modMessage("§cCannot blink — dungeon started without blood room"); state = State.DONE; return }
        forceSneak = true
        if (onlyOnGround && !player.isOnGround) return
        if (startRoom == null) startRoom = ScanUtils.currentRoom
        val room = startRoom ?: return
        if (room.rotation == Rotations.NONE) return
        etherwarpToSlab(State.AWAIT_SLAB1_LAND)
    }

    private fun exploreForBlood() {
        if (bloodFound) { state = State.BLOOD_RUSH; return }
        if (serverTick % 40 >= EXPLORE_EXIT) return
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
            val p = mc.player ?: return@register
            val (angleYaw, _) = AngleUtils.calcAimAnglesBetween(Vec3d(p.x, p.y, p.z), Vec3d(MAP_CENTER.x, p.y, MAP_CENTER.z))
            val dx = (p.x - MAP_CENTER.x).toFloat(); val dz = (p.z - MAP_CENTER.z).toFloat()
            val range = p.mainHandStack.getInstantTransmissionRange()
            lookAndEtherwarp(p, p.yaw, -90f, 8)
            lookAndEtherwarp(p, angleYaw, 0f, (sqrt(dx * dx + dz * dz) / range).roundToInt())
            explored = true; state = State.AWAIT_EXPLORE_LAND
        }
    }

    private fun slabToBlood(player: PlayerEntity) {
        forceSneak = true
        if (onlyOnGround && !player.isOnGround) return
        if (startRoom == null) return
        if (explored && !bloodFound) { modMessage("§cCould not find blood room"); state = State.DONE; return }
        etherwarpToSlab(State.AWAIT_SLAB2_LAND)
    }

    private fun bloodRush() {
        SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
        val started = DungeonListener.isBloodOpened || (startCountdown != -67 && startCountdown <= 0)
        if (!started) return
        if (serverTick % 40 >= 40 - MAP_LOAD_TIME) return
        startCountdown = -67
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
            val p = mc.player ?: return@register
            val room = startRoom ?: return@register
            val dir = voidDirection(room.roomComponents.first().x, room.roomComponents.first().z)
            lookAndEtherwarp(p, dir.horizontalDegrees(), 0f, 4)
            lookAndEtherwarp(p, p.yaw, 90f, 10)
            val predicted = Vec3d(p.x, p.y, p.z).add(RotationUtils.rotateByDirection(dir, 0.0, 0.0, -48.0))
            val dx = (targetX + 0.5 - predicted.x).toFloat(); val dz = (targetZ + 0.5 - predicted.z).toFloat()
            val (bloodYaw, _) = AngleUtils.calcAimAnglesFromDelta(dx.toDouble(), 0.0, dz.toDouble())
            val range = p.mainHandStack.getInstantTransmissionRange()
            lookAndEtherwarp(p, bloodYaw, 3f, (sqrt(dx * dx + dz * dz) / range).roundToInt())
            lookAndEtherwarp(p, p.yaw, -90f, 5)
            pearlDelay = 0; state = State.PEARL_DOWN
        }
    }

    private fun pearlForward(player: PlayerEntity) {
        if (forwardPearlDelay < 4) { forwardPearlDelay++; return }
        modMessage("§e[BB] Pearling forward into blood room")
        pearl(player.yaw, 0f) { modMessage("§aBlood Blink complete!"); state = State.DONE }
    }

    private fun pearlDown(player: PlayerEntity) {
        if (pearlDelay < 4) { pearlDelay++; return }
        if (pearlAttempts >= MAX_PEARL_RETRIES) { modMessage("§c[BB] Pearl failed after $MAX_PEARL_RETRIES attempts"); state = State.DONE; return }
        pearlAttempts++
        modMessage("§e[BB] Pearl attempt #$pearlAttempts")
        pearl(player.yaw, -90f) { pearlLandWait = 0; state = State.AWAIT_PEARL_DOWN_LAND }
    }

    private fun awaitPearlDownTimeout() {
        pearlLandWait++
        if (pearlLandWait > 10) { modMessage("§c[BB] Pearl timeout, retrying..."); pearlDelay = 4; state = State.PEARL_DOWN }
    }

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (!isBlinking() || inBoss) return
        val packet = event.packet
        if (packet is WorldTimeUpdateS2CPacket) serverTick = (packet.time() % 40).toInt()
        if (packet is PlayerPositionLookS2CPacket) onPositionLook(packet.change().position())
    }

    private fun onPositionLook(pos: Vec3d) {
        when (state) {
            State.AWAIT_SLAB1_LAND -> state = State.PEARL_UP_1
            State.AWAIT_PEARL_UP_1_LAND -> state = if (pearlUpComplete(pos.y)) State.EXPLORE else State.PEARL_UP_1
            State.AWAIT_EXPLORE_LAND -> if (pos.y == 76.5 || pos.y == 75.5) state = State.ETHERWARP_SLAB2
            State.AWAIT_SLAB2_LAND -> state = State.PEARL_UP_2
            State.AWAIT_PEARL_UP_2_LAND -> state = if (pearlUpComplete(pos.y)) State.BLOOD_RUSH else State.PEARL_UP_2
            State.AWAIT_PEARL_DOWN_LAND -> onPearlDownLanded(pos)
            else -> {}
        }
    }

    private fun onPearlDownLanded(pos: Vec3d) {
        if (MathHelper.abs(targetX - MathHelper.floor(pos.x)) >= 16 || MathHelper.abs(targetZ - MathHelper.floor(pos.z)) >= 16) return
        if (pos.y >= PEARL_SUCCESS_Y) {
            modMessage("§e[BB] Landed at Y=${pos.y}, pearling forward")
            forwardPearlDelay = 0; pearlSwapped = false; state = State.FORWARD_PEARL
        } else if (pos.y in (targetBottom - 1.0)..PEARL_SUCCESS_Y) {
            modMessage("§c[BB] Too low (Y=${pos.y}), retrying pearl")
            pearlDelay = 4; pearlSwapped = false; state = State.PEARL_DOWN
        }
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        serverTick++; tickCount++
        if (startCountdown != -67) startCountdown--
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inDungeons || !enabled) return
        if (event.message == "Starting in 1 second.") startCountdown = 20
    }

    @SubscribeEvent
    fun onTickCheckBlood(event: ClientTickEvent.Pre) {
        if (!enabled || bloodFound || !inDungeons || !DungeonMap.hasScanned) return
        val grid = DungeonMap.grid
        for (row in 0 until MapConstants.GRID_SIZE step 2) for (col in 0 until MapConstants.GRID_SIZE step 2) {
            val tile = grid[row * MapConstants.GRID_SIZE + col]
            if (tile is MapTile.Room && tile.data.type == RoomType.BLOOD) {
                targetX = MapConstants.START_X + col * MapConstants.HALF_ROOM
                targetZ = MapConstants.START_Z + row * MapConstants.HALF_ROOM
                targetBottom = 63; bloodFound = true
                modMessage("§aBlood room found at $targetX, $targetZ")
                return
            }
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled) return
        val room = startRoom ?: ScanUtils.currentRoom ?: return
        if (room.data.type != RoomType.ENTRANCE) return
        for (slab in Slab.entries) {
            val p = room.getRealCoords(slab.offset)
            val box = Box(p.x.toInt().toDouble(), p.y.toInt().toDouble(), p.z.toInt().toDouble(),
                p.x.toInt() + 1.0, p.y.toInt() + 1.0, p.z.toInt() + 1.0)
            draw3DBox(event.matrixStack, event.camera, box, slab.color, depthTest = false)
        }
    }

    fun reset() {
        bloodFound = false; startRoom = null
        serverTick = -1; tickCount = 0; startCountdown = -67
        resetState()
    }

    fun retryBlink(): Boolean {
        if (!inDungeons) { errorMessage("§cMust be in a dungeon"); return false }
        val current = ScanUtils.currentRoom
        if (current == null || current.data.type != RoomType.ENTRANCE) { modMessage("§cMust be in the entrance room"); return false }
        if (!bloodFound) { errorMessage("§cBlood room has not been scanned yet"); return false }
        val savedX = targetX; val savedZ = targetZ; val savedBottom = targetBottom
        reset()
        bloodFound = true; targetX = savedX; targetZ = savedZ; targetBottom = savedBottom
        doBlink(); modMessage("§aRetrying Blood Blink"); return true
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = reset()
}