package gobby.events

import net.minecraft.network.protocol.Packet

class PacketReceivedEvent(val packet: Packet<*>) : Events.Cancelable<Unit>()

class PacketSentEvent(val packet: Packet<*>) : Events.Cancelable<Unit>()

