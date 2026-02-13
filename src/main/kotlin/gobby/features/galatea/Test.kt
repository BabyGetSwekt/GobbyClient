package gobby.features.galatea

import gobby.Gobbyclient
import gobby.Gobbyclient.Companion.mc
import gobby.events.CharTypedEvent
import gobby.events.ChatReceivedEvent
import gobby.events.ClientTickEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.RightClickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.gui.GuiOpenEvent
import gobby.events.render.NewRender3DEvent
import gobby.events.render.Render2DEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.toColor
import gobby.utils.LocationUtils.area
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.LocationUtils.location
import gobby.utils.LocationUtils.onHypixel
import gobby.utils.LocationUtils.onSkyblock
import gobby.utils.Utils.getBlockIdAt
import gobby.utils.Utils.getSBMaxHealth
import gobby.utils.render.Render2D
import gobby.utils.render.Render2D.drawString
import gobby.utils.render.RenderUtils.drawStringInWorld
import gobby.utils.render.Renderer
import gobby.utils.render.ScreenUtils.getKey
import gobby.utils.skyblock.dungeon.ScanUtils.currentRoom
import gobby.utils.timer.Executor
import net.minecraft.block.Blocks
import net.minecraft.client.font.TextRenderer
import net.minecraft.entity.mob.MagmaCubeEntity
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.passive.TurtleEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color


object Test {

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
//        modMessage("Message received: ${event.message.string}")
//        if(event.message.string.contains("gaoen")) {
//            Renderer.displayTitle("§6§lRIGHT", 20, Color.WHITE)
//        }
    }

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        //modMessage("Key pressed: ${event.key}")
        //if(mc.currentScreen == null) return
        //event.cancel()
    }

    @SubscribeEvent
    fun onCharPress(event: CharTypedEvent) {
//        modMessage(mc.currentScreen.toString())
//        if(!(mc.currentScreen.toString().contains("vigi"))) return
//        event.cancel()

    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
//        if (mc != null && mc.player != null) {
//            val world = mc.world ?: return
//            val hitResult = mc.crosshairTarget
//
//            if (hitResult is BlockHitResult && hitResult.type == HitResult.Type.BLOCK) {
//                val blockPos = hitResult.blockPos
//                modMessage("Looking at block ID:${getBlockIdAt(blockPos)!!}")
//                val blockState = world.getBlockState(blockPos)
//                val blockId = Registries.BLOCK.getRawId(blockState.block)
//                if (blockState.block == Blocks.BEDROCK) modMessage("TRUE")
//            }
//        }
    }

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        //modMessage("Right clicked")
    }

    @SubscribeEvent
    fun onWorldJoin(event: WorldLoadEvent) {

    }

    @SubscribeEvent
    fun onParticleSpawn(event: gobby.events.SpawnParticleEvent) {
//        val id = Registries.PARTICLE_TYPE.getId(event.type)
//        modMessage("Particle spawned: $id")
//        if (event.type == ParticleTypes.EXPLOSION) {
//            modMessage("Particle explosions: ${event.type}, pos ${event.pos}, packettype ${event.type}")
//            event.cancel()
//        }
    }

    @SubscribeEvent
    fun onGuiOpen(event: GuiOpenEvent) {
        //modMessage("Opening gui ${event.screen.getKey()}")
    }

    @SubscribeEvent
    fun onRender(event: NewRender3DEvent) {
        //if (mc.currentScreen != null || mc.player == null) return

    }

//    @SubscribeEvent
//    fun onRender2D(event: Render2DEvent) {
//        if (mc.player == null) return
//        val text = "§4§lBACK"
//        val scale = 2.0f
//        val screenWidth = mc.window.scaledWidth
//        val screenHeight = mc.window.scaledHeight
//        val textWidth = mc.textRenderer.getWidth(text) * scale
//        val x = (screenWidth / 2f) - (textWidth / 2f / scale)
//        val y = (screenHeight / 2f) - 30f // Adjust this to move higher/lower
//
//        drawString(
//            text,
//            x,
//            y,
//            Color(255, 0, 0),
//            scale,
//            event.matrices
//        )
//    }
}