package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.MinecraftClientAccessor
import gobby.mixinterface.IMinecraftClient


object PlayerUtils {

    fun leftClick() {
        val attackCooldown: Int = (mc as MinecraftClientAccessor).getAttackCooldown()
        if (attackCooldown == 10000) {
            (mc as MinecraftClientAccessor).setAttackCooldown(0)
        }
        mc.options.attackKey.isPressed = true
        (mc as MinecraftClientAccessor).leftClick()
        mc.options.attackKey.isPressed = false
    }

    // Does not work yet
    fun rightClick() {
        (mc as IMinecraftClient).`gobbyclient$rightClick`()
    }
}