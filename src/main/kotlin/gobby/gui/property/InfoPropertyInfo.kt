package gobby.gui.property

import gg.essential.vigilance.data.PropertyInfo
import gg.essential.vigilance.gui.settings.SettingComponent
import gobby.gui.components.InfoPropertyComponent

class InfoPropertyInfo : PropertyInfo() {
    override fun createSettingComponent(initialValue: Any?): SettingComponent {
        return InfoPropertyComponent(initialValue as? String ?: "")
    }
}
