package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.ClickGUITheme
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
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.awt.Color

object MapRenderer {

    private val COL_NORMAL = Color(107, 58, 17)
    private val COL_PUZZLE = Color(117, 0, 133)
    private val COL_TRAP = Color(216, 127, 51)
    private val COL_BLOOD = Color(255, 0, 0)
    private val COL_ENTRANCE = Color(20, 133, 0)
    private val COL_FAIRY = Color(224, 0, 224)
    private val COL_RARE = Color(255, 203, 89)
    private val COL_BG = Color(0, 0, 0, 100)

    private val COL_DOOR_NORMAL = Color(92, 52, 14)
    private val COL_DOOR_WITHER = Color(0, 0, 0)
    private val COL_DOOR_BLOOD = Color(255, 0, 0)
    private val COL_DOOR_ENTRANCE = Color(20, 133, 0)

    private val CHECKMARK_ID = Identifier.of("gobbyclient", "textures/dungeon/room_cleared")
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

    private fun doorColor(type: DoorType): Color = when (type) {
        DoorType.NORMAL -> COL_DOOR_NORMAL
        DoorType.WITHER -> COL_DOOR_WITHER
        DoorType.BLOOD -> COL_DOOR_BLOOD
        DoorType.ENTRANCE -> COL_DOOR_ENTRANCE
    }

    fun drawMap(
        ctx: DrawContext,
        grid: Array<MapTile>,
        checkmarks: Array<MapCheckmark>,
        renderNames: Boolean,
        nameScale: Int,
        renderCheckmarks: Boolean,
        renderHeads: Boolean,
        headScale: Int
    ) {
        val size = getMapSize()
        ctx.fill(0, 0, size, size, COL_BG.rgb)

        for (row in 0 until GRID_SIZE) {
            for (col in 0 until GRID_SIZE) {
                val tile = grid[row * GRID_SIZE + col]
                val px = (col / 2) * STEP
                val py = (row / 2) * STEP
                val colOdd = col and 1 == 1
                val rowOdd = row and 1 == 1

                when (tile) {
                    is MapTile.Room -> ctx.fill(px, py, px + CELL_SIZE, py + CELL_SIZE, roomColor(tile.data).rgb)
                    is MapTile.Connection -> {
                        val c = roomColor(tile.data).rgb
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
                val px = (col / 2) * STEP
                val py = (row / 2) * STEP
                val c = doorColor(tile.type).rgb
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

                if (renderCheckmarks) {
                    var bestCheckmark = MapCheckmark.NONE
                    for ((c, r) in cells) {
                        val cm = checkmarks[r * GRID_SIZE + c]
                        if (cm.ordinal > bestCheckmark.ordinal) bestCheckmark = cm
                    }
                    if (bestCheckmark != MapCheckmark.NONE) {
                        val (cx, cy) = getRoomCheckmarkCenter(cells, tile.data.shape)
                        drawCheckmark(ctx, bestCheckmark, cx, cy)
                    }
                }

                if (renderNames) {
                    val (nameCol, nameRow) = cells.first()
                    val namePx = (nameCol / 2) * STEP
                    val namePy = (nameRow / 2) * STEP
                    drawRoomName(ctx, tile.data, namePx, namePy, nameScale)
                }
            }
        }

        if (renderHeads) drawPlayers(ctx, headScale)
    }

    private fun drawRoomName(ctx: DrawContext, data: RoomData, px: Int, py: Int, scalePercent: Int) {
        val tr = mc.textRenderer
        val name = data.name
        if (name.isEmpty()) return

        val displayName = HYPHENATED[name] ?: name
        val lines = displayName.split(" ", "\n")
        val styledLines = lines.map { ClickGUITheme.styledText(it) }
        val totalHeight = tr.fontHeight * lines.size
        val scale = NAME_SCALE * (scalePercent / 100f)

        ctx.matrices.pushMatrix()
        ctx.matrices.translate(px + CELL_SIZE / 2f, py + CELL_SIZE / 2f)
        ctx.matrices.scale(scale, scale)

        val startY = -(totalHeight / 2)
        for (i in styledLines.indices) {
            val tw = tr.getWidth(styledLines[i])
            ctx.drawText(tr, styledLines[i], -tw / 2, startY + i * tr.fontHeight, Color.WHITE.rgb, true)
        }
        ctx.matrices.popMatrix()
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
    private fun drawCheckmark(ctx: DrawContext, checkmark: MapCheckmark, centerX: Int, centerY: Int) {
        registerCheckmarkTexture()
        val checkSize = (CELL_SIZE * 0.5f).toInt()
        val cx = centerX - checkSize / 2
        val cy = centerY - checkSize / 2

        val tint = when (checkmark) {
            MapCheckmark.GREEN -> Color(0, 255, 0).rgb
            MapCheckmark.FAILED -> Color(255, 0, 0).rgb
            else -> Color.WHITE.rgb
        }

        ctx.drawTexture(
            RenderPipelines.GUI_TEXTURED, CHECKMARK_ID,
            cx, cy, 0f, 0f, checkSize, checkSize, checkSize, checkSize, tint
        )
    }

    private fun registerCheckmarkTexture() {
        if (checkmarkRegistered) return
        try {
            val stream = MapRenderer::class.java.classLoader.getResourceAsStream(
                "assets/${CHECKMARK_ID.namespace}/${CHECKMARK_ID.path}.png"
            ) ?: return
            val image = NativeImage.read(stream)
            mc.textureManager.registerTexture(CHECKMARK_ID, NativeImageBackedTexture({ CHECKMARK_ID.toString() }, image))
            stream.close()
        } catch (_: Exception) {}
        checkmarkRegistered = true
    }

    private fun drawPlayers(ctx: DrawContext, headScalePercent: Int) {
        val world = mc.world ?: return
        val self = mc.player ?: return
        val teammateNames = DungeonListener.teammates.keys
        val headSize = maxOf(MIN_HEAD_SIZE, (DEFAULT_HEAD_SIZE * headScalePercent / 100f).toInt())

        for (player in world.players) {
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

            val entry = mc.networkHandler?.getPlayerListEntry(player.uuid) ?: continue
            val skinTexture = entry.skinTextures?.body()?.texturePath()
            if (skinTexture != null) {
                val scale = headSize / FACE_SIZE.toFloat()
                ctx.matrices.pushMatrix()
                ctx.matrices.translate(hx.toFloat(), hy.toFloat())
                ctx.matrices.scale(scale, scale)
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skinTexture, 0, 0, FACE_U, FACE_V, FACE_SIZE, FACE_SIZE, SKIN_TEX_SIZE, SKIN_TEX_SIZE, -1)
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skinTexture, 0, 0, HAT_U, HAT_V, FACE_SIZE, FACE_SIZE, SKIN_TEX_SIZE, SKIN_TEX_SIZE, -1)
                ctx.matrices.popMatrix()
            } else {
                ctx.fill(hx - 1, hy - 1, hx + headSize + 1, hy + headSize + 1, Color.BLACK.rgb)
                val c = if (isSelf) Color(0, 220, 0).rgb else Color(0, 180, 220).rgb
                ctx.fill(hx, hy, hx + headSize, hy + headSize, c)
            }
        }
    }
}
