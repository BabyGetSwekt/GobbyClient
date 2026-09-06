package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.ServerTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render2DEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.mixin.accessor.BossHealthOverlayAccessor
import gobby.mixin.accessor.MoveEntityPacketAccessor
import gobby.pathfinder.etherwarp.EtherwarpRaycaster
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.PlayerUtils
import gobby.utils.Utils.equalsOneOf
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.Interpolate
import gobby.utils.render.RenderUtils.drawStringInWorld
import gobby.utils.render.TitleUtils
import gobby.utils.rotation.AngleUtils.calcAimAngles
import gobby.utils.rotation.AngleUtils.calcAimAnglesBetween
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.dungeonTeammates
import gobby.utils.skyblock.dungeon.DungeonUtils.myDungeonClass
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.ScanUtils.currentRoom
import gobby.utils.skyblockID
import gobby.utils.textureValue
import gobby.utils.timer.Clock
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

object BloodCampHelper : Module(
    "Bloodcamp Helper", "Various helpers for blood camping",
    Category.DUNGEONS
) {

    private val movePrediction by BooleanSetting("Move Prediction", true, desc = "Predicts when the watcher will move after its initial spawns, only works on floor 7")
    private val killTitle by BooleanSetting("Kill Title", true, desc = "Shows a title telling you when to kill the initial spawns")
        .withDependency { movePrediction }
    private val bloodAssist by BooleanSetting("Blood Camp Assist", true, desc = "Draws boxes on mobs spawning in the blood room, mobs spawn randomly between 37 and 41 ticks so this is not perfectly accurate")
    private val watcherBar by BooleanSetting("Watcher Bar", true, desc = "Adds the remaining mob count to the watcher boss bar")
    private val autoBloodCamp by BooleanSetting("Auto Blood Camp", false, desc = "Automatically blood camps when you've killed the first 4 mobs. You MUST hold a claymore, giant sword or midas sword")

    private val inClear: Boolean get() = enabled && inDungeons && !inBoss
    private val inBloodRoom: Boolean get() = inClear && currentRoom?.data?.name == "Blood"

    private var watcher: Zombie? = null
    private var firstSpawns = true
    private var tickTime = 0L
    private var bloodStartedAt: Long? = null
    private var killTitleAt: Long? = null
    private var killTitleShown = false
    private var spawnedMobs = 0
    private var watcherRemaining = -1
    private var camping = false
    private var announced = false
    private var ownsAngleLock = false
    private var target: LivingEntity? = null
    private val shotClock = Clock()

    private data class Spawn(
        val startVector: Vec3, val started: Long, val firstSpawns: Boolean,
        var lastPosition: Vec3, val clock: Clock = Clock(), var totalDelta: Vec3 = Vec3.ZERO,
        var currVector: Vec3 = startVector, var endVector: Vec3 = startVector, var endUpdated: Long = started,
        var speed: Vec3 = Vec3.ZERO, var lastEnd: Vec3? = null
    )

    private val spawns = ConcurrentHashMap<ArmorStand, Spawn>()

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        when (val packet = event.packet) {
            is ClientboundMoveEntityPacket -> trackSpawn(packet)
            is ClientboundSetEquipmentPacket -> findWatcher(packet)
            is ClientboundRemoveEntitiesPacket -> onEntitiesRemoved(packet)
        }
    }

    private fun trackSpawn(packet: ClientboundMoveEntityPacket) {
        if (!inBloodRoom || (!bloodAssist && !autoBloodCamp)) return
        val moved = packet as MoveEntityPacketAccessor
        if (moved.deltaX == 0.toShort() && moved.deltaY == 0.toShort() && moved.deltaZ == 0.toShort()) return
        val stand = packet.getEntity(mc.level ?: return) as? ArmorStand ?: return
        if (watcher?.distanceTo(stand)?.let { it > 20f } == true || !stand.isBloodStand) return

        val position = stand.position()
        spawns.getOrPut(stand) { Spawn(position, tickTime, firstSpawns, position) }.apply {
            totalDelta = totalDelta.add(position.subtract(lastPosition))
            lastPosition = position
            val heading = if (totalDelta.lengthSqr() > 0) totalDelta.normalize() else Vec3.ZERO
            lastEnd = endVector
            endVector = startVector.add(heading.scale(if (this.firstSpawns) 16.1 else 11.9))
            endUpdated = tickTime
            speed = position.subtract(startVector).scale(1.0 / (tickTime - started).coerceAtLeast(1L))
            currVector = position
        }
    }

    private fun findWatcher(packet: ClientboundSetEquipmentPacket) {
        if (!inClear || watcher != null) return
        val head = packet.slots.firstOrNull { it.first == EquipmentSlot.HEAD }?.second ?: return
        if (head.textureValue !in WATCHER_SKULLS) return
        mc.execute { watcher = mc.level?.getEntity(packet.entity) as? Zombie }
    }

    private fun onEntitiesRemoved(packet: ClientboundRemoveEntitiesPacket) {
        if (packet.entityIds.any { it == watcher?.id }) watcher = null
        if (!autoBloodCamp || !inClear) return
        val level = mc.level ?: return
        packet.entityIds.forEach { id -> countSpawn(level.getEntity(id) as? ArmorStand ?: return@forEach) }
    }

    private fun countSpawn(stand: ArmorStand) {
        if (!stand.isBloodStand) return
        val delta = Vec3(stand.x - stand.xOld, stand.y - stand.yOld, stand.z - stand.zOld)
        if (delta.lengthSqr() == 0.0 && stand.x.onGrid && stand.z.onGrid) return
        spawnedMobs++
    }

    private val ArmorStand.isBloodStand: Boolean
        get() = getItemBySlot(EquipmentSlot.HEAD).textureValue in MOB_SKULLS

    private val Double.onGrid: Boolean get() = (this % 1 + 1) % 1 == 0.5

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inClear) return
        if (BLOOD_START.matches(event.message)) return run {
            bloodStartedAt = tickTime
            killTitleShown = false
            spawnedMobs = 0
        }
        if (!BLOOD_MOVE.matches(event.message)) return
        firstSpawns = false
        if (!movePrediction) return
        val elapsed = (tickTime - (bloodStartedAt ?: return)) / 1000
        killTitleAt = tickTime + predictedMoveTicks(elapsed) * 50L
    }

    private fun predictedMoveTicks(elapsed: Long): Long = when (elapsed) {
        in 31..33 -> 36
        in 28..30 -> 33
        in 25..27 -> 30
        in 22..24 -> 27
        in 1..21 -> 24
        else -> elapsed + 3
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        tickTime += 50
        if (tickTime < (killTitleAt ?: return)) return
        killTitleAt = null
        killTitleShown = true
        if (killTitle) TitleUtils.displayStyledTitleTicks("Kill Mobs", 40, Color.WHITE)
    }

    @SubscribeEvent
    fun onRender2D(event: Render2DEvent) {
        if (!watcherBar || !inClear) return
        val total = 12 + dungeonFloor
        (mc.gui.hud.bossOverlay as BossHealthOverlayAccessor).events.values.forEach { bar ->
            if (!bar.name.string.noControlCodes.startsWith("The Watcher") || bar.progress < 0.05f) return@forEach
            watcherRemaining = (total * bar.progress).roundToInt()
            bar.name = Component.literal("The Watcher $watcherRemaining/$total")
        }
    }

    @SubscribeEvent
    fun onCampTick(event: ClientTickEvent.Post) {
        spawns.keys.removeIf { !it.isAlive }
        if (!autoBloodCamp || !inBloodRoom || myDungeonClass != DungeonClass.Mage) return stopCamping()
        announceOnce()
        if (watcherRemaining == 0) return stopCamping()
        if (!mc.player?.mainHandItem?.skyblockID.equalsOneOf(CAMP_WEAPONS)) return stopCamping(swapped = true)
        if (!killTitleShown || spawnedMobs < 4) return releaseAim()
        camping = true
        target = nearestBloodMob()
        if (target == null && nextSpawn() == null) return releaseAim()
        if (!ownsAngleLock) RotationUtils.startAngleLock(220L, 0.55f, ::aimAngles)
        ownsAngleLock = true
        val mob = target ?: return
        if (!shotClock.hasTimePassed(250L) || !onTarget(mob.boundingBox.center)) return
        shotClock.update()
        PlayerUtils.swingHand()
    }

    private fun nearestBloodMob(): LivingEntity? {
        val self = mc.player ?: return null
        return mc.level?.entitiesForRendering()
            ?.filterIsInstance<LivingEntity>()
            ?.filter { it.isBloodMob(self) }
            ?.minByOrNull { it.distanceToSqr(self) }
    }

    private fun LivingEntity.isBloodMob(self: Player): Boolean {
        if (this === self || this is ArmorStand || isRemoved || !isAlive || health <= 0f) return false
        if (!inRoom || (this is Player && gameProfile.name in dungeonTeammates)) return false
        return this is Silverfish || BLOOD_MOB_TAG.containsMatchIn(nameTag)
    }

    private val LivingEntity.inRoom: Boolean
        get() = currentRoom?.roomComponents.orEmpty().any { it.vec2 == ScanUtils.getRoomCenter(blockPosition().x, blockPosition().z) }

    private val LivingEntity.nameTag: String
        get() = (mc.level?.getEntity(id + 1) as? ArmorStand)?.customName?.string?.noControlCodes?.trim().orEmpty()

    private fun onTarget(point: Vec3): Boolean {
        val player = mc.player ?: return false
        val (yaw, pitch) = calcAimAngles(point) ?: return false
        if (abs(Mth.wrapDegrees(player.yRot - yaw)) > 14f || abs(player.xRot - pitch) > 14f) return false
        val eye = Interpolate.interpolatedEyePos()
        return EtherwarpRaycaster.transmission(eye, point.subtract(eye))?.equals(BlockPos.containing(point)) != false
    }

    private fun aimAngles(): Pair<Float, Float>? =
        aimPoint()?.let { calcAimAnglesBetween(Interpolate.interpolatedEyePos(), it) }

    private fun aimPoint(): Vec3? =
        target?.takeIf { it.isAlive }?.let { Interpolate.getLerpedBox(it).center } ?: predictedSpawnPoint()

    private fun predictedSpawnPoint(): Vec3? = nextSpawn()?.let { (stand, spawn) ->
        lerp(spawn.endPoint, Interpolate.getRenderPosition(stand), spawn.travelProgress).add(0.0, 2.0, 0.0)
    }

    private fun nextSpawn(): Map.Entry<ArmorStand, Spawn>? =
        spawns.entries.filter { it.key.isAlive && it.value.remainingMs > 0 }.minByOrNull { it.value.remainingMs }

    private fun releaseAim() {
        if (ownsAngleLock) RotationUtils.stopAngleLock()
        ownsAngleLock = false
        target = null
    }

    private fun announceOnce() {
        if (announced) return
        announced = true
        modMessage("Kill initial 4 mobs, then swap to your mage weapon and chill")
    }

    private fun stopCamping(swapped: Boolean = false) {
        if (camping && swapped) errorMessage("Stopped auto bloodcamp due to item swap")
        camping = false
        releaseAim()
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !inClear || !bloodAssist) return
        val ping = averagePing()
        spawns.forEach { (stand, spawn) -> if (stand.isAlive) drawSpawn(event, stand, spawn, ping) }
    }

    private fun drawSpawn(event: Render3DEvent, stand: ArmorStand, spawn: Spawn, ping: Float) {
        val end = spawn.endPoint
        val remaining = spawn.remainingMs
        val pingPoint = Vec3(stand.x + spawn.speed.x * ping, stand.y + spawn.speed.y * ping, stand.z + spawn.speed.z * ping)

        val endBox = boxAt(end)
        if (ping < remaining) {
            draw3DBox(event.matrixStack, event.camera, boxAt(pingPoint), Color(85, 255, 85), filled = false, depthTest = true)
            draw3DBox(event.matrixStack, event.camera, endBox, Color(255, 85, 85), filled = false, depthTest = true)
        } else draw3DBox(event.matrixStack, event.camera, endBox, Color(0, 170, 170), filled = false, depthTest = true)

        drawLine3D(event.matrixStack, event.camera, spawn.currVector.add(0.0, 2.0, 0.0), end.add(0.0, 2.0, 0.0), Color(255, 85, 85))
        val seconds = (remaining - 40) / 1000f
        val color = if (seconds > 1.5f) "§a" else if (seconds > 0.5f) "§6" else if (seconds > 0f) "§c" else "§b"
        drawStringInWorld("$color${"%.2f".format(seconds)}s", end.add(0.0, 2.0, 0.0), event.matrixStack, event.camera, Color.WHITE, 0.025f)
    }

    private val Spawn.endPoint: Vec3 get() = lerp(endVector, lastEnd, min(tickTime - endUpdated, 100L) / 100f)

    private val Spawn.totalMs: Long get() = (if (firstSpawns) 2000L else 0L) + 38 * 50 + 40

    private val Spawn.remainingMs: Long get() = totalMs - (tickTime - started)

    private val Spawn.travelProgress: Float get() = (clock.getTime().toFloat() / totalMs).coerceIn(0f, 1f)

    private fun lerp(current: Vec3, last: Vec3?, factor: Float): Vec3 =
        last?.let { it.add(current.subtract(it).scale(factor.toDouble())) } ?: current

    private fun boxAt(center: Vec3): AABB =
        AABB(center.x - 0.5, center.y + 1.5, center.z - 0.5, center.x + 0.5, center.y + 2.5, center.z + 0.5)

    private fun averagePing(): Float {
        val log = mc.debugOverlay.pingLogger
        val samples = min(log.size(), 20)
        return if (samples == 0) 0f else (0 until samples).sumOf { log.get(it) }.toFloat() / samples
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        watcher = null
        spawns.clear()
        tickTime = 0
        firstSpawns = true
        bloodStartedAt = null
        killTitleAt = null
        killTitleShown = false
        spawnedMobs = 0
        watcherRemaining = -1
        announced = false
        stopCamping()
    }
}

