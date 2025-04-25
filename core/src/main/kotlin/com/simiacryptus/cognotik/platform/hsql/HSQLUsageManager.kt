package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModel
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp

class HSQLUsageManager(private val dbFile: File) : UsageInterface {

  private val connection: Connection by lazy {
    log.info("Initializing HSQLUsageManager with database file: ${dbFile.absolutePath}")
    Class.forName("org.hsqldb.jdbc.JDBCDriver")
    val connection = DriverManager.getConnection("jdbc:hsqldb:file:${dbFile.absolutePath}/usage;shutdown=true", "SA", "")
    log.debug("Database connection established: $connection")
    createSchema(connection)
    connection
  }

  private fun createSchema(connection: Connection) {
    log.info("Creating database schema if not exists")
    connection.createStatement().executeUpdate(
      """
         CREATE TABLE IF NOT EXISTS usage (
             session_id VARCHAR(255),
             api_key VARCHAR(255),
             model VARCHAR(255),
            prompt_tokens BIGINT,
            completion_tokens BIGINT,
             cost DOUBLE,
            datetime TIMESTAMP,
            PRIMARY KEY (session_id, api_key, model, prompt_tokens, completion_tokens, cost, datetime)
             )
          """
    )
  }


  override fun incrementUsage(session: Session, apiKey: String?, model: OpenAIModel, tokens: ApiModel.Usage) {
    try {
      log.debug("Incrementing usage for session: ${session.sessionId}, apiKey: $apiKey, model: ${model.modelName}")
      val usageKey = UsageInterface.UsageKey(session, apiKey, model)
      val usageValues = UsageInterface.UsageValues() //getUsageValues(usageKey)
      usageValues.addAndGet(tokens)
      saveUsageValues(usageKey, usageValues)
      log.debug("Usage incremented for session: ${session.sessionId}, apiKey: $apiKey, model: ${model.modelName}")
    } catch (e: Exception) {
      log.error("Error incrementing usage", e)
    }
  }

  override fun getUserUsageSummary(apiKey: String): Map<OpenAIModel, ApiModel.Usage> {
    log.debug("Executing SQL query to get user usage summary for apiKey: $apiKey")
    val statement = connection.prepareStatement(
      """
            SELECT model, SUM(prompt_tokens), SUM(completion_tokens), SUM(cost)
            FROM usage
            WHERE api_key = ?
            GROUP BY model
            """
    )
    statement.setString(1, apiKey)
    val resultSet = statement.executeQuery()
    return generateUsageSummary(resultSet)
  }

  override fun getSessionUsageSummary(session: Session): Map<OpenAIModel, ApiModel.Usage> {
    log.info("Getting session usage summary for session: ${session.sessionId}")
    val statement = connection.prepareStatement(
      """
            SELECT model, SUM(prompt_tokens), SUM(completion_tokens), SUM(cost)
            FROM usage
            WHERE session_id = ?
            GROUP BY model
            """
    )
    statement.setString(1, session.sessionId)
    val resultSet = statement.executeQuery()
    return generateUsageSummary(resultSet)
  }

  override fun clear() {
    log.debug("Executing SQL statement to clear all usage data")
    connection.createStatement().executeUpdate("DELETE FROM usage")
  }

  private fun saveUsageValues(usageKey: UsageInterface.UsageKey, usageValues: UsageInterface.UsageValues) {
    log.debug("Saving usage values for session: ${usageKey.session.sessionId}, apiKey: ${usageKey.apiKey}, model: ${usageKey.model.modelName}")
    val statement = connection.prepareStatement(
      """
         INSERT INTO usage (session_id, api_key, model, prompt_tokens, completion_tokens, cost, datetime)
         VALUES (?, ?, ?, ?, ?, ?, ?)
         """
    )
    statement.setString(1, usageKey.session.sessionId)
    statement.setString(2, usageKey.apiKey ?: "")
    statement.setString(3, usageKey.model.modelName)
    statement.setLong(4, usageValues.inputTokens.get())
    statement.setLong(5, usageValues.outputTokens.get())
    statement.setDouble(6, usageValues.cost.get())
    statement.setTimestamp(7, Timestamp(System.currentTimeMillis()))
    log.debug("Executing statement: $statement")
    log.debug("With parameters: ${usageKey.session.sessionId}, ${usageKey.apiKey}, ${usageKey.model.modelName}, ${usageValues.inputTokens.get()}, ${usageValues.outputTokens.get()}, ${usageValues.cost.get()}")
    statement.executeUpdate()
  }

  private fun generateUsageSummary(resultSet: ResultSet): Map<OpenAIModel, ApiModel.Usage> {
    log.debug("Generating usage summary from result set")
    val summary = mutableMapOf<OpenAIModel, ApiModel.Usage>()
    while (resultSet.next()) {
      val string = resultSet.getString(1)
      val model = openAIModel(string) ?: continue
      val usage = ApiModel.Usage(
        prompt_tokens = resultSet.getLong(2),
        completion_tokens = resultSet.getLong(3),
        cost = resultSet.getDouble(4)
      )
      summary[model] = usage
    }
    return summary
  }

  private fun openAIModel(string: String): OpenAIModel? {
    log.debug("Retrieving OpenAI model for string: $string")
    val model = ChatModel.values().filter {
      it.key == string || it.value.modelName == string || it.value.name == string
    }.toList().firstOrNull()?.second ?: return null
    log.debug("OpenAI model retrieved: $model")
    return model
  }

  companion object {
    private val log = LoggerFactory.getLogger(HSQLUsageManager::class.java)
  }
}