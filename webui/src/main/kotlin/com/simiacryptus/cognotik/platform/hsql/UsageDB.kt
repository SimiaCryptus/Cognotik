package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.toJson
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.sql.Date as SqlDate

class UsageDB : UsageInterface {

    override fun incrementUsage(session: Session, user: User, model: AIModel, tokens: ModelSchema.Usage) {
        try {
            val usageKey = UsageInterface.UsageKey(session, user, model)
            val usageValues = UsageInterface.UsageValues()
            usageValues.addAndGet(tokens)
            val now = Instant.now()
            val day = LocalDate.ofInstant(now, ZoneOffset.UTC)
            facet.withTransaction { conn ->
                saveUsageValues(conn, usageKey, usageValues, Timestamp.from(now))
                upsertDailyUsage(conn, usageKey, usageValues, day)
                val cost = usageValues.cost.get()
                if (cost != 0.0 && user.email.isNotEmpty()) {
                    applyBudgetDelta(conn, user.email, -cost)
                }
            }
            log.info("Usage incremented for session: {}, user: {}, model: {}", session, user.email, model.modelId)
        } catch (e: Exception) {
            log.error(
                "Error incrementing usage for session={}, user={}, model={}", session, user.email, model.modelId, e
            )
        }
    }

    override fun getUserUsageSummary(user: User, from: LocalDate, to: LocalDate): Map<String, ModelSchema.Usage> {
        require(!to.isBefore(from)) { "'to' must be on or after 'from'" }
        log.info("Get user usage summary user={} from={} to={} (to exclusive)", user.email, from, to)
        return facet.withConnection { conn ->
            conn.prepareStatement(
                """
                    SELECT model, SUM(prompt_tokens), SUM(completion_tokens), SUM(cost)
                    FROM usage_daily
                    WHERE user_id = ? AND day >= ? AND day < ?
                    GROUP BY model
                    """
            ).use { stmt ->
                stmt.setString(1, user.email)
                stmt.setDate(2, SqlDate.valueOf(from))
                stmt.setDate(3, SqlDate.valueOf(to))
                stmt.executeQuery().use { rs -> generateUsageSummary(rs) }
            }
        }
    }

    override fun getSessionUsageSummary(session: Session): Map<String, ModelSchema.Usage> {
        log.info("Getting session usage summary for session: {}", session)
        return facet.withConnection { conn ->
            val allSessionIds = collectSessionIds(conn, session.sessionId)
            log.debug("Collected session IDs (including children): {}", allSessionIds)
            if (allSessionIds.isEmpty()) return@withConnection emptyMap<String, ModelSchema.Usage>()
            val placeholders = allSessionIds.joinToString(",") { "?" }
            conn.prepareStatement(
                """
                    SELECT model, SUM(prompt_tokens), SUM(completion_tokens), SUM(cost)
                    FROM usage
                    WHERE session_id IN ($placeholders)
                    GROUP BY model
                    """
            ).use { stmt ->
                allSessionIds.forEachIndexed { index, sessionId ->
                    stmt.setString(index + 1, sessionId)
                }
                stmt.executeQuery().use { rs -> generateUsageSummary(rs) }
            }
        }
    }

