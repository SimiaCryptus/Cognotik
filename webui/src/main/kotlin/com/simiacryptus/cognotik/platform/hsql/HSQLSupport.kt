package com.simiacryptus.cognotik.platform.hsql

import org.hsqldb.server.Server
import org.slf4j.Logger
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
 *   - lazily starts an embedded HSQL [Server] bound to [serverHost]:[serverPort].
 *
 * Responsibilities centralized here:
 *   - JDBC driver loading (once per JVM)
 *   - Embedded server lifecycle (one [Server] per facet)
 *   - Connection caching keyed by JDBC URL
 *   - Schema initialization deduplication per JDBC URL
 */
class HSQLFacet(
    private val name: String,
    private val schemaSql: List<String>,
) {
    val log: Logger = LoggerFactory.getLogger("HSQLFacet[$name]")

    var serviceUrl: String? = System.getProperty("cognotik.hsql.$name.serviceUrl")
    var serviceUser: String = System.getProperty("cognotik.hsql.$name.serviceUser", "SA")
    var servicePassword: String = System.getProperty("cognotik.hsql.$name.servicePassword", "")
    var serverHost: String = System.getProperty("cognotik.hsql.$name.serverHost", "localhost")
    var serverPort: Int = System.getProperty("cognotik.hsql.$name.serverPort", defaultPort(name).toString()).toInt()
    var serverSilent: Boolean = System.getProperty("cognotik.hsql.$name.serverSilent", "true").toBoolean()
    var dbName: String = System.getProperty("cognotik.hsql.$name.dbName", name)

    @Volatile
    private var embeddedServer: Server? = null
    private val serverLock = Any()

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

    private fun ensureSchema(url: String, connection: Connection) {
        if (schemasInitialized.contains(url)) return
        synchronized(schemasInitialized) {
            if (schemasInitialized.contains(url)) return
            log.debug("Creating $name database schema if not exists for {}", url)
            val stmt = connection.createStatement()
            for (ddl in schemaSql) {
                stmt.executeUpdate(ddl)
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

    companion object {
        private val log = LoggerFactory.getLogger(HSQLFacet::class.java)

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

        private fun defaultPort(name: String): Int = when (name) {
            "metadata" -> 9001
            "usage" -> 9002
            else -> 0
        }
    }
}

/**
 * Utility helpers shared by HSQL storage implementations.
 */
internal object HSQLUtils {
    fun ensureRoot(root: File?) {
        require(root?.exists() != false || root.mkdirs()) { "Failed to create root directory: $root" }
    }
}