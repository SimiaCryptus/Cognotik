package com.simiacryptus.cognotik.platform.hsql

import org.hsqldb.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Common HSQL plumbing shared by all HSQL-backed storage facets.
 *
 * Each "facet" (metadata, usage, user settings, ...) is a logically separate
 * database identified by [dbName]. A facet either:
 *   - connects to an external HSQL server (when [serviceUrl] is non-null), or
 *   - lazily starts an embedded HSQL [org.hsqldb.server.Server] bound to [serverHost]:[serverPort].
 *
 * Responsibilities centralized here:
 *   - JDBC driver loading (once per JVM)
 *   - Embedded server lifecycle (one [org.hsqldb.server.Server] per facet)
 *   - Connection caching keyed by JDBC URL (one shared Connection per URL;
 *     callers must synchronize externally if they need transactional isolation
 *     across multiple statements -- see [withTransaction]).
 *   - Schema initialization deduplication per JDBC URL
 */
class HSQLFacet(
    private val name: String,
    private val schemaSql: List<String>,
) {


    fun getLocalServiceUrl(root: File): String {
        val server = ensureServerStarted(root)
        return "jdbc:hsqldb:hsql://${serverHost}:${server.port}/${dbName}"
    }

    fun getConnection(root: File? = null): Connection {
        ensureDriverLoaded()
        val url: String
        val username: String
        val password: String
        val remoteUrl = serviceUrl
        if (remoteUrl != null) {
            log.info("Connecting to external HSQL $name service at: {}", remoteUrl)
            url = remoteUrl
            username = serviceUser
            password = servicePassword
        } else {
            val server = ensureServerStarted(root)
            url = "jdbc:hsqldb:hsql://${serverHost}:${server.port}/${dbName}"
            username = "SA"
            password = ""
        }
        val existing = connections[url]
        if (existing != null && !existing.isClosed) return existing
        return synchronized(connections) {
            val again = connections[url]
            if (again != null && !again.isClosed) {
                again
            } else {
                log.info("Opening HSQL $name connection to: {}", url)
                val conn = DriverManager.getConnection(url, username, password)
                ensureSchema(url, conn)
                connections[url] = conn
                conn
            }
        }
    }

    /**
     * Acquire an exclusive lock on the underlying connection while [block] runs.
     * Use this for multi-statement transactions on the shared connection so that
     * autoCommit toggling does not race with other threads.
     */
    fun <T> withTransaction(root: File? = null, block: (Connection) -> T): T {
        val conn = getConnection(root)
        synchronized(conn) {
            val prevAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                val result = block(conn)
                conn.commit()
                return result
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (rb: Exception) {
                    log.warn("Rollback failed for $name", rb)
                }
                throw e
            } finally {
                try {
                    conn.autoCommit = prevAutoCommit
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Acquire a lock on the shared connection for the duration of [block].
     * Use for single-statement operations that still need to be serialized
     * against [withTransaction] callers.
     */
    fun <T> withConnection(root: File? = null, block: (Connection) -> T): T {
        val conn = getConnection(root)
        return synchronized(conn) { block(conn) }
    }


    private fun ensureSchema(url: String, connection: Connection) {
        if (schemasInitialized.contains(url)) return
        synchronized(schemasInitialized) {
            if (schemasInitialized.contains(url)) return
            log.debug("Creating $name database schema if not exists for {}", url)
            connection.createStatement().use { stmt ->
                for (ddl in schemaSql) {
                    stmt.executeUpdate(ddl)
                }
            }
            schemasInitialized.add(url)
        }
    }

    private fun ensureServerStarted(root: File?): Server {
        embeddedServer?.let { return it }
        synchronized(serverLock) {
            embeddedServer?.let { return it }
            val server = Server()
            server.setSilent(serverSilent)
            if (serverSilent) {
                server.setLogWriter(null)
                server.setErrWriter(null)
            }
            server.setAddress(serverHost)
            server.port = serverPort
            if (null == root) {
                server.setDatabaseName(0, dbName)
                server.setDatabasePath(0, "mem:$dbName")
            } else {
                server.setDatabaseName(0, dbName)
                server.setDatabasePath(0, "file:${File(root, dbName).absolutePath};shutdown=true")
            }
            server.start()
            log.info(
                "Started embedded HSQL $name server on {}:{} (db={})",
                serverHost, server.port, server.getDatabaseName(0, true)
            )
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    server.shutdown()
                    log.info("Embedded HSQL $name server stopped")
                } catch (e: Exception) {
                    log.warn("Error shutting down embedded HSQL $name server", e)
                }
            })
            embeddedServer = server
            return server
        }
    }

    var dbName: String = System.getProperty("cognotik.hsql.dbName", name)

    companion object {
        private val log = LoggerFactory.getLogger(HSQLFacet::class.java)

        @Volatile
        private var embeddedServer: Server? = null
        private val serverLock = Any()

        var serviceUrl: String? = System.getProperty("cognotik.hsql.serviceUrl")
        var serviceUser: String = System.getProperty("cognotik.hsql.serviceUser", "SA")
        var servicePassword: String = System.getProperty("cognotik.hsql.servicePassword", "")
        var serverHost: String = System.getProperty("cognotik.hsql.serverHost", "localhost")
        var serverPort: Int = System.getProperty("cognotik.hsql.serverPort")?.toInt() ?: 9010
        var serverSilent: Boolean = System.getProperty("cognotik.hsql.serverSilent", "true").toBoolean()

        @Volatile
        private var driverLoaded: Boolean = false
        private val driverLock = Any()

        /** Cached connections keyed by JDBC URL across all facets. */
        private val connections = ConcurrentHashMap<String, Connection>()

        /** JDBC URLs whose schema DDL has already executed. */
        private val schemasInitialized = ConcurrentHashMap.newKeySet<String>()

        private fun ensureDriverLoaded() {
            if (driverLoaded) return
            synchronized(driverLock) {
                if (!driverLoaded) {
                    Class.forName("org.hsqldb.jdbc.JDBCDriver")
                    driverLoaded = true
                }
            }
        }

    }
}