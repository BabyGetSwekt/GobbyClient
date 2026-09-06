package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.styledText
import gobby.gui.hud.HudSetting
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.LocationUtils.onSkyblock
import gobby.utils.Utils.equalsOneOf
import gobby.utils.Utils.swapDelayTicks
import gobby.utils.managers.ArmorPiece
import gobby.utils.managers.ArmorTracker
import gobby.utils.managers.EquipmentManager
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.Interpolate
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.myDungeonClass
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

object SpringBootsHelper : Module(
    "Spring Boots Helper", "Various functions for spring boots",
    Category.SKYBLOCK
) {

    private val landingBox by BooleanSetting("Landing Box", true, desc = "Outlines the block your charged jump will reach")
    private val autoSwapBoots by BooleanSetting("Auto Spring Boots on F7 bossfight", false, desc = "Automatically equips spring boots when entering F7 boss. Only for mage/bers")
    private val swapBackAfterCrystals by BooleanSetting("Auto Swap back after Crystals", false, desc = "Automatically swaps back to the boots you were wearing prior to the Auto Swap. Swaps back after 2 crystal pickups")
        .withDependency { autoSwapBoots }

    private val FIRM_PITCHES = setOf(0.82539684f, 0.8888889f, 0.93650794f, 1.0476191f, 1.1746032f, 1.3174603f, 1.7777778f)
    private val RELEASE_PITCHES = setOf(0.0952381f, 1.6984127f)
    private val COLOR_CODES = listOf(13.5f to "§c", 22.5f to "§e", 33.0f to "§6", 43.5f to "§a")

    private val REACH_PER_STEP = listOf(
        0.0f, 3.0f, 6.5f, 9.0f, 11.5f, 13.5f, 16.0f, 18.0f, 19.0f,
        20.5f, 22.5f, 25.0f, 26.5f, 28.0f, 29.0f, 30.0f, 31.0f, 33.0f,
        34.0f, 35.5f, 37.0f, 38.0f, 39.5f, 40.0f, 41.0f, 42.5f, 43.5f,
        44.0f, 45.0f, 46.0f, 47.0f, 48.0f, 49.0f, 50.0f, 51.0f, 52.0f,
        53.0f, 54.0f, 55.0f, 56.0f, 57.0f, 58.0f, 59.0f, 60.0f, 61.0f
    )

    private var softSteps = 0
    private var firmSteps = 0
    private var crystals = 0
    private var previousBoots = ""
    private var wasInBoss = false

    private val charging: Boolean
        get() = enabled && onSkyblock && mc.player?.isCrouching == true &&
            ArmorTracker.idOf(EquipmentSlot.FEET) == "SPRING_BOOTS"

    private val reach: Float
        get() = REACH_PER_STEP[(softSteps + firmSteps).coerceIn(REACH_PER_STEP.indices)]

    private val crystalMessage: String
        get() = "${mc.player?.gameProfile?.name} picked up an Energy Crystal!"

    private val reachHud by HudSetting("Spring Boots", "Shows how high you will jump", visible = { onSkyblock }) { example ->
        val ctx = drawContext ?: return@HudSetting
        val blocks = if (example) 22.5f else reach
        if (blocks == 0f) return@HudSetting
        val label = styledText("Height: ")
        val value = styledText("${tierOf(blocks)}$blocks")
        val labelWidth = mc.font.width(label)
        ctx.text(mc.font, label, 0, 0, Color(255, 85, 255).rgb, true)
        ctx.text(mc.font, value, labelWidth, 0, Color.WHITE.rgb, true)
        setSize(labelWidth + mc.font.width(value), mc.font.lineHeight)
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        val packet = event.packet as? ClientboundSoundPacket ?: return
        mc.execute {
            when (packet.sound.value().location()) {
                SoundEvents.NOTE_BLOCK_PLING.value().location() -> if (charging) chargeStep(packet.pitch)
                SoundEvents.FIREWORK_ROCKET_LAUNCH.location() -> if (packet.pitch in RELEASE_PITCHES) resetCharge()
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!charging) resetCharge()
        val enteringBoss = inDungeons && inBoss && dungeonFloor == 7
        if (enteringBoss == wasInBoss) return
        wasInBoss = enteringBoss
        if (enteringBoss) equipForBoss()
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!swapBackAfterCrystals || previousBoots.isEmpty() || event.message != crystalMessage) return
        if (++crystals < 2) return
        crystals = 0
        EquipmentManager.swapByUuid(ArmorPiece.BOOTS, previousBoots, delayTicks = swapDelayTicks())
        previousBoots = ""
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !landingBox || !charging) return
        val blocks = reach
        if (blocks == 0f) return
        val player = mc.player ?: return
        val landing = Interpolate.getRenderPosition(player).add(0.0, blocks.toDouble(), 0.0)
        draw3DBox(event.matrixStack, event.camera, boxAt(landing), Color(255, 85, 85), filled = false, depthTest = true)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        crystals = 0
        previousBoots = ""
        wasInBoss = false
        resetCharge()
    }

    private fun chargeStep(pitch: Float) {
        when (pitch) {
            0.6984127f -> softSteps = (softSteps + 1).coerceAtMost(2)
            in FIRM_PITCHES -> firmSteps++
        }
    }

    private fun resetCharge() {
        softSteps = 0
        firmSteps = 0
    }

    private fun equipForBoss() {
        if (!autoSwapBoots || !myDungeonClass.equalsOneOf(DungeonClass.Berserk, DungeonClass.Mage)) return
        if (!EquipmentManager.hasInInventory("SPRING_BOOTS")) return errorMessage("No spring boots found")
        previousBoots = ArmorTracker.uuidOf(EquipmentSlot.FEET)
        EquipmentManager.swap(ArmorPiece.BOOTS, "SPRING_BOOTS", delayTicks = swapDelayTicks())
    }

    private fun tierOf(blocks: Float): String = COLOR_CODES.firstOrNull { blocks <= it.first }?.second ?: "§b"

    private fun boxAt(feet: Vec3): AABB =
        AABB(feet.x - 0.5, feet.y, feet.z - 0.5, feet.x + 0.5, feet.y + 1.0, feet.z + 0.5)
}
