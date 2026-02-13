package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.ChatReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.partyCoordRegex
import gobby.utils.ChatUtils.publicCoordRegex
import gobby.utils.render.RenderBeacon
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

object CoordWaypoints {

    private var waypointCoords: Vec3i = Vec3i(0, 0, 0)

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (mc.player == null || mc.world == null || !GobbyConfig.renderCoordBeacons) return
        val msg = event.message

        val match = publicCoordRegex.matchEntire(msg) ?: partyCoordRegex.matchEntire(msg)
        if (match != null) {
            val x = match.groups[2]?.value?.toIntOrNull() ?: return
            val y = match.groups[3]?.value?.toIntOrNull() ?: return
            val z = match.groups[4]?.value?.toIntOrNull() ?: return

            waypointCoords = Vec3i(x, y, z)

            // DEBUG, DELETE LATER @forceWarning
            modMessage("New waypoint set: ${waypointCoords.x}, ${waypointCoords.y}, ${waypointCoords.z} for 30 seconds")
            RenderBeacon.addBeacon(BlockPos(waypointCoords.x, waypointCoords.y, waypointCoords.z), GobbyConfig.turtleEspColor, "Waypoint")
        }
    }
}