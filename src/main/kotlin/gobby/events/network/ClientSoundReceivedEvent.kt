package gobby.events.network

import gobby.events.Events
import net.minecraft.sounds.SoundEvent

class ClientSoundReceivedEvent(val sound: SoundEvent, val pitch: Float, val volume: Float) : Events.Cancelable<Unit>()
