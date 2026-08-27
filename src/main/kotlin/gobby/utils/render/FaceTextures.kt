package gobby.utils.render

import gobby.Gobbyclient.Companion.MOD_ID
import gobby.Gobbyclient.Companion.logger
import gobby.Gobbyclient.Companion.mc
import gobby.utils.ConfigUtils
import net.minecraft.resources.Identifier as ResourceLocation
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

private const val FACE_X = 8
private const val FACE_Y = 8
private const val FACE_SIZE = 8
private const val HAT_X = 40
private const val SKIN_WIDTH = 64
private const val FACE_SCALE = 8

/**
 * Keeps the cropped face of a skin on disk per folder, so head icons survive restarts and are
 * downloaded only when the skin behind them actually changed.
 */
object FaceTextures {

    private val ready = ConcurrentHashMap.newKeySet<String>()

    fun textureFor(folder: String, key: String): ResourceLocation? {
        if (cacheKey(folder, key) !in ready) return null
        val id = idOf(folder, key)
        TextureRegistry.ensureRegistered(id, fileFor(folder, key))
        return id
    }

    /**
     * Brings the folder in line with the wanted skins, downloading what is missing or changed and
     * dropping what nobody asks for any more.
     */
    fun sync(folder: String, skins: Map<String, String>) {
        val index = ConfigUtils.makeConfig("index", folder) { HashMap<String, String>() }
        val known = index.data
        known.keys.filterNot { it in skins }.forEach { forget(folder, it) }
        skins.filter { (key, url) -> known[key] != url }.keys.forEach { forget(folder, it) }
        index.save(HashMap(skins))
        skins.keys.filter { fileFor(folder, it).exists() }.forEach { ready += cacheKey(folder, it) }
        val missing = skins.filterKeys { cacheKey(folder, it) !in ready }
        if (missing.isEmpty()) return
        CompletableFuture.runAsync { missing.forEach { (key, url) -> download(folder, key, url) } }
    }

    private fun forget(folder: String, key: String) {
        ready -= cacheKey(folder, key)
        mc.execute { TextureRegistry.forget(idOf(folder, key)) }
        fileFor(folder, key).delete()
    }

    private fun download(folder: String, key: String, url: String) {
        runCatching {
            val skin = URI(url).toURL().openStream().use(ImageIO::read) ?: error("unreadable skin")
            ConfigUtils.directory(folder)
            ImageIO.write(faceOf(skin), "png", fileFor(folder, key))
            ready += cacheKey(folder, key)
        }.onFailure { logger.error("face download failed for {}", key, it) }
    }

    private fun faceOf(skin: BufferedImage): BufferedImage {
        val scale = skin.width / SKIN_WIDTH
        val size = FACE_SIZE * scale
        val out = BufferedImage(FACE_SIZE * FACE_SCALE, FACE_SIZE * FACE_SCALE, BufferedImage.TYPE_INT_ARGB)
        val canvas = out.createGraphics()
        canvas.drawImage(skin.getSubimage(FACE_X * scale, FACE_Y * scale, size, size), 0, 0, out.width, out.height, null)
        if (skin.width >= (HAT_X + FACE_SIZE) * scale) {
            canvas.drawImage(skin.getSubimage(HAT_X * scale, FACE_Y * scale, size, size), 0, 0, out.width, out.height, null)
        }
        canvas.dispose()
        return out
    }

    private fun fileFor(folder: String, key: String): File = ConfigUtils.file("$key.png", folder)

    private fun idOf(folder: String, key: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "$folder/${key.lowercase()}")

    private fun cacheKey(folder: String, key: String) = "$folder/$key"
}
