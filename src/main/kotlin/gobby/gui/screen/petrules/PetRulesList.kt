package gobby.gui.screen.petrules

import gobby.Gobbyclient.Companion.mc
import gobby.features.petrules.PetRule
import gobby.features.petrules.PetRules
import gobby.features.petrules.TriggerCategory
import gobby.features.petrules.TriggerOption
import gobby.gui.click.ClickGUI
import gobby.gui.click.TextField
import gobby.utils.Utils.executeLater
import gobby.utils.managers.PetEntry
import gobby.utils.managers.PetManager

private fun searchText(raw: String): String =
    raw.lowercase().filter { it.isLetterOrDigit() || it in " -_" }

internal enum class PickerStep { CLOSED, CATEGORY, OPTION, PET }

internal object PetRulesList {

    val searchField = TextField(::searchText, 32)

    var searchFocused = false
        private set

    var step = PickerStep.CLOSED
        private set

    var pickedCategory: TriggerCategory? = null
        private set

    private var pickedOption: TriggerOption? = null

    var pickerScroll = 0
        private set

    fun close() {
        searchFocused = false
        searchField.clear()
        closePicker()
    }

    fun focusSearch() {
        searchFocused = true
    }

    fun blurSearch() {
        searchFocused = false
    }

    fun startPicker() {
        pickedCategory = null
        pickedOption = null
        pickerScroll = 0
        step = PickerStep.CATEGORY
    }

    fun closePicker() {
        step = PickerStep.CLOSED
        pickedCategory = null
        pickedOption = null
        pickerScroll = 0
    }

    fun pickCategory(category: TriggerCategory) {
        pickedCategory = category
        pickerScroll = 0
        step = PickerStep.OPTION
    }

    fun pickOption(option: TriggerOption) {
        pickedOption = option
        pickerScroll = 0
        step = PickerStep.PET
    }

    fun pickPet(pet: PetEntry) {
        val category = pickedCategory
        val option = pickedOption
        if (category != null && option != null) PetRules.add(PetRule(category.id, option.id, pet.uuid))
        closePicker()
    }

    fun scrollPicker(amount: Int, rows: Int, visible: Int) {
        pickerScroll = (pickerScroll + amount).coerceIn(0, (rows - visible).coerceAtLeast(0))
    }

    fun pickerTitle(): String = when (step) {
        PickerStep.CATEGORY -> "Choose Rule Trigger"
        PickerStep.OPTION -> "Choose a Catacombs Floor"
        PickerStep.PET -> "Choose Pet"
        PickerStep.CLOSED -> ""
    }

    fun pickerRows(): List<String> = when (step) {
        PickerStep.CATEGORY -> PetRules.categories.map { it.title }
        PickerStep.OPTION -> pickedCategory?.options?.map { it.label }.orEmpty()
        PickerStep.PET -> PetManager.pets.map { it.label }
        PickerStep.CLOSED -> emptyList()
    }

    fun choose(index: Int) {
        when (step) {
            PickerStep.CATEGORY -> PetRules.categories.getOrNull(index)?.let(::pickCategory)
            PickerStep.OPTION -> pickedCategory?.options?.getOrNull(index)?.let(::pickOption)
            PickerStep.PET -> PetManager.pets.getOrNull(index)?.let(::pickPet)
            PickerStep.CLOSED -> Unit
        }
    }

    fun visibleRules(): List<PetRule> {
        val query = searchField.text.trim()
        if (query.isEmpty()) return PetRules.rules
        return PetRules.rules.filter { rule ->
            PetRules.labelOf(rule).lowercase().contains(query) ||
                PetRules.petFor(rule)?.name?.lowercase()?.contains(query) == true
        }
    }

    fun delete(rule: PetRule) = PetRules.remove(rule)
}

fun openPetRules() = mc.executeLater {
    val existing = mc.gui.screen() as? ClickGUI
    val screen = existing ?: ClickGUI().also { mc.gui.setScreen(it) }
    screen.openView(PetRulesView, standalone = existing == null)
}
