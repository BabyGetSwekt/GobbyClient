package gobby.features.dungeons

internal typealias BrushBlockData = MutableMap<String, MutableMap<String, MutableList<String>>>

internal fun blockDataMap(): BrushBlockData = mutableMapOf()

internal class FavoriteBlocks {

    private var blocks: MutableSet<String>? = null

    var showFavorites: Boolean = false

    val favorites: MutableSet<String>
        get() = blocks ?: mutableSetOf<String>().also { blocks = it }
}