private val BLOOD_START = Regex("""^\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\.|Ah, you've finally arrived\.|Ah, we meet again\.\.\.|So you made it this far\.\.\. interesting\.|You've managed to scratch and claw your way here, eh\?|I'm starting to get tired of seeing you around here\.\.\.|Oh\.\. hello\?|Things feel a little more roomy now, eh\?)$""")
private val BLOOD_MOVE = Regex("""^\[BOSS] The Watcher: Let's see how you can handle this\.$""")
private val BLOOD_MOB_TAG = Regex("""\b(?:Healthy|Speedy|Stealth|Golden|Boomer|Stormy)\b""")
private val CAMP_WEAPONS = setOf("DARK_CLAYMORE", "GIANTS_SWORD", "MIDAS_SWORD", "ASTRAEA", "HYPERION", "VALKYRIE", "SCYLLA")

private val WATCHER_SKULLS = setOf(
    "ewogICJ0aW1lc3RhbXAiIDogMTY5NzMwOTQxNzI1NiwKICAicHJvZmlsZUlkIiA6ICJjYjYxY2U5ODc4ZWI0NDljODA5MzliNWYxNTkwMzE1MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJWb2lkZWRUcmFzaDUxODUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTY2MmI2ZmI0YjhiNTg2ZGM0Y2RmODAzYjA0NDRkOWI0MWQyNDVjZGY2NjhkYWIzOGZhNmMwNjRhZmU4ZTQ2MSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
    "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjM1MjMyMiwKICAicHJvZmlsZUlkIiA6ICI3MmY5MTdjNWQyNDU0OTk0YjlmYzQ1YjVhM2YyMjIzMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGF0X0d1eV9Jc19NZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yNzM5ZDdmNGU2NmE3ZGIyZWE2Y2Q0MTRlNGM0YmE0MWRmN2E5MjQ1NWM5ZmM0MmNhYWIwMTQ2NjVjMzY3YWQ1IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
    "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjI5MjgzNiwKICAicHJvZmlsZUlkIiA6ICIzZDIxZTYyMTk2NzQ0Y2QwYjM3NjNkNTU3MWNlNGJlZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTcl83MUJsYWNrYmlyZCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iZjZlMWU3ZWQzNjU4NmMyZDk4MDU3MDAyYmMxYWRjOTgxZTI4ODlmN2JkN2I1YjM4NTJiYzU1Y2M3ODAyMjA0IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
    "ewogICJ0aW1lc3RhbXAiIDogMTY5NzIzODQ0NjgxMiwKICAicHJvZmlsZUlkIiA6ICJmMjc0YzRkNjI1MDQ0ZTQxOGVmYmYwNmM3NWIyMDIxMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJIeXBpZ3NlbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80Y2VjNDAwMDhlMWMzMWMxOTg0ZjRkNjUwYWJiMzQxMGYyMDM3MTE5ZmQ2MjRhZmM5NTM1NjNiNzM1MTVhMDc3IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
    "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjAwOTg2NywKICAicHJvZmlsZUlkIiA6ICJiMGQ0YjI4YmMxZDc0ODg5YWYwZTg2NjFjZWU5NmFhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaW5lU2tpbl9vcmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjM3ZGQxOGI1OTgzYTc2N2U1NTZkYzY0NDI0YWY0YjlhYmRiNzVkNGM5ZThiMDk3ODE4YWZiYzQzMWJmMGUwOSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
    "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNTkyNDIwNSwKICAicHJvZmlsZUlkIiA6ICIzZDIxZTYyMTk2NzQ0Y2QwYjM3NjNkNTU3MWNlNGJlZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTcl83MUJsYWNrYmlyZCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mNWYwZDc4ZmUzOGQxZDdmNzVmMDhjZGNmMmExODU1ZDZkYTAzMzdlMTE0YTNjNjNlM2JmM2M2MThiYzczMmIwIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
    "ewogICJ0aW1lc3RhbXAiIDogMTU4OTU1MDkyNjM2MSwKICAicHJvZmlsZUlkIiA6ICI0ZDcwNDg2ZjUwOTI0ZDMzODZiYmZjOWMxMmJhYjRhZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJzaXJGYWJpb3pzY2hlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzUxOTY3ZGI1ZTMxOTk5MTYyNTIwMjE5MDNjZjRlOTk1MmVmN2NlYzIyMGZhYWNhMWJhNzliYWZlNTkzOGJkODAiCiAgICB9CiAgfQp9",
    "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjIxMjc1NSwKICAicHJvZmlsZUlkIiA6ICI2NGRiNmMwNTliOTk0OTM2YTY0M2QwODEwODE0ZmJkMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVTaWx2ZXJEcmVhbXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWZkNjFlODA1NWY2ZWU5N2FiNWI2MTk2YThkN2VjOTgwNzhhYzM3ZTAwMzc2MTU3YjZiNTIwZWFhYTJmOTNhZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
    "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjIzOTU4NiwKICAicHJvZmlsZUlkIiA6ICJhYWZmMDUwYTExOTk0NzM1YjEyNDVlNDk0MGFlZjY4NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMYXN0SW1tb3J0YWwiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVjMWRjNDdhMDRjZTU3MDAxYThiNzI2ZjAxOGNkZWY0MGI3ZWE5ZDdiZDZkODM1Y2E0OTVhMGVmMTY5Zjg5MyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"
)

