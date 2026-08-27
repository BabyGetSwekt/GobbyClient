package gobby.features.petrules

interface TriggerOption {

    val id: String

    val label: String
}

interface TriggerCategory {

    val id: String

    val title: String

    val options: List<TriggerOption>

    fun optionById(id: String): TriggerOption? = options.firstOrNull { it.id == id }
}
