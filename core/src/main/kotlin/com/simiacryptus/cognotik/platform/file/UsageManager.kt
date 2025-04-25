package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.UsageInterface.*
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.jopenai.models.*
import com.simiacryptus.util.JsonUtil
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

open class UsageManager(val root: File) : UsageInterface {

  private val scheduler = Executors.newSingleThreadScheduledExecutor()
  private val txLogFile = File(root, "log.csv")

  @Volatile
  private var txLogFileWriter: FileWriter?
  private val usagePerSession = ConcurrentHashMap<Session, UsageCounters>()
  private val sessionsByUser = ConcurrentHashMap<String, HashSet<Session>>()
  private val usersBySession = ConcurrentHashMap<Session, HashSet<String>>()

  init {
    txLogFile.parentFile.mkdirs()
    loadFromLog(txLogFile)
    txLogFileWriter = FileWriter(txLogFile, true)
    scheduler.scheduleAtFixedRate({ saveCounters() }, 1, 1, TimeUnit.HOURS)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  private fun loadFromLog(file: File) {
    if (file.exists()) {
      try {
        file.readLines().forEach { line ->
          val (sessionId, user, model, value, direction) = line.split(",")
          try {
            val modelEnum = listOf(
              ChatModel.values(),
              CompletionModels.values(),
              EditModels.values(),
              EmbeddingModels.values()
            ).flatMap { it.values }.find { model == it.modelName }
              ?: throw RuntimeException("Unknown model $model")
            when (direction) {
              "input" -> incrementUsage(
                Session(sessionId),
                User(email = user),
                modelEnum,
                ApiModel.Usage(prompt_tokens = value.toLong())
              )

              "output" -> incrementUsage(
                Session(sessionId),
                User(email = user),
                modelEnum,
                ApiModel.Usage(completion_tokens = value.toLong())
              )

              "cost" -> incrementUsage(
                session = Session(sessionId = sessionId),
                user = User(email = user),
                model = modelEnum,
                tokens = ApiModel.Usage(cost = value.toDouble())
              )

              else -> throw RuntimeException("Unknown direction $direction")
            }
          } catch (e: Exception) {
            //log.debug("Error loading log line: ${e.message}")
          }
        }
      } catch (e: Exception) {
        log.warn("Error loading log file", e)
      }
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  private fun writeCompactLog(file: File) {
    FileWriter(file).use { writer ->
      usagePerSession.forEach { (sessionId, usage) ->
        val apiKey = usersBySession[sessionId]?.firstOrNull()
        usage.tokensPerModel.forEach { (model, counter) ->
          writer.write("$sessionId,${apiKey},${model.model.modelName},${counter.inputTokens.get()},input\n")
          writer.write("$sessionId,${apiKey},${model.model.modelName},${counter.outputTokens.get()},output\n")
          writer.write("$sessionId,${apiKey},${model.model.modelName},${counter.cost.get()},cost\n")
        }
      }
      writer.flush()
    }
  }

  private fun saveCounters() {
    txLogFileWriter = FileWriter(txLogFile, true)
    val timedFile = File(txLogFile.absolutePath + "." + System.currentTimeMillis())
    writeCompactLog(timedFile)
    val swapFile = File(txLogFile.absolutePath + ".old")
    synchronized(txLogFile) {
      try {
        txLogFileWriter?.close()
      } catch (e: Exception) {
        log.warn("Error closing log file", e)
      }
      try {
        txLogFile.renameTo(swapFile)
      } catch (e: Exception) {
        log.warn("Error renaming log file", e)
      }
      try {
        timedFile.renameTo(txLogFile)
      } catch (e: Exception) {
        log.warn("Error renaming log file", e)
      }
      try {
        swapFile.renameTo(timedFile)
      } catch (e: Exception) {
        log.warn("Error renaming log file", e)
      }
      txLogFileWriter = FileWriter(txLogFile, true)
    }
    val text = JsonUtil.toJson(usagePerSession)
    File(root, "counters.json").writeText(text)
    val toClean = txLogFile.parentFile.listFiles()
      ?.filter { it.name.startsWith(txLogFile.name) && it.name != txLogFile.absolutePath }
      ?.sortedBy { it.lastModified() } // oldest first
      ?.dropLast(2) // keep 2 newest
      ?.drop(2) // keep 2 oldest
    toClean?.forEach { it.delete() }
  }

  override fun incrementUsage(
    session: Session,
    apiKey: String?,
    model: OpenAIModel,
    tokens: ApiModel.Usage
  ) {
    usagePerSession.computeIfAbsent(session) { UsageCounters() }
      .tokensPerModel.computeIfAbsent(UsageKey(session, apiKey, model)) { UsageValues() }
      .addAndGet(tokens)
    if (apiKey != null) {
      sessionsByUser.computeIfAbsent(apiKey) { HashSet() }.add(session)
    }
    try {
      val txLogFileWriter = txLogFileWriter
      if (null != txLogFileWriter) {
        synchronized(txLogFile) {
          txLogFileWriter.write("$session,${apiKey},${model.modelName},${tokens.prompt_tokens},input\n")
          txLogFileWriter.write("$session,${apiKey},${model.modelName},${tokens.completion_tokens},output\n")
          txLogFileWriter.write("$session,${apiKey},${model.modelName},${tokens.cost},cost\n")
          txLogFileWriter.flush()
        }
      }
    } catch (e: Exception) {
      log.warn("Error incrementing usage", e)
    }
  }

  override fun getUserUsageSummary(apiKey: String): Map<OpenAIModel, ApiModel.Usage> {
    return sessionsByUser[apiKey]?.flatMap { sessionId ->
      val usage = usagePerSession[sessionId]
      usage?.tokensPerModel?.entries?.map { (model, counter) ->
        model.model to counter.toUsage()
      } ?: emptyList()
    }?.groupBy { it.first }?.mapValues {
      it.value.map { it.second }.reduce { a, b ->
        ApiModel.Usage(
          prompt_tokens = a.prompt_tokens + b.prompt_tokens,
          completion_tokens = a.completion_tokens + b.completion_tokens,
          cost = (a.cost ?: 0.0) + (b.cost ?: 0.0)
        )
      }
    } ?: emptyMap()
  }

  override fun getSessionUsageSummary(session: Session): Map<OpenAIModel, ApiModel.Usage> =
    usagePerSession[session]?.tokensPerModel?.entries?.map { (model, counter) ->
      model.model to counter.toUsage()
    }?.groupBy { it.first }?.mapValues {
      it.value.map { it.second }.reduce { a, b ->
        ApiModel.Usage(
          prompt_tokens = a.prompt_tokens + b.prompt_tokens,
          completion_tokens = a.completion_tokens + b.completion_tokens,
          cost = (a.cost ?: 0.0) + (b.cost ?: 0.0)
        )
      }
    } ?: emptyMap()

  override fun clear() {
    usagePerSession.clear()
    sessionsByUser.clear()
    usersBySession.clear()
    saveCounters()
  }

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(UsageManager::class.java)
  }
}