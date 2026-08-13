package gobby.pathfinder.etherwarp

import gobby.utils.VecUtils
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealDungeonGraphTest {

    companion object {
        private const val RANGE = 55.0
        private const val STAND_OFFSET = 1.05
        private const val MIN_ROOM_SEPARATION = 4
        private const val GOAL_TOLERANCE = 6
        private const val WARMUP_QUERIES = 20
        private const val MEASURED_QUERIES = 31
        private const val QUERY_LIMIT_NANOS = 1_000_000L
        private const val REPAIR_MIN_SPEEDUP = 3.0
        private const val COARSE_RANGE = 28.0
        private const val COARSE_SOLVED_TOLERANCE = 1
    }

    @Test
    fun graphQueryStaysUnderOneMillisecondAcrossTheDungeon() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val dungeon = RealDungeonCache.loadOrNull() ?: return
        val buildStart = System.nanoTime()
        val nodeSet = dungeon.floorCandidates
        val graph = EtherwarpGraphBuilder.build(nodeSet, RANGE, dungeon.access) ?: return
        val buildDoneAt = System.nanoTime()
        val fullMsBudget = (buildDoneAt - buildStart) / 1_000_000.0
        println("[Graph] landingSpots=${nodeSet.size} nodes=${graph.nodeCount} edges=${graph.edgeCount} buildMs=${(System.nanoTime() - buildStart) / 1_000_000.0}")

        val rooms = dungeon.occupiedRooms()
        val origin = rooms.first()
        val mainComponent = largestComponent(graph)
        val originCenter = RealDungeonCache.roomCenter(origin.first, origin.second)
        val start = mainComponent.minByOrNull { VecUtils.centerDistanceSq(Vec3(originCenter.x + 0.5, originCenter.y + 0.5, originCenter.z + 0.5), it) } ?: return
        println("[Graph] mainComponentSize=${mainComponent.size}")
        val from = Vec3(start.x + 0.5, start.y + STAND_OFFSET, start.z + 0.5)
        val reach = graph.reachableFrom(from, dungeon.access).map { graph.nodeAt(it) }
        println("[Graph] reachableFromStart=${reach.size} of ${graph.nodeCount}")
        println("[Graph] box x=${reach.minOf { it.x }}..${reach.maxOf { it.x }} z=${reach.minOf { it.z }}..${reach.maxOf { it.z }} y=${reach.minOf { it.y }}..${reach.maxOf { it.y }}")
        println("[Graph] cellsReached=${reach.map { "${(it.x + 185) / 16},${(it.z + 185) / 16}" }.distinct().sorted()}")
        println("[Graph] startBlock=$start")
        var worstMedian = 0L
        var solved = 0
        var attempted = 0

        val mainSet = mainComponent.toHashSet()
        var connectedGoals = 0
        val unsolvedButConnected = mutableListOf<Pair<Int, Int>>()
        var fieldMsTotal = 0.0
        rooms.filter { separation(origin, it) >= MIN_ROOM_SEPARATION }.forEach { room ->
            val goal = dungeon.landingNearRoom(room.first, room.second) ?: return@forEach
            val goalNodes = (0 until graph.nodeCount).filter { reached(goal)(graph.nodeAt(it)) }
            attempted++
            val fieldStart = System.nanoTime()
            val field = graph.fieldTo(goalNodes)
            fieldMsTotal += (System.nanoTime() - fieldStart) / 1_000_000.0
            val path = field.pathFrom(from, dungeon.access)
            val median = medianNanos { field.pathFrom(from, dungeon.access) }
            if (path != null) solved++
            if (goalNodes.any { mainSet.contains(graph.nodeAt(it)) }) connectedGoals++
            if (goalNodes.any { mainSet.contains(graph.nodeAt(it)) } && path == null) unsolvedButConnected.add(room)
            worstMedian = maxOf(worstMedian, median)
            println("[Graph] cells=${separation(origin, room)} room=$room goalNodes=${goalNodes.size} goalInMain=${goalNodes.count { mainSet.contains(graph.nodeAt(it)) }} hops=${path?.let { it.size - 1 } ?: -1} medianUs=${median / 1_000.0}")
        }
        println("[Graph] solved=$solved of $attempted worstMedianUs=${worstMedian / 1_000.0} fieldBuildMsTotal=$fieldMsTotal")
        println("[Graph] connectedGoals=$connectedGoals unsolvedButConnected=$unsolvedButConnected")
        val coarseStart = System.nanoTime()
        val coarse = EtherwarpGraphBuilder.build(nodeSet, COARSE_RANGE, dungeon.access)
        val coarseMs = (System.nanoTime() - coarseStart) / 1_000_000.0
        assertTrue(coarse != null, "coarse stage must produce a graph")
        coarse?.let { stage ->
            val usable = rooms.filter { separation(origin, it) >= MIN_ROOM_SEPARATION }.count { room ->
                val goal = dungeon.landingNearRoom(room.first, room.second) ?: return@count false
                stage.fieldTo((0 until stage.nodeCount).filter { reached(goal)(stage.nodeAt(it)) }).pathFrom(from, dungeon.access) != null
            }
            val goalRoom = rooms.first { separation(origin, it) >= MIN_ROOM_SEPARATION }
            val goalBlock = dungeon.landingNearRoom(goalRoom.first, goalRoom.second)
            val stageField = stage.fieldTo((0 until stage.nodeCount).filter { i -> goalBlock?.let { reached(it)(stage.nodeAt(i)) } == true })
            val stageMedian = medianNanos { stageField.pathFrom(from, dungeon.access) }
            println("[Graph] coarseMs=$coarseMs coarseSolved=$usable coarseMedianUs=${stageMedian / 1_000.0}")
            assertTrue(coarseMs < fullMsBudget, "coarse stage must be much cheaper than the exact stage")
            assertTrue(stageMedian < QUERY_LIMIT_NANOS, "coarse stage queries must also stay under budget")
            assertTrue(usable >= solved - COARSE_SOLVED_TOLERANCE, "coarse stage must already solve nearly everything")
        }

        val doorPos = BlockPos(-137, 68, -89)
        val repairStart = System.nanoTime()
        val repaired = EtherwarpGraphBuilder.rebuildAround(graph, doorPos, RANGE, dungeon.access)
        val repairMs = (System.nanoTime() - repairStart) / 1_000_000.0
        val fullMs = (buildDoneAt - buildStart) / 1_000_000.0
        println("[Graph] repairMs=$repairMs fullMs=$fullMs speedup=${"%.1f".format(fullMs / repairMs)}x repairedEdges=${repaired?.edgeCount}")
        assertTrue(repaired != null, "localized repair must produce a graph")
        val repairedReach = repaired?.reachableFrom(from, dungeon.access)?.size ?: 0
        println("[Graph] reachBefore=${reach.size} reachAfterRepair=$repairedReach")
        assertTrue(repairedReach >= reach.size, "repair must never lose reachability: $repairedReach < ${reach.size}")
        repaired?.let { fixed ->
            val stillSolved = rooms.filter { separation(origin, it) >= MIN_ROOM_SEPARATION }.count { room ->
                val goal = dungeon.landingNearRoom(room.first, room.second) ?: return@count false
                fixed.fieldTo((0 until fixed.nodeCount).filter { reached(goal)(fixed.nodeAt(it)) }).pathFrom(from, dungeon.access) != null
            }
            println("[Graph] solvedAfterRepair=$stillSolved")
            assertTrue(stillSolved >= solved, "repair must never lose solvable rooms")
        }
        assertTrue(repairMs < fullMs / REPAIR_MIN_SPEEDUP, "repair must be much cheaper than a full rebuild")

        assertTrue(worstMedian < QUERY_LIMIT_NANOS, "query budget exceeded: ${worstMedian / 1_000.0} us")
        assertEquals(emptyList(), unsolvedButConnected, "every reachable room must be solved")
        assertEquals(connectedGoals, solved, "solved count must equal reachable room count")
    }

    private fun largestComponent(graph: EtherwarpGraph): List<BlockPos> {
        val seen = BooleanArray(graph.nodeCount)
        var best = emptyList<Int>()
        (0 until graph.nodeCount).forEach { root ->
            if (seen[root]) return@forEach
            val members = ArrayList<Int>()
            val stack = ArrayDeque(listOf(root))
            seen[root] = true
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                members.add(node)
                graph.neighbours(node).forEach { if (!seen[it]) { seen[it] = true; stack.addLast(it) } }
            }
            if (members.size > best.size) best = members
        }
        return best.map { graph.nodeAt(it) }
    }

    private fun componentSizes(graph: EtherwarpGraph): List<Int> {
        val seen = BooleanArray(graph.nodeCount)
        return (0 until graph.nodeCount).mapNotNull { root ->
            if (seen[root]) return@mapNotNull null
            var size = 0
            val stack = ArrayDeque(listOf(root))
            seen[root] = true
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                size++
                graph.neighbours(node).forEach { if (!seen[it]) { seen[it] = true; stack.addLast(it) } }
            }
            size
        }.sortedDescending()
    }

    private fun medianNanos(query: () -> Any?): Long {
        repeat(WARMUP_QUERIES) { query() }
        return (0 until MEASURED_QUERIES).map {
            val started = System.nanoTime()
            query()
            System.nanoTime() - started
        }.sorted()[MEASURED_QUERIES / 2]
    }

    private fun reached(goal: BlockPos): (BlockPos) -> Boolean = { pos ->
        abs(pos.x - goal.x) <= GOAL_TOLERANCE && abs(pos.z - goal.z) <= GOAL_TOLERANCE
    }

    private fun separation(a: Pair<Int, Int>, b: Pair<Int, Int>): Int = abs(a.first - b.first) + abs(a.second - b.second)
}
