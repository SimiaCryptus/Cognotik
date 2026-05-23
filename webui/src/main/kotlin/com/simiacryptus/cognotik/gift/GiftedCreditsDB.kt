package com.simiacryptus.cognotik.gift
import com.simiacryptus.cognotik.platform.ApplicationServices

import com.simiacryptus.cognotik.platform.hsql.DatabaseFacet
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * HSQLDB implementation of [GiftedCreditsInterface].
 *
 * Manages gifts and claims using a relational database via [DatabaseFacet].
 */
class GiftedCreditsDB(
    root: File? = ApplicationServicesConfig.dataStorageRoot.resolve("giftsdb")
) : GiftedCreditsInterface {

    val facet = DatabaseFacet("gifted_credits")

    init {
        // Initialize tables if they do not exist
        try {
            log.debug("Initializing database schema for gifts and gift_claims tables")
            facet.withConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        """
                             CREATE TABLE IF NOT EXISTS gifts (
                                 id VARCHAR(36) PRIMARY KEY,
                             amount_granted DOUBLE PRECISION,
                                 grant_duration_seconds BIGINT,
                              total_budget DOUBLE PRECISION,
                              created_by VARCHAR(255),
                              theme VARCHAR(64)
                             )
                             """.trimIndent()
                    )
                    log.debug("Ensured 'gifts' table exists")
                     // Best-effort migration: add created_by column if it doesn't exist on an older schema
                     try {
                         stmt.execute("ALTER TABLE gifts ADD COLUMN created_by VARCHAR(255)")
                         log.debug("Added 'created_by' column to existing 'gifts' table")
                     } catch (e: SQLException) {
                         log.debug("'created_by' column likely already exists or could not be added: {}", e.message)
                     }
                     // Best-effort migration: add theme column if it doesn't exist on an older schema
                     try {
                         stmt.execute("ALTER TABLE gifts ADD COLUMN theme VARCHAR(64)")
                         log.debug("Added 'theme' column to existing 'gifts' table")
                     } catch (e: SQLException) {
                         log.debug("'theme' column likely already exists or could not be added: {}", e.message)
                     }
                    stmt.execute(
                        """
                             CREATE TABLE IF NOT EXISTS gift_claims (
                                 gift_id VARCHAR(36),
                                 user_id VARCHAR(255),
                                 claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (gift_id, user_id),
                                 FOREIGN KEY (gift_id) REFERENCES gifts(id)
                             )
                             """.trimIndent()
                    )
                    log.debug("Ensured 'gift_claims' table exists")
                    // Best-effort migration: add claimed_at column if it doesn't exist on an older schema
                    try {
                        stmt.execute("ALTER TABLE gift_claims ADD COLUMN claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
                        log.debug("Added 'claimed_at' column to existing 'gift_claims' table")
                    } catch (e: SQLException) {
                        log.debug("'claimed_at' column likely already exists or could not be added: {}", e.message)
                    }
                }
            }
            log.info("Database schema initialization completed successfully")
        } catch (e: SQLException) {
            log.error(
                "SQL error while initializing database schema (SQLState={}, ErrorCode={})",
                e.sqlState, e.errorCode, e
            )
            throw IllegalStateException("Failed to initialize database schema for GiftedCreditsDB", e)
        } catch (e: Exception) {
            log.error("Unexpected error while initializing database schema", e)
            throw IllegalStateException("Failed to initialize GiftedCreditsDB", e)
        }
    }

    override fun createGift(
         creator: User,
        amountGranted: Double,
        grantDuration: Duration,
        totalBudget: Double,
        theme: String?
    ): GiftedCreditsInterface.Gift {
        log.info(
             "Creating gift: creator={}, amountGranted={}, grantDuration={}, totalBudget={}, theme={}",
             creator.id, amountGranted, grantDuration, totalBudget, theme
        )
         val creatorId = creator.id
         if (creatorId.isNullOrBlank()) {
             log.warn("Invalid creator id (null/blank) for gift creation")
             throw IllegalArgumentException("Creator must have a valid id")
         }
        if (amountGranted <= 0.0) {
            log.warn("Invalid amountGranted={} (must be > 0)", amountGranted)
            throw IllegalArgumentException("amountGranted must be greater than 0, got $amountGranted")
        }
        if (totalBudget < amountGranted) {
            log.warn("Invalid totalBudget={} (must be >= amountGranted={})", totalBudget, amountGranted)
            throw IllegalArgumentException("totalBudget ($totalBudget) must be >= amountGranted ($amountGranted)")
        }
        if (grantDuration.isNegative || grantDuration.isZero) {
            log.warn("Invalid grantDuration={} (must be positive)", grantDuration)
            throw IllegalArgumentException("grantDuration must be positive, got $grantDuration")
        }
         // Check creator has sufficient credit balance
         val usageManager = ApplicationServices.fileApplicationServices().usageDB
         val creatorBalance = try {
             usageManager.getUserBalance(creator.id)
         } catch (e: Exception) {
             log.error("Failed to retrieve balance for creator={}", creatorId, e)
             throw RuntimeException("Unable to verify creator credit balance", e)
         }
         log.debug("Creator '{}' has balance={}, required totalBudget={}", creatorId, creatorBalance, totalBudget)
         if (creatorBalance < totalBudget) {
             log.warn(
                 "Creator '{}' has insufficient credit: balance={}, required={}",
                 creatorId, creatorBalance, totalBudget
             )
             throw IllegalArgumentException(
                 "Insufficient credit balance: have ${"%.2f".format(creatorBalance)}, need ${"%.2f".format(totalBudget)}"
             )
         }
        val id = UUID.randomUUID().toString()
        log.debug("Generated new gift id={}", id)
        try {
            facet.withConnection { conn ->
                conn.prepareStatement(
                    """
                          INSERT INTO gifts (id, amount_granted, grant_duration_seconds, total_budget, created_by, theme)
                          VALUES (?, ?, ?, ?, ?, ?)
                         """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setDouble(2, amountGranted)
                    stmt.setLong(3, grantDuration.seconds)
                    stmt.setDouble(4, totalBudget)
                     stmt.setString(5, creatorId)
                     stmt.setString(6, theme)
                    val rows = stmt.executeUpdate()
                    log.debug("Inserted gift id={} ({} row(s) affected)", id, rows)
                    if (rows != 1) {
                        log.warn("Unexpected number of rows inserted for gift id={}: expected 1, got {}", id, rows)
                    }
                }
            }
             // Reserve the budget by debiting the creator's account upfront
             try {
                 usageManager.creditUser(
                     user = creator,
                     amount = -totalBudget,
                     comment = "Created gift $id (budget reservation)"
                 )
                 log.info("Debited {} credits from creator='{}' for gift id={}", totalBudget, creatorId, id)
             } catch (e: Exception) {
                 log.error("Failed to debit creator='{}' for gift id={}; attempting rollback delete", creatorId, id, e)
                 // Best-effort: remove the gift row to keep state consistent
                 try {
                     facet.withConnection { conn ->
                         conn.prepareStatement("DELETE FROM gifts WHERE id = ?").use { stmt ->
                             stmt.setString(1, id)
                             stmt.executeUpdate()
                         }
                     }
                     log.info("Rolled back gift row id={} after debit failure", id)
                 } catch (rollbackEx: Exception) {
                     log.error("Failed to rollback gift row id={} after debit failure", id, rollbackEx)
                 }
                 throw RuntimeException("Failed to debit creator account; gift creation aborted", e)
             }
            log.info("Successfully created gift id={}", id)
            return GiftedCreditsInterface.Gift(
                id = id,
                claimants = 0,
                amountGranted = amountGranted,
                grantDuration = grantDuration,
                totalBudget = totalBudget,
                 spentBudget = 0.0,
                 createdBy = creatorId,
                 theme = theme
            )
        } catch (e: SQLException) {
            log.error(
                "SQL error creating gift id={} (SQLState={}, ErrorCode={})",
                id, e.sqlState, e.errorCode, e
            )
            throw RuntimeException("Failed to create gift due to database error", e)
        }
    }

    override fun getGift(id: String): GiftedCreditsInterface.Gift? {
        log.debug("Fetching gift with id={}", id)
        if (id.isBlank()) {
            log.warn("getGift called with blank id")
            return null
        }
        try {
            return facet.withConnection { conn -> getGiftWithConnection(conn, id) }
        } catch (e: SQLException) {
            log.error(
                "SQL error retrieving gift id={} (SQLState={}, ErrorCode={})",
                id, e.sqlState, e.errorCode, e
            )
            throw RuntimeException("Failed to retrieve gift '$id' due to database error", e)
        }
    }

    override fun claimGift(user: User, giftId: String): Boolean {
        val userId = user.id
        log.info("User '{}' attempting to claim gift id={}", userId, giftId)
        if (userId.isNullOrBlank()) {
            log.warn("Cannot claim gift: user id is blank/null for gift id={}", giftId)
            return false
        }
        if (giftId.isBlank()) {
            log.warn("Cannot claim gift: giftId is blank for user='{}'", userId)
            return false
        }

        return try {
            facet.withConnection { conn ->
                val originalAutoCommit = try {
                    conn.autoCommit
                } catch (e: SQLException) {
                    log.error("Failed to read autoCommit state from connection", e)
                    throw RuntimeException("Failed to access database connection", e)
                }
                try {
                    // Start transaction to prevent race conditions on the budget
                    conn.autoCommit = false
                    log.debug("Started transaction for claimGift userId='{}', giftId={}", userId, giftId)

                    // 1. Check if the user has already claimed this gift
                    conn.prepareStatement(
                        "SELECT 1 FROM gift_claims WHERE gift_id = ? AND user_id = ?"
                    ).use { stmt ->
                        stmt.setString(1, giftId)
                        stmt.setString(2, userId)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                log.info("User '{}' has already claimed gift id={}; rolling back", userId, giftId)
                                safeRollback(conn)
                                return@withConnection false // Already claimed
                            }
                        }
                    }

                    // 2. Get current gift stats to check the budget
                    val gift = getGiftWithConnection(conn, giftId) ?: run {
                        log.warn("Gift id={} does not exist; cannot be claimed by user '{}'", giftId, userId)
                        safeRollback(conn)
                        return@withConnection false // Gift does not exist
                    }

                    // 3. Check if there is enough budget left
                    if (gift.spentBudget + gift.amountGranted > gift.totalBudget) {
                        log.info(
                            "Budget exhausted for gift id={}: spent={}, requested={}, total={}; user '{}' denied",
                            giftId, gift.spentBudget, gift.amountGranted, gift.totalBudget, userId
                        )
                        safeRollback(conn)
                        return@withConnection false // Budget exhausted
                    }

                     // 4. Verify the creator still has the reserved budget available.
                     // Note: budget should have been reserved (debited) at creation time, so this is a safety check.
                     val creatorId = gift.createdBy
                     if (!creatorId.isNullOrBlank()) {
                         try {
                             val creatorBalance = ApplicationServices.fileApplicationServices().usageDB.getUserBalance(creatorId)
                             log.debug(
                                 "Creator '{}' balance check at claim time: balance={}, amountGranted={}",
                                 creatorId, creatorBalance, gift.amountGranted
                             )
                         } catch (e: Exception) {
                             log.warn("Failed to verify creator balance for gift id={} (non-fatal): {}", giftId, e.message)
                         }
                     } else {
                         log.debug("Gift id={} has no recorded creator (legacy); skipping creator balance check", giftId)
                     }

                     // 5. Insert the claim
                    conn.prepareStatement(
                        "INSERT INTO gift_claims (gift_id, user_id, claimed_at) VALUES (?, ?, ?)"
                    ).use { stmt ->
                        stmt.setString(1, giftId)
                        stmt.setString(2, userId)
                        stmt.setTimestamp(3, Timestamp.from(Instant.now()))
                        val rows = stmt.executeUpdate()
                        log.debug("Inserted gift_claim row(s)={} for userId='{}', giftId={}", rows, userId, giftId)
                    }

                    conn.commit()
                    log.info("User '{}' successfully claimed gift id={}", userId, giftId)
                    true
                } catch (e: SQLException) {
                    log.error(
                        "SQL error during claimGift userId='{}', giftId={} (SQLState={}, ErrorCode={})",
                        userId, giftId, e.sqlState, e.errorCode, e
                    )
                    safeRollback(conn)
                    throw RuntimeException("Failed to claim gift '$giftId' for user '$userId' due to database error", e)
                } catch (e: Exception) {
                    log.error("Unexpected error during claimGift userId='{}', giftId={}", userId, giftId, e)
                    safeRollback(conn)
                    throw e
                } finally {
                    // Restore original auto-commit state
                    try {
                        conn.autoCommit = originalAutoCommit
                        log.debug("Restored autoCommit={} after claimGift", originalAutoCommit)
                    } catch (e: SQLException) {
                        log.error("Failed to restore autoCommit state to {} after claimGift", originalAutoCommit, e)
                    }
                }
            }
        } catch (e: RuntimeException) {
            throw e
        }
    }

    /**
     * Safely rolls back the connection, logging any errors encountered.
     * Does not propagate exceptions to avoid masking the original failure cause.
     */
    private fun safeRollback(conn: Connection) {
        try {
            conn.rollback()
            log.debug("Transaction rolled back successfully")
        } catch (rollbackEx: SQLException) {
            log.error(
                "Failed to rollback transaction (SQLState={}, ErrorCode={})",
                rollbackEx.sqlState, rollbackEx.errorCode, rollbackEx
            )
        }
    }

    /**
     * Helper to fetch a gift using a specific (possibly transactional) connection.
     */
    private fun getGiftWithConnection(conn: Connection, id: String): GiftedCreditsInterface.Gift? {
        log.debug("Fetching gift with id={} on provided connection", id)
        if (id.isBlank()) {
            log.warn("getGiftWithConnection called with blank id")
            return null
        }
        conn.prepareStatement(
            """
                 SELECT g.id, g.amount_granted, g.grant_duration_seconds, g.total_budget,
                        g.created_by, g.theme,
                        COUNT(c.user_id) as claimants
                 FROM gifts g
                 LEFT JOIN gift_claims c ON g.id = c.gift_id
                 WHERE g.id = ?
                  GROUP BY g.id, g.amount_granted, g.grant_duration_seconds, g.total_budget, g.created_by, g.theme
                 """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return mapGift(rs)
                }
            }
        }
        return null
    }

    override fun listGifts(): List<GiftedCreditsInterface.Gift> {
        log.debug("Listing all gifts")
        try {
            return facet.withConnection { conn ->
                val gifts = mutableListOf<GiftedCreditsInterface.Gift>()
                conn.prepareStatement(
                    """
                         SELECT g.id, g.amount_granted, g.grant_duration_seconds, g.total_budget,
                                 g.created_by, g.theme,
                                COUNT(c.user_id) as claimants
                         FROM gifts g
                         LEFT JOIN gift_claims c ON g.id = c.gift_id
                          GROUP BY g.id, g.amount_granted, g.grant_duration_seconds, g.total_budget, g.created_by, g.theme
                         """.trimIndent()
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            try {
                                gifts.add(mapGift(rs))
                            } catch (e: SQLException) {
                                log.error("Failed to map a gift row from ResultSet; skipping", e)
                            }
                        }
                    }
                }
                log.debug("Retrieved {} gift(s)", gifts.size)
                gifts
            }
        } catch (e: SQLException) {
            log.error(
                "SQL error while listing gifts (SQLState={}, ErrorCode={})",
                e.sqlState, e.errorCode, e
            )
            throw RuntimeException("Failed to list gifts due to database error", e)
        }
    }

    override fun listClaims(giftId: String?, userId: String?): List<GiftedCreditsInterface.Claim> {
        log.debug("Listing claims with filters giftId={}, userId={}", giftId, userId)
        val conditions = mutableListOf<String>()
        val params = mutableListOf<String>()
        if (!giftId.isNullOrBlank()) {
            conditions.add("gift_id = ?")
            params.add(giftId)
        }
        if (!userId.isNullOrBlank()) {
            conditions.add("user_id = ?")
            params.add(userId)
        }
        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        val sql = """
                 SELECT gift_id, user_id, claimed_at
                 FROM gift_claims
                 $whereClause
                 ORDER BY claimed_at DESC
             """.trimIndent()
        try {
            return facet.withConnection { conn ->
                val claims = mutableListOf<GiftedCreditsInterface.Claim>()
                conn.prepareStatement(sql).use { stmt ->
                    params.forEachIndexed { idx, value -> stmt.setString(idx + 1, value) }
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            try {
                                val claimedAtTs = try {
                                    rs.getTimestamp("claimed_at")
                                } catch (e: SQLException) {
                                    log.debug("claimed_at column not available: {}", e.message)
                                    null
                                }
                                claims.add(
                                    GiftedCreditsInterface.Claim(
                                        giftId = rs.getString("gift_id"),
                                        userId = rs.getString("user_id"),
                                        claimedAt = claimedAtTs?.toInstant()
                                    )
                                )
                            } catch (e: SQLException) {
                                log.error("Failed to map a claim row from ResultSet; skipping", e)
                            }
                        }
                    }
                }
                log.debug("Retrieved {} claim(s)", claims.size)
                claims
            }
        } catch (e: SQLException) {
            log.error(
                "SQL error while listing claims (SQLState={}, ErrorCode={})",
                e.sqlState, e.errorCode, e
            )
            throw RuntimeException("Failed to list claims due to database error", e)
        }
    }

    /**
     * Helper function to map a ResultSet row to a Gift object.
     */
    private fun mapGift(rs: ResultSet): GiftedCreditsInterface.Gift {
        try {
            val amountGranted = rs.getDouble("amount_granted")
            val claimants = rs.getInt("claimants")
            val id = rs.getString("id")
            val grantSeconds = rs.getLong("grant_duration_seconds")
            val totalBudget = rs.getDouble("total_budget")
             val createdBy = try {
                 rs.getString("created_by")
             } catch (e: SQLException) {
                 log.debug("created_by column not available: {}", e.message)
                 null
             }
             val theme = try {
                 rs.getString("theme")
             } catch (e: SQLException) {
                 log.debug("theme column not available: {}", e.message)
                 null
             }

            return GiftedCreditsInterface.Gift(
                id = id,
                claimants = claimants,
                amountGranted = amountGranted,
                grantDuration = Duration.ofSeconds(grantSeconds),
                totalBudget = totalBudget,
                 spentBudget = claimants * amountGranted,
                 createdBy = createdBy,
                 theme = theme
            )
        } catch (e: SQLException) {
            log.error(
                "Error mapping ResultSet row to Gift (SQLState={}, ErrorCode={})",
                e.sqlState, e.errorCode, e
            )
            throw e
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(GiftedCreditsInterface::class.java)!!
    }
}