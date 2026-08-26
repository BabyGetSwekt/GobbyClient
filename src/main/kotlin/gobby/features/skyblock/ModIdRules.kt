package gobby.features.skyblock

object ModIdRules {

    const val PROTECTED_ID = "gobbyclient"

    fun clean(id: String): String = id.trim().lowercase()

    fun isProtected(id: String): Boolean = clean(id) == PROTECTED_ID

    fun storable(ids: List<String>): List<String> =
        ids.map(::clean).filter { it.isNotEmpty() && !isProtected(it) }.distinct()

    fun effective(stored: List<String>): List<String> = listOf(PROTECTED_ID) + storable(stored)
}
