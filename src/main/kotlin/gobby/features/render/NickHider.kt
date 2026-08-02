package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.StringSetting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

object NickHider : Module("Nick Hider", "Visually changes your IGN", Category.RENDER) {

    private val nick by StringSetting("Nick", "", desc = "Name to show instead of your IGN")

    private fun target(): Pair<String, String>? {
        if (!enabled) return null
        val fake = nick.ifBlank { return null }
        val real = mc.user?.name ?: return null
        return if (real == fake) null else real to fake
    }

    fun replace(text: String): String {
        val (real, fake) = target() ?: return text
        return if (real in text) text.replace(real, fake) else text
    }

    fun replaceComponent(component: Component): Component {
        val (real, fake) = target() ?: return component
        if (real !in component.string) return component
        val result = Component.empty()
        component.visit(FormattedText.StyledContentConsumer<Unit> { style, str ->
            result.append(Component.literal(str.replace(real, fake)).setStyle(style))
            Optional.empty()
        }, Style.EMPTY)
        return result
    }
}
