package com.simiacryptus.jopenai.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PercentileToolTest {
    private lateinit var percentileTool: PercentileTool

    @BeforeEach
    fun setUp() {
        percentileTool = PercentileTool(memorySize = 10)
    }

    @Test
    fun testAddAndSortedOrder() {
        val values = listOf(5.0, 3.0, 9.0, 1.0, 4.0)
        values.forEach { percentileTool.add(it) }
        val expected = listOf(1.0, 3.0, 4.0, 5.0, 9.0)
        assertEquals(expected, percentileTool.memory)
    }

    @Test
    fun testGetValueOfPercentile() {
        val values = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        values.forEach { percentileTool.add(it) }

        assertEquals(10.0, percentileTool.getValueOfPercentile(0.0), 0.0001)
        assertEquals(30.0, percentileTool.getValueOfPercentile(0.5), 0.0001)
        assertEquals(50.0, percentileTool.getValueOfPercentile(1.0), 0.0001)

        assertEquals(20.0, percentileTool.getValueOfPercentile(0.25), 0.0001)
        assertEquals(40.0, percentileTool.getValueOfPercentile(0.75), 0.0001)
    }

    @Test
    fun testGetValueOfPercentileWithNonIntegerIndex() {
        val values = listOf(1.0, 2.0, 3.0, 4.0)
        values.forEach { percentileTool.add(it) }

        // 25th percentile should be index 1 (value 2.0)
        assertEquals(2.0, percentileTool.getValueOfPercentile(0.25), 0.0001)
        // 75th percentile should be index 3 (value 4.0)
        assertEquals(4.0, percentileTool.getValueOfPercentile(0.75), 0.0001)
    }

    @Test
    fun testGetValueOfPercentileEmptyMemory() {
        val emptyTool = PercentileTool(memorySize = 5)
        assertEquals(0.0, emptyTool.getValueOfPercentile(0.5), 0.0001)
    }

    @Test
    fun testAddDuplicateValues() {
        val values = listOf(5.0, 5.0, 5.0, 5.0)
        values.forEach { percentileTool.add(it) }
        assertEquals(listOf(5.0, 5.0, 5.0, 5.0), percentileTool.memory)
        assertEquals(5.0, percentileTool.getValueOfPercentile(0.5), 0.0001)
    }

    @Test
    fun testAddNegativeValues() {
        val values = listOf(-10.0, -20.0, 0.0, 10.0)
        values.forEach { percentileTool.add(it) }
        val expected = listOf(-20.0, -10.0, 0.0, 10.0)
        assertEquals(expected, percentileTool.memory)
        assertEquals(-10.0, percentileTool.getValueOfPercentile(0.25), 0.0001)
        assertEquals(10.0, percentileTool.getValueOfPercentile(0.75), 0.0001)
    }

    @Test
    fun testGetValueOfPercentileBoundaryValues() {
        val values = listOf(100.0)
        values.forEach { percentileTool.add(it) }
        assertEquals(100.0, percentileTool.getValueOfPercentile(0.0), 0.0001)
        assertEquals(100.0, percentileTool.getValueOfPercentile(0.5), 0.0001)
        assertEquals(100.0, percentileTool.getValueOfPercentile(1.0), 0.0001)
    }
}