package com.simiacryptus.cognotik.platform.hsql

import org.h2.tools.Server
import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction as exposedTransaction
import org.jetbrains.exposed.v1.jdbc.vendors.DatabaseDialectMetadata
import org.slf4j.LoggerFactory
import java.io.File
import java.net.ServerSocket
import java.sql.Connection
import java.sql.DriverManager
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap


class DatabaseFacet(
    private val name: String,
    private val schema: ((String) -> List<String>) = { emptyList() },
    val tables: List<Table> = emptyList()
) {
    /**
     * Lazily-initialized Exposed [Database] bound to this facet's JDBC connection.
     * Exposed DSL operations (selectAll, insert, update, etc.) require an Exposed
     * `Transaction` in thread-local context; callers should wrap DSL usage in
     * `org.jetbrains.exposed.v1.jdbc.transactions.transaction(facet.database) { ... }`.
     *
     * The connection manager returns the facet's shared/cached JDBC connection,
     * so Exposed transactions reuse the same physical connection that
     * [transaction] / [withConnection] synchronize on.
     */
    val database: Database by lazy {
        ensureDriverLoaded()
        // Ensure Exposed dialects are registered before connecting. Exposed v1
        // resolves the dialect from the JDBC URL prefix (e.g. "h2",
        // "postgresql"); if the corresponding dialect class hasn't been
        // class-loaded and registered, the registry will be empty and Exposed
        // throws "Can't resolve dialect for connection".
        registerDialectsOnce()
        val (url, username, password) = resolveJdbcCoordinates()
        val driverClass = when {
            url.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
            url.startsWith("jdbc:mysql:") -> "com.mysql.cj.jdbc.Driver"
            url.startsWith("jdbc:hsqldb:") -> "org.hsqldb.jdbc.JDBCDriver"
            url.startsWith("jdbc:h2:") -> "org.h2.Driver"
            else -> ""
        }
        val db = Database.connect(
            url = url,
            driver = driverClass,
            user = username,
            password = password,
        )
        // Ensure schema (DDL strings + Exposed table definitions) is initialized
        // for this facet. Exposed transactions bypass [getConnection], so
        // schema initialization must also be triggered here.
        try {
            initializeSchemaForDatabase(url, db)
        } catch (e: Exception) {
            log.error("Failed to initialize schema for $name on $url", e)
        }
        db
    }

    /**
     * Resolve the JDBC URL/user/password for this facet, starting the
     * embedded server and registering the database if needed.
     */
    private fun resolveJdbcCoordinates(): Triple<String, String, String> {
        ensureDriverLoaded()
        val remoteUrl = serviceUrl?.ifBlank { null }
        return if (remoteUrl != null) {
            Triple(remoteUrl, serviceUser, filterPassword(servicePassword))
        } else {
            registerDatabase(name, dbName ?: "default", root)
            startSharedServer()
            Triple(
                buildLocalJdbcUrl(dbName ?: "default"),
                "SA",
                ""
            )
        }
    }

    fun getConnection(): Connection {
        ensureDriverLoaded()
        val url: String
        val username: String
        val password: String
        val remoteUrl = serviceUrl?.ifBlank { null }
        if (remoteUrl != null) {
            log.debug("Connecting to external $name service at: {}", remoteUrl)
            url = remoteUrl
            username = serviceUser
            password = filterPassword(servicePassword)
        } else {
            registerDatabase(name, dbName ?: "default", root)
            startSharedServer()
            url = buildLocalJdbcUrl(dbName ?: "default")
            username = "SA"
            password = ""
        }
        // Each facet keeps its own connection so per-facet transactions (which
        // toggle autoCommit) cannot interfere with each other on the same JDBC
        // URL. Sharing one connection across facets would serialize all
        // database work in the JVM.
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
                log.debug("Opening $name connection to: {}", cacheKey)
                val conn = openConnectionWithRetry(url, username, password)
                ensureSchema(url, conn)
                connections[cacheKey] = conn
                conn
            }
        }
    }

    /**
     * Initialize schema using an Exposed [Database] handle. This is used
     * when callers obtain the database via the lazy [database] property and
     * never go through [getConnection]. Runs both string DDL statements and
     * Exposed `SchemaUtils.create` for any registered [tables].
     */
    private fun initializeSchemaForDatabase(url: String, db: Database) {
        val schemaKey = url + "|" + name
        if (schemasInitialized.contains(schemaKey)) {
            log.debug("$name database schema already initialized for {}", schemaKey)
            return
        }
        synchronized(schemasInitialized) {
            if (schemasInitialized.contains(schemaKey)) return
            log.info("Creating $name database schema (via Exposed) if not exists for {}", schemaKey)
            exposedTransaction(db) {
                val ddls = schema.invoke(dbProvider)
                if (ddls.isNotEmpty()) {
                    val jdbcConn = (this.connection as? JdbcTransaction)?.let { null }
                    // Execute raw DDL strings via the Exposed transaction's connection.
                    val rawConn =
                        (this.connection as ExposedConnection<*>).connection as Connection
                    rawConn.createStatement().use { stmt ->
                        log.info("Executing {} $name schema DDL statements for {}", ddls.size, url)
                        var failures = 0
                        for (ddl in ddls) {
                            val ddlSummary = ddl.trim().replace("\n", " ").take(200)
                            try {
                                stmt.executeUpdate(ddl)
                            } catch (e: Exception) {
                                failures++
                                log.warn(
                                    "Failed to execute $name schema DDL statement [{}]: {}",
                                    ddlSummary,
                                    e.message,
                                    e
                                )
                            }
                        }
                        if (failures > 0) {
                            log.warn(
                                "Completed $name DDL initialization for {} with {} failure(s)",
                                schemaKey,
                                failures
                            )
                        }
                    }
                }
                if (tables.isNotEmpty()) {
                    try {
                        log.info("Creating {} Exposed table(s) for $name on {}", tables.size, url)
                        SchemaUtils.create(tables = tables.toTypedArray())
                    } catch (e: Exception) {
                        log.error("Failed to create Exposed tables for $name on $url", e)
                        throw e
                    }
                }
            }
            schemasInitialized.add(schemaKey)
            log.info("Completed $name database schema initialization for {}", schemaKey)
        }
    }

    private fun isUsable(conn: Connection): Boolean {
        return try {
            !conn.isClosed && conn.isValid(1)
        } catch (e: Exception) {
            log.debug("Connection usability check failed for $name: ${e.message}", e)
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
                log.warn("JDBC $name connection attempt $attempt/5 to $url failed: ${e.message}", e)
                try {
                    Thread.sleep(50L * attempt)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log.warn("Interrupted while waiting to retry $name connection to $url", ie)
                    throw RuntimeException("Interrupted while opening connection to $url", ie)
                }
            }
        }
        log.error("Exhausted all 5 attempts to open $name connection to $url", lastError)
        throw lastError ?: RuntimeException("Failed to open $name connection to $url after 5 attempts")
    }

    /**
     * Acquire an exclusive lock on the underlying connection while [block] runs.
     * Use this for multi-statement transactions on the shared connection so that
     * autoCommit toggling does not race with other threads.
     */
    fun <T> transaction(block: (Connection) -> T): T {
        val conn = getConnection()
        synchronized(conn) {
            val prevAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                val result = block(conn)
                conn.commit()
                return result
            } catch (e: Exception) {
                log.warn("Transaction failed on $name connection; attempting rollback: ${e.message}", e)
                try {
                    conn.rollback()
                } catch (rb: Exception) {
                    log.error("Rollback failed for $name after transaction error", rb)
                }
                throw e
            } finally {
                try {
                    conn.autoCommit = prevAutoCommit
                } catch (e: Exception) {
                    log.warn("Failed to restore autoCommit=$prevAutoCommit on $name connection: ${e.message}", e)
                }
                // PostgreSQL may leave the connection in an aborted state after
                // certain errors; if so, drop it from the cache so the next
                // caller gets a fresh connection.
                try {
                    if (!isUsable(conn)) {
                        log.warn("Dropping unusable $name connection from cache after transaction")
                        connections.entries.removeIf { it.value === conn }
                        try {
                            conn.close()
                        } catch (e: Exception) {
                            log.debug("Error closing unusable $name connection: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    log.debug("Error during $name connection health check after transaction: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Acquire a lock on the shared connection for the duration of [block].
     * Use for single-statement operations that still need to be serialized
     * against [transaction] callers.
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
                    log.warn("Dropping unusable $name connection from cache after operation error")
                    connections.entries.removeIf { it.value === conn }
                    try {
                        conn.close()
                    } catch (ce: Exception) {
                        log.debug("Error closing unusable $name connection: ${ce.message}", ce)
                    }
                }
            } catch (he: Exception) {
                log.debug("Error during $name connection health check after operation: ${he.message}", he)
            }
            log.warn("Error during $name connection operation: ${e.message}", e)
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
                log.debug("No $name schema DDL statements provided, skipping initialization")
            } else {
                connection.createStatement().use { stmt ->
                    log.info("Executing {} $name schema DDL statements for {}", ddls.size, url)
                    var failures = 0
                    for (ddl in ddls) {
                        val ddlSummary = ddl.trim().replace("\n", " ").take(200)
                        log.debug("Executing $name schema DDL: {}", ddlSummary)
                        try {
                            stmt.executeUpdate(ddl)
                        } catch (e: Exception) {
                            failures++
                            log.warn(
                                "Failed to execute $name schema DDL statement [{}]: {}",
                                ddlSummary, e.message, e
                            )
                        }
                    }
                    if (failures > 0) {
                        log.warn(
                            "Completed $name database schema initialization for {} with {} DDL failure(s) out of {}",
                            schemaKey, failures, ddls.size
                        )
                    } else {
                        log.info("Completed $name database schema initialization for {}", schemaKey)
                    }
                }
            }
            // Also create any Exposed table definitions registered with this facet.
            if (tables.isNotEmpty()) {
                try {
                    // Initialize via Exposed by using the lazy database property.
                    // Using a nested transaction here would require an Exposed
                    // Database handle; we instead defer to initializeSchemaForDatabase
                    // by accessing `database` which itself triggers initialization
                    // (but is guarded by schemasInitialized). To avoid recursion,
                    // we mark schemasInitialized before delegating.
                    schemasInitialized.add(schemaKey)
                    log.info("Creating {} Exposed table(s) for $name on {}", tables.size, url)
                    exposedTransaction(database) {
                        SchemaUtils.create(tables = tables.toTypedArray())
                    }
                    return
                } catch (e: Exception) {
                    log.error("Failed to create Exposed tables for $name on $url", e)
                    throw e
                }
            }
            schemasInitialized.add(schemaKey)
        }
    }

    val dbProvider: String
        get() = serviceUrl.let {
            when {
                null == it -> "h2"
                it.startsWith("jdbc:postgresql:") -> "postgresql"
                it.startsWith("jdbc:hsqldb:") -> "hsql"
                it.startsWith("jdbc:h2:") -> "h2"
                else -> throw IllegalStateException("Unsupported JDBC URL scheme for serviceUrl: $it")
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(DatabaseFacet::class.java)

        var root: String? = System.getProperty("cognotik.db.root") ?: File(
            System.getProperty(
                "user.home",
                "."
            )
        ).resolve(".cognotik2").absolutePath
        val dbName = System.getProperty("cognotik.db.dbName")
        val serviceUrl: String? = System.getProperty("cognotik.db.serviceUrl")
        val serviceUser: String = System.getProperty("cognotik.db.serviceUser", "SA")
        val servicePassword: String = System.getProperty("cognotik.db.servicePassword", "")
        val serverHost: String = System.getProperty("cognotik.db.serverHost") ?: "localhost"

        @Volatile
        private var driverLoaded: Boolean = false
        private val driverLock = Any()

        @Volatile
        private var dialectsRegistered: Boolean = false
        private val dialectLock = Any()

        /**
         * Register Exposed dialects for the database engines we support.
         * Exposed v1 resolves dialects by matching a prefix of the JDBC URL
         * (everything before the second colon) against registered dialect
         * names. We register the H2 and PostgreSQL dialects explicitly so
         * that URLs like `jdbc:h2:tcp://host:port/db` resolve correctly.
         */
        private fun registerDialectsOnce() {
            if (dialectsRegistered) return
            synchronized(dialectLock) {
                if (dialectsRegistered) return
                try {
                    try {
                        DatabaseApi.registerDialect("h2") { H2Dialect() }
                        Database.registerJdbcDriver("jdbc:h2", "org.h2.Driver", H2Dialect.dialectName)
                        Database.registerDialectMetadata("h2") { H2Dialect().metadata() }
                    } catch (e: Exception) {
                        log.debug("H2 dialect registration skipped: {}", e.message)
                    }

                    try {
                        DatabaseApi.registerDialect("postgresql") { PostgreSQLDialect() }
                        Database.registerDialectMetadata("postgresql") { PostgreSQLDialect().metadata() }
                    } catch (e: Exception) {
                        log.debug("PostgreSQL dialect registration skipped: {}", e.message)
                    }
                } finally {
                    dialectsRegistered = true
                }
            }
        }

        /** Cached connections keyed by JDBC URL across all facets. */
        private val connections = ConcurrentHashMap<String, Connection>()

        /** Keep-alive connections retained for embedded mem: databases. */
        private val keepAliveConnections = ConcurrentHashMap<String, Connection>()

        /** JDBC URLs whose schema DDL has already executed. */
        private val schemasInitialized = ConcurrentHashMap.newKeySet<String>()

        // ----- Shared embedded H2 server (one per JVM) -----
        private val serverLock = Any()

        @Volatile
        private var embeddedServer: Server? = null

        @Volatile
        private var actualPort: Int = -1

        /**
         * Databases registered for hosting on the shared server.
         * Keyed by dbName -> database path. For H2 this is either an
         * in-memory alias like "mem:foo" or an absolute file path like
         * "/abs/path/foo".
         * Order of insertion is preserved for stable index assignment.
         */
        private val registeredDatabases = LinkedHashMap<String, String>()

        /**
         * Build a JDBC URL for connecting to a database hosted on the
         * shared embedded H2 TCP server.
         *
         * H2's TCP server resolves the database name from the URL path. For
         * file-backed databases we pass the absolute path; for in-memory
         * databases we pass `mem:<name>`. We append `;DB_CLOSE_DELAY=-1` to
         * keep in-memory databases alive across connection close/open
         * cycles. PostgreSQL-mode is enabled so that DDL/SQL written for
         * PostgreSQL is broadly compatible.
         */
        private fun buildLocalJdbcUrl(db: String): String {
            val path = registeredDatabases[db] ?: "mem:$db"
            val dbPart = if (path.startsWith("mem:")) {
                "mem:$db;DB_CLOSE_DELAY=-1"
            } else {
                // File-backed: use absolute path as the database name.
                path
            }
            return "jdbc:h2:tcp://${serverHost}:${actualPort}/$dbPart;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
        }

        private fun registerDatabase(facetName: String, dbName: String, root: String?) {
            val path = if (root == null) {
                "mem:$dbName"
            } else {
                // For H2 we register the absolute file path (without the
                // `file:` prefix used by HSQL). H2 derives the database
                // files (`<path>.mv.db`, `<path>.trace.db`, ...) from this
                // base name.
                File(root, dbName).absolutePath
            }
            synchronized(serverLock) {
                val existing = registeredDatabases[dbName]
                if (existing == null) {
                    if (embeddedServer != null) {
                        // H2's TCP server resolves databases dynamically from
                        // the connection URL, so we don't need to restart the
                        // server to register a new database. Just record it.
                        log.info(
                            "Registering new H2 database '{}' for facet '{}' on running shared server",
                            dbName, facetName
                        )
                    }
                    registeredDatabases[dbName] = path
                } else if (existing != path) {
                    // Conflict resolution: prefer file-backed storage over mem,
                    // since file storage is durable and is what the user most
                    // likely intended once a root directory becomes available.
                    val existingIsMem = existing.startsWith("mem:")
                    val requestedIsMem = path.startsWith("mem:")
                    when {
                        existingIsMem && !requestedIsMem -> {
                            log.warn(
                                "H2 database '{}' was previously registered as '{}' but facet '{}' now requests '{}'; upgrading to file-backed storage",
                                dbName, existing, facetName, path
                            )
                            registeredDatabases[dbName] = path
                        }

                        !existingIsMem && requestedIsMem -> {
                            // Existing file-backed registration is preferred;
                            // ignore the in-memory request and reuse the
                            // already-registered file path.
                            log.warn(
                                "H2 database '{}' is already registered with file-backed path '{}'; ignoring in-memory request '{}' from facet '{}'",
                                dbName, existing, path, facetName
                            )
                        }

                        else -> {
                            throw IllegalStateException(
                                "H2 database '$dbName' is already registered with path '$existing' but facet '$facetName' requested '$path'"
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

        private fun startServerLocked(): Server {
            // Caller must hold serverLock.
            if (registeredDatabases.isEmpty()) {
                throw IllegalStateException("Cannot start shared H2 server: no databases registered")
            }
            // Try multiple times in case the chosen ephemeral port is taken between
            // our probe and H2's bind (common when multiple application instances
            // are racing for the same port range).
            var server: Server? = null
            var lastError: Exception? = null
            var chosenPort = -1
            var attempt = 0
            while (attempt < 10 && server == null) {
                attempt++
                // Pick a free ephemeral port ourselves. H2's TCP server does
                // not auto-assign a free port, so we allocate one explicitly.
                val freePort = ServerSocket(0).use { it.localPort }
                // Build the H2 TCP server arguments. We allow connections
                // from other databases (`-ifNotExists`) and from any host
                // only on the configured host. We do NOT pass `-tcpDaemon`
                // since we install our own shutdown hook.
                val args = mutableListOf(
                    "-tcpPort", freePort.toString(),
                    "-tcpAllowOthers",
                    "-ifNotExists"
                    // Intentionally no `-baseDir`: on Windows, setting
                    // baseDir to a filesystem root anchors the server to a
                    // single drive letter, which rejects database paths on
                    // other drives with "<path> outside <drive>:/". Without
                    // baseDir, H2 accepts absolute paths on any drive.
                )
                val candidate = try {
                    Server.createTcpServer(*args.toTypedArray())
                } catch (e: Exception) {
                    lastError = e
                    log.warn(
                        "Failed to construct H2 TCP server (attempt $attempt/10): ${e.message}",
                        e
                    )
                    try {
                        Thread.sleep(100L * attempt)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException("Interrupted while starting shared H2 server", ie)
                    }
                    continue
                }
                try {
                    candidate.start()
                    // Verify we can actually connect locally before declaring success.
                    val firstDb = registeredDatabases.keys.first()
                    val probeUrl = buildLocalJdbcUrlForProbe(firstDb, freePort)
                    DriverManager.getConnection(probeUrl, "SA", "").close()
                    server = candidate
                    chosenPort = freePort
                } catch (e: Exception) {
                    lastError = e
                    log.warn(
                        "Shared H2 server start attempt $attempt/10 on port $freePort failed: ${e.message}",
                        e
                    )
                    try {
                        candidate.stop()
                    } catch (se: Exception) {
                        log.debug("Error stopping failed H2 server candidate: ${se.message}", se)
                    }
                    try {
                        Thread.sleep(100L * attempt)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        log.warn("Interrupted while waiting to retry H2 server start", ie)
                        throw RuntimeException("Interrupted while starting shared H2 server", ie)
                    }
                }
            }
            if (server == null) {
                log.error(
                    "Failed to start shared embedded H2 server after 10 attempts. Registered databases: {}",
                    registeredDatabases, lastError
                )
                throw lastError ?: RuntimeException("Failed to start shared embedded H2 server")
            }
            actualPort = chosenPort
            log.info(
                "Started shared embedded H2 server on {}:{} hosting databases: {}",
                serverHost, actualPort, registeredDatabases.keys
            )
            // Open and retain a keep-alive connection per database so each
            // in-memory alias is not disposed between client connections.
            // Although we also set DB_CLOSE_DELAY=-1, the keep-alive ensures
            // the database stays open even if that property is ignored.
            for (db in registeredDatabases.keys) {
                try {
                    val keepAliveUrl = buildLocalJdbcUrlForProbe(db, actualPort)
                    val keepAlive = DriverManager.getConnection(keepAliveUrl, "SA", "")
                    keepAliveConnections[keepAliveUrl] = keepAlive
                    log.debug("Opened keep-alive H2 connection to {}", keepAliveUrl)
                } catch (e: Exception) {
                    log.warn(
                        "Failed to open keep-alive H2 connection for database '{}' on {}:{}: {}",
                        db, serverHost, actualPort, e.message, e
                    )
                }
            }
            return server
        }

        /**
         * Build a JDBC URL for a specific port; used during server startup
         * before [actualPort] has been assigned to the companion-level field.
         */
        private fun buildLocalJdbcUrlForProbe(db: String, port: Int): String {
            val path = registeredDatabases[db] ?: "mem:$db"
            val dbPart = if (path.startsWith("mem:")) {
                "mem:$db;DB_CLOSE_DELAY=-1"
            } else {
                path
            }
            return "jdbc:h2:tcp://${serverHost}:${port}/$dbPart;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
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
                            } catch (e: Exception) {
                                log.debug("Error closing keep-alive H2 connection during shutdown: ${e.message}", e)
                            }
                        }
                        keepAliveConnections.clear()
                        embeddedServer?.stop()
                        log.info("Shared embedded H2 server stopped")
                    } catch (e: Exception) {
                        log.warn("Error shutting down shared embedded H2 server: ${e.message}", e)
                    }
                })
                shutdownHookInstalled = true
            }
        }

        private fun ensureDriverLoaded() {
            if (driverLoaded) return
            synchronized(driverLock) {
                if (!driverLoaded) {
                    // Always load the H2 driver for embedded mode.
                    try {
                        Class.forName("org.h2.Driver")
                        log.debug("Loaded H2 JDBC driver")
                    } catch (e: ClassNotFoundException) {
                        log.error("Failed to load required H2 JDBC driver", e)
                        throw e
                    }
                    // If a remote service URL is configured, also try to load the
                    // appropriate driver based on the URL scheme.
                    val remoteUrl = serviceUrl
                    if (remoteUrl != null) {
                        val driverClass = when {
                            remoteUrl.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
                            remoteUrl.startsWith("jdbc:mysql:") -> "com.mysql.cj.jdbc.Driver"
                            remoteUrl.startsWith("jdbc:hsqldb:") -> "org.hsqldb.jdbc.JDBCDriver"
                            remoteUrl.startsWith("jdbc:h2:") -> "org.h2.Driver"
                            else -> {
                                log.warn(
                                    "Unrecognized JDBC URL scheme for serviceUrl='{}'; no remote driver will be loaded",
                                    remoteUrl
                                )
                                null
                            }
                        }
                        if (driverClass != null) {
                            try {
                                Class.forName(driverClass)
                                log.info("Loaded JDBC driver: {}", driverClass)
                            } catch (e: ClassNotFoundException) {
                                log.warn(
                                    "JDBC driver {} not on classpath; remote DB connections to {} may fail",
                                    driverClass, remoteUrl, e
                                )
                            }
                        }
                    }
                    driverLoaded = true
                }
            }
        }

        var filterPassword: (String) -> String = { it }
        fun DatabaseDialect.metadata(): DatabaseDialectMetadata {
            val dialectClassName = this::class.java.name
            // Map core dialect class names to their JDBC metadata counterparts.
            // Exposed v1 splits dialect definitions across `core` (DDL/SQL generation)
            // and `jdbc` (runtime metadata querying); we need the latter for
            // Database.registerDialectMetadata.
            val metadataClassName = when {
                dialectClassName.contains("HSQLDBDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.HSQLDBDialectMetadata"

                dialectClassName.contains("PostgreSQLNGDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.PostgreSQLNGDialectMetadata"

                dialectClassName.contains("PostgreSQLDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.PostgreSQLDialectMetadata"

                dialectClassName.contains("H2Dialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.H2DialectMetadata"

                dialectClassName.contains("MysqlDialect", ignoreCase = true) ||
                        dialectClassName.contains("MySqlDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.MysqlDialectMetadata"

                dialectClassName.contains("MariaDBDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.MariaDBDialectMetadata"

                dialectClassName.contains("SQLServerDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.SQLServerDialectMetadata"

                dialectClassName.contains("OracleDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.OracleDialectMetadata"

                dialectClassName.contains("SQLiteDialect", ignoreCase = true) ->
                    "org.jetbrains.exposed.v1.jdbc.vendors.SQLiteDialectMetadata"

                else -> throw IllegalStateException(
                    "No DatabaseDialectMetadata mapping known for dialect class '$dialectClassName'"
                )
            }
            val metadataClass = try {
                Class.forName(metadataClassName)
            } catch (e: ClassNotFoundException) {
                throw IllegalStateException(
                    "Exposed JDBC metadata class '$metadataClassName' not found on classpath " +
                            "for dialect '$dialectClassName'. Ensure the exposed-jdbc artifact is " +
                            "included as a runtime dependency.",
                    e
                )
            }
            // Prefer a singleton INSTANCE field if present (Kotlin `object`), else
            // fall back to a no-arg constructor.
            return try {
                val instanceField = metadataClass.declaredFields.firstOrNull { it.name == "INSTANCE" }
                if (instanceField != null) {
                    instanceField.isAccessible = true
                    instanceField.get(null) as DatabaseDialectMetadata
                } else {
                    val ctor = metadataClass.getDeclaredConstructor()
                    ctor.isAccessible = true
                    ctor.newInstance() as DatabaseDialectMetadata
                }
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to instantiate DatabaseDialectMetadata '$metadataClassName' for dialect '$dialectClassName'",
                    e
                )
            }
        }
    }
}