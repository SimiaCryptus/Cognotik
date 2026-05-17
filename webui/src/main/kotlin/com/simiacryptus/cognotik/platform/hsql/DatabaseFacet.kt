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


    fun getConnection(): Connection {
        ensureDriverLoaded()
        val url: String
        val username: String
        val password: String
        val remoteUrl = serviceUrl?.ifBlank { null }
        if (remoteUrl != null) {
            log.debug("Connecting to external HSQL $name service at: {}", remoteUrl)
            url = remoteUrl
            username = serviceUser
            password = filterPassword(servicePassword)
        } else {
            registerDatabase(name, dbName ?: "default", root)
            startSharedServer()
            url = "jdbc:hsqldb:hsql://${serverHost}:${actualPort}/${dbName ?: "default"}"
            username = "SA"
            password = ""
        }
        val cacheKey = url + "|" + name
        val existing = connections[cacheKey]
        return if (existing != null && isUsable(existing)) {
            log.debug("Reusing existing $name connection to: {}", cacheKey)
            existing
        } else synchronized(connections) {
            val again = connections[cacheKey]
            if (again != null && isUsable(again)) {
                log.debug("Reusing existing $name connection to: {}", cacheKey)
                again
            } else {
                if (again != null) {
                    try {
                        again.close()
                    } catch (_: Exception) {
                    }
                    connections.remove(cacheKey)
                }
                log.debug("Opening HSQL $name connection to: {}", cacheKey)
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
    fun <T> withTransaction(block: (Connection) -> T): T {
        val conn = getConnection()
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
    fun <T> withConnection(block: (Connection) -> T): T {
        val conn = getConnection()
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

        var root = System.getProperty("cognotik.db.root") ?: File(System.getProperty("user.home", ".")).resolve(".cognotik").absolutePath
        val dbName = System.getProperty("cognotik.db.dbName")
        val serviceUrl: String? = System.getProperty("cognotik.db.serviceUrl")
        val serviceUser: String = System.getProperty("cognotik.db.serviceUser", "SA")
        val servicePassword: String = System.getProperty("cognotik.db.servicePassword", "")
        val serverHost: String = System.getProperty("cognotik.db.serverHost") ?: "localhost"

        @Volatile
        private var driverLoaded: Boolean = false
        private val driverLock = Any()

        /** Cached connections keyed by JDBC URL across all facets. */
        private val connections = ConcurrentHashMap<String, Connection>()

        /** Keep-alive connections retained for embedded mem: databases. */
        private val keepAliveConnections = ConcurrentHashMap<String, Connection>()

        /** JDBC URLs whose schema DDL has already executed. */
        private val schemasInitialized = ConcurrentHashMap.newKeySet<String>()

        // ----- Shared embedded HSQL server (one per JVM) -----
        private val serverLock = Any()

        @Volatile
        private var embeddedServer: Server? = null

        @Volatile
        private var actualPort: Int = -1

        /**
         * Databases registered for hosting on the shared server.
         * Keyed by dbName -> database path (e.g. "mem:foo" or "file:/abs/path;shutdown=true").
         * Order of insertion is preserved for stable index assignment.
         */
        private val registeredDatabases = java.util.LinkedHashMap<String, String>()
        private fun registerDatabase(facetName: String, dbName: String, root: String?) {
            val path = if (root == null) {
                "mem:$dbName"
            } else {
                // Note: do NOT append ";shutdown=true" here. That property is for
                // in-process JDBC connection URLs; when used as a server database
                // path it causes the database to shut down as soon as the last
                // client disconnects, which races with server startup and leaves
                // the server in the SHUTDOWN state (16) instead of ONLINE (1).
                "file:${File(root, dbName).absolutePath}"
            }
            synchronized(serverLock) {
                val existing = registeredDatabases[dbName]
                if (existing == null) {
                    if (embeddedServer != null) {
                        // The shared server is already running. HSQL does not
                        // support adding databases to a running server in a
                        // clean way, so we must restart it to include the
                        // newly registered database.
                        log.info(
                            "Registering new HSQL database '{}' for facet '{}'; restarting shared server to include it",
                            dbName, facetName
                        )
                        registeredDatabases[dbName] = path
                        restartSharedServerLocked()
                    } else {
                        registeredDatabases[dbName] = path
                    }
                } else if (existing != path) {
                    // Conflict resolution: prefer file-backed storage over mem,
                    // since file storage is durable and is what the user most
                    // likely intended once a root directory becomes available.
                    val existingIsMem = existing.startsWith("mem:")
                    val requestedIsMem = path.startsWith("mem:")
                    when {
                        existingIsMem && !requestedIsMem -> {
                            log.warn(
                                "HSQL database '{}' was previously registered as '{}' but facet '{}' now requests '{}'; upgrading to file-backed storage and restarting shared server",
                                dbName, existing, facetName, path
                            )
                            registeredDatabases[dbName] = path
                            if (embeddedServer != null) {
                                restartSharedServerLocked()
                            }
                        }

                        !existingIsMem && requestedIsMem -> {
                            // Existing file-backed registration is preferred;
                            // ignore the in-memory request and reuse the
                            // already-registered file path.
                            log.warn(
                                "HSQL database '{}' is already registered with file-backed path '{}'; ignoring in-memory request '{}' from facet '{}'",
                                dbName, existing, path, facetName
                            )
                        }

                        else -> {
                            throw IllegalStateException(
                                "HSQL database '$dbName' is already registered with path '$existing' but facet '$facetName' requested '$path'"
                            )
                        }
                    }
                }
            }
        }

        private fun startSharedServer(): Server {
            embeddedServer?.let { return it }
            synchronized(serverLock) {
                embeddedServer?.let { return it }
                val server = startServerLocked()
                embeddedServer = server
                installShutdownHookOnce()
                return server
            }
        }

        private fun restartSharedServerLocked() {
            // Caller must hold serverLock.
            val old = embeddedServer
            if (old != null) {
                // Close keep-alive connections so the server can shut down cleanly.
                keepAliveConnections.values.forEach { c ->
                    try {
                        c.close()
                    } catch (_: Exception) {
                    }
                }
                keepAliveConnections.clear()
                // Drop cached client connections that point at the old server;
                // they will be re-established against the new server on next use.
                connections.entries.removeIf { (_, conn) ->
                    try {
                        conn.close()
                    } catch (_: Exception) {
                    }
                    true
                }
                try {
                    old.shutdown()
                } catch (e: Exception) {
                    log.warn("Error shutting down shared HSQL server during restart", e)
                }
                embeddedServer = null
                actualPort = -1
            }
            embeddedServer = startServerLocked()
        }

        private fun startServerLocked(): Server {
            // Caller must hold serverLock.
            if (registeredDatabases.isEmpty()) {
                throw IllegalStateException("Cannot start shared HSQL server: no databases registered")
            }
           // Before starting the server, check for stale lock files on file-backed
           // databases. A stale lock file is one whose heartbeat hasn't been
           // updated recently, indicating the owning JVM crashed without
           // releasing it. If the lock file is live (recent heartbeat), another
          // process is actively using the database; in that case we log an
          // error and fall back to an ephemeral in-memory database so the
          // application can still start (with non-persistent storage).
           for ((db, path) in registeredDatabases) {
               if (!path.startsWith("file:")) continue
               val dbFilePath = path.removePrefix("file:")
               val lockFile = File("$dbFilePath.lck")
               if (!lockFile.exists()) continue
               val ageMs = System.currentTimeMillis() - lockFile.lastModified()
               // HSQL updates the heartbeat every ~10s. Consider a lock file
               // stale if its heartbeat is older than 30s.
               if (ageMs > 30_000) {
                   log.warn(
                       "Found stale HSQL lock file for database '{}' at {} (age={}ms); removing",
                       db, lockFile.absolutePath, ageMs
                   )
                   try {
                       if (!lockFile.delete()) {
                           log.warn("Failed to delete stale lock file: {}", lockFile.absolutePath)
                       }
                   } catch (e: Exception) {
                       log.warn("Error deleting stale lock file ${lockFile.absolutePath}", e)
                   }
               } else {
                   log.error(
                       "HSQL database '{}' at '{}' is locked by another running process " +
                               "(lock file {} heartbeat age={}ms). " +
                               "Falling back to an ephemeral in-memory database for '{}'; " +
                               "data will NOT be persisted. To enable persistence, stop the other " +
                               "instance, configure a different database location via " +
                               "-Dcognotik.db.root=<path>, or point this instance at a shared " +
                               "database server via -Dcognotik.db.serviceUrl=<jdbc-url>.",
                       db, dbFilePath, lockFile.absolutePath, ageMs, db
                   )
                   registeredDatabases[db] = "mem:$db"
               }
           }
            // Try multiple times in case the chosen ephemeral port is taken between
            // our probe and HSQL's bind (common when multiple application instances
            // are racing for the same port range).
            var server: Server? = null
            var lastError: Exception? = null
            var chosenPort = -1
            for (attempt in 1..10) {
                val candidate = Server()
                // Keep the server's own logging enabled until we have successfully
                // started at least once, so that startup failures surface clearly.
                candidate.setSilent(false)
                candidate.setAddress(serverHost)
                // Pick a free ephemeral port ourselves. HSQL's Server.setPort(0)
                // does not auto-assign a free port like java.net.ServerSocket(0)
                // does, so we need to allocate one explicitly.
                val freePort = ServerSocket(0).use { it.localPort }
                candidate.port = freePort
                // Register every database on the same server.
                registeredDatabases.entries.forEachIndexed { index, (db, path) ->
                    candidate.setDatabaseName(index, db)
                    candidate.setDatabasePath(index, path)
                }
                try {
                    candidate.start()
                    // Server.start() is asynchronous; wait until it reaches the
                    // ONLINE state (or fails) before proceeding.
                    val deadline = System.currentTimeMillis() + 15_000
                    // States: 1=ONLINE, 4=OPENING, 8=CLOSING, 16=SHUTDOWN
                    while (candidate.state != 1 && candidate.state != 16
                        && System.currentTimeMillis() < deadline
                    ) {
                        Thread.sleep(50)
                    }
                    if (candidate.state != 1) {
                        val cause = candidate.serverError
                       // If the server shut down because it couldn't acquire
                       // the database lock, retrying won't help - another
                       // process owns it. Convert all file-backed databases
                       // to in-memory and retry so the application can still
                       // start (with non-persistent storage).
                       val causeMsg = cause?.message ?: ""
                       val isLockFailure = causeMsg.contains("lock", ignoreCase = true) ||
                               registeredDatabases.any { (_, p) ->
                                   if (!p.startsWith("file:")) false
                                   else {
                                       val lf = File("${p.removePrefix("file:")}.lck")
                                       lf.exists() &&
                                               (System.currentTimeMillis() - lf.lastModified()) < 30_000
                                   }
                               }
                       if (isLockFailure) {
                           try { candidate.shutdown() } catch (_: Exception) {}
                           val converted = mutableListOf<String>()
                           registeredDatabases.entries.forEach { e ->
                               if (e.value.startsWith("file:")) {
                                   converted += e.key
                                   e.setValue("mem:${e.key}")
                               }
                           }
                           if (converted.isNotEmpty()) {
                               log.error(
                                   "HSQL server failed to start because database(s) {} are locked by " +
                                           "another running process (cause: {}). Falling back to " +
                                           "ephemeral in-memory storage for these databases; data " +
                                           "will NOT be persisted. To enable persistence, stop the " +
                                           "other instance, configure a different database location " +
                                           "via -Dcognotik.db.root=<path>, or point this instance at " +
                                           "a shared database server via -Dcognotik.db.serviceUrl=<jdbc-url>.",
                                   converted, causeMsg, cause
                               )
                               // Retry immediately with the converted registrations.
                               continue
                           } else {
                               // Nothing left to convert; surface the error.
                               throw IllegalStateException(
                                   "HSQL server failed to start due to a lock failure but no " +
                                           "file-backed databases were registered. Registered: $registeredDatabases",
                                   cause
                               )
                           }
                       }
                        val stateName = when (candidate.state) {
                            1 -> "ONLINE"
                            4 -> "OPENING"
                            8 -> "CLOSING"
                            16 -> "SHUTDOWN"
                            else -> "UNKNOWN"
                        }
                        throw cause ?: RuntimeException(
                            "Shared HSQL server did not reach ONLINE state on port $freePort " +
                                    "(state=${candidate.state}/$stateName). Registered databases: $registeredDatabases"
                        )
                    }
                    // Verify we can actually connect locally before declaring success.
                    val firstDb = registeredDatabases.keys.first()
                    val probeUrl = "jdbc:hsqldb:hsql://${serverHost}:${freePort}/${firstDb}"
                    DriverManager.getConnection(probeUrl, "SA", "").close()
                    server = candidate
                    chosenPort = freePort
                    break
                } catch (e: Exception) {
                   // Lock-acquisition failures are handled above by converting
                   // file-backed databases to in-memory; any other exception
                   // here is treated as potentially transient and retried.
                    lastError = e
                    log.warn(
                        "Shared HSQL server start attempt $attempt on port $freePort failed: ${e.message}",
                        e
                    )
                    try {
                        candidate.shutdown()
                    } catch (_: Exception) {
                    }
                    try {
                        Thread.sleep(100L * attempt)
                    } catch (_: InterruptedException) {
                    }
                }
            }
            if (server == null) {
                throw lastError ?: RuntimeException("Failed to start shared embedded HSQL server")
            }
            actualPort = chosenPort
            log.info(
                "Started shared embedded HSQL server on {}:{} hosting databases: {}",
                serverHost, actualPort, registeredDatabases.keys
            )
            // Open and retain a keep-alive connection per database so each
            // in-memory alias is not disposed between client connections. HSQL
            // disposes a mem: database once its last connection closes, which
            // causes "database alias does not exist" errors for subsequent
            // clients.
            for (db in registeredDatabases.keys) {
                try {
                    val keepAliveUrl = "jdbc:hsqldb:hsql://${serverHost}:${actualPort}/${db}"
                    val keepAlive = DriverManager.getConnection(keepAliveUrl, "SA", "")
                    keepAliveConnections[keepAliveUrl] = keepAlive
                    log.debug("Opened keep-alive HSQL connection to {}", keepAliveUrl)
                } catch (e: Exception) {
                    log.warn("Failed to open keep-alive HSQL connection for database '$db'", e)
                }
            }
            return server
        }

        @Volatile
        private var shutdownHookInstalled: Boolean = false
        private fun installShutdownHookOnce() {
            if (shutdownHookInstalled) return
            synchronized(serverLock) {
                if (shutdownHookInstalled) return
                Runtime.getRuntime().addShutdownHook(Thread {
                    try {
                        keepAliveConnections.values.forEach { c ->
                            try {
                                c.close()
                            } catch (_: Exception) {
                            }
                        }
                        keepAliveConnections.clear()
                        embeddedServer?.shutdown()
                        log.info("Shared embedded HSQL server stopped")
                    } catch (e: Exception) {
                        log.warn("Error shutting down shared embedded HSQL server", e)
                    }
                })
                shutdownHookInstalled = true
            }
        }

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