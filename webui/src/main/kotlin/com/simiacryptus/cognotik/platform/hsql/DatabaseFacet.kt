package com.simiacryptus.cognotik.platform.hsql

import org.hsqldb.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.net.ServerSocket
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap


class DatabaseFacet(
    private val name: String,
    private val schema: ((String) -> List<String>) = { emptyList() },
) {
    @Volatile
    private var embeddedServer: Server? = null
    private val serverLock = Any()

    @Volatile
    private var actualPort: Int = -1

    fun getConnection(root: File? = null): Connection {
        ensureDriverLoaded()
        val url: String
        val username: String
        val password: String
        val remoteUrl = serviceUrl?.ifBlank { null }
        if (remoteUrl != null) {
            log.info("Connecting to external HSQL $name service at: {}", remoteUrl)
            url = remoteUrl
            username = serviceUser
            password = filterPassword(servicePassword)
        } else {
            ensureServerStarted(root)
            url = "jdbc:hsqldb:hsql://${serverHost}:${actualPort}/${dbName}"
            username = "SA"
            password = ""
        }
        val cacheKey = url + "|" + name
        val existing = connections[cacheKey]
        return if (existing != null && isUsable(existing)) {
            log.info("Reusing existing $name connection to: {}", cacheKey)
            existing
        } else synchronized(connections) {
                val again = connections[cacheKey]
                if (again != null && isUsable(again)) {
                    log.info("Reusing existing $name connection to: {}", cacheKey)
                    again
                } else {
                    if (again != null) {
                        try {
                            again.close()
                        } catch (_: Exception) {
                        }
                        connections.remove(cacheKey)
                    }
                    log.info("Opening HSQL $name connection to: {}", cacheKey)
                    val conn = openConnectionWithRetry(url, username, password)
                    ensureSchema(url, conn)
                    connections[cacheKey] = conn
                    conn
                }
            }
    }

    private fun isUsable(conn: Connection): Boolean {
        return try {
            !conn.isClosed && conn.isValid(1)
        } catch (_: Exception) {
            false
        }
    }

    private fun openConnectionWithRetry(url: String, username: String, password: String): Connection {
        var lastError: Exception? = null
        for (attempt in 1..5) {
            try {
                return DriverManager.getConnection(url, username, password)
            } catch (e: Exception) {
                lastError = e
                log.warn("JDBC $name connection attempt $attempt to $url failed: ${e.message}")
                try {
                    Thread.sleep(50L * attempt)
                } catch (_: InterruptedException) {
                }
            }
        }
        throw lastError ?: RuntimeException("Failed to open HSQL connection to $url")
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
                // PostgreSQL may leave the connection in an aborted state after
                // certain errors; if so, drop it from the cache so the next
                // caller gets a fresh connection.
                try {
                    if (!isUsable(conn)) {
                        connections.entries.removeIf { it.value === conn }
                        try {
                            conn.close()
                        } catch (_: Exception) {
                        }
                    }
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
        return try {
            synchronized(conn) { block(conn) }
        } catch (e: Exception) {
            // PostgreSQL may leave the connection in an aborted state after
            // certain errors; if so, drop it from the cache so the next
            // caller gets a fresh connection.
            try {
                if (!isUsable(conn)) {
                    connections.entries.removeIf { it.value === conn }
                    try {
                        conn.close()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            log.warn("Error during $name connection operation: ${e.message}")
            throw e
        }
    }


    private fun ensureSchema(url: String, connection: Connection) {
        val schemaKey = url + "|" + name
        if (schemasInitialized.contains(schemaKey)) {
            log.debug("$name database schema already initialized for {}", schemaKey)
            return
        }
        synchronized(schemasInitialized) {
            if (schemasInitialized.contains(schemaKey)) {
                log.debug("$name database schema already initialized for {}", schemaKey)
                return
            }
            log.info("Creating $name database schema if not exists for {}", schemaKey)
            val ddls = schema.invoke(dbProvider)
            if (ddls.isEmpty()) {
                log.info("No $name schema DDL statements provided, skipping initialization")
            } else {
                connection.createStatement().use { stmt ->
                    log.info("Executing {} $name schema DDL statements for {}", ddls.size, url)
                    for (ddl in ddls) {
                        log.info("Executing $name schema DDL: {}", ddl.trim().replace("\n", " "))
                        try {
                            stmt.executeUpdate(ddl)
                        } catch (e: Exception) {
                            log.warn("Failed to execute $name schema DDL statement: ${e.message}")
                        }
                    }
                }
                log.info("Completed $name database schema initialization for {}", schemaKey)
            }
            schemasInitialized.add(schemaKey)
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
            // Pick a free ephemeral port ourselves. HSQL's Server.setPort(0)
            // does not auto-assign a free port like java.net.ServerSocket(0)
            // does, so we need to allocate one explicitly. This avoids
            // collisions when multiple facets run their own embedded
            // servers in the same JVM (e.g. during tests).
            val freePort = ServerSocket(0).use { it.localPort }
            server.port = freePort
            actualPort = freePort
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
                serverHost, actualPort, server.getDatabaseName(0, true)
            )
            // Open and retain a keep-alive connection so the in-memory database
            // alias is not disposed between client connections. HSQL will dispose
            // a mem: database once its last connection closes, which causes
            // "database alias does not exist" errors for subsequent clients.
            try {
                val keepAliveUrl = "jdbc:hsqldb:hsql://${serverHost}:${actualPort}/${dbName}"
                val keepAlive = DriverManager.getConnection(keepAliveUrl, "SA", "")
                keepAliveConnections[keepAliveUrl] = keepAlive
                log.debug("Opened keep-alive HSQL $name connection to {}", keepAliveUrl)
            } catch (e: Exception) {
                log.warn("Failed to open keep-alive HSQL $name connection", e)
            }
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    keepAliveConnections.values.forEach { c ->
                        try {
                            c.close()
                        } catch (_: Exception) {
                        }
                    }
                    keepAliveConnections.clear()
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

    var dbName: String = System.getProperty("cognotik.db.dbName", name)

    val dbProvider: String
        get() = serviceUrl.let {
            when {
                null == it -> "hsql"
                it.startsWith("jdbc:postgresql:") -> "postgresql"
                it.startsWith("jdbc:hsqldb:") -> "hsql"
                else -> throw IllegalStateException("Unsupported JDBC URL scheme for serviceUrl: $it")
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(DatabaseFacet::class.java)

        val serviceUrl: String? = System.getProperty("cognotik.db.serviceUrl")
        val serviceUser: String = System.getProperty("cognotik.db.serviceUser", "SA")
        val servicePassword: String = System.getProperty("cognotik.db.servicePassword", "")
        val serverHost: String = System.getProperty("cognotik.db.serverHost") ?: "localhost"
        val serverSilent: Boolean = System.getProperty("cognotik.db.serverSilent", "true").toBoolean()

        @Volatile
        private var driverLoaded: Boolean = false
        private val driverLock = Any()

        /** Cached connections keyed by JDBC URL across all facets. */
        private val connections = ConcurrentHashMap<String, Connection>()

        /** Keep-alive connections retained for embedded mem: databases. */
        private val keepAliveConnections = ConcurrentHashMap<String, Connection>()

        /** JDBC URLs whose schema DDL has already executed. */
        private val schemasInitialized = ConcurrentHashMap.newKeySet<String>()

        private fun ensureDriverLoaded() {
            if (driverLoaded) return
            synchronized(driverLock) {
                if (!driverLoaded) {
                    // Always load the HSQL driver for embedded mode.
                    Class.forName("org.hsqldb.jdbc.JDBCDriver")
                    // If a remote service URL is configured, also try to load the
                    // appropriate driver based on the URL scheme.
                    val remoteUrl = serviceUrl
                    if (remoteUrl != null) {
                        val driverClass = when {
                            remoteUrl.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
                            remoteUrl.startsWith("jdbc:mysql:") -> "com.mysql.cj.jdbc.Driver"
                            remoteUrl.startsWith("jdbc:hsqldb:") -> "org.hsqldb.jdbc.JDBCDriver"
                            else -> null
                        }
                        if (driverClass != null) {
                            try {
                                Class.forName(driverClass)
                                log.info("Loaded JDBC driver: {}", driverClass)
                            } catch (e: ClassNotFoundException) {
                                log.warn("JDBC driver $driverClass not on classpath; remote DB connections may fail", e)
                            }
                        }
                    }
                    driverLoaded = true
                }
            }
        }

        var filterPassword: (String) -> String = { it }
    }
}