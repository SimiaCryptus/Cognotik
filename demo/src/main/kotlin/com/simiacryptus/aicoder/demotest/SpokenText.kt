package com.simiacryptus.aicoder.demotest

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

class SpokenText(
    val text: String,
    val byteInputStream: InputStream,
    val renderTime: Long
) {
    fun play(totalMs: Long = 0) {
        log.info("Speaking: $text")
        val startTime = System.currentTimeMillis()
        AudioSystem.getAudioInputStream(byteInputStream).use { originalAudioInputStream ->
            val format = originalAudioInputStream.format
            val frameSize = format.frameSize
            val audioData = originalAudioInputStream.readAllBytes()
            // Ensure all values are positive
            for (i in audioData.indices) {
                audioData[i] = (audioData[i].toInt() and 0xFF).toByte()
            }
            val correctedAudioInputStream =
                AudioInputStream(ByteArrayInputStream(audioData), format, audioData.size.toLong() / frameSize)

            val clip: Clip = AudioSystem.getClip()
            clip.open(correctedAudioInputStream)
            clip.apply {
                start()
                // Wait for the audio to finish playing
                val millis = (frameLength * 1000L) / format.frameRate.toLong()
                log.info("Playing audio for $millis ms")
                Thread.sleep(millis)
                // Ensure the clip is closed after playing
                close()
            }
        }
        val elapsed = System.currentTimeMillis() - startTime
        log.info("Audio playback completed in $elapsed ms")
        val remaining = totalMs - (elapsed + renderTime)
        if (remaining > 0) {
            log.debug("Waiting for $remaining ms")
            Thread.sleep(remaining)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}