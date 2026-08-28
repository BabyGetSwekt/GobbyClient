package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.*
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.gui.click.*
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.isDead
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.tiles.Room
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import net.minecraft.world.phys.Vec3
import net.minecraft.util.Mth
import java.awt.Color
import gobby.utils.MovementPacketSuppressor

object BloodBlink : Module("Blood Blink", "Auto navigates to the Blood Room", Category.DUNGEONS, hidden = true) {

    internal const val PEARL_SUCCESS_Y = 68.0
    internal const val MAX_PEARL_RETRIES = 6
    internal const val SNEAK_EYE_HEIGHT = 1.54
    internal const val EXPLORE_EXIT = 25
    internal const val MAP_LOAD_TIME = 10
    internal const val FORWARD_PEARL_DELAY_TICKS = 4
    internal const val PEARL_DOWN_DELAY_TICKS = 4
    internal const val PEARL_LAND_TIMEOUT_TICKS = 10
    internal const val START_COUNTDOWN_IDLE = -67
    internal val MAP_CENTER = Vec3(-104.5, 0.0, -104.5)
    internal val onlyOnGround by BooleanSetting("Only on Ground", false, desc = "Only start bloodrushing when you're on the ground")
    internal val autoBlink by BooleanSetting("Auto Blink", true, desc = "Automatically bloodrushes on dungeon load")
    internal enum class Slab(val offset: Vec3, val color: Color) {
        FIRST(Vec3(5.0, 82.0, 2.0), Color(255, 50, 50, 120)),
        SECOND(Vec3(9.0, 82.0, 2.0), Color(255, 165, 0, 120)),
        THIRD(Vec3(21.0, 82.0, 2.0), Color(50, 255, 50, 120)),
        FOURTH(Vec3(25.0, 82.0, 2.0), Color(50, 100, 255, 120))
    }

    internal enum class State {
        IDLE, INIT, AWAIT_SLAB1_LAND, PEARL_UP_1, AWAIT_PEARL_UP_1_LAND,
        EXPLORE, AWAIT_EXPLORE_LAND, ETHERWARP_SLAB2, AWAIT_SLAB2_LAND,
        PEARL_UP_2, AWAIT_PEARL_UP_2_LAND, BLOOD_RUSH, FORWARD_PEARL,
        PEARL_DOWN, AWAIT_PEARL_DOWN_LAND, DONE
    }

    internal var state = State.IDLE
    internal var startRoom: Room? = null
    internal var lowSlab = false
    internal var explored = false
    var forceSneak = false
        internal set
    internal var pearlDelay = 0
    internal var pearlAttempts = 0
    internal var pearlLandWait = 0
    internal var pearlSwapped = false
    internal var forwardPearlDelay = 0
    internal var targetX = 0
    internal var targetZ = 0
    internal var targetBottom = 63
    internal var bloodFound = false
    internal var serverTick = -1
    internal var tickCount = 0
    internal var startCountdown = START_COUNTDOWN_IDLE
    val isBlinking: Boolean get() = state != State.IDLE && state != State.DONE

    fun consumeForceSneak() { forceSneak = false }

    fun cancelBlink() { resetState(); state = State.DONE; modMessage("§cBlood Blink cancelled") }

    fun doBlink() { resetState(); state = State.INIT; KeyMapping.releaseAll() }

    fun retryBlink(): Boolean {
        if (!inDungeons) { errorMessage("§cMust be in a dungeon"); return false }
        val current = ScanUtils.currentRoom
        if (current == null || current.data.type != RoomType.ENTRANCE) {
            modMessage("§cMust be in the entrance room"); return false
        }
        if (!bloodFound) { errorMessage("§cBlood room has not been scanned yet"); return false }
        val (savedX, savedZ, savedBottom) = Triple(targetX, targetZ, targetBottom)
        reset()
        bloodFound = true; targetX = savedX; targetZ = savedZ; targetBottom = savedBottom
        doBlink(); modMessage("§aRetrying Blood Blink"); return true
    }

    fun reset() {
        bloodFound = false; startRoom = null
        serverTick = -1; tickCount = 0; startCountdown = START_COUNTDOWN_IDLE
        resetState()
    }

    internal fun resetState() {
        state = State.IDLE
        lowSlab = false; forceSneak = false; explored = false
        pearlDelay = 0; pearlAttempts = 0; pearlLandWait = 0
        pearlSwapped = false; forwardPearlDelay = 0
        MovementPacketSuppressor.clear()
    }

    internal fun finish(msg: String? = null) {
        msg?.let { modMessage(it) }
        state = State.DONE
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!enabled || !inDungeons || inBoss) return
        val player = mc.player ?: return
        if (tickCount <= 2) return
        if (isBlinking && isDead) {
            errorMessage("§cBlood Blink stopped. You died lol"); resetState(); state = State.DONE; return
        }
        when (state) {
            State.IDLE -> tryAutoStart(player)
            State.INIT -> initSequence(player)
            State.PEARL_UP_1 -> pearl(player.yRot, -90f) { state = State.AWAIT_PEARL_UP_1_LAND }
            State.EXPLORE -> exploreForBlood()
            State.ETHERWARP_SLAB2 -> slabToBlood(player)
            State.PEARL_UP_2 -> pearl(player.yRot, -90f) { state = State.AWAIT_PEARL_UP_2_LAND }
            State.BLOOD_RUSH -> bloodRush()
            State.FORWARD_PEARL -> pearlForward(player)
            State.PEARL_DOWN -> pearlDown(player)
            State.AWAIT_PEARL_DOWN_LAND -> awaitPearlDownTimeout()
            else -> {}
        }
    }

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (!isBlinking || inBoss) return
        when (val packet = event.packet) {
            is ClientboundSetTimePacket -> serverTick = (packet.gameTime % 40).toInt()
            is ClientboundPlayerPositionPacket -> onPositionLook(packet.change().position())
        }
    }

    private fun onPositionLook(pos: Vec3) {
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

    private fun onPearlDownLanded(pos: Vec3) {
        if (Mth.abs(targetX - Mth.floor(pos.x)) >= 16 || Mth.abs(targetZ - Mth.floor(pos.z)) >= 16) return
        when {
            pos.y >= PEARL_SUCCESS_Y -> {
                modMessage("§e[BB] Landed at Y=${pos.y}, pearling forward")
                forwardPearlDelay = 0; pearlSwapped = false; state = State.FORWARD_PEARL
            }
            pos.y in (targetBottom - 1.0)..PEARL_SUCCESS_Y -> {
                modMessage("§c[BB] Too low (Y=${pos.y}), retrying pearl")
                pearlDelay = PEARL_DOWN_DELAY_TICKS; pearlSwapped = false; state = State.PEARL_DOWN
            }
        }
    }



    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        serverTick++; tickCount++
        if (startCountdown != START_COUNTDOWN_IDLE) startCountdown--
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inDungeons || !enabled) return
        if (event.message == "Starting in 1 second.") startCountdown = 20
    }

    @SubscribeEvent
    fun onTickCheckBlood(event: ClientTickEvent.Pre) {
        if (!enabled || bloodFound || !inDungeons || !DungeonMap.hasScanned) return
        val target = BloodBlinkSupport.findBloodRoom(DungeonMap.grid) ?: return
        targetX = target.x; targetZ = target.z
        targetBottom = 63; bloodFound = true
        modMessage("Blood room found at $targetX, $targetZ")
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !enabled) return
        val room = startRoom ?: ScanUtils.currentRoom ?: return
        if (room.data.type != RoomType.ENTRANCE) return
        BloodBlinkSupport.renderSlabs(event, room)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = reset()
}
