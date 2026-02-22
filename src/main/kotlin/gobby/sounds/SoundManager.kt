package gobby.sounds

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.sound.OggAudioStream
import net.minecraft.sound.SoundCategory
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

object SoundManager {

    fun play(path: String) {
        val masterVolume = mc.options.getSoundVolume(SoundCategory.MASTER)
        if (masterVolume <= 0f) return

        Thread {
            try {
                val stream = SoundManager::class.java.classLoader.getResourceAsStream(path) ?: return@Thread
                val oggStream = OggAudioStream(stream)
                val format = oggStream.format
                val line = AudioSystem.getSourceDataLine(format) as SourceDataLine
                line.open(format)

                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    val gain = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    gain.value = 20f * log10(masterVolume.toDouble()).toFloat()
                }

                line.start()

                val samples = mutableListOf<Float>()
                @Suppress("ControlFlowWithEmptyBody")
                while (oggStream.read { samples.add(it) }) {}

                val bytes = ByteArray(samples.size * 2)
                for (i in samples.indices) {
                    val sample = (samples[i] * 32767f).coerceIn(-32768f, 32767f).toInt()
                    bytes[i * 2] = (sample and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                }

                line.write(bytes, 0, bytes.size)
                line.drain()
                line.close()
                oggStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
