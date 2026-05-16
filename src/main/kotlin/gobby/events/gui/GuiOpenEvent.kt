package gobby.events.gui

import gobby.events.Events
import net.minecraft.client.gui.screens.Screen

class GuiOpenEvent(val screen: Screen) : Events.Cancelable<Unit>()