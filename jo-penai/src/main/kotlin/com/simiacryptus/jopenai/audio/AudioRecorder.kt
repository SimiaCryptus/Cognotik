package com.simiacryptus.jopenai.audio

import org.apache.commons.io.input.buffer.CircularByteBuffer
import org.slf4j.LoggerFactory
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

open class AudioRecorder(
    val audioBuffer: Queue<AudioPacket> = LinkedList(),
    val msPerPacket: Long = 100,
    val continueFn: () -> Boolean = { true },
    val selectedMicLine: String? = null,
    val audioFormat: AudioFormat = AudioFormat(16000f, 16, 1, true, false),
) {
    private var totalBytesRecorded: Long = 0L
    private var startTimeNanos: Long = System.nanoTime()

    fun run() {
        val targetDataLine = openMic()
        val audioFormat = targetDataLine.format
        val packetLength = (audioFormat.frameRate * audioFormat.frameSize * (msPerPacket / 1000.0)).toInt()
        try {
            log.info("Audio recording started with packet length: {} bytes", packetLength)
            val buffer = ByteArray(packetLength)
            val circularBuffer = CircularByteBuffer(packetLength * 2)
            startTimeNanos = System.nanoTime()
            while (continueFn()) {
                try {
                    var bytesRead = 0
                    val endTime = System.currentTimeMillis() + msPerPacket
                    while (bytesRead != -1 && System.currentTimeMillis() < endTime) {
                        bytesRead = targetDataLine.read(buffer, 0, buffer.size)
                        circularBuffer.add(buffer, 0, bytesRead)
                        totalBytesRecorded += bytesRead
                        while (circularBuffer.currentNumberOfBytes >= packetLength) {
                            val array = ByteArray(packetLength)
                            circularBuffer.read(array, 0, packetLength)
                            val chunkStartNanos = startTimeNanos +
                                    ((totalBytesRecorded.toDouble() * 1_000_000_000.0) /
                                            (audioFormat.frameRate * audioFormat.frameSize)).toLong()
                            val chunkStartMillis = chunkStartNanos / 1_000_000
                            audioBuffer.add(
                                AudioPacket(
                                    AudioPacket.convertRaw(array, audioFormat),
                                    audioFormat,
                                    chunkStartMillis
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error during audio recording", e)
                }
            }
            log.info("Audio recording stopped, final buffer size: {}", audioBuffer.size)
        } finally {
            targetDataLine.close()
            log.info("Microphone line closed")
        }
    }

    open fun openMic(): TargetDataLine {
        val mixerInfo = AudioSystem.getMixerInfo()
        val micLine = (selectedMicLine ?: "Microphone").let { selectedMicLine ->
            mixerInfo.firstOrNull { it.toString().contains(selectedMicLine, true) }
        } ?: throw IllegalStateException("No microphone line found; available lines: ${mixerInfo.joinToString { it.name }}, selected: $selectedMicLine")
        try {
            val targetLineInfo = AudioSystem.getMixer(micLine).lineInfo
            log.info(" Audio Mixer Target Line: $targetLineInfo (${targetLineInfo.javaClass.canonicalName})")
            if (targetLineInfo is DataLine.Info) {
                targetLineInfo.formats.forEach { format ->
                    log.info("  Audio Mixer Target Line Format: $format; Channels: ${format.channels}; Sample Rate: ${format.sampleRate}")
                }
            }
        } catch (e: Exception) {
            log.error("Error getting audio mixer target line", e)
        }
        return AudioSystem.getTargetDataLine(audioFormat, micLine).apply {
            open(audioFormat)
            start()
            log.info("Microphone line opened with format: {}", audioFormat)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AudioRecorder::class.java)

    }

}