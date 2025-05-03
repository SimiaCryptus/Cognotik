package com.simiacryptus.jopenai.audio

import kotlin.math.absoluteValue
import kotlin.math.log

class PercentileTool(
    val memorySize: Int = 10000
) {
    companion object {

        /**
         * Compute the KL-divergence between two sorted lists of values.
         */
        fun computeKLDivergence(a: DoubleArray, b: DoubleArray): Double {
            var klDiv = 0.0
            val maxValue = kotlin.math.max(a.lastOrNull() ?: 0.0, b.lastOrNull() ?: 0.0)
            if (maxValue == 0.0) return 0.0
            val aList = a.map { it / maxValue }.toMutableList()
            val bList = b.map { it / maxValue }.toMutableList()
            while (aList.isNotEmpty() && bList.isNotEmpty()) {
                val aV = aList.first()
                val bV = bList.first()
                when {
                    aV < bV -> {
                        klDiv += if (aV > 0.0 && bV > 0.0) aV * kotlin.math.ln(aV / bV) else 0.0
                        aList.removeAt(0)
                    }

                    else -> {
                        klDiv += if (aV > 0.0 && bV > 0.0) bV * kotlin.math.ln(bV / aV) else 0.0
                        bList.removeAt(0)
                    }
                }
            }
            return klDiv / (a.size + b.size)
        }
    }

    internal var memory = ArrayList<Double>()

        private set

    /**
     * Add a value to the internal memory in a threadsafe manner.
     * Ensures that the list remains sorted and does not exceed memorySize.
     */
    @Synchronized

    fun add(value: Double) {

        val index = memory.binarySearch(value).let { if (it < 0) -it - 1 else it }
        memory.add(index, value)

        if (memory.size > memorySize) {
            val newSize = memorySize / 2
            val newMemory = ArrayList<Double>(newSize)
            for (i in 0 until memory.size step 2) {
                newMemory.add(memory[i])
            }
            memory = newMemory
        }
    }

    @Synchronized

    fun getValueOfPercentile(percentile: Double): Double {
        if (memory.isEmpty()) return 0.0
        val index = (percentile * memory.size).toInt().coerceIn(0, memory.size - 1)
        return memory[index]
    }

    fun getPercentileOfValue(value: Double): Double {
        if (memory.isEmpty()) return 0.0
        val idx = memory.binarySearch(value).let { if (it < 0) -it - 1 else it }
        return idx.toDouble() / memory.size
    }

    fun getDensityOfValue(value: Double): Double {
        if (memory.isEmpty()) return 0.0
        if (value < memory.first() || value > memory.last()) return 0.0
        val index = memory.binarySearch(value).let { if (it < 0) -it - 1 else it }.coerceIn(0, memory.size - 1)
        val neighborIndex = (if (index >= memory.size - 1) memory.size - 2 else index + 1).coerceIn(0, memory.size - 1)
        return 1.0 / ((memory[neighborIndex] - memory[index]).absoluteValue * memory.size.toDouble())
    }

    fun getDistanceFromBounds(value: Double): Double {
        if (memory.isEmpty()) return 0.0
        if (value < memory.first()) return memory.first() - value
        if (value > memory.last()) return value - memory.last()
        return 0.0
    }

    fun findEntropyThreshold5(percentileBias: Double = 0.0): Double {
        if (memory.size < 2) return 0.0
        if (memory.first() == memory.last()) {
            return memory.first()
        }

        var bestThreshold = memory[0]
        var bestIndex = -1
        var maxJS = Double.NEGATIVE_INFINITY
        val min = memory.first()
        val max = memory.last()

        fun jensenShannonDivergence(p: Double, q: Double): Double {

            if (p <= 0.0 || p >= 1.0 || q <= 0.0 || q >= 1.0) return Double.NEGATIVE_INFINITY
            val m = (p + q) / 2
            val p1 = 1 - p
            val q1 = 1 - q
            val m1 = (p1 + q1) / 2
            return 0.5 * (
                    p * log(p / m, 2.0) +
                            p1 * log(p1 / m1, 2.0)
                    ) + 0.5 * (
                    q * log(q / m, 2.0) +
                            q1 * log(q1 / m1, 2.0)
                    )
        }

        for (i in 1 until memory.size) {
            if (memory[i] == memory[i - 1]) continue
            val threshold = (memory[i - 1] + memory[i]) / 2
            if (threshold <= min || threshold >= max) continue

            val fractionOfValues = i.toDouble() / memory.size
            val fractionOfRange = (threshold - min) / (max - min)

            if (fractionOfValues <= 0.0 || fractionOfRange <= 0.0) continue
            if (fractionOfValues >= 1.0 || fractionOfRange >= 1.0) break

            val js = jensenShannonDivergence(fractionOfValues, fractionOfRange)

            if (js > maxJS) {
                maxJS = js
                bestThreshold = threshold
                bestIndex = i
            }
        }

        if (percentileBias != 0.0 && bestIndex != -1) {
            val percentileIndex = (bestIndex + (percentileBias * memory.size)).toInt().coerceIn(0, memory.size - 1)
            if (percentileIndex != bestIndex) {
                bestThreshold = memory[percentileIndex]
            }
        }

        return bestThreshold
    }

    fun isEmpty(): Boolean {
        return memory.isEmpty()
    }

    fun clear() {
        memory.clear()
    }

    fun computeKLDivergence(other: PercentileTool): Double {
        return computeKLDivergence(memory.toDoubleArray(), other.memory.toDoubleArray())
    }
}