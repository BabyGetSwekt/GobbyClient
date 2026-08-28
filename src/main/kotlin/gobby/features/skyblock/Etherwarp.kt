package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.PacketSentEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.events.network.ClientSoundReceivedEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.DropDownSetting
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.gui.click.StringSetting
import gobby.utils.ChatUtils
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.isEtherwarpable
import gobby.utils.managers.SoundManager
import gobby.utils.render.BlockRenderUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.AABB
import java.awt.Color
import java.net.URI

object Etherwarp : Module("Etherwarp", "Etherwarp highlighter, death prevention and custom sound", Category.SKYBLOCK) {

    val highlighter by BooleanSetting("Highlighter", false, desc = "Highlights the block you would etherwarp to")
    private val preventDeath by BooleanSetting(
        "Prevent Accidental Death",
        true,
        desc = "Cancels etherwarps that would land on the highest block of the current dungeon room (at death barrier)"
    )

    private val soundGroup = DropDownSetting("Sound", desc = "Replace the etherwarp sound with a custom one").also { settings.add(it) }
    private val customSound by BooleanSetting("Custom Sound", false, desc = "Cancels the etherwarp sound and plays your own instead").childOf(soundGroup)
    private val soundName by StringSetting("Etherwarp Sound", "minecraft:entity.experience_orb.pickup", desc = "Sound id to play", length = 60, onCommit = ::validateSound).childOf(soundGroup).withDependency { customSound }
    private val soundPitch = NumberSetting("Etherwarp Sound Pitch", 1.0f, 0.1f, 2.0f, 0.05f, desc = "Pitch of the custom sound").also { settings.add(it) }.childOf(soundGroup).withDependency { customSound }
    private val soundVolume = NumberSetting("Etherwarp Sound Volume", 1.0f, 0.1f, 1.0f, 0.05f, desc = "Volume of the custom sound").also { settings.add(it) }.childOf(soundGroup).withDependency { customSound }

    private const val ETHERWARP_SOUND_PITCH = 0.53968257f
    private const val SOUND_LIST_URL = "https://discord.com/channels/1500450422694084620/1533138074731811077/1533140035896082613"
    private val validColor = Color(0, 255, 0, 80)
    private val invalidColor = Color(255, 0, 0, 80)

    private var currentHighestY: Int? = null
    private var isInEntrance = false

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        currentHighestY = event.highestY
        isInEntrance = event.room?.data?.type == RoomType.ENTRANCE
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        if (!enabled || !preventDeath || mc.gui.screen() != null || !inDungeons || isInEntrance) return
        if (event.packet !is ServerboundUseItemPacket && event.packet !is ServerboundUseItemOnPacket) return
        val player = mc.player ?: return
        if (!player.mainHandItem.isEtherwarpable()) return
        val highestY = currentHighestY ?: return
        val hit = EtherwarpUtils.getEtherPos().pos ?: return
        if (hit.y >= highestY) {
            event.cancel()
            ChatUtils.errorMessage("Prevented you from going out of the dungeon")
        }
    }

    @SubscribeEvent
    fun onSound(event: ClientSoundReceivedEvent) {
        if (!enabled || !customSound) return
        if (event.sound.location() != SoundEvents.ENDER_DRAGON_HURT.location() || event.pitch != ETHERWARP_SOUND_PITCH) return
        if (soundName.isBlank() || !SoundManager.soundExists(soundName)) return
        event.cancel()
        SoundManager.playCustomSound(soundName, soundPitch.floatValue, soundVolume.floatValue)
    }

    private fun validateSound(name: String) {
        if (name.isBlank() || SoundManager.soundExists(name)) return
        ChatUtils.errorMessage(
            Component.literal("Unknown sound, the full list of sounds are ").append(
                Component.literal("here").withStyle(
                    Style.EMPTY.withUnderlined(true)
                        .withClickEvent(ClickEvent.OpenUrl(URI.create(SOUND_LIST_URL)))
                        .withHoverEvent(HoverEvent.ShowText(Component.literal("§eOpen the sound list")))
                )
            )
        )
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity) return
        if (!enabled || !highlighter) return
        val player = mc.player ?: return
        if (!player.isShiftKeyDown || !player.mainHandItem.isEtherwarpable()) return

        val etherPos = EtherwarpUtils.getEtherPos()
        val pos = etherPos.pos ?: return
        val color = if (etherPos.succeeded) validColor else invalidColor
        val box = AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
        BlockRenderUtils.draw3DBox(event.matrixStack, event.camera, box, color)
    }
}
