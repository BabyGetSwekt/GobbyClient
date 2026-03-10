package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.LeftClickEvent
import gobby.events.PacketSentEvent
import gobby.events.RightClickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.Utils.eyePosX
import gobby.utils.Utils.eyePosY
import gobby.utils.Utils.eyePosZ
import gobby.utils.Utils.pitch
import gobby.utils.Utils.toRadians
import gobby.utils.Utils.yaw
import gobby.utils.timer.Clock
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.MathHelper
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

object FreeCam : Module(
    "Free Cam", "This module is compatible with brush mode. Set a keybind to toggle this module on and off.",
    Category.SKYBLOCK
) {

    private const val ACCELERATION = 20.0
    private const val MAX_SPEED = 35.0
    private const val SLOWDOWN = 0.05

    var camX = 0.0
    var camY = 0.0
    var camZ = 0.0
    var camYaw = 0f
    var camPitch = 0f

    private var forwardVelocity = 0.0
    private var leftVelocity = 0.0
    private var upVelocity = 0.0
    private val clock = Clock()
    private var wasEnabled = false

    fun enable() {
        val yawRad = yaw.toRadians()
        val pitchRad = pitch.toRadians()

        val lookX = -sin(yawRad) * cos(pitchRad)
        val lookY = -sin(pitchRad)
        val lookZ = cos(yawRad) * cos(pitchRad)
        camX = eyePosX - lookX * 1.5
        camY = eyePosY - lookY * 1.5
        camZ = eyePosZ - lookZ * 1.5
        camYaw = yaw
        camPitch = pitch
        forwardVelocity = 0.0
        leftVelocity = 0.0
        upVelocity = 0.0
        clock.update()
    }

    private fun disable() {
        forwardVelocity = 0.0
        leftVelocity = 0.0
        upVelocity = 0.0
    }

    fun updateAngles(deltaX: Float, deltaY: Float) {
        camYaw += deltaX * 0.15f
        camPitch = MathHelper.clamp(camPitch + deltaY * 0.15f, -90f, 90f)
    }

    fun updateMovement() {
        val dt = clock.getTime() / 1000.0
        clock.update()

        val forwardInput = (if (mc.options.forwardKey.isPressed) 1.0 else 0.0) -
                (if (mc.options.backKey.isPressed) 1.0 else 0.0)
        val leftInput = (if (mc.options.leftKey.isPressed) 1.0 else 0.0) -
                (if (mc.options.rightKey.isPressed) 1.0 else 0.0)
        val upInput = (if (mc.options.jumpKey.isPressed) 1.0 else 0.0) -
                (if (mc.options.sneakKey.isPressed) 1.0 else 0.0)

        forwardVelocity = calculateVelocity(forwardVelocity, forwardInput, dt)
        leftVelocity = calculateVelocity(leftVelocity, leftInput, dt)
        upVelocity = calculateVelocity(upVelocity, upInput, dt)

        if (forwardInput != 0.0 && leftInput != 0.0) {
            val maxHVel = maxOf(abs(forwardVelocity), abs(leftVelocity))
            if (abs(forwardVelocity) < maxHVel)
                forwardVelocity = sign(forwardInput) * maxHVel
            if (abs(leftVelocity) < maxHVel)
                leftVelocity = sign(leftInput) * maxHVel
        }

        val yawRad = camYaw.toRadians()
        val forwardX = -sin(yawRad)
        val forwardZ = cos(yawRad)
        val leftX = cos(yawRad)
        val leftZ = sin(yawRad)

        var dx = forwardX * forwardVelocity + leftX * leftVelocity
        var dy = upVelocity
        var dz = forwardZ * forwardVelocity + leftZ * leftVelocity

        val speed = sqrt(dx * dx + dy * dy + dz * dz)
        if (speed > MAX_SPEED) {
            val scale = MAX_SPEED / speed
            forwardVelocity *= scale
            leftVelocity *= scale
            upVelocity *= scale
            dx *= scale
            dy *= scale
            dz *= scale
        }

        camX += dx * dt
        camY += dy * dt
        camZ += dz * dt
    }

    private fun calculateVelocity(current: Double, input: Double, dt: Double): Double {
        if (input == 0.0) return current * SLOWDOWN.pow(dt)
        val accel = ACCELERATION * input * dt
        return if (sign(input) == sign(current)) accel + current else accel
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (enabled && !wasEnabled) enable()
        if (!enabled && wasEnabled) disable()
        wasEnabled = enabled
    }

    @SubscribeEvent
    fun onPacket(event: PacketSentEvent) {
        if (!enabled) return
        if (event.packet is PlayerMoveC2SPacket) event.cancel()
    }

    @SubscribeEvent
    fun onLeftClick(event: LeftClickEvent) {
        if (enabled) event.cancel()
    }

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (enabled) event.cancel()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        enabled = false
        wasEnabled = false
    }
}
