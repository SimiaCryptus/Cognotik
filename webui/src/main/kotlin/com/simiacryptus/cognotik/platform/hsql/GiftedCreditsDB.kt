package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.GiftedCreditsInterface
import com.simiacryptus.cognotik.platform.model.User
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Exposed-based implementation of [GiftedCreditsInterface].
 *
 * Manages gifts and claims using a relational database via [DatabaseFacet] and Exposed DSL.
 */
class GiftedCreditsDB(
    root: File? = ApplicationServicesConfig.dataStorageRoot.resolve("giftsdb")
) : GiftedCreditsInterface {

    /**
     * Exposed table definition for gifts.
     */
    object GiftsTable : Table("gifts") {
        val id = varchar("id", 36)
        val amountGranted = double("amount_granted")
        val grantDurationSeconds = long("grant_duration_seconds")
        val totalBudget = double("total_budget")
        val createdBy = varchar("created_by", 255).nullable()
        val theme = varchar("theme", 64).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Exposed table definition for gift claims.
     */
    object GiftClaimsTable : Table("gift_claims") {
        val giftId = varchar("gift_id", 36).references(GiftsTable.id)
        val userId = varchar("user_id", 255)
        val claimedAt = timestamp("claimed_at").clientDefault { Instant.now() }
        override val primaryKey = PrimaryKey(giftId, userId)
    }

    val facet = DatabaseFacet(
        name = "gifted_credits",
        tables = listOf(GiftsTable, GiftClaimsTable)
    )

    init {
        // Trigger lazy database initialization and schema creation.
        try {
            log.debug("Initializing Exposed schema for GiftedCreditsDB")
            transaction(facet.database) {
                // Touch the tables to ensure schema is created via the facet.
                GiftsTable.selectAll().limit(1).toList()
                GiftClaimsTable.selectAll().limit(1).toList()
            }
            log.info("GiftedCreditsDB schema initialization completed successfully")
        } catch (e: Exception) {
            log.error("Failed to initialize GiftedCreditsDB schema", e)
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
            transaction(facet.database) {
                GiftsTable.insert {
                    it[GiftsTable.id] = id
                   it[GiftsTable.amountGranted] = amountGranted
                   it[GiftsTable.grantDurationSeconds] = grantDuration.seconds
                   it[GiftsTable.totalBudget] = totalBudget
                   it[GiftsTable.createdBy] = creatorId
                   it[GiftsTable.theme] = theme
                }
                log.debug("Inserted gift id={}", id)
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
                try {
                    transaction(facet.database) {
                        GiftsTable.deleteWhere { GiftsTable.id eq id }
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
        } catch (e: Exception) {
            log.error("Error creating gift id={}", id, e)
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
            return transaction(facet.database) {
                fetchGiftById(id)
            }
        } catch (e: Exception) {
            log.error("Error retrieving gift id={}", id, e)
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
            transaction(facet.database) {
                // 1. Check if the user has already claimed this gift
                val alreadyClaimed = GiftClaimsTable
                    .selectAll()
                    .where { (GiftClaimsTable.giftId eq giftId) and (GiftClaimsTable.userId eq userId) }
                    .limit(1)
                    .any()
                if (alreadyClaimed) {
                    log.info("User '{}' has already claimed gift id={}", userId, giftId)
                    return@transaction false
                }

                // 2. Get current gift stats to check the budget
                val gift = fetchGiftById(giftId) ?: run {
                    log.warn("Gift id={} does not exist; cannot be claimed by user '{}'", giftId, userId)
                    return@transaction false
                }

                // 3. Check if there is enough budget left
                if (gift.spentBudget + gift.amountGranted > gift.totalBudget) {
                    log.info(
                        "Budget exhausted for gift id={}: spent={}, requested={}, total={}; user '{}' denied",
                        giftId, gift.spentBudget, gift.amountGranted, gift.totalBudget, userId
                    )
                    return@transaction false
                }

                // 4. Verify the creator's balance (safety check; non-fatal)
                val creatorId = gift.createdBy
                if (!creatorId.isNullOrBlank()) {
                    try {
                        val creatorBalance =
                            ApplicationServices.fileApplicationServices().usageDB.getUserBalance(creatorId)
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
                GiftClaimsTable.insert {
                    it[GiftClaimsTable.giftId] = giftId
                    it[GiftClaimsTable.userId] = userId
                    it[claimedAt] = Instant.now()
                }
                log.info("User '{}' successfully claimed gift id={}", userId, giftId)
                true
            }
        } catch (e: Exception) {
            log.error("Error during claimGift userId='{}', giftId={}", userId, giftId, e)
            throw RuntimeException("Failed to claim gift '$giftId' for user '$userId' due to database error", e)
        }
    }

    /**
     * Helper to fetch a gift inside an active Exposed transaction.
     */
    private fun fetchGiftById(id: String): GiftedCreditsInterface.Gift? {
        val claimCountExpr = GiftClaimsTable.userId.count()
        val row = GiftsTable
            .leftJoin(GiftClaimsTable, { GiftsTable.id }, { GiftClaimsTable.giftId })
            .select(
                GiftsTable.id,
                GiftsTable.amountGranted,
                GiftsTable.grantDurationSeconds,
                GiftsTable.totalBudget,
                GiftsTable.createdBy,
                GiftsTable.theme,
                claimCountExpr
            )
            .where { GiftsTable.id eq id }
            .groupBy(
                GiftsTable.id,
                GiftsTable.amountGranted,
                GiftsTable.grantDurationSeconds,
                GiftsTable.totalBudget,
                GiftsTable.createdBy,
                GiftsTable.theme
            )
            .firstOrNull() ?: return null
        return mapGift(row, claimCountExpr)
    }

    override fun listGifts(): List<GiftedCreditsInterface.Gift> {
        log.debug("Listing all gifts")
        try {
            return transaction(facet.database) {
                val claimCountExpr = GiftClaimsTable.userId.count()
                val rows = GiftsTable
                    .leftJoin(GiftClaimsTable, { GiftsTable.id }, { GiftClaimsTable.giftId })
                    .select(
                        GiftsTable.id,
                        GiftsTable.amountGranted,
                        GiftsTable.grantDurationSeconds,
                        GiftsTable.totalBudget,
                        GiftsTable.createdBy,
                        GiftsTable.theme,
                        claimCountExpr
                    )
                    .groupBy(
                        GiftsTable.id,
                        GiftsTable.amountGranted,
                        GiftsTable.grantDurationSeconds,
                        GiftsTable.totalBudget,
                        GiftsTable.createdBy,
                        GiftsTable.theme
                    )
                    .toList()
                val gifts = rows.mapNotNull { row ->
                    try {
                        mapGift(row, claimCountExpr)
                    } catch (e: Exception) {
                        log.error("Failed to map a gift row; skipping", e)
                        null
                    }
                }
                log.debug("Retrieved {} gift(s)", gifts.size)
                gifts
            }
        } catch (e: Exception) {
            log.error("Error while listing gifts", e)
            throw RuntimeException("Failed to list gifts due to database error", e)
        }
    }

    override fun listClaims(giftId: String?, userId: String?): List<GiftedCreditsInterface.Claim> {
        log.debug("Listing claims with filters giftId={}, userId={}", giftId, userId)
        try {
            return transaction(facet.database) {
                val query = GiftClaimsTable.selectAll()
                if (!giftId.isNullOrBlank()) {
                    query.andWhere { GiftClaimsTable.giftId eq giftId }
                }
                if (!userId.isNullOrBlank()) {
                    query.andWhere { GiftClaimsTable.userId eq userId }
                }
                query.orderBy(GiftClaimsTable.claimedAt, SortOrder.DESC)
                val claims = query.mapNotNull { row ->
                    try {
                        GiftedCreditsInterface.Claim(
                            giftId = row[GiftClaimsTable.giftId],
                            userId = row[GiftClaimsTable.userId],
                            claimedAt = row[GiftClaimsTable.claimedAt]
                        )
                    } catch (e: Exception) {
                        log.error("Failed to map a claim row; skipping", e)
                        null
                    }
                }
                log.debug("Retrieved {} claim(s)", claims.size)
                claims
            }
        } catch (e: Exception) {
            log.error("Error while listing claims", e)
            throw RuntimeException("Failed to list claims due to database error", e)
        }
    }

    /**
     * Helper function to map a ResultRow to a Gift object.
     */
    private fun mapGift(
        row: ResultRow,
        claimCountExpr: org.jetbrains.exposed.v1.core.Expression<Long>
    ): GiftedCreditsInterface.Gift {
        val id = row[GiftsTable.id]
        val amountGranted = row[GiftsTable.amountGranted]
        val grantSeconds = row[GiftsTable.grantDurationSeconds]
        val totalBudget = row[GiftsTable.totalBudget]
        val createdBy = row[GiftsTable.createdBy]
        val theme = row[GiftsTable.theme]
        val claimants = row[claimCountExpr].toInt()

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
    }

    companion object {
        val log = LoggerFactory.getLogger(GiftedCreditsInterface::class.java)!!
    }
}