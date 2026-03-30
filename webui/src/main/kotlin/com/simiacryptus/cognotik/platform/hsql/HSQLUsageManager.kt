package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp

class HSQLUsageManager(root: File? = null) : UsageInterface {

  init {
    require(root?.exists() != false || root.mkdirs()) { "Failed to create root directory: $root" }
    log.info("Initializing HSQLUsageManager with root directory: {}", root)
  }

  private val connection: Connection by lazy {
    Class.forName("org.hsqldb.jdbc.JDBCDriver")
    val url = if (null == root) {
      "jdbc:hsqldb:mem:usage"
    } else {
      "jdbc:hsqldb:file:${root.absolutePath};shutdown=true;hsqldb.lock_file=false"
    }
    val connection = DriverManager.getConnection(url, "SA", "")
    log.debug("Database connection established: {}", connection)
    createSchema(connection)
    connection
  }

  private fun createSchema(connection: Connection) {
    log.info("Creating database schema if not exists")
    connection.createStatement().executeUpdate(
      """
         CREATE TABLE IF NOT EXISTS usage (
             session_id VARCHAR(255),
             user_id VARCHAR(255),
             model VARCHAR(255),
            prompt_tokens BIGINT,
            completion_tokens BIGINT,
             cost DOUBLE,
            datetime TIMESTAMP,
            PRIMARY KEY (session_id, user_id, model, prompt_tokens, completion_tokens, cost, datetime)
             )
          """
    )
     connection.createStatement().executeUpdate(
       """
          CREATE TABLE IF NOT EXISTS session_parents (
              child_session_id VARCHAR(255),
              parent_session_id VARCHAR(255),
              PRIMARY KEY (child_session_id, parent_session_id)
              )
           """
     )
  }

  override fun incrementUsage(session: Session, user: User, model: AIModel, tokens: ModelSchema.Usage) {
    try {
      log.debug("Incrementing usage for session: {}, user: {}, model: {}", session, user.email, model.modelId)
      val usageKey = UsageInterface.UsageKey(session, user, model)
      val usageValues = UsageInterface.UsageValues()

      usageValues.addAndGet(tokens)
      saveUsageValues(usageKey, usageValues)
      log.debug("Usage incremented for session: {}, user: {}, model: {}", session, user.email, model.modelId)
    } catch (e: Exception) {
      log.error("Error incrementing usage", e)
    }
  }

  override fun getUserUsageSummary(user: User): Map<String, ModelSchema.Usage> {
    log.info("Executing SQL query to get user usage summary for user: ${user.email}")
    val statement = connection.prepareStatement(
      """
            SELECT model, SUM(prompt_tokens), SUM(completion_tokens), SUM(cost)
            FROM usage
            WHERE user_id = ?
            GROUP BY model
            """
    )
    statement.setString(1, user.email)
    val resultSet = statement.executeQuery()
    return generateUsageSummary(resultSet)
  }

  override fun getSessionUsageSummary(session: Session): Map<String, ModelSchema.Usage> {
    log.info("Getting session usage summary for session: ${session}")
     val allSessionIds = collectSessionIds(session.sessionId)
     log.debug("Collected session IDs for summary (including children): {}", allSessionIds)
     val placeholders = allSessionIds.joinToString(",") { "?" }
    val statement = connection.prepareStatement(
      """
            SELECT model, SUM(prompt_tokens), SUM(completion_tokens), SUM(cost)
            FROM usage
             WHERE session_id IN ($placeholders)
            GROUP BY model
            """
    )
     allSessionIds.forEachIndexed { index, sessionId ->
       statement.setString(index + 1, sessionId)
     }
    val resultSet = statement.executeQuery()
    return generateUsageSummary(resultSet)
  }

  override fun clear() {
    log.debug("Executing SQL statement to clear all usage data")
    connection.createStatement().executeUpdate("DELETE FROM usage")
     connection.createStatement().executeUpdate("DELETE FROM session_parents")
  }
   override fun setParentSession(child: Session, parent: Session) {
     log.info("Setting parent session: child={}, parent={}", child.sessionId, parent.sessionId)
     val statement = connection.prepareStatement(
       """
          MERGE INTO session_parents
          USING (VALUES(CAST(? AS VARCHAR(255)), CAST(? AS VARCHAR(255)))) AS vals(c, p)
          ON session_parents.child_session_id = vals.c AND session_parents.parent_session_id = vals.p
          WHEN NOT MATCHED THEN INSERT (child_session_id, parent_session_id) VALUES (vals.c, vals.p)
          """
     )
     statement.setString(1, child.sessionId)
     statement.setString(2, parent.sessionId)
     statement.executeUpdate()
   }
   /**
    * Collects all descendant session IDs for a given session (including itself),
    * by traversing the parent->child relationship in reverse (i.e. finding all children).
    */
   private fun collectSessionIds(sessionId: String): Set<String> {
     val visited = mutableSetOf<String>()
     val queue = ArrayDeque<String>()
     queue.add(sessionId)
     while (queue.isNotEmpty()) {
       val current = queue.removeFirst()
       if (visited.add(current)) {
         val childStatement = connection.prepareStatement(
           "SELECT child_session_id FROM session_parents WHERE parent_session_id = ?"
         )
         childStatement.setString(1, current)
         val rs = childStatement.executeQuery()
         while (rs.next()) {
           val childId = rs.getString(1)
           if (childId !in visited) {
             queue.add(childId)
           }
         }
       }
     }
     return visited
   }


  private fun saveUsageValues(usageKey: UsageInterface.UsageKey, usageValues: UsageInterface.UsageValues) {
    log.debug(
      "Saving usage values for session: {}, user: {}, model: {}",
      usageKey.session,
      usageKey.user?.email,
      usageKey.model.modelId
    )
    val statement = connection.prepareStatement(
      """
         INSERT INTO usage (session_id, user_id, model, prompt_tokens, completion_tokens, cost, datetime)
         VALUES (?, ?, ?, ?, ?, ?, ?)
         """
    )
    statement.setString(1, usageKey.session.sessionId)
    statement.setString(2, usageKey.user?.email ?: "")
    statement.setString(3, usageKey.model.modelId)
    statement.setLong(4, usageValues.inputTokens.get())
    statement.setLong(5, usageValues.outputTokens.get())
    statement.setDouble(6, usageValues.cost.get())
    statement.setTimestamp(7, Timestamp(System.currentTimeMillis()))
    log.debug("Executing statement: {}", statement)
    log.debug(
      "With parameters: {}, {}, {}, {}, {}, {}",
      usageKey.session,
      usageKey.user?.email,
      usageKey.model.modelId,
      usageValues.inputTokens.get(),
      usageValues.outputTokens.get(),
      usageValues.cost.get()
    )
    statement.executeUpdate()
  }

  private fun generateUsageSummary(resultSet: ResultSet): Map<String, ModelSchema.Usage> {
    log.info("Generating usage summary from result set")
    val summary = mutableMapOf<String, ModelSchema.Usage>()
    while (resultSet.next()) {
      val string = resultSet.getString(1)
      val usage = ModelSchema.Usage(
        prompt_tokens = resultSet.getLong(2),
        completion_tokens = resultSet.getLong(3),
        cost = resultSet.getDouble(4)
      )
      summary[string] = usage
    }
    return summary
  }

  companion object {
    private val log = LoggerFactory.getLogger(HSQLUsageManager::class.java)
  }
}