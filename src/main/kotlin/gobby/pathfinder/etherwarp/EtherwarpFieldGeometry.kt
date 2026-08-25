package gobby.pathfinder.etherwarp

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

internal object EtherwarpFieldGeometry {
    const val QUERY_RANGE_MARGIN = 1.0
    const val INITIAL_DISTANCE = 0
    const val HOP_INCREMENT = 1
    const val BLOCK_CENTER_OFFSET = 0.5
    const val QUERY_BUCKET_SIZE = 4
    const val ZERO_AIM = 0f
    const val FALLBACK_BUILD_BUDGET_NANOS = 8_000_000_000L
    private const val BLOCK_HEIGHT = 1.0
    private const val TOP_FACE_EPSILON = 0.001
    private const val COLUMN_KEY_SHIFT = 32
    private const val COLUMN_KEY_MASK = 0xFFFFFFFFL

    fun landingEye(pos: BlockPos): Vec3 = Vec3(
        pos.x + BLOCK_CENTER_OFFSET,
        EtherwarpKind.ETHERWARP.landingY(pos.y) + EtherwarpKind.ETHERWARP.eyeHeight(),
        pos.z + BLOCK_CENTER_OFFSET
    )

    fun eye(position: Vec3): Vec3 = Vec3(position.x, position.y + EtherwarpKind.ETHERWARP.eyeHeight(), position.z)

    fun topFacePoint(target: BlockPos): Vec3 =
        Vec3(target.x + BLOCK_CENTER_OFFSET, target.y + BLOCK_HEIGHT - TOP_FACE_EPSILON, target.z + BLOCK_CENTER_OFFSET)

    fun columnKey(x: Int, z: Int): Long = (x.toLong() shl COLUMN_KEY_SHIFT) xor (z.toLong() and COLUMN_KEY_MASK)

    fun queryColumnKey(x: Int, z: Int): Long =
        columnKey(floor(x.toDouble() / QUERY_BUCKET_SIZE).toInt(), floor(z.toDouble() / QUERY_BUCKET_SIZE).toInt())

    fun Pair<Float, Float>.toAim(): Aim = Aim(first, second)
}