private val MOB_SKULLS = setOf(
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDEwNjQwNTAsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVhNzk4NjBhY2E3OTk0MDdjMGZhYTEwYjFiYmNmNDI5OThmYWQ0ZWJjZjMxZDdhMjE0MTgwODI2YjRhYzk0ZTEifX19",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDExODY2MzYsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzQ3NzQ4NzExOTBjODc4YzlhMmM0NDk2YzFlMTAyNTdjNmM0ZWExMzgwN2Q3MmMxNWQ3YWM2YWIzYTdhOWE4ZGMifX19",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDAyMDM1NzMsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Y0NjI0YTlhOGM2OWNhMjA0NTA0YWJiMDQzZDQ3NDU2Y2Q5YjA5NzQ5YTM2MzU3NDYyMzAzZjI3NmEyMjlkNCJ9fX0=",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDExNDUyMjIsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2M5MTllNWI4ZDU2ZjA2MmEyMWQyMjRkZTE0YWY3NzFlMmY1NWQwOWI1OWU3YjA5OWQwOWRhYTU3NTQwYjc5Y2YiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDA1MzgzODIsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2E4OWY2MzAzYWY4NTg3NzYxMDkxMmRjMDRiOGIxZTg5NzI0NzUyZjBhN2VlYTA1YWI2NTQ3ZTIyODE3OWMwNmYiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDA5ODk1NTgsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzY3MjM3ZWRkYWViZGJiZGFhY2ZhOTEyODg1NTYwY2NkYzY1ZGE5M2I0YzNkNTEzNTMyODY4ZWMyM2JiNWI0NDgifX19",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDA0OTUwMjgsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZmMTg0YzE5ZTcyNTYyM2QzMjgyOGEwYTRlNzQxZTg2ZjEzNWFjNjNkYmM4MjhmZjNjODQ2ODMzOGYzNjgzYiJ9fX0=",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDEwMzA3NjUsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVjY2NkNTNmNTE5MWMyOWE5ZGM4ZjAxNzBmYmRjNGU1OWU2NjQ3NmFhZTMzZGUyN2I0NjhmMWRlMWI3Y2YzYjIifX19",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDA5MTc4NzYsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2I1YmE3NmUwMmNhYjcyZmE3ZDhhYzU0Y2VlYzg0OTk3NmFiMGIwMGEwMTA2OGQ2OGMyNjY3NjZiZjcwYzM5OTcifX19",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDA3Njk2MTQsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FhMjNjOGNkZTI5NDNjODQyNDlkZTgzNTFiYzM1NDBiZTVmOGFmYWFiYThiMmNiMDMyZmM1YWNhZDc4YTI2OWIifX19",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDA4MTg4MDMsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzkxNzFmMzViOGY1MDgxNDJiZDhjNjU0MTdkMGYzMjQxNTNhYjkxNDc3MzllZTRkMTBkZWE3MzNjYzgwZWFhMjAifX19",
    "eyJ0aW1lc3RhbXAiOjE1ODYwNDA5NTY0MjIsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzdkMTJiMmFkZTQxM2E2Y2Q3Y2NhM2M5NWU5NjFiYTlmMGFlNzE2NWZhNDFmYzdiNWQ1ZjA5NGEwMTI0MGM2MDkifX19",
    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZjM2UzMWNmYzY2NzMzMjc1YzQyZmNmYjVkOWE0NDM0MmQ2NDNiNTVjZDE0YzljNzdkMjczYTIzNTIifX19",
    "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzE2OTIxMSwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODQyMWJhNWI4ZTM1NzNlZjk3YmViNWI0MGUxNWQxNWIyMGYzMDYzMWM0YzUzMzBjM2RlZGEzMDQ3ZGYwZTkyIgogICAgfQogIH0KfQ==",
    "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzExMjUwMCwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWQyMjc3MmY3NjkwNDVmZGM1YmU4MTlhZDY4YjAxYTk3YWMwNGM2MDg4NmQyY2E3YWZlZTM5YjI4MmY3YTM4MyIKICAgIH0KICB9Cn0=",
    "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzM4Njc5NCwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWQ2N2Y5N2Q3ZjgyMTcyOWJlYjM0YTgyYzNmMTM1OTJiNDA0MzlmZTUyNDhlNzI1NzZmZGU3YWExODBiZjc3IgogICAgfQogIH0KfQ==",
    "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzIxNTkwNSwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmIzOTczYTc1MmIyNGEyZjNhYmIwMDM0MjdmNmRiZTZjYTNhNjFkYjBhMWJjZjM1MWM2ZWFiMjdlYzI3ZTUwIgogICAgfQogIH0KfQ==",
    "eyJ0aW1lc3RhbXAiOjE1NzQ0MTkzMTAxNjQsInByb2ZpbGVJZCI6Ijc1MTQ0NDgxOTFlNjQ1NDY4Yzk3MzlhNmUzOTU3YmViIiwicHJvZmlsZU5hbWUiOiJUaGFua3NNb2phbmciLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzEyNzE2ZWNiZjViOGRhMDBiMDVmMzE2ZWM2YWY2MWU4YmQwMjgwNWIyMWViOGU0NDAxNTE0NjhkYzY1NjU0OWMifX19",
    "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzAyODAxNSwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzI2MDMyNTE3MWE3YmE4NDYwODMwYzBlZWE1MTVjNzU3YTY2NWU1YjE2YTE0MjA3YmExYTMxODI3NTJiZWU4NyIKICAgIH0KICB9Cn0=",
    "ewogICJ0aW1lc3RhbXAiIDogMTU5NTQyODIyMDAyMCwKICAicHJvZmlsZUlkIiA6ICJkYTQ5OGFjNGU5Mzc0ZTVjYjYxMjdiMzgwODU1Nzk4MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJOaXRyb2hvbGljXzIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjJkOGZkM2FhNTYxN2IxZGFjMGFhZTljODFmNmRkNzBhZDkzYTU5OTQyZjQ2MGQyN2U0ZDU1YTVjYjg5MThlOCIKICAgIH0KICB9Cn0=",
    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZmYzg1NGJiODRjZjRiNzY5NzI5Nzk3M2UwMmI3OWJjMTA2OTg0NjBiNTFhNjM5YzYwZTVlNDE3NzM0ZTExIn19fQ==",
    "ewogICJ0aW1lc3RhbXAiIDogMTU4OTc5MzA2ODgzOSwKICAicHJvZmlsZUlkIiA6ICIyYzEwNjRmY2Q5MTc0MjgyODRlM2JmN2ZhYTdlM2UxYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJOYWVtZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83ZGU3YmJiZGYyMmJmZTE3OTgwZDRlMjA2ODdlMzg2ZjExZDU5ZWUxZGI2ZjhiNDc2MjM5MWI3OWE1YWM1MzJkIgogICAgfQogIH0KfQ==",
    "ewogICJ0aW1lc3RhbXAiIDogMTU5ODk3NzI1OTM1NywKICAicHJvZmlsZUlkIiA6ICJlNzkzYjJjYTdhMmY0MTI2YTA5ODA5MmQ3Yzk5NDE3YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVfSG9zdGVyX01hbiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMTAwN2M1YjcxMTRhYmVjNzM0MjA2ZDRmYzYxM2RhNGYzYTBlOTlmNzFmZjk0OWNlZGFkYzk5MDc5MTM1YTBiIgogICAgfQogIH0KfQ=="
)
