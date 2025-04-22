package com.simiacryptus.jopenai.audio

import org.slf4j.LoggerFactory
import java.util.*
import kotlin.math.ln

open class TrainedSilenceDiscriminator(
    inputBuffer: Queue<AudioPacket>,
    outputBuffer: Queue<AudioPacket>,
    onPacket: (AudioPacket) -> Unit,
    continueFn: () -> Boolean,
    var isVerbose: Boolean = false,
    val frequencyBands: List<Pair<Double, Double>> = listOf(
        85.0 to 255.0,    // Low band
        256.0 to 2000.0,  // Mid band
        2001.0 to 8000.0  // High band
    )
) : SilenceDiscriminator(
    inputBuffer = inputBuffer,
    outputBuffer = outputBuffer,
    onPacket = onPacket,
    continueFn = continueFn
) {

    init {
        onModeChanged.addListener {
            // Log KL-divergence when training data is updated
            if (isTraining != null && silence.rmsPercentileTool.memory.size > 1 && speech.rmsPercentileTool.memory.size > 1) {
                logKL()
            }
        }
    }
    protected open fun newPercentile() = PercentileTool(10000)
    protected open fun PercentileTool.getCurrentRMSThreshold() = findEntropyThreshold5(0.0)
    inner class Statistics {
        val rmsPercentileTool by lazy { newPercentile() }
        val iec61672PercentileTool by lazy { newPercentile() }
        val spectralEntropyPercentileTool by lazy { newPercentile() }
        val spectralCentroidPercentileTool by lazy { newPercentile() }
        val spectralFlatnessPercentileTool by lazy { newPercentile() }

        // Dynamic frequency band tools
        val frequencyBandTools by lazy { frequencyBands.map { newPercentile() } }

        fun isQuiet(vararg packets: AudioPacket): Double {
            return packets.map { packet ->
                (listOf(
                    packet.rms < rmsPercentileTool.getCurrentRMSThreshold(),
                    packet.aWeighting < iec61672PercentileTool.getCurrentRMSThreshold(),
                    packet.spectralEntropy < spectralEntropyPercentileTool.getCurrentRMSThreshold(),
                ) + frequencyBands.mapIndexed { index, (low, high) ->
                    packet.frequencyBandPower(low, high) < frequencyBandTools[index].getCurrentRMSThreshold()
                }).map { if (it) 1.0 else 0.0 }.average()
            }.average()
        }

        fun isEmpty() =
            rmsPercentileTool.isEmpty() ||
            iec61672PercentileTool.isEmpty() ||
            spectralEntropyPercentileTool.isEmpty() ||
            spectralCentroidPercentileTool.isEmpty() ||
            spectralFlatnessPercentileTool.isEmpty() ||
            frequencyBandTools.any { it.isEmpty() }
    }

    private val silence = Statistics()
    private val speech = Statistics()
    private val unknown = Statistics()
    var isTraining: Boolean? = false
        set(value) {
            if (field != value) {
                field = value
                if (silence.rmsPercentileTool.memory.size > 1 && speech.rmsPercentileTool.memory.size > 1) {
                    logKL()
                }
                if(value == null) {
                    // TODO: Calculate a more sophisticated discriminator
                }
            }
        }

    private fun logKL() {
        log.info("KL-Divergence Update:")
        log.info("RMS KL-Divergence: ${silence.rmsPercentileTool.computeKLDivergence(speech.rmsPercentileTool)}")
        log.info("IEC61672 KL-Divergence: ${silence.iec61672PercentileTool.computeKLDivergence(speech.iec61672PercentileTool)}")
        log.info(
            "Spectral Entropy KL-Divergence: ${
                silence.spectralEntropyPercentileTool.computeKLDivergence(
                    speech.spectralEntropyPercentileTool
                )
            }"
        )
        log.info(
            "Spectral Centroid KL-Divergence: ${
                silence.spectralCentroidPercentileTool.computeKLDivergence(
                    speech.spectralCentroidPercentileTool
                )
            }"
        )
        log.info(
            "Spectral Flatness KL-Divergence: ${
                silence.spectralFlatnessPercentileTool.computeKLDivergence(
                    speech.spectralFlatnessPercentileTool
                )
            }"
        )
        frequencyBands.forEachIndexed { index, (low, high) ->
            log.info(
                "Frequency Band ${index + 1} (${low}-${high}Hz) KL-Divergence: " +
                        "${silence.frequencyBandTools[index].computeKLDivergence(speech.frequencyBandTools[index])}"
            )
        }
    }

    var bias: Double = -0.1

    override fun processPacket(packet: AudioPacket) {
        when (isTraining) {
            true -> speech
            false -> silence
            else -> unknown
        }.let { statistics ->
            statistics.rmsPercentileTool.add(packet.rms)
            statistics.iec61672PercentileTool.add(packet.aWeighting)
            statistics.spectralEntropyPercentileTool.add(packet.spectralEntropy)
            statistics.spectralCentroidPercentileTool.add(packet.spectralCentroid)
            statistics.spectralFlatnessPercentileTool.add(packet.spectralFlatness)
            frequencyBands.forEachIndexed { index, (low, high) ->
                statistics.frequencyBandTools[index].add(packet.frequencyBandPower(low, high))
            }
        }
        logPacket(packet)
        super.processPacket(packet)
    }

    override fun isQuiet(vararg packets: AudioPacket): Boolean {
        if (silence.isEmpty() || speech.isEmpty()) return unknown.isQuiet(*packets) < 0.5
        if (isTraining != null) return !isTraining!!
        return packets.map { computeQuietMetrics(it).average() > bias }.all { it }
    }

    protected open fun compare(v: Double, a: PercentileTool, b: PercentileTool): Double {
        val vB = b.getDensityOfValue(v)
        val vA = a.getDensityOfValue(v)
        return when {
            vB == 0.0 || vA == 0.0 -> {
                val pA = a.getPercentileOfValue(v)
                val pB = b.getPercentileOfValue(v)
                when {
                    pA == 1.0 && pB == 0.0 -> {
                        val dA = a.getDistanceFromBounds(v)
                        val dB = b.getDistanceFromBounds(v)
                        if (dA < dB) 1.0 else -1.0
                    }

                    pA == 1.0 -> -1.0
                    pB == 0.0 -> 1.0
                    else -> (pA - pB)
                }
            }

            else -> ln(vA / vB)
        }
    }

    private fun computeQuietMetrics(packet: AudioPacket) = listOf(
        compare(packet.rms, silence.rmsPercentileTool, speech.rmsPercentileTool),
        compare(packet.aWeighting, silence.iec61672PercentileTool, speech.iec61672PercentileTool),
        compare(packet.spectralEntropy, silence.spectralEntropyPercentileTool, speech.spectralEntropyPercentileTool),
        // Intentionally inverted - these metrics trend lower for active speech
//        compare(packet.spectralCentroid, speech.spectralCentroidPercentileTool, silence.spectralCentroidPercentileTool),
//        compare(packet.spectralFlatness, speech.spectralFlatnessPercentileTool, silence.spectralFlatnessPercentileTool)
    ) + frequencyBands.mapIndexed { index, (low, high) ->
        compare(
            packet.frequencyBandPower(low, high),
            silence.frequencyBandTools[index],
            speech.frequencyBandTools[index]
        )
    }

    private fun logPacket(packet: AudioPacket) {
        if (isVerbose) log.debug(
            "Audio metrics comparison: ${
                listOf(
                    "RMS: ${
                        compare(
                            silence.rmsPercentileTool.getPercentileOfValue(packet.rms),
                            speech.rmsPercentileTool.getPercentileOfValue(packet.rms)
                        )
                    }",
                    "IEC61672: ${
                        compare(
                            silence.iec61672PercentileTool.getPercentileOfValue(packet.aWeighting),
                            speech.iec61672PercentileTool.getPercentileOfValue(packet.aWeighting)
                        )
                    }",
                    "Spectral Entropy: ${
                        compare(
                            silence.spectralEntropyPercentileTool.getPercentileOfValue(packet.spectralEntropy),
                            speech.spectralEntropyPercentileTool.getPercentileOfValue(packet.spectralEntropy)
                        )
                    }",
                    "Spectral Centroid: ${
                        compare(
                            speech.spectralCentroidPercentileTool.getPercentileOfValue(packet.spectralCentroid),
                            silence.spectralCentroidPercentileTool.getPercentileOfValue(packet.spectralCentroid)
                        )
                    }",
                    "Spectral Flatness: ${
                        compare(
                            speech.spectralFlatnessPercentileTool.getPercentileOfValue(packet.spectralFlatness),
                            silence.spectralFlatnessPercentileTool.getPercentileOfValue(packet.spectralFlatness)
                        )
                    }",
                    "isTalk: ${isTraining}",
                ).joinToString(" ")
            }"
        )
    }

    fun reset() {
        silence.clear()
        speech.clear()
    }

    private fun Statistics.clear() {
        rmsPercentileTool.clear()
        iec61672PercentileTool.clear()
        spectralEntropyPercentileTool.clear()
        spectralCentroidPercentileTool.clear()
        spectralFlatnessPercentileTool.clear()
        frequencyBandTools.forEach { it.clear() }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrainedSilenceDiscriminator::class.java)
        fun compare(a: Double, b: Double) = when {
            a > b -> "${a.format("%.2f")} > ${b.format("%.2f")}"
            a < b -> "${a.format("%.2f")} < ${b.format("%.2f")}"
            else -> "${a.format("%.2f")} = ${b.format("%.2f")}"
        }
    }
}

private fun Number.format(s: String) = String.format(s, this)