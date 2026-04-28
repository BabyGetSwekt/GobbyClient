package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.sound.OggAudioStream
import net.minecraft.sound.SoundCategory
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.log10

object SoundManager {

    fun play(path: String) {
        val volume = mc.options.getSoundVolume(SoundCategory.MASTER)
        if (volume <= 0f) return
        thread(name = "GobbyClient-Sound", isDaemon = true) {
            runCatching { playResource(path, volume) }.onFailure(Throwable::printStackTrace)
        }
    }

    private fun playResource(path: String, volume: Float) {
        val resource = SoundManager::class.java.classLoader.getResourceAsStream(path) ?: return
        resource.use { stream ->
            val ogg = OggAudioStream(stream)
            try {
                (AudioSystem.getSourceDataLine(ogg.format) as SourceDataLine).use { line ->
                    line.open(ogg.format)
                    line.applyGain(volume)
                    line.start()
                    val pcm = ogg.readAllPcm()
                    line.write(pcm, 0, pcm.size)
                    line.drain()
                }
            } finally {
                ogg.close()
            }
        }
    }

    private fun SourceDataLine.applyGain(volume: Float) {
        if (!isControlSupported(FloatControl.Type.MASTER_GAIN)) return
        (getControl(FloatControl.Type.MASTER_GAIN) as FloatControl).value = 20f * log10(volume.toDouble()).toFloat()
    }

    private fun OggAudioStream.readAllPcm(): ByteArray {
        val samples = mutableListOf<Float>()
        while (read { samples.add(it) }) { /* drain stream */ }
        return ByteArray(samples.size * 2).also { bytes ->
            samples.forEachIndexed { i, sample ->
                val v = (sample * 32767f).coerceIn(-32768f, 32767f).toInt()
                bytes[i * 2] = (v and 0xFF).toByte()
                bytes[i * 2 + 1] = (v shr 8 and 0xFF).toByte()
            }
        }
    }
}
