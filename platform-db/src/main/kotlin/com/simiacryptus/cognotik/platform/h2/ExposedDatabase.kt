package com.simiacryptus.cognotik.platform.h2

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges Exposed's [Database] API with the existing [DatabaseFacet] connection management.
 *
  * Exposed's `transaction { ... }` block obtains a [java.sql.Connection] from the [Database]
  * via the connection provider lambda we pass to `Database.connect`. We delegate to
  * [DatabaseFacet.borrowConnection], which hands out a connection used by exactly one
  * transaction at a time. Exposed's `close()` returns it to a small per-facet pool.
 *
  * Concurrency note: connections must NOT be shared between concurrent Exposed
  * transactions. Exposed sets autoCommit, isolation level and read-only on each
  * transaction's connection. PostgreSQL rejects the latter two while another
  * transaction is active on the same connection ("Cannot change transaction
  * isolation level in the middle of a transaction").
 */
object ExposedDatabase {
  private val log = LoggerFactory.getLogger(ExposedDatabase::class.java)
  private val databases = ConcurrentHashMap<DatabaseFacet, Database>()

  fun get(facet: DatabaseFacet): Database {
    return databases.getOrPut(facet) {
      log.info("Initializing Exposed Database for facet '{}'", facet)
      val db = Database.connect(
        getNewConnection = {
           // A dedicated (pooled) connection per transaction. The URL is
           // re-resolved on every borrow, so memory demotion / server
           // restarts are picked up automatically.
          try {
             facet.borrowConnection()
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

}