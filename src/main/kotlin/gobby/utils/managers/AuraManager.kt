package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketSentEvent
import gobby.events.WorldUnloadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import net.minecraft.block.ShapeContext
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.ArrayDeque

enum class InteractType { INTERACT, INTERACT_AT }

object AuraManager {

    private val queue = ArrayDeque<() -> Unit>()
    private var ready = true

    fun auraEntity(entity: Entity, type: InteractType = InteractType.INTERACT_AT) {
        submit { sendEntityInteraction(entity, type) }
    }

    fun auraBlock(pos: BlockPos, onMissing: (() -> Unit)? = null) {
        submit { sendBlockInteraction(pos, onMissing) }
    }

    private fun submit(action: () -> Unit) {
        if (ready) action() else queue.add(action)
    }

    private fun sendEntityInteraction(entity: Entity, type: InteractType) {
        val player = mc.player ?: return
        val sneaking = player.isSneaking

        if (type == InteractType.INTERACT_AT) {
            val entityPos = Vec3d(entity.x, entity.y, entity.z)
            val expanded = entity.boundingBox.expand(entity.targetingMargin.toDouble())
            val target = entityPos.add(0.0, entity.height.toDouble() / 2.0, 0.0)
            val hitVec = expanded.raycast(player.eyePos, target).orElse(null)?.subtract(entityPos) ?: return

            modMessage("Interacting with ${entity.name.string} at ${hitVec.x}, ${hitVec.y}, ${hitVec.z}")
            mc.networkHandler?.sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, sneaking, Hand.MAIN_HAND, hitVec))
        }

        mc.networkHandler?.sendPacket(PlayerInteractEntityC2SPacket.interact(entity, sneaking, Hand.MAIN_HAND))
    }

    private fun sendBlockInteraction(pos: BlockPos, onMissing: (() -> Unit)?) {
        val player = mc.player ?: return
        val world = mc.world ?: return

        val shape = world.getBlockState(pos).getOutlineShape(world, pos, ShapeContext.of(player))
        if (shape.isEmpty) {
            onMissing?.invoke()
            return
        }

        val center = shape.boundingBox.center.add(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        val hitResult = shape.raycast(player.eyePos, center, pos)
            ?: BlockHitResult(center, Direction.UP, pos, false)

        mc.networkHandler?.sendPacket(PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0))
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!ready && queue.isNotEmpty()) {
            ready = true
            queue.poll().invoke()
        }
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        when (event.packet) {
            is PlayerInteractEntityC2SPacket, is PlayerInteractBlockC2SPacket -> ready = false
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldUnloadEvent) {
        queue.clear()
        ready = false
    }
}
