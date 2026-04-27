package gobby.events

class HotbarUpdateEvent(
    val itemBefore: String,
    val itemAfter: String,
    val slot: Int,
    val countBefore: Int,
    val countAfter: Int
) : Events()
