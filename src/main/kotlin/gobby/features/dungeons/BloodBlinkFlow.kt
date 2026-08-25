package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.PlayerUtils
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.Utils.getRandomInt
import gobby.utils.findHotbarSlot
import gobby.utils.getInstantTransmissionRange
import gobby.utils.managers.PacketOrderManager
import gobby.utils.managers.SwapManager
import gobby.utils.rotation.AngleUtils
import gobby.utils.rotation.AngleUtils.horizontalDegrees
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.map.MapConstants
import gobby.utils.skyblock.dungeon.tiles.RoomType
import gobby.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.world.level.block.Blocks
import net.minecraft.client.KeyMapping
import net.minecraft.world.entity.player.Player
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraft.util.Mth
import kotlin.math.roundToInt
import kotlin.math.sqrt
import gobby.utils.MovementPacketSuppressor

    internal fun BloodBlink.slabWorldPos(): Vec3? = startRoom?.getRealCoords(BloodBlink.Slab.entries[getRandomInt(0, 3)].offset)

    internal fun BloodBlink.voidDirection(roomX: Int, roomZ: Int): Direction {
        val x = (roomX - MapConstants.START_X) / (MapConstants.HALF_ROOM * 2)
        val z = (roomZ - MapConstants.START_Z) / (MapConstants.HALF_ROOM * 2)
        return when {
            x == 0 -> Direction.WEST
            z == 0 -> Direction.NORTH
            x > z -> Direction.EAST
            else -> Direction.SOUTH
        }
    }

    internal fun BloodBlink.pearlUpComplete(y: Double): Boolean = y > if (lowSlab) 97.0 else 98.0

    internal fun BloodBlink.sendEtherwarps(count: Int, yaw: Float, pitch: Float) =
        repeat(count) { PlayerUtils.useItem(yaw, pitch) }

    internal fun BloodBlink.lookAndEtherwarp(p: Player, yaw: Float, pitch: Float, count: Int) {
        mc.connection?.send(ServerboundMovePlayerPacket.Rot(yaw, pitch, p.onGround(), p.horizontalCollision))
        sendEtherwarps(count, yaw, pitch)
        MovementPacketSuppressor.suppressNext()
    }

    internal fun BloodBlink.etherwarpToSlab(nextState: BloodBlink.State) {
        val world = mc.level ?: return
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            val p = mc.player ?: return@register
            if (SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") < 0) {
                errorMessage("No AOTV/AOTE found in hotbar"); finish(); return@register
            }
            if (!p.lastSentInput.shift()) return@register
            val slab = slabWorldPos() ?: return@register
            val slabBlock = BlockPos(Mth.floor(slab.x), Mth.floor(slab.y), Mth.floor(slab.z))
            if (world.getBlockAtPos(slabBlock) == Blocks.AIR) lowSlab = true
            val targetY = if (lowSlab) slab.y - 1.0 else slab.y
            val target = Vec3(Mth.floor(slab.x) + 0.5, targetY, Mth.floor(slab.z) + 0.5)
            val (yaw, pitch) = AngleUtils.calcAimAnglesBetween(Vec3(p.x, p.y + SNEAK_EYE_HEIGHT, p.z), target)
            PlayerUtils.useItem(yaw, pitch)
            MovementPacketSuppressor.suppressNext()
            state = nextState
        }
    }

    internal fun BloodBlink.pearl(yaw: Float, pitch: Float, onSuccess: () -> Unit) {
        if (!pearlSwapped) {
            val slot = findHotbarSlot("ENDER_PEARL")
            if (slot < 0 || !SwapManager.swapSlot(slot)) return
            pearlSwapped = true; return
        }
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            pearlSwapped = false
            if (PlayerUtils.useItem(yaw, pitch)) {
                MovementPacketSuppressor.suppressNext()
                onSuccess()
            }
        }
    }

    internal fun BloodBlink.tryAutoStart(player: Player) {
        if (!autoBlink || ScanUtils.currentRoom?.data?.type != RoomType.ENTRANCE) return
        KeyMapping.releaseAll()
        state = BloodBlink.State.INIT
        initSequence(player)
    }

    internal fun BloodBlink.initSequence(player: Player) {
        if (!bloodFound && DungeonListener.isBloodOpened) {
            finish("§cCannot blink - dungeon started without blood room"); return
        }
        forceSneak = true
        if (onlyOnGround && !player.onGround()) return
        if (startRoom == null) startRoom = ScanUtils.currentRoom
        val room = startRoom ?: return
        if (room.rotation == Rotations.NONE) return
        etherwarpToSlab(BloodBlink.State.AWAIT_SLAB1_LAND)
    }

    internal fun BloodBlink.exploreForBlood() {
        if (bloodFound) { state = BloodBlink.State.BLOOD_RUSH; return }
        if (serverTick % 40 >= EXPLORE_EXIT) return
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
            val p = mc.player ?: return@register
            val (angleYaw, _) = AngleUtils.calcAimAnglesBetween(Vec3(p.x, p.y, p.z), Vec3(MAP_CENTER.x, p.y, MAP_CENTER.z))
            val dx = (p.x - MAP_CENTER.x).toFloat()
            val dz = (p.z - MAP_CENTER.z).toFloat()
            val range = p.mainHandItem.getInstantTransmissionRange()
            lookAndEtherwarp(p, p.yRot, -90f, 8)
            lookAndEtherwarp(p, angleYaw, 0f, (sqrt(dx * dx + dz * dz) / range).roundToInt())
            explored = true; state = BloodBlink.State.AWAIT_EXPLORE_LAND
        }
    }

    internal fun BloodBlink.slabToBlood(player: Player) {
        forceSneak = true
        if (onlyOnGround && !player.onGround()) return
        if (startRoom == null) return
        if (explored && !bloodFound) { finish("§cCould not find blood room"); return }
        etherwarpToSlab(BloodBlink.State.AWAIT_SLAB2_LAND)
    }

    internal fun BloodBlink.bloodRush() {
        SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
        val started = DungeonListener.isBloodOpened || (startCountdown != START_COUNTDOWN_IDLE && startCountdown <= 0)
        if (!started) return
        if (serverTick % 40 >= 40 - MAP_LOAD_TIME) return
        startCountdown = START_COUNTDOWN_IDLE
        PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
            SwapManager.swapToItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
            val p = mc.player ?: return@register
            val room = startRoom ?: return@register
            val dir = voidDirection(room.roomComponents.first().x, room.roomComponents.first().z)
            lookAndEtherwarp(p, dir.horizontalDegrees(), 0f, 4)
            lookAndEtherwarp(p, p.yRot, 90f, 10)
            val predicted = Vec3(p.x, p.y, p.z).add(RotationUtils.rotateByDirection(dir, 0.0, 0.0, -48.0))
            val dx = (targetX + 0.5 - predicted.x).toFloat()
            val dz = (targetZ + 0.5 - predicted.z).toFloat()
            val (bloodYaw, _) = AngleUtils.calcAimAnglesFromDelta(dx.toDouble(), 0.0, dz.toDouble())
            val range = p.mainHandItem.getInstantTransmissionRange()
            lookAndEtherwarp(p, bloodYaw, 3f, (sqrt(dx * dx + dz * dz) / range).roundToInt())
            lookAndEtherwarp(p, p.yRot, -90f, 5)
            pearlDelay = 0; state = BloodBlink.State.PEARL_DOWN
        }
    }

    internal fun BloodBlink.pearlForward(player: Player) {
        if (forwardPearlDelay < FORWARD_PEARL_DELAY_TICKS) { forwardPearlDelay++; return }
        modMessage("§e[BB] Pearling forward into blood room")
        pearl(player.yRot, 0f) { finish("§aBlood Blink complete!") }
    }

    internal fun BloodBlink.pearlDown(player: Player) {
        if (pearlDelay < PEARL_DOWN_DELAY_TICKS) { pearlDelay++; return }
        if (pearlAttempts >= MAX_PEARL_RETRIES) {
            finish("§c[BB] Pearl failed after $MAX_PEARL_RETRIES attempts"); return
        }
        pearlAttempts++
        modMessage("§e[BB] Pearl attempt #$pearlAttempts")
        pearl(player.yRot, -90f) { pearlLandWait = 0; state = BloodBlink.State.AWAIT_PEARL_DOWN_LAND }
    }

    internal fun BloodBlink.awaitPearlDownTimeout() {
        pearlLandWait++
        if (pearlLandWait > PEARL_LAND_TIMEOUT_TICKS) {
            modMessage("§c[BB] Pearl timeout, retrying...")
            pearlDelay = PEARL_DOWN_DELAY_TICKS; state = BloodBlink.State.PEARL_DOWN
        }
    }

