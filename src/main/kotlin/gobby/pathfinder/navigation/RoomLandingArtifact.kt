package gobby.pathfinder.navigation

import java.io.DataInput

data class LocalRoomCell(val x: Int, val z: Int, val core: Int)

data class LocalDirectedEdge(
    val from: LocalLanding,
    val to: LocalLanding,
    val yaw: Float,
    val pitch: Float
)

data class RoomLandingRecord(
    val name: String,
    val shape: String,
    val cells: List<LocalRoomCell>,
    val landings: List<LocalLanding>,
    val anchors: List<LocalLanding>,
    val edges: List<LocalDirectedEdge>
)

data class RoomLandingArtifact(val records: List<RoomLandingRecord>) {
    companion object {
        private const val MAGIC = 0x54444c41
        private const val SCHEMA_VERSION = 3
        private const val MAX_RECORDS = 512
        private const val MAX_CELLS = 4
        private const val MAX_LANDINGS = 512
        private const val MAX_EDGES = 16_384

        fun read(input: DataInput): RoomLandingArtifact {
            require(input.readInt() == MAGIC) { INVALID_ARTIFACT }
            require(input.readInt() == SCHEMA_VERSION) { UNSUPPORTED_SCHEMA }
            val count = bounded(input.readInt(), 0, MAX_RECORDS)
            return RoomLandingArtifact(List(count) { readRecord(input) })
        }

        private fun readRecord(input: DataInput): RoomLandingRecord {
            val name = input.readUTF()
            val shape = input.readUTF()
            val cells = readCells(input)
            val landings = readLandings(input)
            val anchors = readAnchors(input, landings)
            val edges = readEdges(input, landings)
            return RoomLandingRecord(name, shape, cells, landings, anchors, edges)
        }

        private fun readCells(input: DataInput): List<LocalRoomCell> {
            val count = bounded(input.readInt(), 1, MAX_CELLS)
            return List(count) { LocalRoomCell(input.readShort().toInt(), input.readShort().toInt(), input.readInt()) }
        }

        private fun readLandings(input: DataInput): List<LocalLanding> {
            val count = bounded(input.readInt(), 1, MAX_LANDINGS)
            val xs = IntArray(count) { input.readShort().toInt() }
            val ys = IntArray(count) { input.readShort().toInt() }
            val zs = IntArray(count) { input.readShort().toInt() }
            return List(count) { LocalLanding(xs[it], ys[it], zs[it]) }
        }

        private fun readAnchors(input: DataInput, landings: List<LocalLanding>): List<LocalLanding> {
            val count = bounded(input.readInt(), 0, landings.size)
            return List(count) { landings[index(input.readUnsignedShort(), landings.size)] }.distinct()
        }

        private fun readEdges(input: DataInput, landings: List<LocalLanding>): List<LocalDirectedEdge> {
            val count = bounded(input.readInt(), 0, MAX_EDGES)
            var running = 0
            val from = IntArray(count) {
                running = (running + input.readUnsignedShort()) and INDEX_MASK
                index(running, landings.size)
            }
            val to = IntArray(count) { index(input.readUnsignedShort(), landings.size) }
            val yaws = FloatArray(count) { input.readFloat() }
            val pitches = FloatArray(count) { input.readFloat() }
            return List(count) {
                require(yaws[it].isFinite() && pitches[it].isFinite()) { INVALID_AIM }
                LocalDirectedEdge(landings[from[it]], landings[to[it]], yaws[it], pitches[it])
            }
        }

        private fun index(value: Int, size: Int): Int {
            require(value in 0 until size) { INVALID_INDEX }
            return value
        }

        private fun bounded(value: Int, minimum: Int, maximum: Int): Int {
            require(value in minimum..maximum) { INVALID_COUNT }
            return value
        }

        private const val INDEX_MASK = 0xFFFF
        private const val INVALID_ARTIFACT = "Invalid room landing artifact"
        private const val UNSUPPORTED_SCHEMA = "Unsupported room landing artifact schema"
        private const val INVALID_AIM = "Non-finite compiled aim"
        private const val INVALID_INDEX = "Invalid room landing index"
        private const val INVALID_COUNT = "Invalid room landing count"
    }
}
