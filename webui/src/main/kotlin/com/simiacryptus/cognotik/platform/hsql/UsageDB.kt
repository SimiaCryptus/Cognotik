package com.simiacryptus.cognotik.platform.hsql

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.toJson
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.let

class UsageDB : UsageInterface {

    object UsageTable : Table("usage") {
        val id = long("id").autoIncrement()
        val sessionId = varchar("session_id", 255).nullable()
        val userId = varchar("user_id", 255).nullable()
        val model = varchar("model", 255).nullable()
        val promptTokens = long("prompt_tokens").nullable()
        val completionTokens = long("completion_tokens").nullable()
        val cost = double("cost").nullable()
        val datetime = timestamp("datetime").nullable()
        val inputText = text("input_text", eagerLoading = true).nullable()
        val outputText = text("output_text", eagerLoading = true).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object UsageTokensTable : Table("usage_tokens") {
        val usageId = long("usage_id")
        val tokenType = varchar("token_type", 64)
        val tokenCount = long("token_count")
        override val primaryKey = PrimaryKey(usageId, tokenType)
    }

    object SessionParentsTable : Table("session_parents") {
        val childSessionId = varchar("child_session_id", 255)
        val parentSessionId = varchar("parent_session_id", 255)
        override val primaryKey = PrimaryKey(childSessionId, parentSessionId)
    }

    object UsageDailyTable : Table("usage_daily") {
        val userId = varchar("user_id", 255)
        val day = date("day")
        val model = varchar("model", 255)
        val tokenType = varchar("token_type", 64).default("Prompt")
        val tokenCount = long("token_count").default(0L)
        val cost = double("cost").default(0.0)
        override val primaryKey = PrimaryKey(userId, day, model, tokenType)
    }

    object UserCreditsTable : Table("user_credits") {
        val id = integer("id").autoIncrement()
        val userId = varchar("user_id", 255).nullable()
        val amount = double("amount").nullable()
        val comment = varchar("comment", 1024).nullable()
        val metadata = varchar("metadata", 4096).nullable()
        val datetime = timestamp("datetime").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object UserBudgetTable : Table("user_budget") {
        val userId = varchar("user_id", 255)
        val available = double("available").default(0.0)
        override val primaryKey = PrimaryKey(userId)
    }

    private val database: Database get() = ExposedDatabase.get(facet)

