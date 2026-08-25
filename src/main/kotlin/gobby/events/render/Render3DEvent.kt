package gobby.events.render

import gobby.events.Events
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

class Render3DEvent(
    val context: LevelRenderContext,
    val type: Type
) : Events() {

    enum class Type {
        BeforeEntity, AfterEntity
    }
}
