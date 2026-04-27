package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.SpawnParticleEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.ChatUtils.modMessage
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.projectile.FireworkRocketEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket
import net.minecraft.registry.Registries
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

object ParticleDebugger : Module("Particle Debugger", "Prints every particle spawned within range", Category.DEVELOPER) {

    private val range by NumberSetting("Range", default = 32, min = 1, max = 64, step = 1, desc = "Block radius around player")
    private val detectFireworks by BooleanSetting("Detect Fireworks", true, desc = "Also log firework rocket explosion effects (client-generated via EntityStatus 17)")

    private data class CachedFirework(val pos: Vec3d, val stack: ItemStack?)
    private val fireworkCache = HashMap<Int, CachedFirework>()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled) return
        val world = mc.world ?: return
        for (entity in world.entities) {
            if (entity is FireworkRocketEntity) {
                fireworkCache[entity.id] = CachedFirework(Vec3d(entity.x, entity.y, entity.z), runCatching { entity.stack }.getOrNull())
            }
        }
    }

    @SubscribeEvent
    fun onParticle(event: SpawnParticleEvent) {
        if (!enabled) return
        val player = mc.player ?: return
        val pos = event.pos
        val dx = pos.x - player.x; val dy = pos.y - player.y; val dz = pos.z - player.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > range) return
        val id = Registries.PARTICLE_TYPE.getId(event.type)?.toString() ?: event.type.toString()
        val x = "%.2f".format(pos.x); val y = "%.2f".format(pos.y); val z = "%.2f".format(pos.z)
        val count = event.packet.count
        val offX = "%.2f".format(event.packet.offsetX); val offY = "%.2f".format(event.packet.offsetY); val offZ = "%.2f".format(event.packet.offsetZ)
        val speed = "%.2f".format(event.packet.speed)
        modMessage("§7[Particle] §f$id §8| §bpos §f($x, $y, $z) §8| §7count §f$count §8| §7offset §f($offX, $offY, $offZ) §8| §7speed §f$speed")
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (!enabled || !detectFireworks) return
        val packet = event.packet as? EntityStatusS2CPacket ?: return
        val status = packet.status.toInt()
        val id = readEntityId(packet)
        val world = mc.world ?: return
        val liveAny = packet.getEntity(world)
        val isFirework = liveAny is FireworkRocketEntity || fireworkCache.containsKey(id)
        if (status != 17 && isFirework) {
            modMessage("§8[debug] firework status=$status entity#$id live=${liveAny?.javaClass?.simpleName}")
        }
        if (status != 17) return

        val player = mc.player ?: return
        val live = liveAny as? FireworkRocketEntity
        val pos: Vec3d = if (live != null) Vec3d(live.x, live.y, live.z) else fireworkCache[id]?.pos ?: run {
            modMessage("§c[Particle] firework status=17 — entity gone and no cache")
            return
        }
        val stack = live?.stack ?: fireworkCache[id]?.stack

        val dx = pos.x - player.x; val dy = pos.y - player.y; val dz = pos.z - player.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > range) return

        modMessage("§7[Particle] §6firework_explosion §8| §bpos §f(${"%.2f".format(pos.x)}, ${"%.2f".format(pos.y)}, ${"%.2f".format(pos.z)}) §8| §7dist §f${"%.2f".format(dist)} §8| §7live=${live != null} §7cached=${fireworkCache.containsKey(id)}")
        if (stack == null) {
            modMessage("§8 └ §7(no ItemStack for firework)")
        } else {
            modMessage("§8 └ §7item=§f${stack.item} §8| §7components=§f${stack.components.size()}")
            val details = extractFireworkDetails(stack)
            if (details.isNullOrEmpty()) {
                modMessage("§8   └ §7(no minecraft:fireworks component — Hypixel likely uses client-simulated effect)")
                stack.components.forEach { modMessage("§8   └ §7component §f${it.type} §8= §f${it.value}") }
            } else {
                details.forEach { modMessage(it) }
            }
        }
    }

    private fun readEntityId(packet: EntityStatusS2CPacket): Int? {
        val f = EntityStatusS2CPacket::class.java.getDeclaredField("entityId")
        f.isAccessible = true
        return f.getInt(packet)
    }

    fun fireworkPos(packet: EntityStatusS2CPacket): Vec3d? {
        val id = readEntityId(packet) ?: return null
        return fireworkCache[id]?.pos
    }

    private fun extractFireworkDetails(stack: ItemStack?): List<String>? {
        val s = stack ?: return null
        val fireworks = s.get(DataComponentTypes.FIREWORKS) ?: return null
        val lines = mutableListOf<String>()
        lines.add("§8 └ §7flight=§f${fireworks.flightDuration}")
        fireworks.explosions.forEachIndexed { i, e ->
            val shape = e.shape.toString().substringAfterLast('.').lowercase()
            val colors = e.colors.joinToString(",") { "#%06X".format(it) }
            val fade = if (e.fadeColors.isEmpty()) "-" else e.fadeColors.joinToString(",") { "#%06X".format(it) }
            lines.add("§8   └ §7#$i §fshape=§e$shape §fcolors=§b[$colors] §ffade=§d[$fade] §ftrail=§f${e.hasTrail} §ftwinkle=§f${e.hasTwinkle}")
        }
        return lines
    }
}
