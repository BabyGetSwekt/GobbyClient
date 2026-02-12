package gobby.utils.skyblock.dungeon

import com.google.gson.GsonBuilder
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import gobby.Gobbyclient
import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.utils.ChatUtils.devMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.equalsOneOf
import gobby.utils.Utils.posX
import gobby.utils.Utils.posZ
import gobby.utils.VecUtils.Vec2
import gobby.utils.skyblock.dungeon.tiles.Room
import gobby.utils.skyblock.dungeon.tiles.RoomComponent
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomDataDeserializer
import gobby.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.block.Blocks
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.chunk.WorldChunk
import java.io.FileNotFoundException

/**
 * Contents of this file are based on OdinClient and the work of odtheking under BSD 3-Clause License.
 * All the credits go to him.
 * @author odtheking (https://github.com/odtheking/)
 * License: https://github.com/odtheking/OdinFabric/blob/main/LICENSE
 * Original source: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/dungeon/ScanUtils.kt
 */
object ScanUtils {

    private const val ROOM_SIZE_SHIFT = 5
    private const val START = -185
    private val roomList: Set<RoomData> = loadRoomData()
    private var lastRoomPos: Vec2 = Vec2(0, 0)

    val coreToRoomData: Map<Int, RoomData> =
        roomList.flatMap { room -> room.cores.map { core -> core to room } }.toMap()
    private val horizontals = Direction.entries.filter { it.axis.isHorizontal }
    private val mutableBlockPos = BlockPos.Mutable()

    var currentRoom: Room? = null
        private set
    var passedRooms: MutableSet<Room> = mutableSetOf()
        private set

    private fun loadRoomData(): Set<RoomData> {
        return try {
            GsonBuilder()
                .registerTypeAdapter(RoomData::class.java, RoomDataDeserializer())
                .create().fromJson(
                    (ScanUtils::class.java.getResourceAsStream("/rooms.json") ?: throw FileNotFoundException()).bufferedReader(),
                    object : TypeToken<Set<RoomData>>() {}.type
                )
        } catch (e: Exception) {
            handleRoomDataError(e)
            setOf()
        }
    }

