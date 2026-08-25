package gobby.pathfinder.search

class PrimitiveSearchArena(initialCapacity: Int = DEFAULT_CAPACITY) {
    var positions = LongArray(initialCapacity)
    var x = DoubleArray(initialCapacity)
    var y = DoubleArray(initialCapacity)
    var z = DoubleArray(initialCapacity)
    var g = DoubleArray(initialCapacity)
    var f = DoubleArray(initialCapacity)
    var parent = IntArray(initialCapacity)
    var yaw = FloatArray(initialCapacity)
    var pitch = FloatArray(initialCapacity)
    var size = 0
        private set

    fun reset() {
        size = 0
    }

    fun add(packedPos: Long, px: Double, py: Double, pz: Double, cost: Double, score: Double, parentIndex: Int, nodeYaw: Float, nodePitch: Float): Int {
        ensure(size + 1)
        val index = size++
        positions[index] = packedPos
        x[index] = px
        y[index] = py
        z[index] = pz
        g[index] = cost
        f[index] = score
        parent[index] = parentIndex
        yaw[index] = nodeYaw
        pitch[index] = nodePitch
        return index
    }

    private fun ensure(required: Int) {
        if (required <= positions.size) return
        val capacity = maxOf(required, positions.size * 2)
        positions = positions.copyOf(capacity)
        x = x.copyOf(capacity)
        y = y.copyOf(capacity)
        z = z.copyOf(capacity)
        g = g.copyOf(capacity)
        f = f.copyOf(capacity)
        parent = parent.copyOf(capacity)
        yaw = yaw.copyOf(capacity)
        pitch = pitch.copyOf(capacity)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 256
    }
}

class IntSearchHeap(initialCapacity: Int = DEFAULT_CAPACITY) {
    private var values = IntArray(initialCapacity)
    var size = 0
        private set

    fun reset() {
        size = 0
    }

    fun add(index: Int, arena: PrimitiveSearchArena) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        var child = size++
        while (child > 0) {
            val parent = (child - 1) ushr 1
            if (arena.f[values[parent]] <= arena.f[index]) break
            values[child] = values[parent]
            child = parent
        }
        values[child] = index
    }

    fun removeFirst(arena: PrimitiveSearchArena): Int {
        val result = values[0]
        val tail = values[--size]
        if (size == 0) return result
        var parent = 0
        while (true) {
            val left = parent * 2 + 1
            if (left >= size) break
            val right = left + 1
            val child = if (right < size && arena.f[values[right]] < arena.f[values[left]]) right else left
            if (arena.f[tail] <= arena.f[values[child]]) break
            values[parent] = values[child]
            parent = child
        }
        values[parent] = tail
        return result
    }

    private companion object {
        const val DEFAULT_CAPACITY = 256
    }
}

class IntGenerationSet(initialCapacity: Int = 0) {
    private var generations = IntArray(initialCapacity)
    private var generation = 1

    fun next(requiredSize: Int) {
        if (requiredSize > generations.size) generations = generations.copyOf(requiredSize)
        generation++
        if (generation == Int.MAX_VALUE) {
            generations.fill(0)
            generation = 1
        }
    }

    fun add(value: Int): Boolean {
        if (generations[value] == generation) return false
        generations[value] = generation
        return true
    }
}
