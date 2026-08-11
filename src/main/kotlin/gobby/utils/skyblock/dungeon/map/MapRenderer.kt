package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.styledText
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.DOOR_THICKNESS
import gobby.utils.skyblock.dungeon.map.MapConstants.GAP
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.HALF_ROOM
import gobby.utils.skyblock.dungeon.map.MapConstants.START_X
import gobby.utils.skyblock.dungeon.map.MapConstants.START_Z
import gobby.utils.skyblock.dungeon.map.MapConstants.STEP
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier as ResourceLocation
import java.awt.Color

object MapRenderer {

    private val COL_NORMAL = Color(114, 67, 27)
    private val COL_PUZZLE = Color(176, 75, 213)
    private val COL_TRAP = Color(213, 126, 50)
    private val COL_BLOOD = Color(255, 0, 0)
    private val COL_ENTRANCE = Color(0, 123, 0)
    private val COL_FAIRY = Color(239, 126, 163)
    private val COL_RARE = Color(226, 226, 50)
    private val COL_BG = Color(0, 0, 0, 100)

    private val COL_DOOR_NORMAL = Color(114, 67, 27)
    private val COL_DOOR_WITHER = Color(0, 0, 0)
    private val COL_DOOR_BLOOD = Color(255, 0, 0)
    private val COL_DOOR_ENTRANCE = Color(0, 123, 0)
    private val COL_DOOR_OPENED = Color(114, 67, 27)
    private val COL_UNKNOWN = Color(64, 64, 64)

    private const val UNDISCOVERED_DIM = 0.6
    private const val DIM_ROUNDING = 0.5
    private const val UNKNOWN_DOOR_PENALTY = 100

    private val CHECKMARK_ID = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/white_checkmark")
    private val QUESTION_ID = ResourceLocation.fromNamespaceAndPath("gobbyclient", "textures/white_question_mark")
    private var checkmarkRegistered = false

    private const val SKIN_TEX_SIZE = 64
    private const val FACE_U = 8f
    private const val FACE_V = 8f
    private const val FACE_SIZE = 8
    private const val HAT_U = 40f
    private const val HAT_V = 8f
    private const val DEFAULT_HEAD_SIZE = 6
    private const val MIN_HEAD_SIZE = 3
    private const val NAME_SCALE = 0.35f
    private const val HEAD_ROTATION_OFFSET = 180.0

    private val HYPHENATED = mapOf(
        "Deathmite" to "Death-\nmite",
        "Withermancer" to "Wither-\nmancer",
        "Scaffolding" to "Scaff-\nolding",
        "Sarcophagus" to "Sarco-\nphagus",
        "Multicolored" to "Multi-\ncolored"
    )

    fun getMapSize(): Int = 6 * CELL_SIZE + 5 * GAP

    fun roomColor(data: RoomData): Color = when (data.type) {
        RoomType.NORMAL -> COL_NORMAL
        RoomType.PUZZLE -> COL_PUZZLE
        RoomType.TRAP -> COL_TRAP
        RoomType.BLOOD -> COL_BLOOD
        RoomType.ENTRANCE -> COL_ENTRANCE
        RoomType.FAIRY -> COL_FAIRY
        RoomType.RARE, RoomType.CHAMPION -> COL_RARE
    }

    private fun roomFill(data: RoomData, discovered: Boolean, legit: Boolean): Int = when {
        discovered || data.type == RoomType.ENTRANCE -> roomColor(data).rgb
        legit -> COL_UNKNOWN.rgb
        else -> dim(roomColor(data)).rgb
    }

    private fun dim(color: Color): Color =
        Color((color.red * UNDISCOVERED_DIM + DIM_ROUNDING).toInt(), (color.green * UNDISCOVERED_DIM + DIM_ROUNDING).toInt(), (color.blue * UNDISCOVERED_DIM + DIM_ROUNDING).toInt())

    private data class DoorRoom(val data: RoomData, val seen: Boolean)

    private fun doorFill(grid: Array<MapTile>, discovered: BooleanArray, legit: Boolean, col: Int, row: Int, type: DoorType, opened: Boolean): Int = when (type) {
        DoorType.WITHER -> if (opened) COL_DOOR_OPENED.rgb else COL_DOOR_WITHER.rgb
        DoorType.BLOOD -> if (opened) COL_DOOR_OPENED.rgb else COL_DOOR_BLOOD.rgb
        DoorType.ENTRANCE -> COL_DOOR_ENTRANCE.rgb
        DoorType.NORMAL -> normalDoorFill(grid, discovered, legit, col, row)
    }

