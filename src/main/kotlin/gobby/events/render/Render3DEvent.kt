package gobby.events.render

import gobby.events.Events
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext


class Render3DEvent(
    val context: WorldRenderContext,
    val type: Type
) : Events() {

    enum class Type {
        BeforeEntity, AfterEntity
    }
}
