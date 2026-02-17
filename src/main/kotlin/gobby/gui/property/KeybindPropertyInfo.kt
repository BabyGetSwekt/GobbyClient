package gobby.gui.property

import gg.essential.vigilance.data.PropertyInfo
import gg.essential.vigilance.gui.settings.SettingComponent
import gobby.gui.components.KeybindPropertyComponent

class KeybindPropertyInfo : PropertyInfo() {
    override fun createSettingComponent(initialValue: Any?): SettingComponent {
        return KeybindPropertyComponent(initialValue as? Int ?: -1)
    }
}
