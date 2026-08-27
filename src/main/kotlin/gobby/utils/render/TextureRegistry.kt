package gobby.utils.render

import gobby.Gobbyclient.Companion.logger
import gobby.Gobbyclient.Companion.mc
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier as ResourceLocation
import java.io.File

private class SmoothTexture(id: ResourceLocation, image: NativeImage) : DynamicTexture({ id.toString() }, image) {
    init {
        RenderSystem.getSamplerCache().let { sampler = it.getClampToEdge(FilterMode.LINEAR) }
    }
}

object TextureRegistry {

    private val attempted = mutableSetOf<ResourceLocation>()

    fun ensureRegistered(ids: Iterable<ResourceLocation>) = ids.filterNot(attempted::contains).forEach(::register)

    fun ensureRegistered(id: ResourceLocation, source: File) {
        if (!attempted.add(id)) return
        runCatching {
            source.inputStream().use { mc.textureManager.register(id, SmoothTexture(id, NativeImage.read(it))) }
        }.onFailure { logger.error("texture registration failed for {}", id, it) }
    }

    fun forget(id: ResourceLocation) {
        attempted -= id
        mc.textureManager.release(id)
    }

    private fun register(id: ResourceLocation) {
        attempted += id
        val path = "assets/${id.namespace}/${id.path}.png"
        runCatching {
            javaClass.classLoader.getResourceAsStream(path)?.use { stream ->
                mc.textureManager.register(id, SmoothTexture(id, NativeImage.read(stream)))
            } ?: error("missing resource $path")
        }.onFailure { logger.error("texture registration failed for {}", id, it) }
    }
}
