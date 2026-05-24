package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class UsageDB : UsageInterface {

    override fun incrementUsage(session: Session, user: User, model: AIModel, usage: ModelSchema.Usage, data: ModelSchema.UsageData?) {
        try {
            val isTracked = tracked_users.contains(user.email)
            val usageKey = UsageInterface.UsageKey(session, user, model)
            val rawCost = model.pricing(usage)
            val cost = rawCost.coerceAtLeast(0.0) * cost_scaling_factor.coerceAtLeast(0.0)
            // TODO: Downgrade this to debug once we have more confidence in the accuracy of the cost estimation
            log.info(
                "Incrementing usage for session: {}, user: {}, model: {} (raw_cost={}, scaled_cost={}, cost_scaling_factor={})",
                session, user.email, model.modelId, rawCost, cost, cost_scaling_factor
            )
            val now = Instant.now()
            val day = LocalDate.ofInstant(now, ZoneOffset.UTC)
            val usageValues = UsageInterface.UsageValues()
            usageValues.addAndGet(usage, cost)
            facet.withTransaction { conn ->
                saveUsageValues(
                    conn = conn,
                    usageKey = usageKey,
                    usageValues = usageValues,
                    ts = Timestamp.from(now),
                    inputText = if (isTracked) data?.input_text else null,
                    outputText = if (isTracked) data?.output_text else null
                )
                upsertDailyUsage(conn, usageKey, usageValues, day)
                if (cost != 0.0 && user.email.isNotEmpty()) {
                    applyBudgetDelta(conn, user.email, -cost)
                }
            }
            log.info(
                "Usage incremented for session: {}, user: {}, model: {} (cost_scaling_factor={})",
                session, user.email, model.modelId, cost_scaling_factor
            )
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
                     SELECT model, token_type, SUM(token_count), SUM(cost)
                    FROM usage_daily
                    WHERE user_id = ? AND day >= ? AND day < ?
                     GROUP BY model, token_type
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
            if (allSessionIds.isEmpty()) return@withConnection emptyMap()
            val placeholders = allSessionIds.joinToString(",") { "?" }
            conn.prepareStatement(
                """
                     SELECT u.model, ut.token_type, SUM(ut.token_count), SUM(u.cost)
                     FROM usage u
                     LEFT JOIN usage_tokens ut ON u.id = ut.usage_id
                     WHERE u.session_id IN ($placeholders)
                     GROUP BY u.model, ut.token_type
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
                 stmt.executeUpdate("DELETE FROM usage_tokens")
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

    override fun getParentSession(child: Session): Session? {
        log.debug("Getting parent session for child: {}", child.sessionId)
        return facet.withConnection { conn ->
            conn.prepareStatement(
                "SELECT parent_session_id FROM session_parents WHERE child_session_id = ?"
            ).use { stmt ->
                stmt.setString(1, child.sessionId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val parentId = rs.getString(1)
                        log.debug("Found parent session ID: {} for child: {}", parentId, child.sessionId)
                        Session(parentId)
                    } else {
                        log.debug("No parent session found for child: {}", child.sessionId)
                        null
                    }
                }
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
                     SELECT day, model, token_type, token_count, cost
                    FROM usage_daily
                    WHERE user_id = ? AND day >= ? AND day < ?
                    ORDER BY day ASC, model ASC
                    """
            ).use { stmt ->
                stmt.setString(1, user.email)
                stmt.setDate(2, SqlDate.valueOf(from))
                stmt.setDate(3, SqlDate.valueOf(to))
                stmt.executeQuery().use { rs ->
                     // Aggregate rows by (day, model) since usage_daily now has one row per token type.
                     val grouped = linkedMapOf<Pair<LocalDate, String>, Pair<MutableMap<TokenTypes, Long>, Double>>()
                    while (rs.next()) {
                        val day = rs.getDate(1).toLocalDate()
                        val model = rs.getString(2)
                         val tokenTypeRaw = rs.getString(3)
                         val tokenCount = rs.getLong(4)
                         val cost = rs.getDouble(5)
                         val key = day to model
                         val entry = grouped.getOrPut(key) { mutableMapOf<TokenTypes, Long>() to 0.0 }
                         val parsedType = tokenTypeRaw?.let { runCatching { TokenTypes.valueOf(it) }.getOrNull() }
                         if (parsedType != null && tokenCount != 0L) {
                             entry.first.merge(parsedType, tokenCount) { a, b -> a + b }
                         }
                         // Cost is replicated across token-type rows for same (day, model); take the max to avoid double counting.
                         grouped[key] = entry.first to maxOf(entry.second, cost)
                     }
                     val out = mutableListOf<UsageInterface.DailyUsage>()
                     for ((key, value) in grouped) {
                         val (day, model) = key
                         val (counts, cost) = value
                         val totalTokens = counts.values.sum()
                         val usage = ModelSchema.Usage(
                             counts = counts, total_tokens = totalTokens, cost = cost
                         )
                         out.add(UsageInterface.DailyUsage(day, model, usage))
                    }
                    out
                }
            }
        }
    }

    override fun getUserCredits(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.CreditEntry> {
        require(!to.isBefore(from)) { "'to' must be on or after 'from'" }
        return facet.withConnection { conn ->
            conn.prepareStatement(
                """
                     SELECT datetime, amount, comment, metadata
                     FROM user_credits
                     WHERE user_id = ? AND datetime >= ? AND datetime < ?
                     ORDER BY datetime ASC
                     """
            ).use { stmt ->
                stmt.setString(1, user.email)
                stmt.setTimestamp(2, Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant()))
                stmt.setTimestamp(3, Timestamp.from(to.atStartOfDay(ZoneOffset.UTC).toInstant()))
                stmt.executeQuery().use { rs ->
                    val out = mutableListOf<UsageInterface.CreditEntry>()
                    while (rs.next()) {
                        val ts = rs.getTimestamp(1)?.toInstant() ?: Instant.EPOCH
                        val amount = rs.getDouble(2)
                        val comment = rs.getString(3)?.takeIf { it.isNotEmpty() }
                        val metaRaw = rs.getString(4)?.takeIf { it.isNotEmpty() }
                        val metadata: Map<String, String>? = metaRaw?.let {
                            try {
                                Gson().fromJson(it, object : TypeToken<Map<String, String>>() {}.type)
                            } catch (e: Throwable) {
                                null
                            }
                        }
                        out.add(UsageInterface.CreditEntry(ts, amount, comment, metadata))
                    }
                    out
                }
            }
        }
    }

    override fun getUserBalance(userId: String): Double {
        if (userId.isEmpty()) return 0.0
        return facet.withConnection { conn ->
            conn.prepareStatement("SELECT available FROM user_budget WHERE user_id = ?").use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getDouble(1) else 0.0
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
        conn: Connection,
        usageKey: UsageInterface.UsageKey,
        usageValues: UsageInterface.UsageValues,
        ts: Timestamp,
        inputText: String? = null,
        outputText: String? = null
    ) {
        log.debug(
            "Saving usage values for session: {}, user: {}, model: {}",
            usageKey.session,
            usageKey.user?.email,
            usageKey.model.modelId
        )
         val tokenSnapshots: Map<TokenTypes, Long> = usageValues.tokenCounts
             .mapValues { it.value.get() }
             .filterValues { it != 0L }
         // Computed total for the legacy aggregate columns (prompt + completion only) for backward compatibility.
         val promptTotal = tokenSnapshots.getOrDefault(TokenTypes.Prompt, 0L)
         val completionTotal = tokenSnapshots.getOrDefault(TokenTypes.Completion, 0L)
         val generatedKeys = conn.prepareStatement(
            """
                INSERT INTO usage (session_id, user_id, model, prompt_tokens, completion_tokens, cost, datetime, input_text, output_text)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """,
             java.sql.Statement.RETURN_GENERATED_KEYS
         ).use { stmt ->
            stmt.setString(1, usageKey.session.sessionId)
            stmt.setString(2, usageKey.user?.email ?: "")
            stmt.setString(3, usageKey.model.modelId)
             stmt.setLong(4, promptTotal)
             stmt.setLong(5, completionTotal)
            stmt.setDouble(6, usageValues.cost.get())
            stmt.setTimestamp(7, ts)
            if (inputText != null) {
                 stmt.setString(8, truncateForClob(inputText))
            } else {
                 stmt.setNull(8, java.sql.Types.LONGVARCHAR)
            }
            if (outputText != null) {
                 stmt.setString(9, truncateForClob(outputText))
            } else {
                 stmt.setNull(9, java.sql.Types.LONGVARCHAR)
            }
            stmt.executeUpdate()
             stmt.generatedKeys.use { gk ->
                 if (gk.next()) gk.getLong(1) else -1L
             }
         }
         if (generatedKeys > 0 && tokenSnapshots.isNotEmpty()) {
             conn.prepareStatement(
                 "INSERT INTO usage_tokens (usage_id, token_type, token_count) VALUES (?, ?, ?)"
             ).use { stmt ->
                 for ((type, count) in tokenSnapshots) {
                     stmt.setLong(1, generatedKeys)
                     stmt.setString(2, type.name)
                     stmt.setLong(3, count)
                     stmt.addBatch()
                 }
                 stmt.executeBatch()
             }
        }
    }

    private fun upsertDailyUsage(
        conn: Connection, usageKey: UsageInterface.UsageKey, usageValues: UsageInterface.UsageValues, day: LocalDate
    ) {
        val userId = usageKey.user?.email ?: ""
        val model = usageKey.model.modelId
        val sqlDay = SqlDate.valueOf(day)
        val cost = usageValues.cost.get()
         val tokenSnapshots: Map<TokenTypes, Long> = usageValues.tokenCounts
             .mapValues { it.value.get() }
             .filterValues { it != 0L }
         // If there are no token counts but there is a cost, record an "Other" entry to preserve cost.
         val entries: Map<TokenTypes, Long> = if (tokenSnapshots.isEmpty() && cost != 0.0) {
             mapOf(TokenTypes.Prompt to 0L)
         } else tokenSnapshots
         // Distribute cost only to the first entry to avoid double counting in joins/aggregations.
         val firstType = entries.keys.firstOrNull()
         for ((type, count) in entries) {
             val rowCost = if (type == firstType) cost else 0.0
             upsertDailyUsageRow(conn, userId, sqlDay, model!!, type, count, rowCost)
         }
     }
     private fun upsertDailyUsageRow(
         conn: Connection,
         userId: String,
         sqlDay: SqlDate,
         model: String,
         tokenType: TokenTypes,
         tokenCount: Long,
         cost: Double
     ) {
        if (facet.dbProvider == "postgresql") {
            conn.prepareStatement(
                """
                      INSERT INTO usage_daily (user_id, day, model, token_type, token_count, cost)
                     VALUES (?, ?, ?, ?, ?, ?)
                      ON CONFLICT (user_id, day, model, token_type) DO UPDATE
                      SET token_count = usage_daily.token_count + EXCLUDED.token_count,
                         cost = usage_daily.cost + EXCLUDED.cost
                     """
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setDate(2, sqlDay)
                stmt.setString(3, model)
                 stmt.setString(4, tokenType.name)
                 stmt.setLong(5, tokenCount)
                stmt.setDouble(6, cost)
                stmt.executeUpdate()
            }
            return
        }
        val updated = conn.prepareStatement(
            """
                 UPDATE usage_daily
                  SET token_count = token_count + ?,
                     cost = cost + ?
                  WHERE user_id = ? AND day = ? AND model = ? AND token_type = ?
                 """
        ).use { stmt ->
             stmt.setLong(1, tokenCount)
             stmt.setDouble(2, cost)
             stmt.setString(3, userId)
             stmt.setDate(4, sqlDay)
             stmt.setString(5, model)
             stmt.setString(6, tokenType.name)
            stmt.executeUpdate()
        }
        if (updated == 0) {
            conn.prepareStatement(
                """
                      INSERT INTO usage_daily (user_id, day, model, token_type, token_count, cost)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setDate(2, sqlDay)
                stmt.setString(3, model)
                 stmt.setString(4, tokenType.name)
                 stmt.setLong(5, tokenCount)
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

    /**
      * Truncate text for CLOB/TEXT storage to prevent runaway memory/storage usage.
      * Default cap is generous (~1 MiB) but configurable.
      */
     private fun truncateForClob(text: String): String {
         val max = max_clob_chars
         return if (text.length <= max) text
         else text.substring(0, max) + "\n…[truncated ${text.length - max} chars]"
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
         // Rows are: model, token_type, SUM(token_count), SUM(cost)
         // We need to aggregate per-model with a counts map and a single cost (avoid double counting cost across token-type rows).
         val counts = linkedMapOf<String, MutableMap<TokenTypes, Long>>()
         val costs = linkedMapOf<String, Double>()
        while (resultSet.next()) {
            val model = resultSet.getString(1)
             val tokenTypeRaw = resultSet.getString(2)
             val tokenCount = resultSet.getLong(3)
             val cost = resultSet.getDouble(4)
             val countsForModel = counts.getOrPut(model) { mutableMapOf() }
             val parsedType = tokenTypeRaw?.let { runCatching { TokenTypes.valueOf(it) }.getOrNull() }
             if (parsedType != null && tokenCount != 0L) {
                 countsForModel.merge(parsedType, tokenCount) { a, b -> a + b }
             }
             // Cost rows for the same model are duplicated across token types in the GROUP BY;
             // use max to approximate the per-model cost without double counting.
             costs[model] = maxOf(costs.getOrDefault(model, 0.0), cost)
         }
         val summary = mutableMapOf<String, ModelSchema.Usage>()
         for ((model, countMap) in counts) {
             val totalTokens = countMap.values.sum()
             summary[model] = ModelSchema.Usage(
                 counts = countMap, total_tokens = totalTokens, cost = costs.getOrDefault(model, 0.0)
             )
        }
        return summary
    }

    companion object {
        private val log = LoggerFactory.getLogger(UsageDB::class.java)

        var cost_scaling_factor: Double = 1.0
            set(value) {
                require(value >= 0.0) { "Cost scaling factor must be non-negative" }
                log.warn("Setting cost scaling factor to {}. This will affect all future cost calculations. Current value: {}", value, field)
                field = value
            }
         /** Maximum number of characters to persist into input_text/output_text columns. */
         var max_clob_chars: Int = 1_048_576
             set(value) {
                 require(value >= 0) { "max_clob_chars must be non-negative" }
                 field = value
             }

        /**
         * Schema DDL designed to be compatible across HSQL and PostgreSQL.
         * - Uses DOUBLE PRECISION (standard SQL) instead of HSQL's DOUBLE alias.
         *   HSQL accepts DOUBLE PRECISION as well.
         * - Uses BIGINT/INTEGER GENERATED BY DEFAULT AS IDENTITY (SQL standard,
         *   supported by HSQL and PostgreSQL 10+).
         * - Uses CREATE INDEX IF NOT EXISTS (supported by HSQL and PostgreSQL 9.5+).
          * - Uses provider-specific large-text type: CLOB on HSQL, TEXT on PostgreSQL.
         */
        internal val facet = DatabaseFacet(
             name = "usage", schema = { provider ->
                 val largeText = when (provider) {
                     "postgresql" -> "TEXT"
                     else -> "CLOB"
                 }
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
                        datetime TIMESTAMP,
                         input_text $largeText,
                         output_text $largeText
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS idx_usage_session ON usage(session_id)",
                    "CREATE INDEX IF NOT EXISTS idx_usage_user_dt ON usage(user_id, datetime)",
                    // Schema upgrade for existing deployments (idempotent via IF NOT EXISTS where supported)
                     "ALTER TABLE usage ADD COLUMN IF NOT EXISTS input_text $largeText",
                     "ALTER TABLE usage ADD COLUMN IF NOT EXISTS output_text $largeText",
                     """
                     CREATE TABLE IF NOT EXISTS usage_tokens (
                         usage_id BIGINT,
                         token_type VARCHAR(64),
                         token_count BIGINT,
                         PRIMARY KEY (usage_id, token_type)
                     )
                     """,
                     "CREATE INDEX IF NOT EXISTS idx_usage_tokens_usage ON usage_tokens(usage_id)",
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
                         token_type VARCHAR(64) DEFAULT 'Prompt',
                         token_count BIGINT DEFAULT 0,
                        cost DOUBLE PRECISION DEFAULT 0,
                         PRIMARY KEY (user_id, day, model, token_type)
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS idx_usage_daily_user_day ON usage_daily(user_id, day)",
                    // Schema migration for existing deployments: add token_type column and token_count column.
                    // These are idempotent via IF NOT EXISTS (supported by HSQL 2.x and PostgreSQL 9.6+).
                    "ALTER TABLE usage_daily ADD COLUMN IF NOT EXISTS token_type VARCHAR(64) DEFAULT 'Prompt'",
                    "ALTER TABLE usage_daily ADD COLUMN IF NOT EXISTS token_count BIGINT DEFAULT 0",
                    // Best-effort backfill: ensure existing rows have non-null token_type.
                    "UPDATE usage_daily SET token_type = 'Prompt' WHERE token_type IS NULL",
                    "UPDATE usage_daily SET token_count = 0 WHERE token_count IS NULL",
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

        val tracked_users = mutableSetOf(
            "acharneski@gmail.com", // Only track debug users
        )
    }
}