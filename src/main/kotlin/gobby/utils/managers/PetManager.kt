package gobby.utils.managers

import gobby.Gobbyclient.Companion.logger
import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.features.skyblock.PetsKeybind
import gobby.utils.ChatUtils
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ConfigUtils
import gobby.utils.ContainerClicks
import gobby.utils.LocationUtils
import gobby.utils.getLoreStrings
import gobby.utils.itemDataJson
import gobby.utils.render.FaceTextures
import gobby.utils.skinUrl
import gobby.utils.stringOrNull
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.item.ItemStack

const val PETS_FOLDER = "pets"

private val PET_SLOTS: List<Int> = listOf(10..16, 19..25, 28..34, 37..43).flatMap { it }

private val ItemStack.petId: String?
    get() = itemDataJson("petInfo")?.stringOrNull("uniqueId")

data class PetEntry(
    val uuid: String,
    val name: String,
    val level: Int,
    val favorite: Boolean = false,
    val hasSkin: Boolean = false,
    var key: Int = 0
) {
    val label: String get() = "[Lvl $level] $name"
}

data class PetsData(
    var lastScan: Long = 0L,
    var preventUnequip: Boolean = false,
    var closeIfEquipped: Boolean = false,
    var swapOutsideMenu: Boolean = false,
    val pets: MutableList<PetEntry> = mutableListOf()
)

object PetManager {

    private enum class State { IDLE, WAITING_SCREEN, COLLECTING }

    private val config = ConfigUtils.makeConfig("pets", PETS_FOLDER) { PetsData() }
    private val collected = HashMap<Int, ItemStack>()

    private var state = State.IDLE
    private var syncId = -1
    private var ticks = 0
    private var quiet = 0
    private var pendingEquip: PetEntry? = null

    val pets: List<PetEntry> get() = config.data.pets

    val scanned: Boolean get() = config.data.lastScan != 0L

    val isScanning: Boolean get() = state != State.IDLE

    var preventUnequip: Boolean
        get() = config.data.preventUnequip
        set(value) = config.edit { this.preventUnequip = value }

    var closeIfEquipped: Boolean
        get() = config.data.closeIfEquipped
        set(value) = config.edit { this.closeIfEquipped = value }

    var swapOutsideMenu: Boolean
        get() = config.data.swapOutsideMenu
        set(value) = config.edit { this.swapOutsideMenu = value }

    fun keyOf(uuid: String): Int = pets.firstOrNull { it.uuid == uuid }?.key ?: 0

    fun bind(uuid: String, key: Int) = config.edit { this.pets.firstOrNull { it.uuid == uuid }?.key = key }

    fun scan(): Boolean {
        if (isScanning || !LocationUtils.onSkyblock) return false
        pendingEquip = null
        return open()
    }

    fun requestEquip(pet: PetEntry) {
        val screen = petsScreen()
        if (screen != null) return equip(screen, pet)
        if (isScanning) return
        if (!LocationUtils.onSkyblock) return errorMessage("Join Skyblock to swap pets")
        pendingEquip = pet
        open()
    }

    private fun open(): Boolean {
        state = State.WAITING_SCREEN
        ticks = 0
        ChatUtils.sendCommand("pets")
        return true
    }

    private fun petsScreen(): AbstractContainerScreen<*>? =
        (mc.gui.screen() as? AbstractContainerScreen<*>)?.takeIf { it.title.string.contains("Pets") }

    @SubscribeEvent
    fun onGuiKey(event: KeyPressGuiEvent) {
        val pet = pets.firstOrNull { it.key != 0 && it.key == event.key } ?: return
        val screen = petsScreen()
        if (screen != null) {
            event.cancel()
            return equip(screen, pet)
        }
        if (mc.gui.screen() != null || !swapOutsideMenu) return
        event.cancel()
        requestEquip(pet)
    }

