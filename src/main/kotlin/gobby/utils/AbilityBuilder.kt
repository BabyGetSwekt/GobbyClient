package gobby.utils

internal class AbilityBuilder(private val name: String, private val trigger: String?) {
    private var mana: Int? = null
    private var soulflow: Int? = null
    private var cooldown: Int? = null

    fun applyCostLine(line: String) {
        MANA_COST.find(line)?.let { mana = parseCost(it) }
        SOULFLOW_COST.find(line)?.let { soulflow = parseCost(it) }
        COOLDOWN.find(line)?.let { cooldown = parseCost(it) }
    }

    fun build() = Ability(name, trigger, mana, soulflow, cooldown)

    private fun parseCost(match: MatchResult): Int? = match.groupValues[1].replace(",", "").toIntOrNull()

    private companion object {
        val MANA_COST = Regex("^Mana Cost:\\s+([\\d,]+)")
        val SOULFLOW_COST = Regex("^Soulflow Cost:\\s+([\\d,]+)")
        val COOLDOWN = Regex("^Cooldown:\\s+([\\d,]+)s")
    }
}
