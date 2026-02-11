package gobby.events.render

import gobby.events.Events

open class LightTexEvent : Events()


class GammaEvent(var gamma: Float) : LightTexEvent()