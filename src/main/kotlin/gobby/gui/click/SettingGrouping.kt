package gobby.gui.click

enum class SettingAlign { AUTO, LEFT, RIGHT }

class SettingSection(val title: String, val align: SettingAlign = SettingAlign.AUTO)

internal val MAIN_SECTION = SettingSection("General")

fun <S : Setting<*>> S.inGroup(section: SettingSection): S = apply { this.section = section }
