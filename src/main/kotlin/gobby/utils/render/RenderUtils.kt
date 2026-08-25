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

    private const val FULL_BRIGHT_LIGHT = 0xF000F0
    private const val TEXT_BACKGROUND_ARGB = 0x40000000
    private const val OPAQUE_ALPHA = 0xFF shl 24
    private const val NO_OUTLINE = 0

    var frameCollector: SubmitNodeCollector? = null

    fun drawStringInWorld(
        text: String,
        vec3: Vec3,
        matrixStack: PoseStack,
        camera: Camera,
        color: Color = Color.WHITE,
        scale: Float = 0.4f
    ) {
        val collector = frameCollector ?: return
        matrixStack.pushPose()
        faceCamera(matrixStack, vec3, camera, scale)
        collector.submitText(
            matrixStack,
            -mc.font.width(text) / 2f,
            0f,
            Component.literal(text).visualOrderText,
            false,
            Font.DisplayMode.SEE_THROUGH,
            FULL_BRIGHT_LIGHT,
            color.rgb or OPAQUE_ALPHA,
            TEXT_BACKGROUND_ARGB,
            NO_OUTLINE
        )
        matrixStack.popPose()
    }

    private fun faceCamera(matrixStack: PoseStack, vec3: Vec3, camera: Camera, scale: Float) {
        val cameraPos = camera.cameraPos
        matrixStack.translate(vec3.x - cameraPos.x, vec3.y - cameraPos.y, vec3.z - cameraPos.z)
        matrixStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()))
        matrixStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()))
        matrixStack.scale(-scale, -scale, scale)
    }
}
