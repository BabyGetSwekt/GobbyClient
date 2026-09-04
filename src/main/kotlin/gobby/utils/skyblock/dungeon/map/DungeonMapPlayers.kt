package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblock.dungeon.map.MapConstants.ROOM_STRIDE
import gobby.utils.skyblock.dungeon.map.MapConstants.START_X
import gobby.utils.skyblock.dungeon.map.MapConstants.START_Z
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.GameType
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import java.util.Locale
import java.util.UUID

object DungeonMapPlayers {

    private const val DECORATION_PIXEL_BIAS = 126.0
    private const val DECORATION_ROTATION_STEPS = 16.0
    private const val HEAD_YAW_OFFSET = 180.0
    private val RIGHT_ANGLES = listOf(-180.0, -90.0, 0.0, 90.0, 180.0)

    data class Head(val uuid: UUID, val roomCol: Double, val roomRow: Double, val yaw: Double)

    private data class Sample(val col: Double, val row: Double, val yaw: Double, val time: Long)

    private class Tracked {
        var current: Sample? = null
        var previous: Sample? = null

        fun record(sample: Sample) {
            if (current != null) previous = current
            current = sample
        }
    }

    private val tracked = LinkedHashMap<UUID, Tracked>()
    private var rotationOffset = 0.0
    private var rotationCalibrated = false

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        tracked.clear()
        rotationOffset = 0.0
        rotationCalibrated = false
    }

    fun sampleMarkers() {
        val decorations = DungeonMapSource.savedData?.decorations?.toList() ?: return
        applyDecorations(decorations)
    }

    fun heads(partialTick: Float): List<Head> = tracked.entries.mapNotNull { (uuid, player) ->
        entityHead(uuid, partialTick) ?: sampledHead(uuid, player)
    }

    fun refreshRoster() {
        val local = mc.player ?: return
        val connection = mc.connection ?: return
        val localUuid = local.gameProfile.id
        val ordered = LinkedHashMap<UUID, Tracked>()
        ordered[localUuid] = tracked[localUuid] ?: Tracked()
        tabOrdered(connection.listedOnlinePlayers).forEach { info ->
            val uuid = info.profile.id
            if (uuid == localUuid || DungeonListener.teammateNameOf(tabLine(info)) == null) return@forEach
            ordered[uuid] = tracked[uuid] ?: Tracked()
        }
        tracked.keys.retainAll(ordered.keys)
        tracked.putAll(ordered)
    }

    private fun tabOrdered(players: Collection<PlayerInfo>): List<PlayerInfo> = players.sortedWith(
        compareBy(
            { if (it.gameMode == GameType.SPECTATOR) 1 else 0 },
            { -it.tabListOrder },
            { it.team?.name.orEmpty() },
            { it.profile.name.lowercase(Locale.ROOT) }
        )
    )

    private fun tabLine(info: PlayerInfo): String = info.tabListDisplayName?.string.orEmpty()

    private fun applyDecorations(decorations: List<MapDecoration>) {
        val icons = decorations.filter { it.type == MapDecorationTypes.FRAME || isPlayerIcon(it) }
        if (icons.isEmpty()) return
        calibrateRotation(icons)
        if (tracked.size != icons.size) return
        val players = tracked.values.iterator()
        if (!players.hasNext()) return
        players.next()
        icons.forEach { icon ->
            if (icon.type == MapDecorationTypes.FRAME) return@forEach
            if (!players.hasNext()) return
            record(players.next(), icon)
        }
    }

    private fun isPlayerIcon(decoration: MapDecoration): Boolean = decoration.type !in NON_PLAYER_ICONS

    private fun record(player: Tracked, decoration: MapDecoration) {
        val (col, row) = MapCheckmarks.roomCoordsFromMapPixel(
            decorationPixel(decoration.x), decorationPixel(decoration.y)
        ) ?: return
        player.record(Sample(col, row, wrapDegrees(rawRotation(decoration) + rotationOffset), System.currentTimeMillis()))
    }

    private fun decorationPixel(raw: Byte): Double = (raw + DECORATION_PIXEL_BIAS) * 0.5

    private fun rawRotation(decoration: MapDecoration): Double =
        -((decoration.rot / DECORATION_ROTATION_STEPS) * 360.0 + 90.0)

    private fun calibrateRotation(icons: List<MapDecoration>) {
        if (rotationCalibrated) return
        val local = mc.player ?: return
        val frame = icons.firstOrNull { it.type == MapDecorationTypes.FRAME } ?: return
        val delta = wrapDegrees(local.getViewYRot(1f) - HEAD_YAW_OFFSET - rawRotation(frame))
        rotationOffset = RIGHT_ANGLES.minByOrNull { kotlin.math.abs(wrapDegrees(delta - it)) } ?: 0.0
        rotationCalibrated = true
    }

    private fun entityHead(uuid: UUID, partialTick: Float): Head? {
        val entity = mc.level?.getPlayerByUUID(uuid) as? AbstractClientPlayer ?: return null
        if (entity.isDeadOrDying || entity.isRemoved) return null
        val delta = partialTick.toDouble()
        return Head(
            uuid,
            (Mth.lerp(delta, entity.xo, entity.x) - START_X) / ROOM_STRIDE,
            (Mth.lerp(delta, entity.zo, entity.z) - START_Z) / ROOM_STRIDE,
            entity.getViewYRot(partialTick) - HEAD_YAW_OFFSET
        )
    }

    private fun sampledHead(uuid: UUID, player: Tracked): Head? {
        val current = player.current ?: return null
        val previous = player.previous ?: return Head(uuid, current.col, current.row, current.yaw)
        val span = current.time - previous.time
        if (span <= 0L) return Head(uuid, current.col, current.row, current.yaw)
        val factor = ((System.currentTimeMillis() - current.time).toDouble() / span).coerceIn(0.0, 1.0)
        return Head(
            uuid,
            Mth.lerp(factor, previous.col, current.col),
            Mth.lerp(factor, previous.row, current.row),
            previous.yaw + wrapDegrees(current.yaw - previous.yaw) * factor
        )
    }

    private fun wrapDegrees(angle: Double): Double {
        var wrapped = angle % 360.0
        if (wrapped >= 180.0) wrapped -= 360.0
        if (wrapped < -180.0) wrapped += 360.0
        return wrapped
    }

    private val NON_PLAYER_ICONS = setOf(
        MapDecorationTypes.FRAME,
        MapDecorationTypes.TRIAL_CHAMBERS,
        MapDecorationTypes.WHITE_BANNER,
        MapDecorationTypes.GREEN_BANNER,
        MapDecorationTypes.RED_X
    )
}
