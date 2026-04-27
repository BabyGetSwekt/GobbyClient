package gobby.utils.managers

import gobby.events.ServerTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.Ability
import gobby.utils.parseAbilities
import gobby.utils.skyblockID
import net.minecraft.item.ItemStack

/**
 * Tracks ability cooldowns parsed from item lore.
 *
 * Cooldowns are keyed by (skyblockID, abilityName) so items with multiple
 * abilities (e.g. Gyrokinetic Wand) are tracked independently.
 *
 * Cooldowns are decremented once per server tick (20 ticks = 1 second).
 * All state resets on world load.
 */
object AbilityManager {

    private val abilityCache = HashMap<String, List<Ability>>()

    /** key = "$skyblockID|$abilityName", value = remaining server ticks */
    private val cooldowns = HashMap<String, Int>()

    /**
     * Returns the abilities for the given item, cached by skyblockID.
     * Returns an empty list if the item has no skyblockID or no abilities.
     */
    fun getAbilities(stack: ItemStack): List<Ability> {
        val id = stack.skyblockID
        if (id.isEmpty()) return emptyList()
        return abilityCache.getOrPut(id) { stack.parseAbilities() }
    }

    /**
     * Looks up a single ability by name on the given item (case-insensitive).
     */
    fun getAbility(stack: ItemStack, abilityName: String): Ability? {
        return getAbilities(stack).firstOrNull { it.name.equals(abilityName, ignoreCase = true) }
    }

    /**
     * Starts a cooldown for the given item+ability based on the ability's
     * cooldown value from lore. Does nothing if the ability has no cooldown.
     */
    fun startCooldown(skyblockID: String, abilityName: String, seconds: Int) {
        if (seconds <= 0) return
        cooldowns[key(skyblockID, abilityName)] = seconds * 20
    }

    /**
     * Starts a cooldown by looking up the ability from the item's lore.
     */
    fun startCooldown(stack: ItemStack, abilityName: String) {
        val id = stack.skyblockID
        if (id.isEmpty()) return
        val ability = getAbility(stack, abilityName) ?: return
        val cd = ability.cooldownSeconds ?: return
        startCooldown(id, abilityName, cd)
    }

    /** Returns remaining cooldown in server ticks, or 0 if ready. */
    fun getCooldownTicks(skyblockID: String, abilityName: String): Int {
        return cooldowns[key(skyblockID, abilityName)] ?: 0
    }

    /** Returns remaining cooldown in seconds, rounded up. */
    fun getCooldownSeconds(skyblockID: String, abilityName: String): Double {
        return getCooldownTicks(skyblockID, abilityName) / 20.0
    }

    fun isOnCooldown(skyblockID: String, abilityName: String): Boolean {
        return getCooldownTicks(skyblockID, abilityName) > 0
    }

    fun clearCooldown(skyblockID: String, abilityName: String) {
        cooldowns.remove(key(skyblockID, abilityName))
    }

    private fun key(skyblockID: String, abilityName: String): String = "$skyblockID|$abilityName"

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        if (cooldowns.isEmpty()) return
        val iter = cooldowns.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val next = entry.value - 1
            if (next <= 0) iter.remove() else entry.setValue(next)
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        cooldowns.clear()
        abilityCache.clear()
    }
}
