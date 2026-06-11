package gobby.pathfinder.movement

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.KeyMappingAccessor
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants

object InputManager {

    @JvmField
    var suppressSprint: Boolean = false

    enum class MoveAction {
        FORWARD, BACKWARD, LEFT, RIGHT, JUMP, SNEAK, SPRINT
    }

    fun getKeyBinding(action: MoveAction): KeyMapping = when (action) {
        MoveAction.FORWARD -> mc.options.keyUp
        MoveAction.BACKWARD -> mc.options.keyDown
        MoveAction.LEFT -> mc.options.keyLeft
        MoveAction.RIGHT -> mc.options.keyRight
        MoveAction.JUMP -> mc.options.keyJump
        MoveAction.SNEAK -> mc.options.keyShift
        MoveAction.SPRINT -> mc.options.keySprint
    }

    private fun getBoundKey(action: MoveAction): InputConstants.Key =
        (getKeyBinding(action) as KeyMappingAccessor).boundKey

    fun press(action: MoveAction) {
        KeyMapping.set(getBoundKey(action), true)
    }

    fun release(action: MoveAction) {
        KeyMapping.set(getBoundKey(action), false)
    }

    fun releaseAll() {
        MoveAction.entries.forEach { release(it) }
        suppressSprint = false
    }

    fun isPressed(action: MoveAction): Boolean = getKeyBinding(action).isDown
}
