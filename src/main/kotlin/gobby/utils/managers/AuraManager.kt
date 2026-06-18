package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketSentEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.entity.Entity
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
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
        val sneaking = player.isShiftKeyDown

        if (type == InteractType.INTERACT_AT) {
            val entityPos = Vec3(entity.x, entity.y, entity.z)
            val expanded = entity.boundingBox.inflate(0.1)
            val target = entityPos.add(0.0, entity.bbHeight.toDouble() / 2.0, 0.0)
            val hitVec = expanded.clip(player.eyePosition, target).orElse(null)?.subtract(entityPos) ?: return

            mc.connection?.send(ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, hitVec, sneaking))
        }

        mc.connection?.send(ServerboundInteractPacket(entity.id, InteractionHand.MAIN_HAND, Vec3.ZERO, sneaking))
        mc.connection?.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
    }

    private fun sendBlockInteraction(pos: BlockPos, onMissing: (() -> Unit)?) {
        val player = mc.player ?: return
        val world = mc.level ?: return

        val shape = world.getBlockState(pos).getShape(world, pos, CollisionContext.of(player))
        if (shape.isEmpty) {
            onMissing?.invoke()
            return
        }

        val center = shape.bounds().center.add(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        val hitResult = shape.clip(player.eyePosition, center, pos)
            ?: BlockHitResult(center, Direction.UP, pos, false)

        mc.connection?.send(ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, 0))
        mc.connection?.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
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
            is ServerboundInteractPacket, is ServerboundUseItemOnPacket -> ready = false
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        queue.clear()
        ready = false
    }
}
