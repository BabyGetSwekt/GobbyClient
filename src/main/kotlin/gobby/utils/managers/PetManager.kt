package gobby.utils.managers

import gobby.Gobbyclient.Companion.logger
import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.features.skyblock.PetsKeybind
import gobby.utils.ChatUtils
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
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

private val PET_SLOTS: List<Int> = listOf(10..16, 19..25, 28..34, 37..43).flatten()

private val PETS_TITLE = Regex("""^(\(\d+/\d+\) )?Pets$""")

private fun isPetsTitle(title: String): Boolean = PETS_TITLE.matches(title.noControlCodes.trim())

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
    var preventUnequip: Boolean = true,
    var closeIfEquipped: Boolean = false,
    var swapOutsideMenu: Boolean = false,
    var equippedUuid: String = "",
    val pets: MutableList<PetEntry> = mutableListOf()
)

object PetManager : SilentContainer {

    private enum class State { IDLE, WAITING_SCREEN, COLLECTING }

    private val config = ConfigUtils.makeConfig("pets", PETS_FOLDER) { PetsData() }
    private val collected = HashMap<Int, ItemStack>()

    private var state = State.IDLE
    private var syncId = -1
    private var ticks = 0
    private var quiet = 0
    private var pendingEquip: PetEntry? = null
    private var awaitingSummon: PetEntry? = null
    private var summonTicks = 0
    private var petsRequested = 0

    val pets: List<PetEntry> get() = config.data.pets

    val scanned: Boolean get() = config.data.lastScan != 0L

    val equipped: PetEntry? get() = pets.firstOrNull { it.uuid == config.data.equippedUuid }

    val isScanning: Boolean get() = state != State.IDLE

    override val isRunning: Boolean get() = isScanning

    override fun yieldToScreen() = closeAndReset()

    init {
        SilentContainerFlow.register(this)
    }

    val isSwapping: Boolean get() = isScanning || awaitingSummon != null

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

    fun requestEquip(pet: PetEntry, announce: Boolean = true) {
        if (equipped?.uuid == pet.uuid) {
            if (announce && preventUnequip) errorMessage("Pet already equipped!")
            return
        }
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
        petsRequested = 100
        ChatUtils.sendCommand("pets")
        return true
    }

    private fun petsScreen(): AbstractContainerScreen<*>? =
        (mc.gui.screen() as? AbstractContainerScreen<*>)?.takeIf { isPetsTitle(it.title.string) }

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
        val slot = PET_SLOTS.firstOrNull { screen.menu.slots.getOrNull(it)?.item?.petId == pet.uuid }
        if (slot == null) {
            screen.onClose()
            return errorMessage("Pet not found, refresh it")
        }
        val alreadyOn = isEquipped(screen.menu.slots[slot].item)
        if (alreadyOn) {
            rememberEquipped(pet.uuid)
            if (closeIfEquipped) return screen.onClose()
            if (preventUnequip) return errorMessage("Pet already equipped!")
        }
        ContainerClicks.pickup(screen.menu.containerId, slot)
        if (alreadyOn) rememberEquipped("") else expectSummon(pet)
    }

    private fun expectSummon(pet: PetEntry) {
        awaitingSummon = pet
        summonTicks = 60
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        val text = event.message.trim()
        if (text.startsWith("You despawned your ")) return rememberEquipped("")
        if (!text.startsWith("You summoned your ") || !text.endsWith("!")) return
        val name = text.removeSurrounding("You summoned your ", "!")
        val pet = awaitingSummon?.takeIf { it.name == name } ?: pets.filter { it.name == name }.singleOrNull()
        rememberEquipped(pet?.uuid.orEmpty())
        val ours = awaitingSummon ?: return
        awaitingSummon = null
        event.cancel()
        modMessage("§aSummoned §6${ours.label}§a!")
    }

    private fun readEquippedFrom(screen: AbstractContainerScreen<*>) {
        val items = PET_SLOTS.mapNotNull { screen.menu.slots.getOrNull(it)?.item?.takeUnless(ItemStack::isEmpty) }
        if (items.isEmpty()) return
        rememberEquipped(items.firstOrNull(::isEquipped)?.petId.orEmpty())
    }

    private fun rememberEquipped(uuid: String) {
        if (config.data.equippedUuid == uuid) return
        config.edit { this.equippedUuid = uuid }
    }

    private fun isEquipped(stack: ItemStack): Boolean =
        stack.getLoreStrings().any { it.contains("Click to despawn!") }

    fun holdingItem(stack: ItemStack): String? = stack.itemDataJson("petInfo")?.stringOrNull("heldItem")

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (suppressStrayPets(event)) return
        if (state == State.IDLE) return
        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> if (acceptScreen(packet.title.string, packet.containerId)) event.cancel()
            is ClientboundContainerSetContentPacket -> acceptContent(packet.containerId, packet.items())
            is ClientboundContainerSetSlotPacket -> acceptSlot(packet.containerId, packet.slot, packet.item)
        }
    }

    private fun suppressStrayPets(event: PacketReceivedEvent): Boolean {
        if (state != State.IDLE || petsRequested <= 0) return false
        val packet = event.packet as? ClientboundOpenScreenPacket ?: return false
        if (!isPetsTitle(packet.title.string)) return false
        petsRequested = 0
        ContainerClicks.close(packet.containerId)
        event.cancel()
        return true
    }

    private fun acceptScreen(title: String, containerId: Int): Boolean {
        if (!isPetsTitle(title)) {
            logger.info("[GobbyPets] another container opened, dropping the scan before any click")
            reset()
            return false
        }
        if (state != State.WAITING_SCREEN) return false
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
        val alreadyOn = isEquipped(hit.value)
        if (alreadyOn) {
            rememberEquipped(pet.uuid)
            if (preventUnequip) errorMessage("Pet already equipped!")
            if (preventUnequip || closeIfEquipped) return closeAndReset()
        }
        ContainerClicks.pickup(syncId, hit.key)
        if (alreadyOn) rememberEquipped("") else expectSummon(pet)
        closeAndReset()
    }

    private fun closeAndReset() {
        ContainerClicks.close(syncId)
        reset()
    }

    private fun finish() {
        if (pendingEquip != null) {
            errorMessage("Pet not found, refresh it")
            return closeAndReset()
        }
        val found = PET_SLOTS.mapNotNull { slot -> collected[slot]?.let(::toEntry) }
        store(found, collected.values.firstOrNull(::isEquipped)?.petId.orEmpty())
        FaceTextures.sync(PETS_FOLDER, collected.values.mapNotNull { stack -> stack.petId?.let { id -> stack.skinUrl?.let { id to it } } }.toMap())
        logger.info("[GobbyPets] scan done: {} filled pet slots, {} pets parsed", collected.size, found.size)
        closeAndReset()
    }

    private fun store(found: List<PetEntry>, equippedUuid: String) = config.edit {
        this.equippedUuid = equippedUuid
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
        if (awaitingSummon != null && --summonTicks <= 0) awaitingSummon = null
        if (petsRequested > 0) petsRequested--
        petsScreen()?.let(::readEquippedFrom)
        if (state == State.IDLE) return
        val expired = ++ticks > 100
        if (state != State.COLLECTING) return if (expired) reset() else Unit
        if (expired || (collected.isNotEmpty() && ++quiet > 6)) finish()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        awaitingSummon = null
        reset()
    }

    private fun reset() {
        state = State.IDLE
        petsRequested = 0
        syncId = -1
        ticks = 0
        quiet = 0
        collected.clear()
        pendingEquip = null
    }
}