    private fun normalDoorFill(grid: Array<MapTile>, discovered: BooleanArray, legit: Boolean, col: Int, row: Int): Int {
        val chosen = doorRooms(grid, discovered, col, row)
            .maxByOrNull { it.data.type.ordinal - if (it.seen) 0 else UNKNOWN_DOOR_PENALTY }
            ?: return COL_DOOR_NORMAL.rgb
        return roomFill(chosen.data, chosen.seen, legit)
    }

    private fun doorRooms(grid: Array<MapTile>, discovered: BooleanArray, col: Int, row: Int): List<DoorRoom> {
        val offsets = if (col and 1 == 1) listOf(-1 to 0, 1 to 0) else listOf(0 to -1, 0 to 1)
        return offsets.mapNotNull { (dc, dr) ->
            val c = col + dc
            val r = row + dr
            if (!MapGrid.inRange(c, r)) return@mapNotNull null
            val idx = MapGrid.index(c, r)
            (grid[idx] as? MapTile.Room)?.let { DoorRoom(it.data, discovered[idx]) }
        }
    }

    private fun neighbourCells(col: Int, row: Int): List<Pair<Int, Int>> {
        val colOdd = col and 1 == 1
        val rowOdd = row and 1 == 1
        return when {
            colOdd && rowOdd -> listOf(col - 1 to row - 1, col + 1 to row - 1, col - 1 to row + 1, col + 1 to row + 1)
            colOdd -> listOf(col - 1 to row, col + 1 to row)
            else -> listOf(col to row - 1, col to row + 1)
        }
    }

    private fun neighbourDiscovered(discovered: BooleanArray, col: Int, row: Int): Boolean =
        neighbourCells(col, row).any { (c, r) -> MapGrid.inRange(c, r) && discovered[MapGrid.index(c, r)] }

    private fun neighbourAll(discovered: BooleanArray, col: Int, row: Int): Boolean =
        neighbourCells(col, row).all { (c, r) -> MapGrid.inRange(c, r) && discovered[MapGrid.index(c, r)] }

