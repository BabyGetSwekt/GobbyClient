package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.DependencyValidatedRoute
import gobby.pathfinder.navigation.PreparedDungeonRouteCache
import gobby.utils.skyblock.dungeon.map.MapTile

internal object DungeonAtlasRouteAcceptance {
    fun accept(route: DependencyValidatedRoute, context: DungeonPathSearchContext, allowedCells: Set<Int>, grid: Array<MapTile>): List<EtherwarpNode> {
        if (context.kind == EtherwarpKind.ETHERWARP) {
            EtherwarpHopField.request(context.goalBlock, context.kind.searchRange(context.config), context.cache, allowedCells, grid)
        }
        pathLog { "atlas route accepted hops=${route.nodes.size - 1}" }
        context.goalCell?.let { target ->
            if (route.edges.size == route.nodes.lastIndex) PreparedDungeonRouteCache.putValidated(route, context.goalBlock, target, context.enterRoom, context.kind, context.config, context.cache, context.mapRevision)
        }
        return route.nodes
    }
}
