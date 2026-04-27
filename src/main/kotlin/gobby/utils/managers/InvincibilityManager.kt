package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ServerTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.BONZO_MASK_IDS
import gobby.utils.SPIRIT_MASK_IDS
import gobby.utils.getHelmetID
import gobby.utils.skyblockID

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
    private const val BONZO_POP_MSG_2 = "Your \u269A Bonzo's Mask saved your life!"

    private const val SPIRIT_COOLDOWN_SECONDS = 30

    /** Fallback if the bonzo cooldown cannot be read from lore (e.g. not held). */
    private const val BONZO_FALLBACK_COOLDOWN_SECONDS = 180

    private var spiritCooldownTicks = 0
    private var bonzoCooldownTicks = 0

    val isSpiritOnCooldown: Boolean get() = spiritCooldownTicks > 0
    val isBonzoOnCooldown: Boolean get() = bonzoCooldownTicks > 0

    val spiritCooldownSeconds: Double get() = spiritCooldownTicks / 20.0
    val bonzoCooldownSeconds: Double get() = bonzoCooldownTicks / 20.0

    fun isWearingSpiritMask(): Boolean = getHelmetID() in SPIRIT_MASK_IDS
    fun isWearingBonzoMask(): Boolean = getHelmetID() in BONZO_MASK_IDS

    private fun lookupBonzoCooldownSeconds(): Int {
        // Try the currently-equipped helmet first (most accurate — respects
        // ability upgrades/reforges on this specific item).
        val helmet = mc.player?.inventory?.getStack(39)
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
                spiritCooldownTicks = SPIRIT_COOLDOWN_SECONDS * 20
            }
            return
        }

        if (msg == BONZO_POP_MSG_1 || msg == BONZO_POP_MSG_2) {
            if (isWearingBonzoMask()) {
                bonzoCooldownTicks = lookupBonzoCooldownSeconds() * 20
            }
            return
        }
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        if (spiritCooldownTicks > 0) spiritCooldownTicks--
        if (bonzoCooldownTicks > 0) bonzoCooldownTicks--
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        spiritCooldownTicks = 0
        bonzoCooldownTicks = 0
    }
}
