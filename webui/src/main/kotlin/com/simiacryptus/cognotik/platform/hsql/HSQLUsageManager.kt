package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.sql.Date as SqlDate

class HSQLUsageManager(root: File? = null) : UsageInterface {

    init {
        HSQLUtils.ensureRoot(root)
        log.info("Initializing HSQLUsageManager with root directory: {}", root)
    }

    private val root: File? = root
    private val connection: Connection get() = getConn(root)

    override fun incrementUsage(session: Session, user: User, model: AIModel, tokens: ModelSchema.Usage) {
        try {
            log.debug("Incrementing usage for session: {}, user: {}, model: {}", session, user.email, model.modelId)
            val usageKey = UsageInterface.UsageKey(session, user, model)
            val usageValues = UsageInterface.UsageValues()

            usageValues.addAndGet(tokens)
            val now = Instant.now()
            val day = LocalDate.ofInstant(now, ZoneOffset.UTC)
            val conn = connection
            val prevAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                saveUsageValues(conn, usageKey, usageValues, Timestamp.from(now))
                upsertDailyUsage(conn, usageKey, usageValues, day)
                val cost = usageValues.cost.get()
                if (cost != 0.0 && user.email.isNotEmpty()) {
                    applyBudgetDelta(conn, user.email, -cost)
                }
                conn.commit()
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (_: Exception) {
                }
                throw e
            } finally {
                conn.autoCommit = prevAutoCommit
            }
            log.debug("Usage incremented for session: {}, user: {}, model: {}", session, user.email, model.modelId)
        } catch (e: Exception) {
            log.error("Error incrementing usage", e)
        }
    }

    override fun getUserUsageSummary(user: User, from: LocalDate, to: LocalDate): Map<String, ModelSchema.Usage> {
        require(!to.isBefore(from)) { "'to' must be on or after 'from'" }
        log.info("Executing SQL query to get user usage summary for user: {} from {} to {}", user.email, from, to)
        val statement = connection.prepareStatement(
            """
            SELECT model, SUM(prompt_tokens), SUM(completion_tokens), SUM(cost)
            FROM usage_daily
            WHERE user_id = ? AND day >= ? AND day < ?
            GROUP BY model
            """
        )
        statement.setString(1, user.email)
        statement.setDate(2, SqlDate.valueOf(from))
        statement.setDate(3, SqlDate.valueOf(to))
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
        connection.createStatement().executeUpdate("DELETE FROM usage_daily")
        connection.createStatement().executeUpdate("DELETE FROM session_parents")
        connection.createStatement().executeUpdate("DELETE FROM user_credits")
        connection.createStatement().executeUpdate("DELETE FROM user_budget")
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

    override fun getAvailableBudget(user: User): Double {
        if (user.email.isEmpty()) return 0.0
        val statement = connection.prepareStatement(
            "SELECT available FROM user_budget WHERE user_id = ?"
        )
        statement.setString(1, user.email)
        val rs = statement.executeQuery()
        return if (rs.next()) rs.getDouble(1) else 0.0
    }

    override fun creditUser(
        user: User,
        amount: Double,
        comment: String?,
        metadata: Map<String, String>?
    ): Double {
        require(user.email.isNotEmpty()) { "User email is required for crediting" }
        log.info("Crediting user {} amount={} comment={}", user.email, amount, comment)
        val conn = connection
        val prevAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            val ledger = conn.prepareStatement(
                """
                INSERT INTO user_credits (user_id, amount, comment, metadata, datetime)
                VALUES (?, ?, ?, ?, ?)
                """
            )
            ledger.setString(1, user.email)
            ledger.setDouble(2, amount)
            ledger.setString(3, comment ?: "")
            ledger.setString(4, encodeMetadata(metadata))
            ledger.setTimestamp(5, Timestamp.from(Instant.now()))
            ledger.executeUpdate()
            applyBudgetDelta(conn, user.email, amount)
            conn.commit()
        } catch (e: Exception) {
            try {
                conn.rollback()
            } catch (_: Exception) {
            }
            throw e
        } finally {
            conn.autoCommit = prevAutoCommit
        }
        return getAvailableBudget(user)
    }

    override fun getUserDailyUsage(
        user: User,
        from: LocalDate,
        to: LocalDate
    ): List<UsageInterface.DailyUsage> {
        require(!to.isBefore(from)) { "'to' must be on or after 'from'" }
        val statement = connection.prepareStatement(
            """
            SELECT day, model, prompt_tokens, completion_tokens, cost
            FROM usage_daily
            WHERE user_id = ? AND day >= ? AND day < ?
            ORDER BY day ASC, model ASC
            """
        )
        statement.setString(1, user.email)
        statement.setDate(2, SqlDate.valueOf(from))
        statement.setDate(3, SqlDate.valueOf(to))
        val rs = statement.executeQuery()
        val out = mutableListOf<UsageInterface.DailyUsage>()
        while (rs.next()) {
            val day = rs.getDate(1).toLocalDate()
            val model = rs.getString(2)
            val usage = ModelSchema.Usage(
                prompt_tokens = rs.getLong(3),
                completion_tokens = rs.getLong(4),
                cost = rs.getDouble(5)
            )
            out.add(UsageInterface.DailyUsage(day, model, usage))
        }
        return out
    }

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

    private fun saveUsageValues(
        conn: Connection,
        usageKey: UsageInterface.UsageKey,
        usageValues: UsageInterface.UsageValues,
        ts: Timestamp
    ) {
        log.debug(
            "Saving usage values for session: {}, user: {}, model: {}",
            usageKey.session,
            usageKey.user?.email,
            usageKey.model.modelId
        )
        val statement = conn.prepareStatement(
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
        statement.setTimestamp(7, ts)
        statement.executeUpdate()
    }

    private fun upsertDailyUsage(
        conn: Connection,
        usageKey: UsageInterface.UsageKey,
        usageValues: UsageInterface.UsageValues,
        day: LocalDate
    ) {
        val userId = usageKey.user?.email ?: ""
        val model = usageKey.model.modelId
        val sqlDay = SqlDate.valueOf(day)
        val update = conn.prepareStatement(
            """
            UPDATE usage_daily
            SET prompt_tokens = prompt_tokens + ?,
                completion_tokens = completion_tokens + ?,
                cost = cost + ?
            WHERE user_id = ? AND day = ? AND model = ?
            """
        )
        update.setLong(1, usageValues.inputTokens.get())
        update.setLong(2, usageValues.outputTokens.get())
        update.setDouble(3, usageValues.cost.get())
        update.setString(4, userId)
        update.setDate(5, sqlDay)
        update.setString(6, model)
        val rows = update.executeUpdate()
        if (rows == 0) {
            val insert = conn.prepareStatement(
                """
                INSERT INTO usage_daily (user_id, day, model, prompt_tokens, completion_tokens, cost)
                VALUES (?, ?, ?, ?, ?, ?)
                """
            )
            insert.setString(1, userId)
            insert.setDate(2, sqlDay)
            insert.setString(3, model)
            insert.setLong(4, usageValues.inputTokens.get())
            insert.setLong(5, usageValues.outputTokens.get())
            insert.setDouble(6, usageValues.cost.get())
            try {
                insert.executeUpdate()
            } catch (e: Exception) {
                update.executeUpdate()
            }
        }
    }

    private fun applyBudgetDelta(conn: Connection, userId: String, delta: Double) {
        val update = conn.prepareStatement(
            "UPDATE user_budget SET available = available + ? WHERE user_id = ?"
        )
        update.setDouble(1, delta)
        update.setString(2, userId)
        val rows = update.executeUpdate()
        if (rows == 0) {
            val insert = conn.prepareStatement(
                "INSERT INTO user_budget (user_id, available) VALUES (?, ?)"
            )
            insert.setString(1, userId)
            insert.setDouble(2, delta)
            try {
                insert.executeUpdate()
            } catch (e: Exception) {
                update.executeUpdate()
            }
        }
    }

    private fun encodeMetadata(metadata: Map<String, String>?): String {
        if (metadata.isNullOrEmpty()) return ""
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in metadata) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"").append(escapeJson(k)).append("\":\"").append(escapeJson(v)).append("\"")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

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

        internal val facet = HSQLFacet(
            name = "usage",
            schemaSql = listOf(
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
                """,
                """
                CREATE TABLE IF NOT EXISTS session_parents (
                    child_session_id VARCHAR(255),
                    parent_session_id VARCHAR(255),
                    PRIMARY KEY (child_session_id, parent_session_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS usage_daily (
                    user_id VARCHAR(255),
                    day DATE,
                    model VARCHAR(255),
                    prompt_tokens BIGINT DEFAULT 0,
                    completion_tokens BIGINT DEFAULT 0,
                    cost DOUBLE DEFAULT 0,
                    PRIMARY KEY (user_id, day, model)
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_usage_daily_user_day ON usage_daily(user_id, day)",
                """
                CREATE TABLE IF NOT EXISTS user_credits (
                    id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id VARCHAR(255),
                    amount DOUBLE,
                    comment VARCHAR(1024),
                    metadata VARCHAR(4096),
                    datetime TIMESTAMP
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_user_credits_user ON user_credits(user_id, datetime)",
                """
                CREATE TABLE IF NOT EXISTS user_budget (
                    user_id VARCHAR(255) PRIMARY KEY,
                    available DOUBLE DEFAULT 0
                )
                """
            )
        )

        // ---- Backwards-compatible static configuration surface ----
        @JvmStatic
        var serviceUrl: String?
            get() = facet.serviceUrl
            set(value) {
                facet.serviceUrl = value
            }

        @JvmStatic
        var serviceUser: String
            get() = facet.serviceUser
            set(value) {
                facet.serviceUser = value
            }

        @JvmStatic
        var servicePassword: String
            get() = facet.servicePassword
            set(value) {
                facet.servicePassword = value
            }

        @JvmStatic
        var serverHost: String
            get() = facet.serverHost
            set(value) {
                facet.serverHost = value
            }

        @JvmStatic
        var serverPort: Int
            get() = facet.serverPort
            set(value) {
                facet.serverPort = value
            }

        @JvmStatic
        var serverSilent: Boolean
            get() = facet.serverSilent
            set(value) {
                facet.serverSilent = value
            }

        @JvmStatic
        var dbName: String
            get() = facet.dbName
            set(value) {
                facet.dbName = value
            }

        fun getLocalServiceUrl(
            root: File = ApplicationServicesConfig.dataStorageRoot.resolve("usagedb")
        ): String = facet.getLocalServiceUrl(root)

        @JvmStatic
        @JvmOverloads
        fun getConn(root: File? = null): Connection = facet.getConnection(root)
    }
}