    private fun handleRoomDataError(e: Exception) {
        when (e) {
            is JsonSyntaxException -> println("Error parsing room data.")
            is JsonIOException -> println("Error reading room data.")
            is FileNotFoundException -> println("Room data not found, something went wrong! Please report this!")
            else -> {
                println("Unknown error while reading room data.")
                println(e.message)
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (mc.world == null || mc.player == null) return
        if ((!inDungeons && !mc.isInSingleplayer) || inBoss) {
            currentRoom?.let { Gobbyclient.EVENT_MANAGER.publish(RoomEnterEvent(null)) }
            return
        } // We want the current room to register as null if we are not in a dungeon

        val roomCenter = getRoomCenter(posX.toInt(), posZ.toInt())
        if (roomCenter == lastRoomPos && mc.isInSingleplayer) return
        lastRoomPos = roomCenter

        passedRooms.find { room -> room.roomComponents.any { it.vec2 == roomCenter } }?.let { cached ->
            if (currentRoom?.roomComponents?.none { it.vec2 == roomCenter } == true) Gobbyclient.EVENT_MANAGER.publish(RoomEnterEvent(cached))
            return
        }

        scanRoom(roomCenter)?.let { room -> if (room.rotation != Rotations.NONE) Gobbyclient.EVENT_MANAGER.publish(RoomEnterEvent(room)) } ?: devMessage("${getCore(roomCenter)} at $roomCenter is not a registered room core (last registered visited room is ${currentRoom?.data?.name})")
    }

    private fun updateRotation(room: Room, roomHeight: Int) {
        if (room.data.name == "Fairy") {
            room.clayPos =
                room.roomComponents.firstOrNull()?.let { BlockPos(it.x - 15, roomHeight, it.z - 15) } ?: return
            room.rotation = Rotations.SOUTH
            return
        }

        val world = mc.world ?: return
        room.rotation = Rotations.entries.dropLast(1).find { rotation ->
            room.roomComponents.any { component ->
                BlockPos(component.x + rotation.x, roomHeight, component.z + rotation.z).let { blockPos ->
                    world.getBlockState(blockPos)?.block == Blocks.BLUE_TERRACOTTA && (room.roomComponents.size == 1 || horizontals.all { facing ->
                        world.getBlockState(
                            blockPos.add(
                                (if (facing.axis == Direction.Axis.X) facing.offsetX else 0),
                                0,
                                (if (facing.axis == Direction.Axis.Z) facing.offsetZ else 0)
                            )
                        )?.block?.equalsOneOf(Blocks.AIR, Blocks.BLUE_TERRACOTTA) == true
                    }).also { isCorrectClay -> if (isCorrectClay) room.clayPos = blockPos }
                }
            }
        } ?: Rotations.NONE
    }


    fun scanRoom(vec2: Vec2): Room? {
        val world = mc.world ?: return null
        val chunk = world.getChunk(vec2.x shr 4, vec2.z shr 4)
        val roomHeight = getTopLayerOfRoom(vec2, chunk)
        return getCoreAtHeight(vec2, roomHeight, chunk).let { core ->
            coreToRoomData[core]?.let { roomData ->
                Room(data = roomData, roomComponents = findRoomComponentsRecursively(vec2, roomData.cores, roomHeight, world))
            }?.apply { updateRotation(this, roomHeight) }
        }
    }


    private fun findRoomComponentsRecursively(vec2: Vec2, cores: List<Int>, roomHeight: Int, world: ClientWorld, visited: MutableSet<Vec2> = mutableSetOf(), tiles: MutableSet<RoomComponent> = mutableSetOf()): MutableSet<RoomComponent> {
        if (vec2 in visited) return tiles else visited.add(vec2)

        val chunk = world.getChunk(vec2.x shr 4, vec2.z shr 4)
        val core = getCoreAtHeight(vec2, roomHeight, chunk)
        if (core !in cores) return tiles

        tiles.add(RoomComponent(vec2.x, vec2.z, core))
        horizontals.forEach { facing ->
            findRoomComponentsRecursively(
                Vec2(
                    vec2.x + ((if (facing.axis == Direction.Axis.X) facing.offsetX else 0) shl ROOM_SIZE_SHIFT),
                    vec2.z + ((if (facing.axis == Direction.Axis.Z) facing.offsetZ else 0) shl ROOM_SIZE_SHIFT)
                ), cores, roomHeight, world, visited, tiles
            )
        }
        return tiles
    }

    fun getRoomCenter(posX: Int, posZ: Int): Vec2 {
        val roomX = (posX - START + (1 shl (ROOM_SIZE_SHIFT - 1))) shr ROOM_SIZE_SHIFT
        val roomZ = (posZ - START + (1 shl (ROOM_SIZE_SHIFT - 1))) shr ROOM_SIZE_SHIFT
        return Vec2((roomX shl ROOM_SIZE_SHIFT) + START, (roomZ shl ROOM_SIZE_SHIFT) + START)
    }


    fun getCore(vec2: Vec2): Int {
        val world = mc.world ?: return 0
        val chunk = world.getChunk(vec2.x shr 4, vec2.z shr 4)
        return getCoreAtHeight(vec2, getTopLayerOfRoom(vec2, chunk), chunk)
    }

    private fun getCoreAtHeight(vec2: Vec2, roomHeight: Int, chunk: WorldChunk): Int {
        val sb = StringBuilder(150)
        val clampedHeight = roomHeight.coerceIn(11..140)
        sb.append(CharArray(140 - clampedHeight) { '0' })
        var bedrock = 0

        for (y in clampedHeight downTo 12) {
            mutableBlockPos.set(vec2.x, y, vec2.z)
            val block = chunk.getBlockState(mutableBlockPos)?.block
            if (block == Blocks.AIR && bedrock >= 2 && y < 69) {
                sb.append(CharArray(y - 11) { '0' })
                break
            }

            if (block == Blocks.BEDROCK) bedrock++
            else {
                bedrock = 0
                if (block?.equalsOneOf(Blocks.OAK_PLANKS, Blocks.TRAPPED_CHEST, Blocks.CHEST) == true) continue
            }
            sb.append(block)
        }
        return sb.toString().hashCode()
    }

    fun getTopLayerOfRoom(vec2: Vec2, chunk: WorldChunk): Int {
        for (y in 160 downTo 12) {
            mutableBlockPos.set(vec2.x, y, vec2.z)
            val blockState = chunk.getBlockState(mutableBlockPos)
            if (blockState?.isAir == false) return if (blockState.block == Blocks.GOLD_BLOCK) y - 1 else y
        }
        return 0
    }



    @SubscribeEvent
    fun enterDungeonRoom(event: RoomEnterEvent) {
        currentRoom = event.room
        val room = event.room ?: return
        if (passedRooms.none { it.data.name == room.data.name }) {
            passedRooms.add(room)
        }
        devMessage("${room.data.name} - ${room.rotation} || clay: ${room.clayPos}")
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        passedRooms.clear()
        currentRoom = null
        lastRoomPos = Vec2(0, 0)
    }
}
