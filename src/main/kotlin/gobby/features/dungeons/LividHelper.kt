package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.ServerTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.features.render.EntityHighlighter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.render.TitleUtils
import gobby.utils.rotation.RotationUtils
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color

object LividHelper : EntityHighlighter(
    "Livid Helper", "Helper modules for F5 bossfight :)",
    Category.DUNGEONS
) {

    private val drawTitle by BooleanSetting("Draw Title", true, desc = "Draws the correct livid name on screen")
    private val drawBox by BooleanSetting("Draw Box", true, desc = "Draws an ESP box on the correct livid")
    private val drawLine by BooleanSetting("Draw Line", false, desc = "Draws a line to the correct livid")
    private val espLineMode by SelectorSetting("Line Mode", 1, listOf("Feet", "Crosshair"), desc = "Line render origin")
        .withDependency { drawLine }
    private val aimLock by BooleanSetting("Aim Lock", false, desc = "Locks aim onto the correct livid until dead")

    private val GLASS_POS = BlockPos(5, 108, 42)
    private const val DETECT_TICK = 335

    private var currentLivid: Livid = Livid.HOCKEY
    private var lividEntity: PlayerEntity? = null
    private var aimLocking = false
    private var serverTickCounter = -1
    private var detected = false

    private enum class Livid(
        val entityName: String,
        val displayColor: Color,
        val glass: Block
    ) {
        ARCADE("Arcade", Color(255, 255, 85), Blocks.YELLOW_STAINED_GLASS),
        CROSSED("Crossed", Color(255, 85, 255), Blocks.MAGENTA_STAINED_GLASS),
        DOCTOR("Doctor", Color(170, 170, 170), Blocks.GRAY_STAINED_GLASS),
        FROG("Frog", Color(0, 170, 0), Blocks.GREEN_STAINED_GLASS),
        HOCKEY("Hockey", Color(255, 85, 85), Blocks.RED_STAINED_GLASS),
        PURPLE("Purple", Color(170, 0, 170), Blocks.PURPLE_STAINED_GLASS),
        SCREAM("Scream", Color(85, 85, 255), Blocks.BLUE_STAINED_GLASS),
        SMILE("Smile", Color(85, 255, 85), Blocks.LIME_STAINED_GLASS),
        VENDETTA("Vendetta", Color(255, 255, 255), Blocks.WHITE_STAINED_GLASS);

        companion object {
            fun fromGlass(block: Block): Livid? =
                entries.firstOrNull { it.glass == block }
        }
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        if (!enabled || dungeonFloor != 5) return

        if (inBoss && serverTickCounter == -1) {
            serverTickCounter = 0
            detected = false
        }

        if (serverTickCounter == -1) return
        serverTickCounter++

        if (serverTickCounter == DETECT_TICK && !detected) {
            detected = true
            detectFromBlock()
        }
    }

    private fun detectFromBlock() {
        val world = mc.world ?: return
        val block = world.getBlockState(GLASS_POS).block
        setLivid(Livid.fromGlass(block) ?: return)
    }

    private fun setLivid(livid: Livid) {
        currentLivid = livid
        lividEntity = null
        if (drawTitle) TitleUtils.displayStyledTitleTicks(livid.entityName, 60, livid.displayColor)
    }

    @SubscribeEvent
    fun onTickLivid(event: ClientTickEvent.Post) {
        if (!enabled || !inBoss || dungeonFloor != 5) return

        val entity = lividEntity
        if (entity == null || !entity.isAlive) {
            val found = findLividEntity()
            lividEntity = found

            if (aimLock && found != null && !aimLocking) {
                aimLocking = true
                val target = Vec3d(found.x, found.y + found.height * 0.5, found.z)
                RotationUtils.easeToVec(target, 1000L) {
                    lividEntity?.let { RotationUtils.startAimLock(it) }
                }
            }
        }

        if (aimLocking) {
            val alive = lividEntity?.let { it.isAlive && !it.isRemoved && it.health > 1f } == true
            if (!alive) {
                RotationUtils.stopAimLock()
                aimLocking = false
            } else if (mc.currentScreen != null) {
                RotationUtils.stopAimLock()
            } else if (!RotationUtils.isAimLocked && !RotationUtils.isEasing) {
                lividEntity?.let { RotationUtils.startAimLock(it) }
            }
        }
    }

    private fun findLividEntity(): PlayerEntity? {
        val world = mc.world ?: return null
        val expectedName = "${currentLivid.entityName} Livid"
        return world.players.firstOrNull {
            it != mc.player && it.name.string == expectedName && it.isAlive && it.health > 1f
        }
    }

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!drawBox || !inBoss || dungeonFloor != 5) return false
        return lividEntity != null && entity == lividEntity
    }

    override fun getColor(): Color = currentLivid.displayColor
    override fun shouldDrawLines(): Boolean = drawLine
    override fun getLineColor(): Color = currentLivid.displayColor
    override fun getLineMode(): Int = espLineMode

    @SubscribeEvent
    fun onWorldChange(event: WorldLoadEvent) {
        if (aimLocking) RotationUtils.stopAimLock()
        currentLivid = Livid.HOCKEY
        lividEntity = null
        aimLocking = false
        serverTickCounter = -1
        detected = false
    }
}
