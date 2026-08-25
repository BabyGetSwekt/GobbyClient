package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.LeftClickEvent
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
import net.minecraft.util.Mth
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
        camPitch = Mth.clamp(camPitch + deltaY * 0.15f, -90f, 90f)
    }

    fun updateMovement() {
        val dt = clock.getTime() / 1000.0
        clock.update()

        val input = readInput()
        val forwardInput = input.forward
        val leftInput = input.left
        val upInput = input.up

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

        val limited = limitSpeed(dx, dy, dz)
        dx = limited.first
        dy = limited.second
        dz = limited.third

        camX += dx * dt
        camY += dy * dt
        camZ += dz * dt
    }

    private fun readInput() = FreeCamInput(
        forward = axis(mc.options.keyUp.isDown, mc.options.keyDown.isDown),
        left = axis(mc.options.keyLeft.isDown, mc.options.keyRight.isDown),
        up = axis(mc.options.keyJump.isDown, mc.options.keyShift.isDown)
    )

    private fun axis(positive: Boolean, negative: Boolean): Double =
        (if (positive) 1.0 else 0.0) - (if (negative) 1.0 else 0.0)

    private fun limitSpeed(dx: Double, dy: Double, dz: Double): Triple<Double, Double, Double> {
        val speed = sqrt(dx * dx + dy * dy + dz * dz)
        if (speed <= MAX_SPEED) return Triple(dx, dy, dz)
        val scale = MAX_SPEED / speed
        forwardVelocity *= scale
        leftVelocity *= scale
        upVelocity *= scale
        return Triple(dx * scale, dy * scale, dz * scale)
    }

    private data class FreeCamInput(val forward: Double, val left: Double, val up: Double)

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