    override fun clear() {
        log.debug("Clearing all usage data")
        facet.withTransaction { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM usage")
                stmt.executeUpdate("DELETE FROM usage_daily")
                stmt.executeUpdate("DELETE FROM session_parents")
                stmt.executeUpdate("DELETE FROM user_credits")
                stmt.executeUpdate("DELETE FROM user_budget")
            }
        }
    }

    override fun setParentSession(child: Session, parent: Session) {
        log.info("Setting parent session: child={}, parent={}", child.sessionId, parent.sessionId)
        facet.withConnection { conn ->
            val sql = when (facet.dbProvider) {
                "postgresql" -> """
                    INSERT INTO session_parents (child_session_id, parent_session_id)
                    VALUES (?, ?)
                    ON CONFLICT (child_session_id, parent_session_id) DO NOTHING
                    """

                else -> """
                    MERGE INTO session_parents
                    USING (VALUES(CAST(? AS VARCHAR(255)), CAST(? AS VARCHAR(255)))) AS vals(c, p)
                    ON session_parents.child_session_id = vals.c AND session_parents.parent_session_id = vals.p
                    WHEN NOT MATCHED THEN INSERT (child_session_id, parent_session_id) VALUES (vals.c, vals.p)
                    """
            }
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, child.sessionId)
                stmt.setString(2, parent.sessionId)
                stmt.executeUpdate()
            }
        }
    }

    override fun getAvailableBudget(user: User): Double {
        if (user.email.isEmpty()) return 0.0
        return facet.withConnection { conn ->
            conn.prepareStatement("SELECT available FROM user_budget WHERE user_id = ?").use { stmt ->
                stmt.setString(1, user.email)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getDouble(1) else 0.0
                }
            }
        }
    }

    override fun creditUser(
        user: User, amount: Double, comment: String?, metadata: Map<String, String>?
    ): Double {
        require(user.email.isNotEmpty()) { "User email is required for crediting" }
        log.info("Crediting user {} amount={} comment={}", user.email, amount, comment)
        facet.withTransaction { conn ->
            conn.prepareStatement(
                """
                    INSERT INTO user_credits (user_id, amount, comment, metadata, datetime)
                    VALUES (?, ?, ?, ?, ?)
                    """
            ).use { stmt ->
                stmt.setString(1, user.email)
                stmt.setDouble(2, amount)
                stmt.setString(3, comment ?: "")
                stmt.setString(4, encodeMetadata(metadata))
                stmt.setTimestamp(5, Timestamp.from(Instant.now()))
                stmt.executeUpdate()
            }
            applyBudgetDelta(conn, user.email, amount)
        }
        return getAvailableBudget(user)
    }

    override fun getUserDailyUsage(
        user: User, from: LocalDate, to: LocalDate
    ): List<UsageInterface.DailyUsage> {
        require(!to.isBefore(from)) { "'to' must be on or after 'from'" }
        return facet.withConnection { conn ->
            conn.prepareStatement(
                """
                    SELECT day, model, prompt_tokens, completion_tokens, cost
                    FROM usage_daily
                    WHERE user_id = ? AND day >= ? AND day < ?
                    ORDER BY day ASC, model ASC
                    """
            ).use { stmt ->
                stmt.setString(1, user.email)
                stmt.setDate(2, SqlDate.valueOf(from))
                stmt.setDate(3, SqlDate.valueOf(to))
                stmt.executeQuery().use { rs ->
                    val out = mutableListOf<UsageInterface.DailyUsage>()
                    while (rs.next()) {
                        val day = rs.getDate(1).toLocalDate()
                        val model = rs.getString(2)
                        val usage = ModelSchema.Usage(
                            prompt_tokens = rs.getLong(3), completion_tokens = rs.getLong(4), cost = rs.getDouble(5)
                        )
                        out.add(UsageInterface.DailyUsage(day, model, usage))
                    }
                    out
                }
            }
        }
    }

    private fun collectSessionIds(conn: Connection, sessionId: String): Set<String> {
        val visited = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(sessionId)
        conn.prepareStatement(
            "SELECT child_session_id FROM session_parents WHERE parent_session_id = ?"
        ).use { stmt ->
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!visited.add(current)) continue
                stmt.setString(1, current)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val childId = rs.getString(1)
                        if (childId !in visited) queue.add(childId)
                    }
                }
            }
        }
        return visited
    }

    private fun saveUsageValues(
        conn: Connection, usageKey: UsageInterface.UsageKey, usageValues: UsageInterface.UsageValues, ts: Timestamp
    ) {
        log.debug(
            "Saving usage values for session: {}, user: {}, model: {}",
            usageKey.session,
            usageKey.user?.email,
            usageKey.model.modelId
        )
        conn.prepareStatement(
            """
                INSERT INTO usage (session_id, user_id, model, prompt_tokens, completion_tokens, cost, datetime)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """
        ).use { stmt ->
            stmt.setString(1, usageKey.session.sessionId)
            stmt.setString(2, usageKey.user?.email ?: "")
            stmt.setString(3, usageKey.model.modelId)
            stmt.setLong(4, usageValues.inputTokens.get())
            stmt.setLong(5, usageValues.outputTokens.get())
            stmt.setDouble(6, usageValues.cost.get())
            stmt.setTimestamp(7, ts)
            stmt.executeUpdate()
        }
    }

    private fun upsertDailyUsage(
        conn: Connection, usageKey: UsageInterface.UsageKey, usageValues: UsageInterface.UsageValues, day: LocalDate
    ) {
        val userId = usageKey.user?.email ?: ""
        val model = usageKey.model.modelId
        val sqlDay = SqlDate.valueOf(day)
        val pTokens = usageValues.inputTokens.get()
        val cTokens = usageValues.outputTokens.get()
        val cost = usageValues.cost.get()
        if (facet.dbProvider == "postgresql") {
            conn.prepareStatement(
                """
                     INSERT INTO usage_daily (user_id, day, model, prompt_tokens, completion_tokens, cost)
                     VALUES (?, ?, ?, ?, ?, ?)
                     ON CONFLICT (user_id, day, model) DO UPDATE
                     SET prompt_tokens = usage_daily.prompt_tokens + EXCLUDED.prompt_tokens,
                         completion_tokens = usage_daily.completion_tokens + EXCLUDED.completion_tokens,
                         cost = usage_daily.cost + EXCLUDED.cost
                     """
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setDate(2, sqlDay)
                stmt.setString(3, model)
                stmt.setLong(4, pTokens)
                stmt.setLong(5, cTokens)
                stmt.setDouble(6, cost)
                stmt.executeUpdate()
            }
            return
        }
        val updated = conn.prepareStatement(
            """
                 UPDATE usage_daily
                 SET prompt_tokens = prompt_tokens + ?,
                     completion_tokens = completion_tokens + ?,
                     cost = cost + ?
                 WHERE user_id = ? AND day = ? AND model = ?
                 """
        ).use { stmt ->
            stmt.setLong(1, pTokens)
            stmt.setLong(2, cTokens)
            stmt.setDouble(3, cost)
            stmt.setString(4, userId)
            stmt.setDate(5, sqlDay)
            stmt.setString(6, model)
            stmt.executeUpdate()
        }
        if (updated == 0) {
            conn.prepareStatement(
                """
                     INSERT INTO usage_daily (user_id, day, model, prompt_tokens, completion_tokens, cost)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setDate(2, sqlDay)
                stmt.setString(3, model)
                stmt.setLong(4, pTokens)
                stmt.setLong(5, cTokens)
                stmt.setDouble(6, cost)
                stmt.executeUpdate()
            }
        }
    }

    private fun applyBudgetDelta(conn: Connection, userId: String, delta: Double) {
        if (facet.dbProvider == "postgresql") {
            conn.prepareStatement(
                """
                     INSERT INTO user_budget (user_id, available) VALUES (?, ?)
                     ON CONFLICT (user_id) DO UPDATE
                     SET available = user_budget.available + EXCLUDED.available
                     """
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setDouble(2, delta)
                stmt.executeUpdate()
            }
            return
        }
        val updated = conn.prepareStatement(
            "UPDATE user_budget SET available = available + ? WHERE user_id = ?"
        ).use { stmt ->
            stmt.setDouble(1, delta)
            stmt.setString(2, userId)
            stmt.executeUpdate()
        }
        if (updated == 0) {
            conn.prepareStatement(
                "INSERT INTO user_budget (user_id, available) VALUES (?, ?)"
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setDouble(2, delta)
                stmt.executeUpdate()
            }
        }
    }

    private fun encodeMetadata(metadata: Map<String, String>?): String {
        if (metadata.isNullOrEmpty()) return ""
        return try {
            metadata.toJson()
        } catch (e: Throwable) {
            log.warn("Failed to JSON-encode metadata, falling back to empty", e)
            ""
        }
    }

    private fun generateUsageSummary(resultSet: ResultSet): Map<String, ModelSchema.Usage> {
        val summary = mutableMapOf<String, ModelSchema.Usage>()
        while (resultSet.next()) {
            val model = resultSet.getString(1)
            summary[model] = ModelSchema.Usage(
                prompt_tokens = resultSet.getLong(2),
                completion_tokens = resultSet.getLong(3),
                cost = resultSet.getDouble(4)
            )
        }
        return summary
    }

    companion object {
        private val log = LoggerFactory.getLogger(UsageDB::class.java)

        /**
         * Schema DDL designed to be compatible across HSQL and PostgreSQL.
         * - Uses DOUBLE PRECISION (standard SQL) instead of HSQL's DOUBLE alias.
         *   HSQL accepts DOUBLE PRECISION as well.
         * - Uses BIGINT/INTEGER GENERATED BY DEFAULT AS IDENTITY (SQL standard,
         *   supported by HSQL and PostgreSQL 10+).
         * - Uses CREATE INDEX IF NOT EXISTS (supported by HSQL and PostgreSQL 9.5+).
         */
        internal val facet = DatabaseFacet(
            name = "usage", schema = {
                listOf(
                    """
                    CREATE TABLE IF NOT EXISTS usage (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        session_id VARCHAR(255),
                        user_id VARCHAR(255),
                        model VARCHAR(255),
                        prompt_tokens BIGINT,
                        completion_tokens BIGINT,
                        cost DOUBLE PRECISION,
                        datetime TIMESTAMP
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS idx_usage_session ON usage(session_id)",
                    "CREATE INDEX IF NOT EXISTS idx_usage_user_dt ON usage(user_id, datetime)",
                    """
                    CREATE TABLE IF NOT EXISTS session_parents (
                        child_session_id VARCHAR(255),
                        parent_session_id VARCHAR(255),
                        PRIMARY KEY (child_session_id, parent_session_id)
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS idx_session_parents_parent ON session_parents(parent_session_id)",
                    """
                    CREATE TABLE IF NOT EXISTS usage_daily (
                        user_id VARCHAR(255),
                        day DATE,
                        model VARCHAR(255),
                        prompt_tokens BIGINT DEFAULT 0,
                        completion_tokens BIGINT DEFAULT 0,
                        cost DOUBLE PRECISION DEFAULT 0,
                        PRIMARY KEY (user_id, day, model)
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS idx_usage_daily_user_day ON usage_daily(user_id, day)",
                    """
                    CREATE TABLE IF NOT EXISTS user_credits (
                        id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        user_id VARCHAR(255),
                        amount DOUBLE PRECISION,
                        comment VARCHAR(1024),
                        metadata VARCHAR(4096),
                        datetime TIMESTAMP
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS idx_user_credits_user ON user_credits(user_id, datetime)",
                    """
                    CREATE TABLE IF NOT EXISTS user_budget (
                        user_id VARCHAR(255) PRIMARY KEY,
                        available DOUBLE PRECISION DEFAULT 0
                    )
                    """
                )
            })

    }
}