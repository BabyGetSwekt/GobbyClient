package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.Camera
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import java.awt.Color

object RenderUtils {

    fun drawStringInWorld(
        text: String,
        vec3: Vec3d,
        matrixStack: MatrixStack,
        camera: Camera,
        color: Color = Color.WHITE,
        depthTest: Boolean = true,
        scale: Float = 0.4f
    ) {
        val textRenderer = mc.textRenderer
        val cameraPos = camera.pos

        matrixStack.push()

        val textX = vec3.x - cameraPos.x
        val textY = vec3.y - cameraPos.y
        val textZ = vec3.z - cameraPos.z

        matrixStack.translate(textX, textY, textZ)

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.yaw))
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.pitch))

        matrixStack.scale(-scale, -scale, scale)
        val textWidth = textRenderer.getWidth(text)

        val immediate = mc.bufferBuilders.entityVertexConsumers
        textRenderer.draw(
            text,
            -textWidth / 2f,
            0f,
            color.rgb and 0xFFFFFF,
            false,
            matrixStack.peek().positionMatrix,
            immediate,
            TextRenderer.TextLayerType.SEE_THROUGH,
            0x40000000,
            15728880
        )

        immediate.drawCurrentLayer()
        matrixStack.pop()
    }
}