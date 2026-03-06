package gobby.pathfinder.movement

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.KeyBindingAccessor
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil

object InputManager {

    enum class MoveAction {
        FORWARD, BACKWARD, LEFT, RIGHT, JUMP, SNEAK, SPRINT
    }

    fun getKeyBinding(action: MoveAction): KeyBinding = when (action) {
        MoveAction.FORWARD -> mc.options.forwardKey
        MoveAction.BACKWARD -> mc.options.backKey
        MoveAction.LEFT -> mc.options.leftKey
        MoveAction.RIGHT -> mc.options.rightKey
        MoveAction.JUMP -> mc.options.jumpKey
        MoveAction.SNEAK -> mc.options.sneakKey
        MoveAction.SPRINT -> mc.options.sprintKey
    }

    private fun getBoundKey(action: MoveAction): InputUtil.Key =
        (getKeyBinding(action) as KeyBindingAccessor).boundKey

    fun press(action: MoveAction) {
        KeyBinding.setKeyPressed(getBoundKey(action), true)
    }

    fun release(action: MoveAction) {
        KeyBinding.setKeyPressed(getBoundKey(action), false)
    }

    fun releaseAll() {
        MoveAction.entries.forEach { release(it) }
    }

    fun isPressed(action: MoveAction): Boolean = getKeyBinding(action).isPressed
}
