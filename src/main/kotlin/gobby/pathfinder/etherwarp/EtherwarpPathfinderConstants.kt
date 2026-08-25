package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.DungeonRoomCoordinates
import kotlin.math.max

internal const val DIRECT_CACHE_CELL = -1
internal const val DIRECT_CACHE_REVISION = 0L
internal const val MAX_LANDING_RISE = 4

internal fun maxLandingY(kind: EtherwarpKind, startY: Int, goalY: Int): Int =
    if (!kind.sneak) Int.MAX_VALUE
    else max(max(startY, goalY), DungeonRoomCoordinates.ROOM_INTERIOR_TOP_Y) + MAX_LANDING_RISE
