package gobby.events

import net.minecraft.world.entity.EquipmentSlot

class ArmorUpdateEvent(
    val slot: EquipmentSlot,
    val uuidBefore: String,
    val uuidAfter: String
) : Events()
