package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.BONZO_MASK_IDS
import gobby.utils.ChatUtils.modMessage
import gobby.utils.SPIRIT_MASK_IDS
import gobby.utils.getHelmetID
import gobby.utils.skyblockID
import gobby.utils.timer.Cooldown

/**
 * Tracks death-save mask invincibility cooldowns (Spirit Mask, Bonzo's Mask).
 *
 * Only starts a cooldown when the user was actually wearing the mask at the
 * time it popped (Adaptive Armor etc. can trigger the same chat message
 * without the mask being equipped).
 *
 * Spirit Mask cooldown is hardcoded to 30 seconds.
 * Bonzo's Mask cooldown is looked up from the item lore via AbilityManager.
 */

object InvincibilityManager {

    private const val SPIRIT_POP_MSG = "Second Wind Activated! Your Spirit Mask saved your life!"
    private const val BONZO_POP_MSG_1 = "Your Bonzo's Mask saved your life!"
    private const val BONZO_POP_MSG_2 = "Your ⚚ Bonzo's Mask saved your life!"
    private const val PHOENIX_POP_MSG = "Your Phoenix Pet saved you from certain death!"

    private const val SPIRIT_COOLDOWN_SECONDS = 30
    private const val PHOENIX_COOLDOWN_SECONDS = 60

    /** Fallback if the bonzo cooldown cannot be read from lore (e.g. not held). */
    private const val BONZO_FALLBACK_COOLDOWN_SECONDS = 180

    private val spirit = Cooldown()
    private val bonzo = Cooldown()
    private val phoenix = Cooldown()

    val isSpiritOnCooldown: Boolean get() = spirit.isActive
    val isBonzoOnCooldown: Boolean get() = bonzo.isActive
    val isPhoenixOnCooldown: Boolean get() = phoenix.isActive

    val spiritCooldownSeconds: Double get() = spirit.remainingSeconds
    val bonzoCooldownSeconds: Double get() = bonzo.remainingSeconds
    val phoenixCooldownSeconds: Double get() = phoenix.remainingSeconds

    fun isWearingSpiritMask(): Boolean = getHelmetID() in SPIRIT_MASK_IDS

    fun isWearingBonzoMask(): Boolean = getHelmetID() in BONZO_MASK_IDS

    private fun lookupBonzoCooldownSeconds(): Int {
        // Try the currently-equipped helmet first (most accurate — respects
        // ability upgrades/reforges on this specific item).
        val helmet = mc.player?.inventory?.getItem(39)
        if (helmet != null && helmet.skyblockID in BONZO_MASK_IDS) {
            AbilityManager.getAbilities(helmet)
                .firstNotNullOfOrNull { it.cooldownSeconds }
                ?.let { return it }
        }
        return BONZO_FALLBACK_COOLDOWN_SECONDS
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (mc.player == null) return
        val msg = event.message

        if (msg == SPIRIT_POP_MSG) {
            if (isWearingSpiritMask()) {
                spirit.start(SPIRIT_COOLDOWN_SECONDS)
            }
            return
        }

        if (msg == BONZO_POP_MSG_1 || msg == BONZO_POP_MSG_2) {
            if (isWearingBonzoMask()) {
                bonzo.start(lookupBonzoCooldownSeconds())
            }
            return
        }

        if (msg == PHOENIX_POP_MSG) phoenix.start(PHOENIX_COOLDOWN_SECONDS)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        spirit.clear()
        bonzo.clear()
        phoenix.clear()
    }
}
