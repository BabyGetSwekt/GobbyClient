package gobby.events.gui

import gobby.events.Events
import net.minecraft.client.gui.screen.Screen

class GuiOpenEvent(val screen: Screen) : Events.Cancelable<Unit>()