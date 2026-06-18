package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.cameraPos
import net.minecraft.client.gui.Font
import net.minecraft.client.Camera
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.world.phys.Vec3
import java.awt.Color

object RenderUtils {

    var frameCollector: SubmitNodeCollector? = null

    fun drawStringInWorld(
        text: String,
        vec3: Vec3,
        matrixStack: PoseStack,
        camera: Camera,
        color: Color = Color.WHITE,
        depthTest: Boolean = true,
        scale: Float = 0.4f
    ) {
        val collector = frameCollector ?: return
        val textRenderer = mc.font
        val cameraPos = camera.cameraPos

        matrixStack.pushPose()

        val textX = vec3.x - cameraPos.x
        val textY = vec3.y - cameraPos.y
        val textZ = vec3.z - cameraPos.z

        matrixStack.translate(textX, textY, textZ)

        val yaw = camera.yRot()
        val pitch = camera.xRot()
        matrixStack.mulPose(Axis.YP.rotationDegrees(-yaw))
        matrixStack.mulPose(Axis.XP.rotationDegrees(pitch))

        matrixStack.scale(-scale, -scale, scale)
        val textWidth = textRenderer.width(text)

        collector.submitText(
            matrixStack,
            -textWidth / 2f,
            0f,
            Component.literal(text).visualOrderText,
            false,
            Font.DisplayMode.SEE_THROUGH,
            15728880,
            color.rgb or 0xFF.shl(24),
            0x40000000,
            0
        )

        matrixStack.popPose()
    }
}
