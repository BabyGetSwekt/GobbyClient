package gobby.utils.managers

private const val SCREEN_TITLE = "Wardrobe"
private const val COMMAND = "wardrobe"
private const val SLOT_OFFSET = 36
private const val MAX_SLOT = 45

object WardrobeManager : ContainerSlotClicker(SCREEN_TITLE, COMMAND, MAX_SLOT) {

    fun swap(wardrobeSlot: Int) = request { openFor(SLOT_OFFSET + wardrobeSlot - 1) }
}
