package gobby.gui.click

open class Module(
    val name: String,
    val description: String = "",
    val category: Category,
    val toggled: Boolean = true,
    defaultEnabled: Boolean = false,
    val hidden: Boolean = false,
    val hasToggle: Boolean = true
) {
    var enabled = defaultEnabled
    var expanded = false
    val settings = mutableListOf<Setting<*>>()

    val isAlwaysEnabled: Boolean get() = this::class.java.isAnnotationPresent(AlwaysEnabled::class.java)

    val keybindSetting: KeybindSetting? by lazy {
        if (hasToggle && toggled && !isAlwaysEnabled) {
            KeybindSetting()
        } else null
    }

    fun allSettings(): List<Setting<*>> {
        val list = settings.toMutableList()
        keybindSetting?.let { list.add(it) }
        return list
    }

    init {
        if (isAlwaysEnabled) {
            enabled = true
        }
        modules.add(this)
        keybindSetting
    }

    companion object {
        val modules = mutableListOf<Module>()

        fun getByCategory(category: Category): List<Module> = modules.filter { it.category == category && !it.hidden }
    }
}
