package gobby.utils.render

import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.text.Text.translatable
import net.minecraft.text.TranslatableTextContent

object ScreenUtils {

    fun Screen.getKey(): String {
        val title: Text = this.title
        val content = title.content
        val key: String = if (content is TranslatableTextContent) {
            content.key
        } else {
            title.string
        }
        return key
    }
}