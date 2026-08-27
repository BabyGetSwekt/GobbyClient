package gobby.gui.screen.pets

import gobby.Gobbyclient.Companion.mc
import gobby.utils.managers.PetEntry
import gobby.utils.managers.PetManager
import gobby.gui.click.ClickGUI
import gobby.gui.click.MOUSE_OFFSET
import gobby.gui.click.TextField
import gobby.utils.Utils.executeLater

private fun searchText(raw: String): String =
    raw.lowercase().filter { it.isLetterOrDigit() || it in " -_" }

internal object PetsList {

    val searchField = TextField(::searchText, 32)

    var searchFocused = false
        private set
    var listening: PetEntry? = null
        private set

    val scanning: Boolean get() = PetManager.isScanning

    val scanned: Boolean get() = PetManager.scanned

    fun open() {
        if (!PetManager.scanned) PetManager.scan()
    }

    fun close() {
        searchFocused = false
        searchField.clear()
        listening = null
    }

    fun refresh() {
        listening = null
        PetManager.scan()
    }

    fun focusSearch() {
        listening = null
        searchFocused = true
    }

    fun blurSearch() {
        searchFocused = false
    }

    fun listenOn(pet: PetEntry) {
        searchFocused = false
        listening = pet
    }

    fun bindMouse(button: Int): Boolean {
        bind(MOUSE_OFFSET + button)
        return true
    }

    fun bind(key: Int) {
        val pet = listening ?: return
        listening = null
        PetManager.bind(pet.uuid, key)
    }

    fun equip(pet: PetEntry) {
        listening = null
        PetManager.requestEquip(pet)
    }

    fun clearKey(pet: PetEntry) {
        listening = null
        PetManager.bind(pet.uuid, 0)
    }

    fun visiblePets(): List<PetEntry> {
        val query = searchField.text.trim()
        if (query.isEmpty()) return PetManager.pets
        return PetManager.pets.filter { it.name.lowercase().contains(query) }
    }
}

fun openPetsList() = mc.executeLater {
    val existing = mc.gui.screen() as? ClickGUI
    val screen = existing ?: ClickGUI().also { mc.gui.setScreen(it) }
    screen.openView(PetsView, standalone = existing == null)
}