    private fun equip(screen: AbstractContainerScreen<*>, pet: PetEntry) {
        val slot = PET_SLOTS.firstOrNull { screen.menu.slots.getOrNull(it)?.item?.petId == pet.uuid } ?: return
        if (isEquipped(screen.menu.slots[slot].item)) {
            if (closeIfEquipped) return screen.onClose()
            if (preventUnequip) return errorMessage("Pet already equipped!")
        }
        ContainerClicks.pickup(screen.menu.containerId, slot)
    }

    private fun isEquipped(stack: ItemStack): Boolean =
        stack.getLoreStrings().any { it.contains("Click to despawn!") }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (state == State.IDLE) return
        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> if (acceptScreen(packet.title.string, packet.containerId)) event.cancel()
            is ClientboundContainerSetContentPacket -> acceptContent(packet.containerId, packet.items())
            is ClientboundContainerSetSlotPacket -> acceptSlot(packet.containerId, packet.slot, packet.item)
        }
    }

    private fun acceptScreen(title: String, containerId: Int): Boolean {
        if (state != State.WAITING_SCREEN) return false
        if (!title.contains("Pets")) {
            reset()
            return false
        }
        syncId = containerId
        state = State.COLLECTING
        quiet = 0
        collected.clear()
        return true
    }

    private fun acceptContent(containerId: Int, items: List<ItemStack>) {
        if (state != State.COLLECTING || containerId != syncId) return
        PET_SLOTS.forEach { slot -> items.getOrNull(slot)?.takeUnless(ItemStack::isEmpty)?.let { collected[slot] = it } }
        quiet = 0
        tryPendingEquip()
    }

    private fun acceptSlot(containerId: Int, slot: Int, stack: ItemStack) {
        if (state != State.COLLECTING || containerId != syncId || slot !in PET_SLOTS) return
        if (!stack.isEmpty) collected[slot] = stack
        quiet = 0
        tryPendingEquip()
    }

    private fun tryPendingEquip() {
        val pet = pendingEquip ?: return
        val hit = collected.entries.firstOrNull { it.value.petId == pet.uuid } ?: return
        if (isEquipped(hit.value)) {
            if (preventUnequip) errorMessage("Pet already equipped!")
            if (preventUnequip || closeIfEquipped) return closeAndReset()
        }
        ContainerClicks.pickup(syncId, hit.key)
        closeAndReset()
    }

    private fun closeAndReset() {
        ContainerClicks.close(syncId)
        reset()
    }

    private fun finish() {
        pendingEquip?.let {
            errorMessage("Could not find ${it.name} in the Pets menu")
            return closeAndReset()
        }
        val found = PET_SLOTS.mapNotNull { slot -> collected[slot]?.let(::toEntry) }
        store(found)
        FaceTextures.sync(PETS_FOLDER, collected.values.mapNotNull { stack -> stack.petId?.let { id -> stack.skinUrl?.let { id to it } } }.toMap())
        logger.info("[GobbyPets] scan done: {} filled pet slots, {} pets parsed", collected.size, found.size)
        closeAndReset()
    }

    private fun store(found: List<PetEntry>) = config.edit {
        val keys = this.pets.associate { it.uuid to it.key }
        this.pets.clear()
        this.pets.addAll(found.map { it.copy(key = keys[it.uuid] ?: it.key) })
        this.lastScan = System.currentTimeMillis()
    }

    private fun toEntry(stack: ItemStack): PetEntry? {
        val uuid = stack.petId ?: return null
        val parsed = PetsKeybind.parse(stack.hoverName.string) ?: return null
        return PetEntry(uuid, parsed.name, parsed.level, parsed.favorite, parsed.hasSkin)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (state == State.IDLE) return
        val expired = ++ticks > 100
        if (state != State.COLLECTING) return if (expired) reset() else Unit
        if (expired || (collected.isNotEmpty() && ++quiet > 6)) finish()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = reset()

    private fun reset() {
        state = State.IDLE
        syncId = -1
        ticks = 0
        quiet = 0
        collected.clear()
        pendingEquip = null
    }
}