    private val FRONT_DIRS = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)

    private fun entranceFrontCell(grid: Array<MapTile>): Int {
        val entrance = grid.indices.firstOrNull { (grid[it] as? MapTile.Room)?.data?.type == RoomType.ENTRANCE } ?: return -1
        val ec = entrance % GRID_SIZE
        val er = entrance / GRID_SIZE
        return FRONT_DIRS.firstNotNullOfOrNull { (dc, dr) ->
            val roomCol = ec + 2 * dc
            val roomRow = er + 2 * dr
            if (MapGrid.inRange(ec + dc, er + dr) && grid.getOrNull(MapGrid.index(ec + dc, er + dr)) is MapTile.Door &&
                MapGrid.inRange(roomCol, roomRow) && grid.getOrNull(MapGrid.index(roomCol, roomRow)) is MapTile.Room
            ) MapGrid.index(roomCol, roomRow) else null
        } ?: -1
    }

    fun drawMap(
        ctx: GuiGraphicsExtractor,
        grid: Array<MapTile>,
        checkmarks: Array<MapCheckmark>,
        discovered: BooleanArray,
        openedDoors: BooleanArray,
        legitMode: Boolean,
        renderNames: Boolean,
        nameScale: Int,
        renderCheckmarks: Boolean,
        renderHeads: Boolean,
        headScale: Int
    ) {
        val size = getMapSize()
        ctx.fill(0, 0, size, size, COL_BG.rgb)
        val forcedFront = entranceFrontCell(grid)

        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val tile = grid[row * GRID_SIZE + col]
                val px = (col / 2) * STEP
                val py = (row / 2) * STEP
                val colOdd = col and 1 == 1
                val rowOdd = row and 1 == 1

                when (tile) {
                    is MapTile.Room -> {
                        val idx = row * GRID_SIZE + col
                        val question = checkmarks[idx] == MapCheckmark.UNKNOWN || (idx == forcedFront && !discovered[idx])
                        if (!legitMode || discovered[idx] || question || tile.data.type == RoomType.ENTRANCE) {
                            ctx.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, roomFill(tile.data, discovered[idx], legitMode))
                            if (renderCheckmarks && question && !discovered[idx]) drawCheckmark(ctx, MapCheckmark.UNKNOWN, px + CELL_SIZE / 2, py + CELL_SIZE / 2)
                        }
                    }
                    is MapTile.Connection -> if (!legitMode || neighbourAll(discovered, col, row)) {
                        val c = roomFill(tile.data, neighbourDiscovered(discovered, col, row), legitMode)
                        when {
                            colOdd && !rowOdd -> ctx.fill(px + CELL_SIZE, py, px + CELL_SIZE + GAP, py + CELL_SIZE, c)
                            !colOdd && rowOdd -> ctx.fill(px, py + CELL_SIZE, px + CELL_SIZE, py + CELL_SIZE + GAP, c)
                            colOdd && rowOdd  -> ctx.fill(px + CELL_SIZE, py + CELL_SIZE, px + CELL_SIZE + GAP, py + CELL_SIZE + GAP, c)
                        }
                    }
                    else -> {}
                }
            }
        }

        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val tile = grid[row * GRID_SIZE + col]
                if (tile !is MapTile.Door) continue
                if (legitMode && tile.type != DoorType.ENTRANCE && !neighbourDiscovered(discovered, col, row)) continue
                val px = (col / 2) * STEP
                val py = (row / 2) * STEP
                val c = doorFill(grid, discovered, legitMode, col, row, tile.type, openedDoors[row * GRID_SIZE + col])
                val inset = (CELL_SIZE - DOOR_THICKNESS) / 2

                if (col and 1 == 1) {
                    ctx.fill(px + CELL_SIZE, py + inset, px + CELL_SIZE + GAP, py + CELL_SIZE - inset, c)
                } else {
                    ctx.fill(px + inset, py + CELL_SIZE, px + CELL_SIZE - inset, py + CELL_SIZE + GAP, c)
                }
            }
        }

        val processedRooms = mutableSetOf<RoomData>()
        for (row in 0 until GRID_SIZE step 2) {
            for (col in 0 until GRID_SIZE step 2) {
                val tile = grid[row * GRID_SIZE + col]
                if (tile !is MapTile.Room) continue
                if (tile.data in processedRooms) continue
                processedRooms.add(tile.data)

                val cells = mutableListOf<Pair<Int, Int>>()
                for (r in 0 until GRID_SIZE step 2) {
                    for (c in 0 until GRID_SIZE step 2) {
                        val t = grid[r * GRID_SIZE + c]
                        if (t is MapTile.Room && t.data === tile.data) cells.add(c to r)
                    }
                }

                val visible = if (legitMode) cells.filter { (c, r) -> discovered[r * GRID_SIZE + c] } else cells
                if (visible.isEmpty()) continue

                if (renderCheckmarks) {
                    var bestCheckmark = MapCheckmark.NONE
                    for ((c, r) in visible) {
                        val cm = checkmarks[r * GRID_SIZE + c]
                        if (cm != MapCheckmark.UNKNOWN && cm.ordinal > bestCheckmark.ordinal) bestCheckmark = cm
                    }
                    if (bestCheckmark != MapCheckmark.NONE) {
                        val (cx, cy) = getRoomCheckmarkCenter(visible, tile.data.shape)
                        drawCheckmark(ctx, bestCheckmark, cx, cy)
                    }
                }

                if (renderNames && (!legitMode || visible.any { (c, r) -> discovered[r * GRID_SIZE + c] })) {
                    val (nameCol, nameRow) = visible.first()
                    val namePx = (nameCol / 2) * STEP
                    val namePy = (nameRow / 2) * STEP
                    drawRoomName(ctx, tile.data, namePx, namePy, nameScale)
                }
            }
        }

        if (renderHeads) drawPlayers(ctx, headScale)
    }

    private fun drawRoomName(ctx: GuiGraphicsExtractor, data: RoomData, px: Int, py: Int, scalePercent: Int) {
        val tr = mc.font
        val name = data.name
        if (name.isEmpty()) return

        val displayName = HYPHENATED[name] ?: name
        val lines = displayName.split(" ", "\n")
        val styledLines = lines.map { styledText(it) }
        val totalHeight = tr.lineHeight * lines.size
        val scale = NAME_SCALE * (scalePercent / 100f)

        ctx.pose().pushMatrix()
        ctx.pose().translate(px + CELL_SIZE / 2f, py + CELL_SIZE / 2f)
        ctx.pose().scale(scale, scale)

        val startY = -(totalHeight / 2)
        for (i in styledLines.indices) {
            val tw = tr.width(styledLines[i])
            ctx.text(tr, styledLines[i], -tw / 2, startY + i * tr.lineHeight, Color.WHITE.rgb, true)
        }
        ctx.pose().popMatrix()
    }

    /** Finds the pixel center for a room's checkmark. For L-shapes, uses the bend cell. */
    private fun getRoomCheckmarkCenter(cells: List<Pair<Int, Int>>, shape: String): Pair<Int, Int> {
        if (shape == "L" && cells.size == 3) {
            for ((c, r) in cells) {
                val neighbors = cells.count { (c2, r2) ->
                    (c2 != c || r2 != r) && (kotlin.math.abs(c - c2) + kotlin.math.abs(r - r2) == 2)
                }
                if (neighbors == 2) {
                    return ((c / 2) * STEP + CELL_SIZE / 2) to ((r / 2) * STEP + CELL_SIZE / 2)
                }
            }
        }

        val cx = cells.sumOf { (c, _) -> (c / 2) * STEP + CELL_SIZE / 2 } / cells.size
        val cy = cells.sumOf { (_, r) -> (r / 2) * STEP + CELL_SIZE / 2 } / cells.size
        return cx to cy
    }

    /** Draws checkmark centered at the given pixel position */
    private fun drawCheckmark(ctx: GuiGraphicsExtractor, checkmark: MapCheckmark, centerX: Int, centerY: Int) {
        registerCheckmarkTexture()
        val checkSize = (CELL_SIZE * 0.5f).toInt()
        val cx = centerX - checkSize / 2
        val cy = centerY - checkSize / 2

        val id = if (checkmark == MapCheckmark.UNKNOWN) QUESTION_ID else CHECKMARK_ID
        val tint = when (checkmark) {
            MapCheckmark.GREEN -> Color(0, 255, 0).rgb
            MapCheckmark.FAILED -> Color(255, 0, 0).rgb
            else -> Color.WHITE.rgb
        }

        ctx.blit(
            RenderPipelines.GUI_TEXTURED, id,
            cx, cy, 0f, 0f, checkSize, checkSize, checkSize, checkSize, tint
        )
    }

    private fun registerCheckmarkTexture() {
        if (checkmarkRegistered) return
        registerTexture(CHECKMARK_ID)
        registerTexture(QUESTION_ID)
        checkmarkRegistered = true
    }

    private fun registerTexture(id: ResourceLocation) {
        try {
            val stream = MapRenderer::class.java.classLoader.getResourceAsStream(
                "assets/${id.namespace}/${id.path}.png"
            ) ?: return
            val image = NativeImage.read(stream)
            mc.textureManager.register(id, DynamicTexture({ id.toString() }, image))
            stream.close()
        } catch (_: Exception) {}
    }

    private fun drawPlayers(ctx: GuiGraphicsExtractor, headScalePercent: Int) {
        val world = mc.level ?: return
        val self = mc.player ?: return
        val teammateNames = DungeonListener.teammates.keys
        val headSize = maxOf(MIN_HEAD_SIZE, (DEFAULT_HEAD_SIZE * headScalePercent / 100f).toInt())

        for (player in world.players()) {
            val name = player.name.string
            val isSelf = player == self
            if (!isSelf && name !in teammateNames) continue

            val gridC = (player.x - START_X) / HALF_ROOM.toDouble()
            val gridR = (player.z - START_Z) / HALF_ROOM.toDouble()
            if (gridC < -3.0 || gridC > 13.0 || gridR < -3.0 || gridR > 13.0) continue

            val pixelX = (gridC / 2.0 * STEP + CELL_SIZE / 2.0).toInt()
            val pixelY = (gridR / 2.0 * STEP + CELL_SIZE / 2.0).toInt()
            val hx = pixelX - headSize / 2
            val hy = pixelY - headSize / 2

            val entry = mc.connection?.getPlayerInfo(player.uuid) ?: continue
            val skinTexture = entry.skin.body().texturePath()
            if (skinTexture != null) {
                val scale = headSize / FACE_SIZE.toFloat()
                val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
                val angle = Math.toRadians(player.getViewYRot(partial) - HEAD_ROTATION_OFFSET).toFloat()
                ctx.pose().pushMatrix()
                ctx.pose().translate(pixelX.toFloat(), pixelY.toFloat())
                ctx.pose().rotate(angle)
                ctx.pose().scale(scale, scale)
                ctx.blit(RenderPipelines.GUI_TEXTURED, skinTexture, -FACE_SIZE / 2, -FACE_SIZE / 2, FACE_U, FACE_V, FACE_SIZE, FACE_SIZE, SKIN_TEX_SIZE, SKIN_TEX_SIZE, -1)
                ctx.blit(RenderPipelines.GUI_TEXTURED, skinTexture, -FACE_SIZE / 2, -FACE_SIZE / 2, HAT_U, HAT_V, FACE_SIZE, FACE_SIZE, SKIN_TEX_SIZE, SKIN_TEX_SIZE, -1)
                ctx.pose().popMatrix()
            } else {
                ctx.fill(hx - 1, hy - 1, hx + headSize + 1, hy + headSize + 1, Color.BLACK.rgb)
                val c = if (isSelf) Color(0, 220, 0).rgb else Color(0, 180, 220).rgb
                ctx.fill(hx, hy, hx + headSize, hy + headSize, c)
            }
        }
    }
}