    override fun incrementUsage(
        session: Session,
        user: User,
        model: AIModel,
        usage: ModelSchema.Usage,
        data: ModelSchema.UsageData?
    ) {
        try {
            val isTracked = tracked_users.contains(user.email)
            val usageKey = UsageInterface.UsageKey(session, user, model)
            val rawCost = model.pricing(usage)
            val cost = rawCost.coerceAtLeast(0.0) * cost_scaling_factor.coerceAtLeast(0.0)
            log.debug(
                "Incrementing usage for session: {}, user: {}, model: {} (raw_cost={}, scaled_cost={}, cost_scaling_factor={})",
                session, user.email, model.modelId, rawCost, cost, cost_scaling_factor
            )
            if (rawCost < 0.0) {
                log.warn(
                    "Negative raw cost {} for session={}, user={}, model={}; clamped to 0.0",
                    rawCost, session, user.email, model.modelId
                )
            }
            val now = Instant.now()
            val day = LocalDate.ofInstant(now, ZoneOffset.UTC)
            val usageValues = UsageInterface.UsageValues()
            usageValues.addAndGet(usage, cost)
            transaction(database) {
                saveUsageValues(
                    usageKey = usageKey,
                    usageValues = usageValues,
                    ts = now,
                    inputText = if (isTracked) data?.input_text else null,
                    outputText = if (isTracked) data?.output_text else null
                )
                upsertDailyUsage(usageKey, usageValues, day)
                if (cost != 0.0 && user.email.isNotEmpty()) {
                    applyBudgetDelta(user.email, -cost)
                }
            }
            log.debug(
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
        log.debug("Get user usage summary user={} from={} to={} (to exclusive)", user.email, from, to)
        return transaction(database) {
            val counts = linkedMapOf<String, MutableMap<TokenTypes, Long>>()
            val costs = linkedMapOf<String, Double>()
            UsageDailyTable
                .selectAll()
                .where {
                    (UsageDailyTable.userId eq user.email) and
                            (UsageDailyTable.day greaterEq from) and
                            (UsageDailyTable.day less to)
                }
                .forEach { row ->
                    val model = row[UsageDailyTable.model]
                    val tokenTypeRaw = row[UsageDailyTable.tokenType]
                    val tokenCount = row[UsageDailyTable.tokenCount]
                    val cost = row[UsageDailyTable.cost]
                    val countsForModel = counts.getOrPut(model) { mutableMapOf() }
                    val parsedType = runCatching { TokenTypes.valueOf(tokenTypeRaw) }
                        .onFailure {
                            log.warn(
                                "Unknown token type '{}' in usage_daily for user={} model={}; skipping count",
                                tokenTypeRaw, user.email, model, it
                            )
                        }
                        .getOrNull()
                    if (parsedType != null && tokenCount != 0L) {
                        countsForModel.merge(parsedType, tokenCount) { a, b -> a + b }
                    }
                     // Cost is stored on the first token-type row per (user, day, model);
                     // remaining token-type rows for the same key carry 0.0. Summing
                     // therefore yields the correct per-model total across days.
                     costs[model] = costs.getOrDefault(model, 0.0) + cost
                }
            val summary = mutableMapOf<String, ModelSchema.Usage>()
            for ((model, countMap) in counts) {
                val totalTokens = countMap.values.sum()
                summary[model] = ModelSchema.Usage(
                    counts = countMap, total_tokens = totalTokens, cost = costs.getOrDefault(model, 0.0)
                )
            }
            summary
        }
    }

    override fun getSessionUsageSummary(session: Session): Map<String, ModelSchema.Usage> {
        log.debug("Getting session usage summary for session: {}", session)
        return transaction(database) {
            val allSessionIds = collectSessionIds(session.sessionId)
            log.debug("Collected session IDs (including children): {}", allSessionIds)
            if (allSessionIds.isEmpty()) return@transaction emptyMap()
            // Fetch usage rows for the relevant sessions.
            val usageRows = UsageTable
                .selectAll()
                .where { UsageTable.sessionId inList allSessionIds.toList() }
                .map { row ->
                    Triple(
                        row[UsageTable.id],
                        row[UsageTable.model] ?: "",
                        row[UsageTable.cost] ?: 0.0
                    )
                }
            if (usageRows.isEmpty()) return@transaction emptyMap()
            val usageIds = usageRows.map { it.first }
            val usageById = usageRows.associateBy { it.first }
            // Fetch token rows for those usage ids.
            val tokenRowsByUsageId = UsageTokensTable
                .selectAll()
                .where { UsageTokensTable.usageId inList usageIds }
                .groupBy { it[UsageTokensTable.usageId] }
            // Aggregate per-model: token counts summed, cost summed (single source of truth per usage row).
            val counts = linkedMapOf<String, MutableMap<TokenTypes, Long>>()
            val costs = linkedMapOf<String, Double>()
            for ((usageId, modelAndCost) in usageById) {
                val model = modelAndCost.second
                val cost = modelAndCost.third
                costs[model] = (costs[model] ?: 0.0) + cost
                val tokenRows = tokenRowsByUsageId[usageId].orEmpty()
                if (tokenRows.isEmpty()) {
                    counts.getOrPut(model) { mutableMapOf() }
                } else {
                    val countsForModel = counts.getOrPut(model) { mutableMapOf() }
                    for (tokenRow in tokenRows) {
                        val tokenTypeRaw = tokenRow[UsageTokensTable.tokenType]
                        val tokenCount = tokenRow[UsageTokensTable.tokenCount]
                        val parsedType = runCatching { TokenTypes.valueOf(tokenTypeRaw) }
                            .onFailure {
                                log.warn(
                                    "Unknown token type '{}' in usage_tokens for usage_id={} model={}; skipping count",
                                    tokenTypeRaw, usageId, model, it
                                )
                            }
                            .getOrNull()
                        if (parsedType != null && tokenCount != 0L) {
                            countsForModel.merge(parsedType, tokenCount) { a, b -> a + b }
                        }
                    }
                }
            }
            val summary = mutableMapOf<String, ModelSchema.Usage>()
            for ((model, countMap) in counts) {
                val totalTokens = countMap.values.sum()
                summary[model] = ModelSchema.Usage(
                    counts = countMap, total_tokens = totalTokens, cost = costs.getOrDefault(model, 0.0)
                )
            }
            summary
        }
    }

    override fun clear() {
        log.warn("Clearing ALL usage data (usage, usage_tokens, usage_daily, session_parents, user_credits, user_budget)")
        try {
            transaction(database) {
                UsageTokensTable.deleteAll()
                UsageTable.deleteAll()
                UsageDailyTable.deleteAll()
                SessionParentsTable.deleteAll()
                UserCreditsTable.deleteAll()
                UserBudgetTable.deleteAll()
            }
            log.info("All usage data cleared successfully")
        } catch (e: Exception) {
            log.error("Failed to clear usage data", e)
            throw e
        }
    }

    override fun setParentSession(child: Session, parent: Session) {
        log.debug("Setting parent session: child={}, parent={}", child.sessionId, parent.sessionId)
        transaction(database) {
            // insertIgnore translates to ON CONFLICT DO NOTHING / MERGE depending on dialect.
            SessionParentsTable.insertIgnore {
                it[childSessionId] = child.sessionId
                it[parentSessionId] = parent.sessionId
            }
        }
    }

    override fun getParentSession(child: Session): Session? {
        log.debug("Getting parent session for child: {}", child.sessionId)
        return transaction(database) {
            val row = SessionParentsTable
                .selectAll()
                .where { SessionParentsTable.childSessionId eq child.sessionId }
                .limit(1)
                .firstOrNull()
            if (row != null) {
                val parentId = row[SessionParentsTable.parentSessionId]
                log.debug("Found parent session ID: {} for child: {}", parentId, child.sessionId)
                Session(parentId)
            } else {
                log.debug("No parent session found for child: {}", child.sessionId)
                null
            }
        }
    }

    override fun getAvailableBudget(user: User): Double {
        if (user.email.isEmpty()) return 0.0
        return transaction(database) {
            UserBudgetTable
                .selectAll()
                .where { UserBudgetTable.userId eq user.email }
                .limit(1)
                .firstOrNull()
                ?.get(UserBudgetTable.available) ?: 0.0
        }
    }

    override fun creditUser(
        user: User, amount: Double, comment: String?, metadata: Map<String, String>?
    ): Double {
        require(user.email.isNotEmpty()) { "User email is required for crediting" }
        log.info("Crediting user {} amount={} comment='{}'", user.email, amount, comment)
        try {
            transaction(database) {
                UserCreditsTable.insert {
                    it[userId] = user.email
                    it[UserCreditsTable.amount] = amount
                    it[UserCreditsTable.comment] = comment ?: ""
                    it[UserCreditsTable.metadata] = encodeMetadata(metadata)
                    it[datetime] = Instant.now()
                }
                applyBudgetDelta(user.email, amount)
            }
        } catch (e: Exception) {
            log.error("Failed to credit user {} amount={}: {}", user.email, amount, e.message, e)
            throw e
        }
        val newBudget = getAvailableBudget(user)
        log.info("User {} new available budget after credit: {}", user.email, newBudget)
        return newBudget
    }

    override fun getUserDailyUsage(
        user: User, from: LocalDate, to: LocalDate
    ): List<UsageInterface.DailyUsage> {
        require(!to.isBefore(from)) { "'to' must be on or after 'from'" }
        return transaction(database) {
            val grouped = linkedMapOf<Pair<LocalDate, String>, Pair<MutableMap<TokenTypes, Long>, Double>>()
            UsageDailyTable
                .selectAll()
                .where {
                    (UsageDailyTable.userId eq user.email) and
                            (UsageDailyTable.day greaterEq from) and
                            (UsageDailyTable.day less to)
                }
                .orderBy(UsageDailyTable.day to SortOrder.ASC)
                .orderBy(UsageDailyTable.model to SortOrder.ASC)
                .forEach { row ->
                    val day = row[UsageDailyTable.day]
                    val model = row[UsageDailyTable.model]
                    val tokenTypeRaw = row[UsageDailyTable.tokenType]
                    val tokenCount = row[UsageDailyTable.tokenCount]
                    val cost = row[UsageDailyTable.cost]
                    val key = day to model
                    val entry = grouped.getOrPut(key) { mutableMapOf<TokenTypes, Long>() to 0.0 }
                    val parsedType = runCatching { TokenTypes.valueOf(tokenTypeRaw) }.getOrNull()
                    if (parsedType != null && tokenCount != 0L) {
                        entry.first.merge(parsedType, tokenCount) { a, b -> a + b }
                    } else if (parsedType == null) {
                        log.warn(
                            "Unknown token type '{}' in usage_daily for user={} day={} model={}; skipping count",
                            tokenTypeRaw, user.email, day, model
                        )
                    }
                     // Cost is stored on a single token-type row per (user, day, model);
                     // other rows carry 0.0. Summing produces the correct per-day total.
                     grouped[key] = entry.first to (entry.second + cost)
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

    override fun getUserCredits(user: User, from: LocalDate, to: LocalDate): List<UsageInterface.CreditEntry> {
        require(!to.isBefore(from)) { "'to' must be on or after 'from'" }
        val fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant()
        val toInstant = to.atStartOfDay(ZoneOffset.UTC).toInstant()
        return transaction(database) {
            UserCreditsTable
                .selectAll()
                .where {
                    (UserCreditsTable.userId eq user.email) and
                            (UserCreditsTable.datetime greaterEq fromInstant) and
                            (UserCreditsTable.datetime less toInstant)
                }
                .orderBy(UserCreditsTable.datetime to SortOrder.ASC)
                .map { row ->
                    val ts = row[UserCreditsTable.datetime] ?: Instant.EPOCH
                    val amount = row[UserCreditsTable.amount] ?: 0.0
                    val comment = row[UserCreditsTable.comment]?.takeIf { it.isNotEmpty() }
                    val metaRaw = row[UserCreditsTable.metadata]?.takeIf { it.isNotEmpty() }
                    val metadata: Map<String, String>? = metaRaw?.let {
                        try {
                            Gson().fromJson(it, object : TypeToken<Map<String, String>>() {}.type)
                        } catch (e: Throwable) {
                            log.warn(
                                "Failed to JSON-decode credit metadata for user {}: {}",
                                user.email, e.message, e
                            )
                            null
                        }
                    }
                    UsageInterface.CreditEntry(ts, amount, comment, metadata)
                }
        }
    }

    override fun getUserBalance(userId: String): Double {
        if (userId.isEmpty()) return 0.0
        return transaction(database) {
            UserBudgetTable
                .selectAll()
                .where { UserBudgetTable.userId eq userId }
                .limit(1)
                .firstOrNull()
                ?.get(UserBudgetTable.available) ?: 0.0
        }
    }

    /**
     * Recursively collect all descendant session IDs (BFS). Must be invoked inside a transaction.
     */
    private fun collectSessionIds(sessionId: String): Set<String> {
        val visited = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(sessionId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            SessionParentsTable
                .selectAll()
                .where { SessionParentsTable.parentSessionId eq current }
                .forEach { row ->
                    val childId = row[SessionParentsTable.childSessionId]
                    if (childId !in visited) queue.add(childId)
                }
        }
        return visited
    }

    private fun saveUsageValues(
        usageKey: UsageInterface.UsageKey,
        usageValues: UsageInterface.UsageValues,
        ts: Instant,
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
        val promptTotal = tokenSnapshots.getOrDefault(TokenTypes.Prompt, 0L)
        val completionTotal = tokenSnapshots.getOrDefault(TokenTypes.Completion, 0L)
        val insertStatement = UsageTable.insert {
            it[sessionId] = usageKey.session.sessionId
            it[userId] = usageKey.user?.email ?: ""
            it[model] = usageKey.model.modelId
            it[promptTokens] = promptTotal
            it[completionTokens] = completionTotal
            it[cost] = usageValues.cost.get()
            it[datetime] = ts
            it[UsageTable.inputText] = if (inputText != null) truncateForClob(inputText) else null
            it[UsageTable.outputText] = if (outputText != null) truncateForClob(outputText) else null
        }
        val insertedId: Long = insertStatement[UsageTable.id]
        if (tokenSnapshots.isNotEmpty()) {
            UsageTokensTable.batchInsert(tokenSnapshots.entries) { (type, count) ->
                this[UsageTokensTable.usageId] = insertedId
                this[UsageTokensTable.tokenType] = type.name
                this[UsageTokensTable.tokenCount] = count
            }
        }
    }

    private fun upsertDailyUsage(
        usageKey: UsageInterface.UsageKey, usageValues: UsageInterface.UsageValues, day: LocalDate
    ) {
        val userId = usageKey.user?.email ?: ""
         val model = usageKey.model.modelId
        val cost = usageValues.cost.get()
        val tokenSnapshots: Map<TokenTypes, Long> = usageValues.tokenCounts
            .mapValues { it.value.get() }
            .filterValues { it != 0L }
         val entries: Map<TokenTypes, Long> = when {
             tokenSnapshots.isNotEmpty() -> tokenSnapshots
             cost != 0.0 -> mapOf(TokenTypes.Prompt to 0L)
             else -> return // nothing to record
         }
        val firstType = entries.keys.firstOrNull()
        for ((type, count) in entries) {
            val rowCost = if (type == firstType) cost else 0.0
            upsertDailyUsageRow(userId, day, model ?: "", type, count, rowCost)
        }
    }

    private fun upsertDailyUsageRow(
        userId: String,
        day: LocalDate,
        model: String,
        tokenType: TokenTypes,
        tokenCount: Long,
        cost: Double
    ) {
        val updated = UsageDailyTable.update({
            (UsageDailyTable.userId eq userId) and
                    (UsageDailyTable.day eq day) and
                    (UsageDailyTable.model eq model) and
                    (UsageDailyTable.tokenType eq tokenType.name)
        }) {
            it.update(UsageDailyTable.tokenCount, UsageDailyTable.tokenCount + tokenCount)
            it.update(UsageDailyTable.cost, UsageDailyTable.cost + cost)
        }
        if (updated == 0) {
            UsageDailyTable.insert {
                it[UsageDailyTable.userId] = userId
                it[UsageDailyTable.day] = day
                it[UsageDailyTable.model] = model
                it[UsageDailyTable.tokenType] = tokenType.name
                it[UsageDailyTable.tokenCount] = tokenCount
                it[UsageDailyTable.cost] = cost
            }
        }
    }

    private fun applyBudgetDelta(userId: String, delta: Double) {
        val updated = UserBudgetTable.update({ UserBudgetTable.userId eq userId }) {
            it.update(UserBudgetTable.available, UserBudgetTable.available + delta)
        }
        if (updated == 0) {
            UserBudgetTable.insert {
                it[UserBudgetTable.userId] = userId
                it[available] = delta
            }
        }
    }

    /**
     * Truncate text for CLOB/TEXT storage to prevent runaway memory/storage usage.
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
            log.warn("Failed to JSON-encode credit metadata (keys={}); falling back to empty", metadata.keys, e)
            ""
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UsageDB::class.java)

        var cost_scaling_factor: Double = 1.0
            set(value) {
                require(value >= 0.0) { "Cost scaling factor must be non-negative" }
                log.warn(
                    "Setting cost scaling factor to {}. This will affect all future cost calculations. Current value: {}",
                    value,
                    field
                )
                field = value
            }

        /** Maximum number of characters to persist into input_text/output_text columns. */
        var max_clob_chars: Int = 1_048_576
            set(value) {
                require(value >= 0) { "max_clob_chars must be non-negative" }
                field = value
            }

        /**
         * Schema DDL maintained alongside the Exposed table definitions.
         *
         * We intentionally retain the raw DDL approach (rather than relying solely on
         * Exposed's SchemaUtils.createMissingTablesAndColumns) because:
         *  - It preserves existing migration statements (ALTER TABLE ... ADD COLUMN IF NOT EXISTS).
         *  - It allows fine-grained control over provider-specific types (CLOB vs TEXT).
         *  - It avoids surprises from Exposed re-shaping legacy schemas.
         *
         * The Exposed table objects (UsageTable, UsageTokensTable, ...) mirror this
         * schema and are used for all DSL operations.
         */
        internal val facet = DatabaseFacet(
            name = "usage",
            schema = { provider ->
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
                        "day" DATE,
                        model VARCHAR(255),
                         token_type VARCHAR(64) DEFAULT 'Prompt',
                         token_count BIGINT DEFAULT 0,
                        cost DOUBLE PRECISION DEFAULT 0,
                         PRIMARY KEY (user_id, "day", model, token_type)
                    )
                    """,
                    "CREATE INDEX IF NOT EXISTS idx_usage_daily_user_day ON usage_daily(user_id, \"day\")",
                    "ALTER TABLE usage_daily ADD COLUMN IF NOT EXISTS token_type VARCHAR(64) DEFAULT 'Prompt'",
                    "ALTER TABLE usage_daily ADD COLUMN IF NOT EXISTS token_count BIGINT DEFAULT 0",
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
            },
            tables = listOf(
                UsageTable,
                UsageTokensTable,
                SessionParentsTable,
                UsageDailyTable,
                UserCreditsTable,
                UserBudgetTable,
            ),
        )

        val tracked_users = mutableSetOf(
            "acharneski@gmail.com",
        )
    }
}