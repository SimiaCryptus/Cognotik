package com.simiacryptus.cognotik.platform.hsql

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges Exposed's [Database] API with the existing [DatabaseFacet] connection management.
 *
 * Exposed's `transaction { ... }` block obtains a [java.sql.Connection] from the [Database]
 * via the connection provider lambda we pass to `Database.connect`. We delegate to
 * [DatabaseFacet.getConnection], which returns the JVM-wide shared connection for that
 * facet. The connection is intentionally NOT closed by Exposed (we use a non-closing
 * wrapper) since the facet caches and reuses it across all callers.
 *
 * Concurrency note: the shared facet connection is reused across all Exposed
 * transactions for that facet. Exposed serializes statements within a single
 * transaction, but multiple concurrent `transaction { ... }` calls would race
 * on the shared connection. Callers that perform multi-statement work outside
 * of Exposed should prefer [DatabaseFacet.transaction] / [DatabaseFacet.withConnection],
 * which acquire `synchronized(conn)` locks. Exposed's own framework manages
 * autoCommit / commit / rollback on the connection it receives, so we rely on
 * the underlying JDBC driver's per-statement thread safety for concurrent
 * Exposed transactions on the same connection.
 */
object ExposedDatabase {
    private val log = LoggerFactory.getLogger(ExposedDatabase::class.java)
    private val databases = ConcurrentHashMap<DatabaseFacet, Database>()

    fun get(facet: DatabaseFacet): Database {
        return databases.getOrPut(facet) {
            log.info("Initializing Exposed Database for facet '{}'", facet)
            val db = Database.connect(
                getNewConnection = {
                    // Force schema initialization and return a non-closing wrapper
                    // around the shared facet connection. Exposed will manage
                    // autoCommit/commit/rollback against this connection.
                    try {
                        val raw = facet.getConnection()
                        NonClosingConnection(raw)
                    } catch (e: Exception) {
                        log.info("Failed to obtain JDBC connection for Exposed facet '{}': {}", facet, e.message, e)
                        throw e
                    }
                }
            )
            // Eagerly ensure schema is initialized for this Database. The
            // facet.getConnection() call inside the lambda runs the raw DDL
            // strings, but if any of them failed silently we still want
            // Exposed's SchemaUtils.create() to run as a backstop so that
            // table definitions match what the DSL queries expect.
            try {
                // Trigger the lambda once so facet.getConnection() runs and
                // executes the raw DDL via ensureSchema().
                facet.getConnection()
                if (facet.tables.isNotEmpty()) {
                    transaction(db) {
                        SchemaUtils.create(tables = facet.tables.toTypedArray())
                    }
                }
            } catch (e: Exception) {
                log.info("Failed to initialize Exposed schema for facet '{}': {}", facet, e.message, e)
            }
            db
        }
    }

    /**
     * Wraps a [Connection] so that Exposed's automatic close on transaction completion
     * does not actually close the shared underlying connection.
     */
    private class NonClosingConnection(
        private val delegate: Connection
    ) : Connection by delegate {
        override fun close() {
            // Intentionally a no-op; the connection is owned by DatabaseFacet.
        }
    }
}
