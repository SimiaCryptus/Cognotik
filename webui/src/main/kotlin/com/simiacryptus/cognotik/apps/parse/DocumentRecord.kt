package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.util.JsonUtil
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
    out.writeUTF(text ?: "")
    out.writeUTF(metadata ?: "")
    out.writeUTF(sourcePath)
    out.writeUTF(jsonPath)
    out.writeObject(vector)
  }

  @Throws(IOException::class, ClassNotFoundException::class)
  fun readObject(input: ObjectInputStream): DocumentRecord {
    val text = input.readUTF().let { if (it.isEmpty()) null else it }
    val metadata = input.readUTF().let { if (it.isEmpty()) null else it }
    val sourcePath = input.readUTF()
    val jsonPath = input.readUTF()
    val vector = input.readObject() as DoubleArray?
    return DocumentRecord(
      text,
      metadata,
      sourcePath,
      jsonPath,
      vector
    )
  }

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(DocumentRecord::class.java)

    fun saveAsBinary(
      openAIClient: OpenAIClient,
      pool: ExecutorService,
      progressState: ProgressState? = null,
      vararg inputPaths: String,
    ) = inputPaths.map { inputPath ->
      val futureList = mutableListOf<Future<*>>()
      val infile = File(inputPath)
      val fileData = JsonUtil.fromJson<Map<String, Any>>(infile.readText(), Map::class.java)
      val records = DocumentParsingModel.getRows(inputPath, progressState, futureList, pool, openAIClient, fileData)
      val outputPath = infile.parentFile.resolve(infile.name.split("\\.".toRegex(), 2).first() + ".index.data").absolutePath
      awaitAll(futureList.toTypedArray())
      writeBinary(outputPath, records)
      outputPath
    }

    fun awaitAll(futureList: Array<Future<*>>) {
      val start = System.currentTimeMillis()
      for (future in futureList) {
        try {
          future.get(
            TimeUnit.MINUTES.toMillis(5) - (System.currentTimeMillis() - start),
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
        out.writeInt(records.size)
        records.forEach { it.writeObject(out) }
      }
    }

    fun readBinary(inputPath: String): List<DocumentRecord> {
      val records = mutableListOf<DocumentRecord>()
      ObjectInputStream(FileInputStream(inputPath)).use { input ->
        val size = input.readInt()
        repeat(size) {
          records.add(
            DocumentRecord(
              text = null,
              metadata = null,
              sourcePath = "",
              jsonPath = "",
              vector = DoubleArray(0)
            ).readObject(input)
          )
        }
      }
      return records
    }
  }
}