package gobby.utils.copy

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChunkLoadEvent
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent

abstract class ChunkAreaScanner {

    protected var minCX = 0
    protected var maxCX = 0
    protected var minCZ = 0
    protected var maxCZ = 0
    protected var totalChunks = 0

    var isScanning = false
        private set

    private val scannedChunks = HashSet<Long>()

    protected fun startScan(minBlockX: Int, maxBlockX: Int, minBlockZ: Int, maxBlockZ: Int) {
        minCX = minBlockX shr 4
        maxCX = maxBlockX shr 4
        minCZ = minBlockZ shr 4
        maxCZ = maxBlockZ shr 4
        totalChunks = (maxCX - minCX + 1) * (maxCZ - minCZ + 1)
        scannedChunks.clear()
        isScanning = true
        scanLoadedChunks()
    }

    protected fun stopScan() {
        isScanning = false
        scannedChunks.clear()
    }

    protected fun scannedCount(): Int = scannedChunks.size

    private fun scanLoadedChunks() {
        val world = mc.world ?: return
        for (cx in minCX..maxCX) for (cz in minCZ..maxCZ) {
            if (!world.chunkManager.isChunkLoaded(cx, cz)) continue
            val packed = packChunk(cx, cz)
            if (packed in scannedChunks) continue
            scanChunk(cx, cz)
            scannedChunks.add(packed)
        }
        if (scannedChunks.size >= totalChunks) finalizeScan() else onScanProgress(scannedChunks.size, totalChunks)
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!isScanning) return
        val cx = event.chunk.pos.x
        val cz = event.chunk.pos.z
        if (cx !in minCX..maxCX || cz !in minCZ..maxCZ) return
        val packed = packChunk(cx, cz)
        if (packed in scannedChunks) return
        scanChunk(cx, cz)
        scannedChunks.add(packed)
        if (scannedChunks.size % 10 == 0) onScanProgress(scannedChunks.size, totalChunks)
        if (scannedChunks.size >= totalChunks) finalizeScan()
    }

    @SubscribeEvent
    fun onTickScan(event: ClientTickEvent.Post) {
        if (!isScanning || scannedChunks.size >= totalChunks) return
        val world = mc.world ?: return
        for (cx in minCX..maxCX) for (cz in minCZ..maxCZ) {
            val packed = packChunk(cx, cz)
            if (packed in scannedChunks) continue
            if (!world.chunkManager.isChunkLoaded(cx, cz)) continue
            scanChunk(cx, cz)
            scannedChunks.add(packed)
        }
        if (scannedChunks.size >= totalChunks) finalizeScan()
    }

    private fun finalizeScan() {
        isScanning = false
        onScanComplete()
    }

    protected abstract fun scanChunk(cx: Int, cz: Int)
    protected abstract fun onScanComplete()
    protected open fun onScanProgress(scanned: Int, total: Int) {}

    private fun packChunk(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
}
