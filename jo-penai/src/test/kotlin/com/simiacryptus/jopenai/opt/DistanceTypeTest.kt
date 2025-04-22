package com.simiacryptus.jopenai.opt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random


class DistanceTypeTest {

    @Test
    fun testCosineSimilarVectors() {
        val vector1 = Array(100) { Random.nextDouble() }
        val vector2 = vector1.map { it + Random.nextDouble(-0.01, 0.01) }.toTypedArray()
        val distance = DistanceType.Cosine.distance(vector1.toDoubleArray(), vector2.toDoubleArray())
        assertEquals(0.0, distance, 0.1)
    }

    @Test
    fun testCosineDissimilarVectors() {
        val vector1 = Array(100) { Random.nextDouble() }
        val vector2 = Array(100) { Random.nextDouble() }
        val distance = DistanceType.Cosine.distance(vector1.toDoubleArray(), vector2.toDoubleArray())
        assertTrue(distance > 0.2, "Distance = $distance")
    }

    @Test
    fun testEuclideanSimilarVectors() {
        val vector1 = Array(100) { Random.nextDouble() }
        val vector2 = vector1.map { it + Random.nextDouble(-0.01, 0.01) }.toTypedArray()
        val distance = DistanceType.Euclidean.distance(vector1.toDoubleArray(), vector2.toDoubleArray())
        assertEquals(0.0, distance, 0.1)
    }

    @Test
    fun testEuclideanDissimilarVectors() {
        val vector1 = Array(100) { Random.nextDouble() }
        val vector2 = Array(100) { Random.nextDouble() }
        val distance = DistanceType.Euclidean.distance(vector1.toDoubleArray(), vector2.toDoubleArray())
        assertTrue(distance > 0.2, "Distance = $distance")
    }

    @Test
    fun testManhattanSimilarVectors() {
        val vector1 = Array(100) { Random.nextDouble() }
        val vector2 = vector1.map { it + Random.nextDouble(-0.001, 0.001) }.toTypedArray()
        val distance = DistanceType.Manhattan.distance(vector1.toDoubleArray(), vector2.toDoubleArray())
        assertEquals(0.0, distance, 0.1)
    }

    @Test
    fun testManhattanDissimilarVectors() {
        val vector1 = Array(100) { Random.nextDouble() }
        val vector2 = Array(100) { Random.nextDouble() }
        val distance = DistanceType.Manhattan.distance(vector1.toDoubleArray(), vector2.toDoubleArray())
        assertTrue(distance > 0.2, "Distance = $distance")
    }
}