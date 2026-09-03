package gobby.features.dungeons

import gobby.Gobbyclient.Companion.logger
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
import gobby.events.render.renderTickCounter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.mixin.accessor.BossHealthOverlayAccessor
import gobby.mixin.accessor.MoveEntityPacketAccessor
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
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.dungeonTeammates
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.ScanUtils.currentRoom
import gobby.utils.skyblock.dungeon.DungeonUtils.myDungeonClass
import gobby.utils.skyblockID
import gobby.utils.textureValue
import gobby.utils.timer.Clock
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

object BloodcampHelper : Module(
    "Bloodcamp Helper", "Various helpers for blood camping",
    Category.DUNGEONS
) {
    private val movePrediction by BooleanSetting("Move Prediction", true, desc = "Predicts when the watcher will move after its initial spawns, only works on floor 7")
    private val killTitle by BooleanSetting("Kill Title", true, desc = "Shows a title telling you when to kill the initial spawns")
        .withDependency { movePrediction }
    private val bloodAssist by BooleanSetting("Blood Camp Assist", true, desc = "Draws boxes on mobs spawning in the blood room, mobs spawn randomly between 37 and 41 ticks so this is not perfectly accurate")
    private val watcherBar by BooleanSetting("Watcher Bar", true, desc = "Adds the remaining mob count to the watcher boss bar")
    private val autoBloodCamp by BooleanSetting("Auto Blood Camp", false, desc = "Automatically blood camps when you've killed the first 4 mobs. You MUST hold a claymore, giant sword or midas sword")

    private val BLOOD_START = Regex("^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$")
    private val BLOOD_MOVE = Regex("^\\[BOSS] The Watcher: Let's see how you can handle this\\.$")

    private val inClear: Boolean get() = enabled && inDungeons && !inBoss
    private val inBloodRoom: Boolean get() = inClear && currentRoom?.data?.name == "Blood"

    private var watcher: Zombie? = null
    private var firstSpawns = true
    internal var tickTime = 0L
    private var startTime: Long? = null
    private var moveTimeSeconds: Float? = null
    private var killTitleAt: Long? = null
    private var watcherRemaining = -1
    private var camping = false
    private var announced = false
    private var killTitleShown = false
    private var ownsAngleLock = false
    private var lastTrace = ""
    private val shotClock = Clock()
    private var aimStand: ArmorStand? = null
    private var spawnedMobs = 0
    private var fireMob: LivingEntity? = null
    internal data class SpawnData(
        val startVector: Vec3, val started: Long, val firstSpawns: Boolean,
        var lastPosition: Vec3, var totalDelta: Vec3 = Vec3.ZERO
    )

    internal data class RenderData(
        var currVector: Vec3, var endVector: Vec3, var endUpdated: Long, var speed: Vec3,
        var lastEndVector: Vec3? = null, var lastEndPoint: Vec3? = null, var lastPingPoint: Vec3? = null
    )

    internal val spawnData = ConcurrentHashMap<ArmorStand, SpawnData>()
    internal val renderData = ConcurrentHashMap<ArmorStand, RenderData>()

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        when (val packet = event.packet) {
            is ClientboundMoveEntityPacket -> trackSpawn(packet)
            is ClientboundSetEquipmentPacket -> findWatcher(packet)
            is ClientboundRemoveEntitiesPacket -> onEntitiesRemoved(packet)
        }
    }

    private fun trackSpawn(packet: ClientboundMoveEntityPacket) {
        if (!inBloodRoom || !bloodAssist) return
        val accessor = packet as MoveEntityPacketAccessor
        if (accessor.deltaX == 0.toShort() && accessor.deltaY == 0.toShort() && accessor.deltaZ == 0.toShort()) return
        val level = mc.level ?: return
        val stand = packet.getEntity(level) as? ArmorStand ?: return
        if (watcher?.distanceTo(stand)?.let { it > WATCHER_RANGE } == true) return
        if (!isBloodMob(stand)) return

        val position = Vec3(stand.x + accessor.deltaX / 4096.0, stand.y + accessor.deltaY / 4096.0, stand.z + accessor.deltaZ / 4096.0)
        val data = spawnData.getOrPut(stand) { SpawnData(position, tickTime, firstSpawns, position) }
        data.totalDelta = data.totalDelta.add(position.subtract(data.lastPosition))
        data.lastPosition = position

        val direction = if (data.totalDelta.lengthSqr() > 0) data.totalDelta.normalize() else Vec3.ZERO
        val endpoint = data.startVector.add(direction.scale(if (data.firstSpawns) 16.1 else 11.9))
        val took = (tickTime - data.started).coerceAtLeast(1L)
        val speed = position.subtract(data.startVector).scale(1.0 / took)

        renderData.getOrPut(stand) { RenderData(position, endpoint, tickTime, speed) }.apply {
            lastEndVector = endVector
            endUpdated = tickTime
            this.speed = speed
            currVector = position
            endVector = endpoint
        }
    }

    private fun onEntitiesRemoved(packet: ClientboundRemoveEntitiesPacket) {
        if (packet.entityIds.any { it == watcher?.id }) watcher = null
        if (!autoBloodCamp || !inClear) return
        val level = mc.level ?: return
        packet.entityIds.forEach { id -> onMobSpawned(level.getEntity(id) as? ArmorStand ?: return@forEach) }
    }

    private fun onMobSpawned(stand: ArmorStand) {
        if (!isBloodMob(stand)) return
        val delta = Vec3(stand.x - stand.xOld, stand.y - stand.yOld, stand.z - stand.zOld)
        if (delta.lengthSqr() == 0.0 && isGridAligned(stand.x) && isGridAligned(stand.z)) return
        spawnedMobs++
        if (spawnedMobs <= INITIAL_SPAWNS) return
    }

    internal fun isBloodMob(stand: ArmorStand): Boolean =
        stand.getItemBySlot(EquipmentSlot.HEAD).textureValue in MOB_SKULLS

    private fun isGridAligned(value: Double): Boolean = (value % 1 + 1) % 1 == 0.5

    private fun findWatcher(packet: ClientboundSetEquipmentPacket) {
        if (!inClear || watcher != null) return
        val head = packet.slots.firstOrNull { it.first == EquipmentSlot.HEAD }?.second ?: return
        if (head.textureValue !in WATCHER_SKULLS) return
        mc.execute { watcher = mc.level?.getEntity(packet.entity) as? Zombie }
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!inClear) return
        if (BLOOD_START.matches(event.message)) return run {
            startTime = tickTime
            killTitleShown = false
            spawnedMobs = 0
        }
        if (!BLOOD_MOVE.matches(event.message)) return
        firstSpawns = false
        if (!movePrediction) return
        val started = startTime ?: return
        val ticks = predictedMoveTicks((tickTime - started) / 20 / 50)
        moveTimeSeconds = ticks / 20f
        killTitleAt = tickTime + ticks * 50L
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
        moveTimeSeconds = moveTimeSeconds?.minus(0.05f)?.takeIf { it > 0f }
        val fireAt = killTitleAt ?: return
        if (tickTime < fireAt) return
        killTitleAt = null
        killTitleShown = true
        if (killTitle) TitleUtils.displayStyledTitleTicks("Kill Mobs", 40, Color.WHITE)
    }

    @SubscribeEvent
    fun onRender2D(event: Render2DEvent) {
        if (!watcherBar || !inClear) return
        val total = 12 + dungeonFloor
        (mc.gui.hud.bossOverlay as BossHealthOverlayAccessor).events.values.forEach { bar ->
            val name = bar.name.string.noControlCodes
            if (!name.startsWith(WATCHER_BAR_NAME) || bar.progress < 0.05f) return@forEach
            watcherRemaining = (total * bar.progress).roundToInt()
            bar.name = Component.literal("$WATCHER_BAR_NAME $watcherRemaining/$total")
        }
    }

    @SubscribeEvent
    fun onCampTick(event: ClientTickEvent.Post) {
        if (!autoBloodCamp) return stopCamping()
        if (!inBloodRoom) return stopCamping().also { trace("not in blood room") }
        if (myDungeonClass != DungeonClass.Mage) return stopCamping().also { trace("class is $myDungeonClass, not Mage") }
        announceOnce()
        if (watcherRemaining == 0) return stopCamping().also { trace("watcher bar empty") }
        if (!holdingCampWeapon()) return stopCamping(swapped = true).also { trace("wrong weapon: ${mc.player?.mainHandItem?.skyblockID}") }
        pruneDead()
        if (!killTitleShown) return releaseAim().also { trace("waiting for kill title") }
        if (spawnedMobs < INITIAL_SPAWNS) return releaseAim().also { trace("only $spawnedMobs of $INITIAL_SPAWNS initial spawns seen") }
        camping = true
        fireMob = nearestBloodMob()
        aimStand = nearestMob()
        if (aimStand == null && fireMob == null) return releaseAim().also { trace("nothing to aim at (tracked=${renderData.size})") }
        if (!ownsAngleLock) {
            RotationUtils.startAngleLock(AIM_LOCK_MS, AIM_ARRIVAL) { aimAngles() }
            ownsAngleLock = true
        }
        val mob = fireMob ?: return trace("tracking spawn, no mob out yet")
        if (!shotClock.hasTimePassed(SHOT_INTERVAL_MS)) return
        val aimAt = mob.boundingBox.center
        if (!BloodcampBeam.onTarget(aimAt)) return trace("off target on ${mob.nameTag}: ${BloodcampBeam.describe(aimAt)}")
        shotClock.update()
        trace("ATTACK ${mob.nameTag} at $aimAt")
        PlayerUtils.swingHand()
    }

    private val LivingEntity.nameTag: String
        get() = (mc.level?.getEntity(id + 1) as? ArmorStand)?.customName?.string?.noControlCodes?.trim().orEmpty()

    private fun nearestBloodMob(): LivingEntity? {
        val self = mc.player ?: return null
        return mc.level?.entitiesForRendering()
            ?.filterIsInstance<LivingEntity>()
            ?.filter { it.isBloodTarget(self) }
            ?.minByOrNull { it.distanceToSqr(self) }
    }

    private fun LivingEntity.isBloodTarget(self: Player): Boolean {
        if (this === self || this is ArmorStand || isRemoved || !isAlive || health <= 0f) return false
        if (!isInsideBloodRoom()) return false
        if (this is Player && gameProfile.name in dungeonTeammates) return false
        return this is Silverfish || BLOOD_MOB_TAG.containsMatchIn(nameTag)
    }

    private fun LivingEntity.isInsideBloodRoom(): Boolean {
        val components = currentRoom?.roomComponents ?: return false
        val center = ScanUtils.getRoomCenter(blockPosition().x, blockPosition().z)
        return components.any { it.vec2 == center }
    }

    private fun trace(reason: String) {
        if (reason == lastTrace) return
        lastTrace = reason
        logger.info("[BloodCamp] {}", reason)
    }

    private fun nearestMob(): ArmorStand? {
        val player = mc.player ?: return null
        return renderData.keys.filter { it.isAlive }.minByOrNull { it.distanceToSqr(player) }
    }

    private fun aimAngles(): Pair<Float, Float>? = currentAimPoint()?.let(::calcAimAngles)

    private fun currentAimPoint(): Vec3? {
        fireMob?.let { return jittered(it.boundingBox.center) }
        return aimStand?.takeIf { it.isAlive }?.let(::aimPoint)
    }

    private fun predictedBox(stand: ArmorStand): AABB? {
        val render = renderData[stand] ?: return null
        val endPoint = interpolate(render.endVector, render.lastEndVector, min(tickTime - render.endUpdated, 100L) / 100f)
        return boxAt(endPoint)
    }

    private fun aimPoint(stand: ArmorStand): Vec3? = predictedBox(stand)?.center?.let(::jittered)

    private fun jittered(point: Vec3): Vec3 {
        val phase = tickTime / 1000.0
        return point.add(sin(phase) * AIM_JITTER, sin(phase * 1.7) * AIM_JITTER, cos(phase * 1.3) * AIM_JITTER)
    }

    private fun releaseAim() {
        if (ownsAngleLock) {
            RotationUtils.stopAngleLock()
            ownsAngleLock = false
        }
        aimStand = null
        fireMob = null
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

    private fun holdingCampWeapon(): Boolean = mc.player?.mainHandItem?.skyblockID.equalsOneOf(CAMP_WEAPONS)

    private fun pruneDead() {
        spawnData.keys.removeIf { !it.isAlive }
        renderData.keys.removeIf { !it.isAlive }
    }

    internal fun remainingFor(stand: ArmorStand): Long? {
        val data = spawnData[stand] ?: return null
        return (if (data.firstSpawns) 2000 else 0) + TICK_ASSUMED * 50 - (tickTime - data.started) + OFFSET_MS
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !inClear || !bloodAssist) return
        val partialTick = event.renderTickCounter.getGameTimeDeltaPartialTick(false)
        val ping = BloodcampRender.averagePing()
        renderData.forEach { (stand, render) -> BloodcampRender.drawSpawn(event, stand, render, partialTick, ping) }
    }


    internal fun boxAt(center: Vec3): AABB = AABB(
        center.x - BOX_SIZE / 2, center.y + 1.5, center.z - BOX_SIZE / 2,
        center.x + BOX_SIZE / 2, center.y + 1.5 + BOX_SIZE, center.z + BOX_SIZE / 2
    )

    internal fun interpolate(current: Vec3, last: Vec3?, multiplier: Float): Vec3 =
        last?.let { it.add(current.subtract(it).scale(multiplier.toDouble())) } ?: current

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        watcher = null
        spawnData.clear()
        renderData.clear()
        tickTime = 0
        firstSpawns = true
        moveTimeSeconds = null
        startTime = null
        killTitleAt = null
        watcherRemaining = -1
        announced = false
        spawnedMobs = 0
        killTitleShown = false
        stopCamping()
    }
}

private const val BOX_SIZE = 1.0
internal const val OFFSET_MS = 40
private const val TICK_ASSUMED = 38
private const val WATCHER_BAR_NAME = "The Watcher"
private const val WATCHER_RANGE = 20f
private val BLOOD_PREFIXES = setOf("Healthy", "Speedy", "Stealth", "Golden", "Boomer", "Stormy")
private val BLOOD_MOB_TAG = Regex("""\b(?:${BLOOD_PREFIXES.joinToString("|")})\b""")
private const val INITIAL_SPAWNS = 4
private const val AIM_LOCK_MS = 260L
private const val AIM_ARRIVAL = 0.45f
private const val AIM_JITTER = 0.12
private const val SHOT_INTERVAL_MS = 250L

private val CAMP_WEAPONS = setOf("DARK_CLAYMORE", "GIANTS_SWORD", "MIDAS_SWORD", "ASTRAEA", "HYPERION", "VALKYRIE", "SCYLLA")
