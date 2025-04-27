package com.simiacryptus.util.index

import com.simiacryptus.util.files.XBytes
import com.simiacryptus.util.files.XElements
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class FileIndexerTest {

    @Test
    fun testDataRead() {
        val tempDataFile = File.createTempFile("testData", ".txt")
        tempDataFile.writeText("This is a test data file.")
        val fileIndexer = FileIndexer(tempDataFile)
        try {
            val buffer = ByteArray(4)
            fileIndexer.data.read(XBytes(0L), buffer)
            val result = String(buffer)
            assertEquals("This", result)
        } finally {

            fileIndexer.close()
            tempDataFile.delete()
        }
    }

    @Test
    fun testIndexGetAndSet() {
        val tempDataFile = File.createTempFile("testData", ".txt")
        tempDataFile.writeText("This is a test data file.")
        val fileIndexer = FileIndexer(tempDataFile)
        try {
            fileIndexer.index.set(XElements(0L), 1)
            val result = fileIndexer.index.get(XElements(0))
            assertEquals(1, result)
        } finally {

            fileIndexer.close()
            tempDataFile.delete()
        }
    }




    private fun smokeSearch(tokenFileFactory: (File) -> TokenFile) {
        val tempDataFile = File.createTempFile("testData", ".txt")
        tempDataFile.writeText("This is a test data file.")
        val data = tokenFileFactory(tempDataFile)
        val fileIndexer =
            FileIndexer(data, File(tempDataFile.parentFile, "${tempDataFile.name}.index"))
        try {
            fileIndexer.buildIndex(XChars(1))
            fileIndexer.apply {
                val strings = (0 until index.length.asLong).toList()
                    .map { XChars(index.get(XElements(it))) }
                    .map { data.charIterator(it) }
                    .map { it.invoke().asSequence().take(data.tokenCount.asInt).joinToString("") }
                strings.mapIndexed { index, s -> println("$index: $s") }
                assertEquals(strings.toList().sorted().joinToString("\n"), strings.joinToString("\n"))
            }

            assertEquals(listOf(0L), fileIndexer.find("This").toList().sortedBy { it.asLong })
            assertEquals(listOf(2L, 5L), fileIndexer.find("is").toList().sortedBy { it.asLong })
            assertEquals(emptyList<Int>(), fileIndexer.find("foo").toList().sortedBy { it.asLong })
            assertEquals(listOf(8L, 16L, 18L), fileIndexer.find("a").toList().sortedBy { it.asLong })
        } finally {

            fileIndexer.close()
            tempDataFile.delete()
        }
    }

    @Test
    fun testDataFile() {
        testDataFile(

            File("C:\\Users\\andre\\Downloads\\pg84.txt")
        ) {

            CharsetTokenFile(it)

        }
    }

    private fun testDataFile(
        dataFile: File,
        tokenFileFactory: (File) -> TokenFile
    ) {
        val indexFile = File(dataFile.parentFile, "${dataFile.name}.index")
        if (indexFile.exists()) indexFile.delete()

        val indexer = FileIndexer(tokenFileFactory(dataFile), indexFile)
        withPerf("buildIndex (${dataFile.length()} bytes)") { indexer.buildIndex(XChars(2)) }
        for (pos in withPerf("find") { indexer.find("This").toList() }.sortedBy { it.asLong }) {

        }
        val characters = indexer.tokenSet
        val dictionaryMaxSize = Integer.MAX_VALUE.toInt() - characters.size
        val codec = withPerf("findCompressionPrefixes") { indexer.findCompressionPrefixes(200, dictionaryMaxSize) }

        val codecMap = (codec.map { it.first } + characters).sorted()
        val (compressed, dictionaryFile) = withPerf("data.compress") { indexer.data.writeCompressed(codecMap) }
        val expandFile = File(dataFile.parentFile, "${dataFile.name}.expand")
        withPerf("data.expand") { indexer.data.expand(codecMap, compressed, expandFile) }

        val compressedIndexer = FileIndexer(CompressedTokenFile(compressed, dictionaryFile))
        withPerf("buildIndex (${compressedIndexer.data.fileLength} bytes)") { compressedIndexer.buildIndex(XChars(3)) }
        for (pos in withPerf("find") { compressedIndexer.find("This").toList() }.sorted()) {

        }
    }

    private fun <T> withPerf(name: String, fn: () -> T): T {
        val startTime = System.nanoTime()
        try {
            return fn()
        } finally {
            val endTime = System.nanoTime()
            println("$name took ${(endTime - startTime) / 1e9} seconds")
        }
    }
}
