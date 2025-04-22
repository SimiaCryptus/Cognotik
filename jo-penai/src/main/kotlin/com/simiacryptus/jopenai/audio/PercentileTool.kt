package com.simiacryptus.jopenai.audio

import kotlin.math.absoluteValue
import kotlin.math.log

class PercentileTool(
    val memorySize: Int = 10000
) {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(PercentileTool::class.java)

        /**
         * Compute the KL-divergence between two sorted lists of values.
         */
        fun computeKLDivergence(a: DoubleArray, b: DoubleArray): Double {
            var klDiv = 0.0
            val maxValue = kotlin.math.max(a.lastOrNull() ?: 0.0, b.lastOrNull() ?: 0.0)
            if (maxValue == 0.0) return 0.0
            val aList = a.map { it/maxValue }.toMutableList()
            val bList = b.map { it/maxValue }.toMutableList()
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

    internal var memory = ArrayList<Double>() // Maintains sorted order
        private set

    /**
     * Add a value to the internal memory in a threadsafe manner.
     * Ensures that the list remains sorted and does not exceed memorySize.
     */
    @Synchronized

    fun add(value: Double) {
        // Insert value in sorted order using binary search
        val index = memory.binarySearch(value).let { if (it < 0) -it - 1 else it }
        memory.add(index, value)
        // If memory exceeds memorySize, rebuild the list to include every other element
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

    fun findEntropyThreshold1(percentileBias: Double = 0.0): Double {
        if (memory.size < 2) return 0.0
        if (memory.first() == memory.last()) {
            return memory.first()
        }
        var bestThreshold = memory[0]
        var bestIndex = -1
        var maxEntropy = Double.NEGATIVE_INFINITY
        val min = memory.first()
        val max = memory.last()
        for (i in 1 until memory.size) {
            if (memory[i] == memory[i - 1]) continue
            val threshold = (memory[i - 1] + memory[i]) / 2
            if (threshold <= min || threshold >= max) continue
            val fractionOfRange = (threshold - min) / (max - min)
            val fractionOfValues = i.toDouble() / memory.size
            if (fractionOfValues == 0.0 || fractionOfRange <= 0.0) continue
            if (fractionOfValues >= 1.0 || fractionOfRange >= 1.0) break
            val entropy = -(fractionOfValues * fractionOfRange * (log(fractionOfRange, 2.0)) +
                    (1.0 - fractionOfValues) * (1.0 - fractionOfRange) * (log(1.0 - fractionOfRange, 2.0)))
            if (entropy > maxEntropy) {
                maxEntropy = entropy
                bestThreshold = threshold
                bestIndex = i
            }
        }
        if (percentileBias != 0.0) {
            val percentileIndex = (bestIndex + (percentileBias * memory.size)).toInt().coerceIn(0, memory.size - 1)
            if (percentileIndex != bestIndex) {
                bestThreshold = memory[percentileIndex]
            }
        }
        return bestThreshold
    }

    fun findEntropyThreshold2(percentileBias: Double = 0.0): Double {
        if (memory.size < 2) return 0.0
        if (memory.first() == memory.last()) {
            return memory.first()
        }
        var bestThreshold = memory[0]
        var bestIndex = -1
        var maxEntropy = Double.NEGATIVE_INFINITY
        val min = memory.first()
        val max = memory.last()
        for (i in 1 until memory.size) {
            if (memory[i] == memory[i - 1]) continue
            val threshold = (memory[i - 1] + memory[i]) / 2
            if (threshold <= min || threshold >= max) continue
            val fractionOfRange = (threshold - min) / (max - min)
            val fractionOfValues = i.toDouble() / memory.size
            if (fractionOfValues == 0.0 || fractionOfRange <= 0.0) continue
            if (fractionOfValues >= 1.0 || fractionOfRange >= 1.0) break
            // Different entropy formulation: using Gini impurity
            val gini1 = fractionOfValues * (1.0 - fractionOfValues)
            val gini2 = fractionOfRange * (1.0 - fractionOfRange)
            val giniScore = gini1 + gini2
            if (giniScore > maxEntropy) {
                maxEntropy = giniScore
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

    fun findEntropyThreshold3(percentileBias: Double = 0.0): Double {
        if (memory.size < 2) return 0.0
        if (memory.first() == memory.last()) {
            return memory.first()
        }
        var bestThreshold = memory[0]
        var bestIndex = -1
        var maxScore = Double.NEGATIVE_INFINITY
        val min = memory.first()
        val max = memory.last()
        for (i in 1 until memory.size) {
            if (memory[i] == memory[i - 1]) continue
            val threshold = (memory[i - 1] + memory[i]) / 2
            if (threshold <= min || threshold >= max) continue
            val fractionOfValues = i.toDouble() / memory.size
            val fractionOfRange = (threshold - min) / (max - min)
            if (fractionOfValues == 0.0 || fractionOfRange <= 0.0) continue
            if (fractionOfValues >= 1.0 || fractionOfRange >= 1.0) break
            val synergyScore = fractionOfValues * fractionOfRange
            if (synergyScore > maxScore) {
                maxScore = synergyScore
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

    fun findEntropyThreshold4(percentileBias: Double = 0.0): Double {
        if (memory.size < 2) return 0.0
        if (memory.first() == memory.last()) {
            return memory.first()
        }
        var bestThreshold = memory[0]
        var bestIndex = -1
        var maxKL = Double.NEGATIVE_INFINITY
        val min = memory.first()
        val max = memory.last()
        for (i in 1 until memory.size) {
            if (memory[i] == memory[i - 1]) continue
            val threshold = (memory[i - 1] + memory[i]) / 2
            if (threshold <= min || threshold >= max) continue
            val fractionOfValues = i.toDouble() / memory.size
            val fractionOfRange = (threshold - min) / (max - min)
            if (fractionOfValues == 0.0 || fractionOfRange <= 0.0) continue
            if (fractionOfValues >= 1.0 || fractionOfRange >= 1.0) break
            // Compute KL divergence in base 2
            val kl = fractionOfValues * log(fractionOfValues / fractionOfRange, 2.0) +
                    (1.0 - fractionOfValues) * log((1.0 - fractionOfValues) / (1.0 - fractionOfRange), 2.0)
            if (kl > maxKL) {
                maxKL = kl
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

        // Inline helper for computing the Jensen-Shannon divergence in base 2.
        fun jensenShannonDivergence(p: Double, q: Double): Double {
            // Avoid edge cases; if p or q are 0 or 1, the JS can be degenerate
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

            // Avoid invalid or degenerate values
            if (fractionOfValues <= 0.0 || fractionOfRange <= 0.0) continue
            if (fractionOfValues >= 1.0 || fractionOfRange >= 1.0) break

            // Compute Jensen–Shannon divergence
            val js = jensenShannonDivergence(fractionOfValues, fractionOfRange)

            if (js > maxJS) {
                maxJS = js
                bestThreshold = threshold
                bestIndex = i
            }
        }

        // Apply percentileBias if requested
        if (percentileBias != 0.0 && bestIndex != -1) {
            val percentileIndex = (bestIndex + (percentileBias * memory.size)).toInt().coerceIn(0, memory.size - 1)
            if (percentileIndex != bestIndex) {
                bestThreshold = memory[percentileIndex]
            }
        }

        return bestThreshold
    }

    fun findEntropyThreshold6(percentileBias: Double = 0.0): Double {
        var bestThreshold = memory[0]
        var bestIndex = -1
        var maxBD = Double.NEGATIVE_INFINITY
        val min = memory.first()
        val max = memory.last()

        // Helper to compute the Bhattacharyya distance for two probabilities p and q,
        // where p,q represent fractions (0 < p,q < 1).
        fun bhattacharyyaDistance(p: Double, q: Double): Double {
            // The Bhattacharyya coefficient is:
            //     BC(p, q) = sqrt(p*q) + sqrt( (1-p)*(1-q) )
            // Distance:
            //     BD(p, q) = -ln( BC(p, q) )
            // We’ll guard against p/q being 0 or 1 because ln(0) → -∞ (which is invalid here).
            if (p <= 0.0 || p >= 1.0 || q <= 0.0 || q >= 1.0) {
                return Double.NEGATIVE_INFINITY
            }
            val bc = kotlin.math.sqrt(p * q) + kotlin.math.sqrt((1 - p) * (1 - q))
            return -kotlin.math.log(bc, 2.0) // Using log base 2 to be consistent with other methods
        }

        for (i in 1 until memory.size) {
            // Skip if the next point is identical to the previous
            if (memory[i] == memory[i - 1]) continue

            // Midpoint threshold between adjacent data
            val threshold = (memory[i - 1] + memory[i]) / 2

            // Skip thresholds that are out of bounds or degenerate
            if (threshold <= min || threshold >= max) continue

            val fractionOfValues = i.toDouble() / memory.size
            val fractionOfRange = (threshold - min) / (max - min)
            if (
                fractionOfValues <= 0.0 || fractionOfValues >= 1.0 ||
                fractionOfRange <= 0.0 || fractionOfRange >= 1.0
            ) {
                continue
            }

            // Compute Bhattacharyya distance
            val bd = bhattacharyyaDistance(fractionOfValues, fractionOfRange)
            if (bd > maxBD) {
                maxBD = bd
                bestThreshold = threshold
                bestIndex = i
            }
        }

        // If desired, apply a percentile bias
        if (percentileBias != 0.0 && bestIndex != -1) {
            val percentileIndex = (bestIndex + (percentileBias * memory.size)).toInt()
                .coerceIn(0, memory.size - 1)
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

    fun rms(): Double {
        return memory.average()
    }

    fun computeKLDivergence(other: PercentileTool): Double {
        return computeKLDivergence(memory.toDoubleArray(), other.memory.toDoubleArray())
    }
}