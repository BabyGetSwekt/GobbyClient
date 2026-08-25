package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier as ResourceLocation

object TextureRegistry {

    private val registered = mutableSetOf<ResourceLocation>()

    fun ensureRegistered(ids: Iterable<ResourceLocation>) = ids.filterNot(registered::contains).forEach(::register)

    private fun register(id: ResourceLocation) {
        val path = "assets/${id.namespace}/${id.path}.png"
        runCatching {
            javaClass.classLoader.getResourceAsStream(path)?.use { stream ->
                mc.textureManager.register(id, DynamicTexture({ id.toString() }, NativeImage.read(stream)))
            } ?: error("missing resource $path")
        }.onSuccess { registered += id }
            .onFailure { println("[GobbyClient] texture registration failed for $id: $it") }
    }
}
