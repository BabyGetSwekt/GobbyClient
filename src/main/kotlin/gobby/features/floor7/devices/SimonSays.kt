package gobby.features.floor7.devices

import gobby.Gobbyclient.Companion.mc
import gobby.events.BlockStateChangeEvent
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.DropDownSetting
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.ChatUtils.partyMessage
import gobby.utils.PlayerUtils
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawRing
import gobby.utils.render.RenderUtils.drawStringInWorld
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.timer.Clock
import net.minecraft.block.Blocks
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

object SimonSays : Module(
    "Simon Says", "Features for the Simon Says device on F7/M7.",
    Category.FLOOR7
) {

    val solver by BooleanSetting("Simon Says Solver", false, desc = "Solver which marks the buttons that should be clicked on")

    private val autoSSDropdown = DropDownSetting("Auto SS", desc = "Automatically solves the Simon Says device").also { settings.add(it) }
    private val autoSSEnabled by BooleanSetting("Enabled", false, desc = "Toggle Auto SS on/off")
        .childOf(autoSSDropdown)
    private val clickDelay by NumberSetting("Click Delay", 200, 50, 500, 10, desc = "Delay between clicks in ms")
        .childOf(autoSSDropdown).withDependency { autoSSEnabled }
    private val rotationDelay by NumberSetting("Rotation Delay", 150, 0, 1000, 50, desc = "Time to ease rotation to buttons in ms")
        .childOf(autoSSDropdown).withDependency { autoSSEnabled }
    private val autoStart by BooleanSetting("Autostart", true, desc = "Automatically starts SS when Goldor speaks")
        .childOf(autoSSDropdown).withDependency { autoSSEnabled }
    private val autoStartDelay by NumberSetting("Autostart Delay", 125, 50, 200, 1, desc = "Delay for starting SS in ms")
        .childOf(autoSSDropdown).withDependency { autoSSEnabled }
    private val drawStartPos by BooleanSetting("Draw Start Position", false, desc = "Draws a ring on where you have to stand in order for auto SS to start")
        .childOf(autoSSDropdown).withDependency { autoSSEnabled }
    private val startSSKeybind by KeybindSetting("Start SS", desc = "Manually starts the SS sequence (example of when you want to press this, is when you were not in time infront of the Device for it to auto start)")
        .withDependency(autoSSDropdown)
    private val sendSSBrokeKeybind by KeybindSetting("Send SS Broke", desc = "Sends 'SS Broke' in party chat")

    private val START_BUTTON = BlockPos(110, 121, 91)
    private const val START_RANGE_SQ = 25.0
    private const val DEVICE_RANGE = 6.0

    private val COL_GREEN = Color(0, 255, 0, 120)
    private val COL_YELLOW = Color(255, 255, 0, 120)
    private val COL_RED = Color(255, 0, 0, 120)

    private val autoClicks = mutableListOf<BlockPos>()
    private var autoProgress = 0
    private var autoDoneFirst = false
    private val clock = Clock()
    private val startClock = Clock()
    private var doingSS = false
    private var clicked = false
    private var rotating = false
    private var startStep = 0

    private fun buttonBox(pos: BlockPos): Box = Box(
        pos.x + 0.875, pos.y + 0.375, pos.z + 0.3125,
        pos.x + 1.0, pos.y + 0.625, pos.z + 0.6875
    )

    private fun resetAutoClicks() {
        autoClicks.clear()
        autoProgress = 0
        autoDoneFirst = false
        doingSS = false
    }

    private fun resetAutoSS() {
        resetAutoClicks()
        clicked = false
        rotating = false
        startStep = 0
    }

    private fun reset() {
        resetAutoSS()
    }

    private fun isInRange(): Boolean {
        val player = mc.player ?: return false
        return player.squaredDistanceTo(START_BUTTON.x + 0.5, START_BUTTON.y + 0.5, START_BUTTON.z + 0.5) <= START_RANGE_SQ
    }

    private fun clickBlock(pos: BlockPos, onClicked: (() -> Unit)? = null) {
        if (rotating || RotationUtils.isEasing) return
        rotating = true
        val buttonFace = Vec3d(pos.x + 0.875, pos.y + 0.5, pos.z + 0.5)
        RotationUtils.easeToVec(buttonFace, rotationDelay.toLong()) {
            PlayerUtils.rightClick()
            clock.update()
            rotating = false
            onClicked?.invoke()
        }
    }

    private fun start() {
        if (!isInRange() || clicked) return
        resetAutoSS()
        clicked = true
        startStep = 1
        startClock.update()
    }

    private fun tickStartSequence() {
        if (startStep == 0) return
        if (!startClock.hasTimePassed(autoStartDelay.toLong())) return
        if (rotating || RotationUtils.isEasing) return

        when (startStep) {
            1, 2 -> {
                resetAutoClicks()
                clickBlock(START_BUTTON)
                startStep++
                startClock.update()
            }
            3 -> {
                doingSS = true
                clickBlock(START_BUTTON)
                startStep = 0
            }
        }
    }

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled) return
        if (event.key == startSSKeybind && startSSKeybind != 0) {
            reset()
            start()
        }
        if (event.key == sendSSBrokeKeybind && sendSSBrokeKeybind != 0 && mc.currentScreen == null && DungeonUtils.getSection() == 1) partyMessage("SS Broke")
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled || !autoSSEnabled || !autoStart) return
        if (!event.message.contains("[BOSS] Goldor: Who dares trespass into my domain?")) return
        start()
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!enabled || !autoSSEnabled || mc.world == null || mc.player == null) return
        if (!isInRange()) return

        if (startStep > 0) {
            if (startClock.hasTimePassed(autoStartDelay.toLong())) tickStartSequence()
            return
        }

        if (!clock.hasTimePassed(clickDelay.toLong())) return
        if (rotating || RotationUtils.isEasing) return

        val player = mc.player ?: return
        val hasDevice = mc.world?.entities?.any {
            it is ArmorStandEntity && it.squaredDistanceTo(player) < DEVICE_RANGE * DEVICE_RANGE && it.name.string.contains("Device")
        } ?: false

        if (!hasDevice) {
            clicked = false
            return
        }

        val detect = mc.world?.getBlockState(BlockPos(110, 123, 92))?.block
        if ((detect == Blocks.STONE_BUTTON || autoDoneFirst) && doingSS) {
            if (!autoDoneFirst && autoClicks.size == 3) {
                autoClicks.removeFirst()
            }
            autoDoneFirst = true
            if (autoProgress < autoClicks.size) {
                val next = autoClicks[autoProgress]
                if (mc.world?.getBlockState(next)?.block == Blocks.STONE_BUTTON) {
                    clickBlock(next) { autoProgress++ }
                }
            }
        }
    }

    @SubscribeEvent
    fun onBlockChange(event: BlockStateChangeEvent) {
        if (!enabled) return
        val pos = event.blockPos
        if (pos.y !in 120..123 || pos.z !in 92..95) return

        if (pos.x == 111 && event.newState.block == Blocks.SEA_LANTERN) {
            val button = BlockPos(110, pos.y, pos.z)

            if (autoClicks.size == 2 && autoClicks[0] == button && !autoDoneFirst) {
                autoDoneFirst = true
                autoClicks.removeFirst()
            }

            if (button !in autoClicks) {
                autoProgress = 0
                autoClicks.add(button)
            }
        }

        if (pos.x == 110 && event.newState.block == Blocks.SEA_LANTERN) {
            val idx = autoClicks.indexOf(pos)
            if (idx >= 0 && idx >= autoProgress) {
                autoProgress = idx + 1
            }
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled) return
        val player = mc.player ?: return
        if (player.squaredDistanceTo(START_BUTTON.x + 0.5, START_BUTTON.y + 0.5, START_BUTTON.z + 0.5) > 1600) return

        val matrixStack = event.matrixStack
        val camera = event.camera

        if (solver && autoProgress < autoClicks.size) {
            for (i in autoProgress until autoClicks.size) {
                val color = when (i - autoProgress) {
                    0 -> COL_GREEN
                    1 -> COL_YELLOW
                    else -> COL_RED
                }
                draw3DBox(matrixStack, camera, buttonBox(autoClicks[i]), color)
            }
        }

        if (autoSSEnabled && drawStartPos) {
            val inRing = player.x in 108.0..109.0 && player.z in 93.0..94.0
            val ringColor = if (inRing) Color(0, 255, 0, 180) else Color(255, 0, 0, 180)
            drawRing(matrixStack, camera, 108.5, 120.01, 93.5, 2.0, 2.0, 0.01, ringColor)
        }

        if (autoClicks.isNotEmpty()) {
            autoClicks.forEachIndexed { index, pos ->
                drawStringInWorld(
                    (index + 1).toString(),
                    Vec3d(pos.x + 0.9375, pos.y + 0.5625, pos.z + 0.5),
                    matrixStack, camera, scale = 0.02f
                )
            }
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        reset()
    }
}
