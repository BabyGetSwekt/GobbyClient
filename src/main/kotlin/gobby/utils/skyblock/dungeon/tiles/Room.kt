package gobby.utils.skyblock.dungeon.tiles

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import gobby.utils.VecUtils
import net.minecraft.util.math.BlockPos
import java.lang.reflect.Type

/**
 * Contents of this file are based on OdinClient and the work of odtheking under BSD 3-Clause License.
 * All the credits go to him.
 * @author odtheking (https://github.com/odtheking/)
 * License: https://github.com/odtheking/Odin/blob/main/LICENSE
 * Original source: https://github.com/odtheking/Odin/blob/d0dd8febc14b5b122202791098eff879aefcfc2e/src/main/kotlin/me/odinmain/utils/skyblock/dungeon/tiles/Room.kt
 */
data class Room(
    var rotation: Rotations = Rotations.NONE,
    var data: RoomData,
    var clayPos: BlockPos = BlockPos(0, 0, 0),
    val roomComponents: MutableSet<RoomComponent>,
)

data class RoomComponent(val x: Int, val z: Int, val core: Int = 0) {
    val vec2 = VecUtils.Vec2(x, z)
    val blockPos = BlockPos(x, 70, z)
}

data class RoomData(
    val name: String, val type: RoomType, val cores: List<Int>,
    val crypts: Int, val secrets: Int, val trappedChests: Int,
)

class RoomDataDeserializer : JsonDeserializer<RoomData> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): RoomData {
        val jsonObject = json?.asJsonObject
        val name = jsonObject?.get("name")?.asString ?: ""
        val type = context?.deserialize(jsonObject?.get("type"), RoomType::class.java) ?: RoomType.NORMAL
        val coresType = object : TypeToken<List<Int>>() {}.type
        val cores = context?.deserialize<List<Int>>(jsonObject?.get("cores"), coresType).orEmpty()
        val crypts = jsonObject?.get("crypts")?.asInt ?: 0
        val secrets = jsonObject?.get("secrets")?.asInt ?: 0
        val trappedChests = jsonObject?.get("trappedChests")?.asInt ?: 0

        return RoomData(name, type, cores, crypts, secrets, trappedChests)
    }
}
