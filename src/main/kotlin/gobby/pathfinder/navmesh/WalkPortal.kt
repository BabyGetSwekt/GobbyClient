package gobby.pathfinder.navmesh

import net.minecraft.world.phys.Vec3

data class WalkPortal(
    val from: WalkPolygon,
    val to: WalkPolygon,
    val left: Vec3,
    val right: Vec3,
    val isHeightStep: Boolean
) {
    fun opposite(here: WalkPolygon): WalkPolygon = if (here === from) to else from
}
