package gobby.features.dungeons.puzzles

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.SpawnParticleEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.map.MapConstants.HALF_ROOM
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.Interpolate
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.mob.BlazeEntity
import java.awt.Color

object Blaze : Module("Blaze", "Highlights blaze kill order in blaze puzzle", Category.DUNGEONS) {

    private val hideBlazes by BooleanSetting("Hide Blazes", true, desc = "Hide blazes and their nametag client-side")

    private val GREEN = Color(0, 255, 0, 255)
    private val ORANGE = Color(255, 165, 0, 255)
    private val BLUE = Color(0, 100, 255, 255)
    private val WHITE = Color(255, 255, 255, 255)
    private val COLORS = listOf(GREEN, ORANGE, BLUE)
    private val HP_REGEX = Regex("""\[Lv15]\s*♨\s*Blaze\s+[\d,]+/([\d,]+)❤""")

    private var roomCenterX = 0
    private var roomCenterZ = 0
    private var isHigher = false
    private var inBlazeRoom = false
    private val hiddenEntities = mutableSetOf<Entity>()

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        inBlazeRoom = false
        val room = event.room ?: return
        val name = room.data.name
        if (name != "Higher Blaze" && name != "Lower Blaze") return
        val center = room.roomComponents.firstOrNull() ?: return
        roomCenterX = center.x
        roomCenterZ = center.z
        isHigher = name == "Higher Blaze"
        inBlazeRoom = true
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled || !inDungeons || !inBlazeRoom) return
        val all = scanTargets()
        if (all.isEmpty()) return

        all.forEachIndexed { i, mob ->
            val color = if (i < 3) COLORS[i] else WHITE
            draw3DBox(event.matrixStack, event.camera, mob.boundingBox, color, filled = true, depthTest = false)
        }
        all.take(3).zipWithNext().forEachIndexed { i, (a, b) ->
            val from = Interpolate.interpolateEntity(a).add(0.0, a.height / 2.0, 0.0)
            val to = Interpolate.interpolateEntity(b).add(0.0, b.height / 2.0, 0.0)
            drawLine3D(event.matrixStack, event.camera, from, to, COLORS[i + 1], depthTest = false)
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled || !inBlazeRoom || !hideBlazes) {
            if (hiddenEntities.isNotEmpty()) restoreHidden()
            return
        }
        val world = mc.world ?: return
        val xRange = (roomCenterX - HALF_ROOM)..(roomCenterX + HALF_ROOM)
        val zRange = (roomCenterZ - HALF_ROOM)..(roomCenterZ + HALF_ROOM)
        world.entities.asSequence()
            .filter { it.x.toInt() in xRange && it.z.toInt() in zRange }
            .forEach { e ->
                when (e) {
                    is BlazeEntity -> { e.isInvisible = true; hiddenEntities.add(e) }
                    is ArmorStandEntity if e.customName?.string?.contains("Blaze") == true -> {
                        e.isInvisible = true
                        e.isCustomNameVisible = false
                        hiddenEntities.add(e)
                    }
                }
            }
    }

    @SubscribeEvent
    fun onParticle(event: SpawnParticleEvent) {
        if (!enabled || !inBlazeRoom || !hideBlazes) return
        val p = event.pos
        val hit = hiddenEntities.asSequence()
            .filterIsInstance<BlazeEntity>()
            .any {
                val dx = p.x - it.x
                val dy = p.y - (it.y + it.height / 2.0)
                val dz = p.z - it.z
                dx * dx + dy * dy + dz * dz < 4.0
            }
        if (hit) event.cancel()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        inBlazeRoom = false
        hiddenEntities.clear()
    }

    private fun parseMaxHp(name: String): Int? =
        HP_REGEX.find(name)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

    private fun getCorrespondingBlaze(label: ArmorStandEntity): Entity? {
        val box = label.boundingBox.offset(0.0, -1.5, 0.0).expand(0.6, 1.0, 0.6)
        return label.entityWorld.getOtherEntities(label, box) { it is BlazeEntity }.firstOrNull()
    }

    private fun scanTargets(): List<Entity> {
        val world = mc.world ?: return emptyList()
        val xRange = (roomCenterX - HALF_ROOM)..(roomCenterX + HALF_ROOM)
        val zRange = (roomCenterZ - HALF_ROOM)..(roomCenterZ + HALF_ROOM)
        return world.entities
            .filterIsInstance<ArmorStandEntity>()
            .filter { it.x.toInt() in xRange && it.z.toInt() in zRange }
            .mapNotNull { stand ->
                val custom = stand.customName?.string ?: return@mapNotNull null
                val hp = parseMaxHp(custom) ?: return@mapNotNull null
                val mob = getCorrespondingBlaze(stand)?.takeIf { it.isAlive } ?: return@mapNotNull null
                hp to mob
            }
            .sortedBy { if (isHigher) it.first else -it.first }
            .map { it.second }
    }

    private fun restoreHidden() {
        hiddenEntities.forEach {
            it.isInvisible = false
            if (it is ArmorStandEntity) it.isCustomNameVisible = true
        }
        hiddenEntities.clear()
    }
}
