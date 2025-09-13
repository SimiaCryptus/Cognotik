package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class DocumentRecord(
    val text: String?,
    val metadata: String?,
    val sourcePath: String,
    val jsonPath: String,
    var vector: DoubleArray?,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val vectorDimension: Int = 0,
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentRecord
        if (text != other.text) return false
        if (metadata != other.metadata) return false
        if (sourcePath != other.sourcePath) return false
        if (jsonPath != other.jsonPath) return false
        if (vector != null) {
            if (other.vector == null) return false
            if (!vector.contentEquals(other.vector)) return false
        } else if (other.vector != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = text?.hashCode() ?: 0
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + sourcePath.hashCode()
        result = 31 * result + jsonPath.hashCode()
        result = 31 * result + (vector?.contentHashCode() ?: 0)
        return result
    }

    @Throws(IOException::class)
    fun writeObject(out: ObjectOutputStream) {
        if (vector == null) {
            throw IllegalStateException("Vector is null for record from $sourcePath at $jsonPath")
        }
        val normalize = normalize(text ?: "")
        out.writeUTF(normalize)
        out.writeUTF(metadata ?: "")
        out.writeUTF(sourcePath)
        out.writeUTF(jsonPath)
        out.writeObject(vector)
        out.writeInt(chunkIndex)
        out.writeInt(totalChunks)
    }

    private fun normalize(string: String): String {
        if (string.length < 65535) return string
        var string = string.trim()
        if (string.length < 65535) return string
        string = string.replace(Regex("\\s{2,}"), " ")
        if (string.length < 65535) return string
        string = string.take(65532)
        return string
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    fun readObject(input: ObjectInputStream): DocumentRecord {
        val text = input.readUTF().let { if (it.isEmpty()) null else it }
        val metadata = input.readUTF().let { if (it.isEmpty()) null else it }
        val sourcePath = input.readUTF()
        val jsonPath = input.readUTF()
        val vector = input.readObject() as DoubleArray?
        val chunkIndex = try {
            input.readInt()
        } catch (e: Exception) {
            0
        }
        val totalChunks = try {
            input.readInt()
        } catch (e: Exception) {
            1
        }
        return DocumentRecord(
            text,
            metadata,
            sourcePath,
            jsonPath,
            vector,
            chunkIndex,
            totalChunks
        )
    }

    companion object {
        val log = LoggerFactory.getLogger(DocumentRecord::class.java)
        private const val RECORD_VERSION = 2

        fun readBinaryStream(inputPath: String, processor: (DocumentRecord) -> Unit) {
            ObjectInputStream(FileInputStream(inputPath)).use { input ->
                val version = try {
                    input.readInt()
                } catch (e: Exception) {
                    1
                }
                val size = if (version == RECORD_VERSION) input.readInt() else version
                var processed = 0
                while (processed < size) {
                    try {
                        val record = DocumentRecord(
                            text = null,
                            metadata = null,
                            sourcePath = "",
                            jsonPath = "",
                            vector = DoubleArray(0)
                        ).readObject(input)
                        processor(record)
                        processed++
                    } catch (e: Exception) {
                        log.warn("Failed to read record $processed of $size from $inputPath", e)
                        processed++
                        // Continue reading remaining records
                    }
                }
            }
        }

        fun indexJsonFile(
            pool: ExecutorService,
            progressState: ProgressState,
            model: EmbeddingModel,
            vararg inputPaths: String,
        ) = inputPaths.map { inputPath ->
            try {
                val futureList = mutableListOf<Future<*>>()
                val infile = File(inputPath)
                if (!infile.exists()) {
                    log.error("Input file does not exist: $inputPath")
                    return@map null
                }
                val fileData = try {
                    JsonUtil.fromJson<Map<String, Any>>(infile.readText(), Map::class.java)
                } catch (e: Exception) {
                    log.error("Failed to parse JSON file: $inputPath", e)
                    return@map null
                }
                val records = model.getRows(
                    inputPath = inputPath,
                    progressState = progressState,
                    futureList = futureList,
                    pool = pool,
                    fileData = fileData
                )
                val outputPath = infile.parentFile.resolve(
                    infile.name.split("\\.".toRegex(), 2).first() + ".index.data"
                ).absolutePath
                awaitAll(futureList.toTypedArray(), TimeUnit.MINUTES.toMillis(5))
                writeBinary(outputPath, records)
                outputPath
            } catch (e: Exception) {
                log.error("Failed to index file: $inputPath", e)
                null
            }
        }

        fun indexTextFiles(
            pool: ExecutorService,
            parsingModel: ParsingModel<*>,
            model: EmbeddingModel,
            progressState: ProgressState,
            vararg inputPaths: String,
        ) = inputPaths.map { inputPath ->
            val infile = File(inputPath)
            val outputPath =
                infile.parentFile.resolve(infile.name.split("\\.".toRegex(), 2).first() + ".index.data").absolutePath
            // Parse the text content using the parsing model
            val parser = parsingModel.getFastParser()
            val textContent = infile.readText()
            val parsedDocument = parser(textContent)
            val futureList = mutableListOf<Future<*>>()
            val rows = model.getRows(
                inputPath = inputPath,
                progressState = progressState,
                futureList = futureList,
                pool = pool,
                fileData = mapOf("content_list" to parsedDocument.content_list) as Map<String, Any>?
            )
            awaitAll(futureList.toTypedArray(), TimeUnit.MINUTES.toMillis(30))
            writeBinary(outputPath, rows)
            outputPath
        }

        fun awaitAll(futureList: Array<Future<*>>, timeoutMs: Long) {
            val start = System.currentTimeMillis()
            for (future in futureList) {
                try {
                    future.get(
                        timeoutMs - (System.currentTimeMillis() - start),
                        TimeUnit.MILLISECONDS
                    )
                } catch (e: Exception) {
                    log.error("Error processing entity", e)
                }
            }
        }

        private fun writeBinary(outputPath: String, records: List<DocumentRecord>) {
            log.info("Writing ${records.size} records to $outputPath")
            ObjectOutputStream(FileOutputStream(outputPath)).use { out ->
                out.writeInt(RECORD_VERSION)
                out.writeInt(records.size)
                records.forEach { it.writeObject(out) }
            }
            // Write metadata file
            val metadataPath = outputPath.replace(".index.data", ".index.meta")
            File(metadataPath).writeText(
                JsonUtil.toJson(
                    mapOf(
                        "version" to RECORD_VERSION,
                        "recordCount" to records.size,
                        "timestamp" to System.currentTimeMillis(),
                        "vectorDimension" to (records.firstOrNull()?.vector?.size ?: 0)
                    )
                )
            )
        }

        fun readBinary(inputPath: String): List<DocumentRecord> {
            val records = mutableListOf<DocumentRecord>()
            ObjectInputStream(FileInputStream(inputPath)).use { input ->
                val version = try {
                    input.readInt()
                } catch (e: Exception) {
                    1
                }
                val size = if (version == RECORD_VERSION) input.readInt() else version
                var processed = 0
                while (processed < size) {
                    try {
                        val record = DocumentRecord(
                            text = null,
                            metadata = null,
                            sourcePath = "",
                            jsonPath = "",
                            vector = DoubleArray(0)
                        ).readObject(input)
                        records.add(record)
                        processed++
                    } catch (e: Exception) {
                        log.warn("Failed to read record $processed of $size from $inputPath", e)
                        processed++
                        // Continue reading remaining records
                    }
                }
            }
            return records.distinct()
        }
    }
}