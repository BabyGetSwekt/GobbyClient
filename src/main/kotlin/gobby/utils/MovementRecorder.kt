package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.render.RenderBlock
import net.minecraft.core.BlockPos
import java.awt.Color
import java.io.File
import java.util.Locale
import kotlin.math.floor

object MovementRecorder {

    private const val JUMP_VY_THRESHOLD = 0.4
    private const val JUMP_RISING_EDGE_RESET = 0.05

    private val TARGET_COLOR = Color(255, 200, 0, 255)

    private var recording = false
    private var startPos: Triple<Double, Double, Double>? = null
    private var targetPos: BlockPos? = null
    private val frames = ArrayList<Frame>(4096)
    private var jumpCount = 0
    private var lastOnGround = true
    private var lastY = 0.0
    private var lastVelY = 0.0
    private var startTickMs = 0L

    private data class Frame(
        val tick: Int,
        val dtMs: Long,
        val x: Double, val y: Double, val z: Double,
        val vx: Double, val vy: Double, val vz: Double,
        val yaw: Float, val pitch: Float,
        val onGround: Boolean,
        val sneaking: Boolean,
        val sprinting: Boolean,
        val jumped: Boolean,
        val groundBlock: String
    )

    fun start(target: BlockPos? = null) {
        val player = mc.player ?: run { errorMessage("No player"); return }
        if (recording) { errorMessage("Already recording. Use /gobby record stop first."); return }
        recording = true
        frames.clear()
        jumpCount = 0
        startPos = Triple(player.x, player.y, player.z)
        lastOnGround = player.onGround()
        lastY = player.y
        lastVelY = player.deltaMovement.y
        startTickMs = System.currentTimeMillis()
        targetPos = target
        target?.let { RenderBlock.addBlock(it, TARGET_COLOR, filled = false) }
        modMessage("§aRecording started at §f(${"%.2f".format(Locale.US, player.x)}, ${"%.2f".format(Locale.US, player.y)}, ${"%.2f".format(Locale.US, player.z)})")
        if (target != null) {
            modMessage("§7Highlighted target §f(${target.x}, ${target.y}, ${target.z})§7. Walk there, then §a/gobby record stop")
        } else {
            modMessage("§7Walk to the destination, then run §a/gobby record stop")
        }
    }

    fun stop() {
        if (!recording) { errorMessage("Not recording"); return }
        val player = mc.player
        recording = false
        targetPos?.let { RenderBlock.removeBlock(it) }
        targetPos = null
        val endPos = player?.let { Triple(it.x, it.y, it.z) }
        val dir = File("./config/gobbyclientFabric/")
        dir.mkdirs()
        val file = File(dir, "movement_${System.currentTimeMillis()}.json")
        file.writeText(buildJson(endPos))
        modMessage("§aRecording stopped. §f${frames.size} frames §7| §f$jumpCount jumps")
        modMessage("§7Saved to §f${file.absolutePath}")
        frames.clear()
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!recording) return
        val player = mc.player ?: return
        val world = mc.level ?: return

        val vel = player.deltaMovement
        val onGround = player.onGround()
        val jumped = lastOnGround && vel.y > JUMP_VY_THRESHOLD && lastVelY < JUMP_RISING_EDGE_RESET
        if (jumped) jumpCount++

        val below = BlockPos(
            floor(player.x).toInt(),
            floor(player.y - 0.05).toInt(),
            floor(player.z).toInt()
        )
        val groundName = world.getBlockState(below).block.toString()

        frames += Frame(
            tick = frames.size,
            dtMs = System.currentTimeMillis() - startTickMs,
            x = player.x, y = player.y, z = player.z,
            vx = vel.x, vy = vel.y, vz = vel.z,
            yaw = player.yRot, pitch = player.xRot,
            onGround = onGround,
            sneaking = player.isShiftKeyDown,
            sprinting = player.isSprinting,
            jumped = jumped,
            groundBlock = groundName
        )

        lastOnGround = onGround
        lastY = player.y
        lastVelY = vel.y
    }

    private fun buildJson(endPos: Triple<Double, Double, Double>?): String {
        val sb = StringBuilder()
        sb.append("{\n")
        startPos?.let {
            sb.append("  \"start\": [${fmt(it.first)}, ${fmt(it.second)}, ${fmt(it.third)}],\n")
        }
        endPos?.let {
            sb.append("  \"end\": [${fmt(it.first)}, ${fmt(it.second)}, ${fmt(it.third)}],\n")
        }
        sb.append("  \"jumpCount\": $jumpCount,\n")
        sb.append("  \"frameCount\": ${frames.size},\n")
        sb.append("  \"frames\": [\n")
        for ((i, f) in frames.withIndex()) {
            sb.append("    {\"t\":${f.tick},\"ms\":${f.dtMs},")
            sb.append("\"p\":[${fmt(f.x)},${fmt(f.y)},${fmt(f.z)}],")
            sb.append("\"v\":[${fmt(f.vx)},${fmt(f.vy)},${fmt(f.vz)}],")
            sb.append("\"yaw\":${fmtF(f.yaw)},\"pitch\":${fmtF(f.pitch)},")
            sb.append("\"ground\":${f.onGround},\"sneak\":${f.sneaking},\"sprint\":${f.sprinting},")
            sb.append("\"jumped\":${f.jumped},\"block\":\"${f.groundBlock}\"}")
            if (i < frames.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    private fun fmt(d: Double) = "%.3f".format(Locale.US, d)
    private fun fmtF(f: Float) = "%.2f".format(Locale.US, f)
}
