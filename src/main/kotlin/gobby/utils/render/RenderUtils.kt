package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.cameraPos
import net.minecraft.client.gui.Font
import net.minecraft.client.Camera
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.world.phys.Vec3
import java.awt.Color

object RenderUtils {

    fun drawStringInWorld(
        text: String,
        vec3: Vec3,
        matrixStack: PoseStack,
        camera: Camera,
        color: Color = Color.WHITE,
        depthTest: Boolean = true,
        scale: Float = 0.4f
    ) {
        val textRenderer = mc.font
        val cameraPos = camera.cameraPos

        matrixStack.pushPose()

        val textX = vec3.x - cameraPos.x
        val textY = vec3.y - cameraPos.y
        val textZ = vec3.z - cameraPos.z

        matrixStack.translate(textX, textY, textZ)

        //? if <=1.21.10 {
        val yaw = camera.yRot
        val pitch = camera.xRot
        //?}
        //? if >=1.21.11 {
        /*val yaw = camera.yRot()
        val pitch = camera.xRot()*/
        //?}
        matrixStack.mulPose(Axis.YP.rotationDegrees(-yaw))
        matrixStack.mulPose(Axis.XP.rotationDegrees(pitch))

        matrixStack.scale(-scale, -scale, scale)
        val textWidth = textRenderer.width(text)

        val immediate = mc.renderBuffers().bufferSource()
        textRenderer.drawInBatch(
            text,
            -textWidth / 2f,
            0f,
            color.rgb or 0xFF.shl(24),
            false,
            matrixStack.last().pose(),
            immediate,
            Font.DisplayMode.SEE_THROUGH,
            0x40000000,
            15728880
        )

        immediate.endBatch()
        matrixStack.popPose()
    }
}
