package gobby.features.skyblock

import gobby.gui.click.AlwaysEnabled
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.screen.pets.openPetsList

@AlwaysEnabled
object PetsKeybind : Module(
    "Pets Keybind",
    "Bind a key to every pet and swap from the Pets menu",
    Category.COMMANDS,
    hasToggle = false
) {
    private val FAVORITE_MARKS = setOf('★', '⭐')
    private const val SKIN_MARK = '✦'
    private val FORMATTING = Regex("§.")
    private val LEVELLED_NAME = Regex("""^\[Lvl\s*(\d+)]\s*(.+)$""")

    data class ParsedPet(val favorite: Boolean, val level: Int, val name: String, val hasSkin: Boolean)

    init {
        onLeftClick = { openPetsList() }
    }

    fun parse(raw: String): ParsedPet? {
        val plain = FORMATTING.replace(raw, "").trim()
        val favorite = plain.firstOrNull()?.let { it in FAVORITE_MARKS } == true
        val body = plain.trimStart { it in FAVORITE_MARKS }.trim()
        val match = LEVELLED_NAME.matchEntire(body) ?: return null
        val level = match.groupValues[1].toIntOrNull() ?: return null
        val tail = match.groupValues[2].trim()
        val hasSkin = tail.endsWith(SKIN_MARK)
        val name = tail.removeSuffix(SKIN_MARK.toString()).trim()
        if (name.isEmpty()) return null
        return ParsedPet(favorite, level, name, hasSkin)
    }
}
