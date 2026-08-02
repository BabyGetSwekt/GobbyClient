package gobby.features.render

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.StringSetting
import gobby.utils.ChatUtils.errorMessage
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.player.PlayerSkin
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlin.concurrent.thread

object SkinChanger : Module("Skin Changer", "Copy someones skin visually", Category.RENDER) {

    private val skinName by StringSetting("Skin", "", desc = "Player name to copy the skin from", onCommit = ::applySkin)

    private const val API = "https://mowojang.matdoes.dev/"
    private const val TIMEOUT_MS = 5000
    private val UUID_DASHES = Regex("(.{8})(.{4})(.{4})(.{4})(.{12})")

    @Volatile private var cachedSkin: PlayerSkin? = null
    @Volatile private var lastApplied = ""

    fun getSkinFor(player: AbstractClientPlayer): PlayerSkin? =
        if (enabled && player === mc.player) cachedSkin else null

    private fun applySkin(name: String) {
        if (name.isBlank() || name.equals(mc.player?.gameProfile?.name, ignoreCase = true)) {
            cachedSkin = null; lastApplied = ""; return
        }
        if (name.equals(lastApplied, ignoreCase = true) && cachedSkin != null) return
        lastApplied = name
        thread(name = "GobbyClient-SkinChanger", isDaemon = true) {
            val profile = fetchProfile(name)
            if (profile == null) {
                mc.execute { if (mc.player != null && name.equals(lastApplied, ignoreCase = true)) errorMessage("Player '$name' does not exist") }
                return@thread
            }
            mc.execute {
                if (name.equals(lastApplied, ignoreCase = true))
                    mc.skinManager.get(profile).thenAccept { opt -> opt.ifPresent { cachedSkin = it } }
            }
        }
    }

    private fun fetchProfile(name: String): GameProfile? {
        val id = getJson(API + name)?.get("id")?.asString ?: return null
        val obj = getJson("${API}session/minecraft/profile/$id") ?: return null
        val textures = obj.getAsJsonArray("properties")?.firstNotNullOfOrNull {
            val p = it.asJsonObject
            if (p.get("name").asString == "textures") p.get("value").asString else null
        } ?: return null
        val props = PropertyMap(ImmutableMultimap.of("textures", Property("textures", textures)))
        return GameProfile(dashedUuid(id), obj.get("name")?.asString ?: name, props)
    }

    private fun getJson(url: String): JsonObject? = try {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "GobbyClient")
        if (conn.responseCode != 200) null
        else JsonParser.parseString(conn.inputStream.bufferedReader().readText()).asJsonObject
    } catch (e: Exception) { null }

    private fun dashedUuid(id: String): UUID =
        UUID.fromString(if (id.contains("-")) id else id.replaceFirst(UUID_DASHES, "$1-$2-$3-$4-$5"))
}
