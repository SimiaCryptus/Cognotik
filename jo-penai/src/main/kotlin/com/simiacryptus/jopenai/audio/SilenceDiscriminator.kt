package com.simiacryptus.jopenai.audio

import com.simiacryptus.jopenai.util.EventDispatcher
import java.util.*

enum class AudioState {
    TALKING,
    QUIET
}

abstract class SilenceDiscriminator(
    val inputBuffer: Queue<AudioPacket>,
    val outputBuffer: Queue<AudioPacket>,
    val onPacket: (AudioPacket) -> Unit,
    val continueFn: () -> Boolean,
) {
    var requiredQuietWindowsForTransition = 5
    var requiredTalkWindowsForTransition = 3
    var currentState = AudioState.QUIET
    var minTalkTime = 2.0
    val onModeChanged = EventDispatcher()
    val talkTime: Double
        @Synchronized
        get() = outputPacketBuffer.sumOf { it.duration }

    private var consecutiveQuietWindows = 0
    private var consecutiveTalkWindows = 0
    private var outputPacketBuffer = ArrayList<AudioPacket>()
    private var lastOutputBuffer: ArrayList<AudioPacket>? = null

    fun run() {
        while (shouldContinue(inputBuffer)) {
            try {
                poll(inputBuffer)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("Polling thread was interrupted.", e)
                break
            }
        }
    }

    fun poll(audioPackets: Queue<AudioPacket>) {
        val audioPacket = audioPackets.poll()
        if (null == audioPacket) {
            Thread.sleep(10) // Increased sleep to reduce CPU usage
        } else {
            processPacket(audioPacket)
        }
    }

    fun shouldContinue(audioPackets: Queue<AudioPacket>): Boolean = this.continueFn() || audioPackets.isNotEmpty()

    protected open fun processPacket(packet: AudioPacket) {
        when (currentState) {
            AudioState.QUIET -> {
                if (!isQuiet(packet)) {
                    consecutiveQuietWindows = 0
                    if (consecutiveTalkWindows++ >= requiredTalkWindowsForTransition) {
                        log.debug("State transition: QUIET -> TALKING")
                        currentState = AudioState.TALKING
                        onModeChanged.notifyListeners()
                    }
                } else {
                    consecutiveTalkWindows = 0
                    outputPacketBuffer.clear()
                }
                synchronized(this.outputPacketBuffer) {
                    this.outputPacketBuffer.add(packet)
                }
            }

            AudioState.TALKING -> {
                synchronized(this.outputPacketBuffer) {
                    this.outputPacketBuffer.add(packet)
                }
                if (isQuiet(packet)) {
                    consecutiveTalkWindows = 0
                    if (consecutiveQuietWindows++ >= requiredQuietWindowsForTransition) {
                        log.debug("State transition: TALKING -> QUIET")
                        currentState = AudioState.QUIET
                        onModeChanged.notifyListeners()
                        val reduced = flushOutput()
                        if (reduced.duration > minTalkTime) {
                            log.debug("Outputting packet size: ${reduced.duration}.")
                            outputBuffer.add(reduced)
                        } else {
                            log.debug("Ignoring packet size: ${reduced.duration}.")
                        }
                    }
                } else {
                    consecutiveQuietWindows = 0
                }
            }
        }
        onPacket(packet)
    }

    protected abstract fun isQuiet(vararg packets: AudioPacket): Boolean

    private fun flushOutput(): AudioPacket {
        val reduced = if (outputPacketBuffer.isNotEmpty()) {
            outputPacketBuffer.reduce { a, b -> a + b }
        } else {
            AudioPacket.empty()
        }
        lastOutputBuffer = outputPacketBuffer
        outputPacketBuffer = ArrayList()
        log.debug("Flushing output buffer: ${reduced.duration}")
        return reduced
    }

    fun clearMemory() {
        lastOutputBuffer = outputPacketBuffer
        outputPacketBuffer = ArrayList()
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(SilenceDiscriminator::class.java)
    }
}