package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.hsql.HSQLMetadataStorage.Companion.serviceUrl
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.model.User
import org.hsqldb.server.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class HSQLMetadataStorage(root: File?) : MetadataStorageInterface {

  init {
    require(root?.exists() != false || root.mkdirs()) { "Failed to create root directory: $root" }
    log.info("Initializing UserSettingsManager with root directory: {}", root)
  }


   private val connection: Connection get() = getConn(root)

   private val root: File? = root

  override fun getSessionName(user: User?, session: Session): String {
    log.debug("Fetching session name for session: {}, user: {}", session, user?.email)
    val statement = connection.prepareStatement(
      "SELECT value FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'name'"
    )
    statement.setString(1, session.sessionId)
    statement.setString(2, user?.email ?: "")
    val resultSet = statement.executeQuery()
    return if (resultSet.next()) {
      val name = resultSet.getString("value")
      log.debug("Retrieved session name: {} for session: {}", name, session)
      name
    } else {
      session.sessionId
    }
  }

  override fun setSessionName(user: User?, session: Session, name: String) {
    log.debug("Setting session name for session: {}, user: {} to {}", session, user?.email, name)
    val statement = connection.prepareStatement(
      """
            MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
            ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
            WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
            WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
            """
    )
    statement.setString(1, session.sessionId)
    statement.setString(2, user?.email ?: "")
    statement.setString(3, "name")
    statement.setString(4, name)
    statement.setTimestamp(5, Timestamp(System.currentTimeMillis()))
    statement.executeUpdate()
    log.info("Session name set successfully for session: ${session}")
  }

  override fun getMessageIds(user: User?, session: Session): List<String> {
    log.debug("Fetching message IDs for session: {}, user: {}", session, user?.email)
    val statement = connection.prepareStatement(
      "SELECT value FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'message_ids'"
    )
    statement.setString(1, session.sessionId)
    statement.setString(2, user?.email ?: "")
    val resultSet = statement.executeQuery()
    return if (resultSet.next()) {
      val ids = resultSet.getString("value").split(",")
      log.debug("Retrieved {} message IDs for session: {}", ids.size, session)
      ids
    } else {
      log.debug("No message IDs found for session: {}", session)
      emptyList()
    }
  }

  override fun setMessageIds(user: User?, session: Session, ids: List<String>) {
    log.debug("Setting message IDs for session: {}, user: {} to {}", session, user?.email, ids)
    val statement = connection.prepareStatement(
      """
            MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
            ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
            WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
            WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
            """
    )
    statement.setString(1, session.sessionId)
    statement.setString(2, user?.email ?: "")
    statement.setString(3, "message_ids")
    statement.setString(4, ids.joinToString(","))
    statement.setTimestamp(5, Timestamp(System.currentTimeMillis()))
    statement.executeUpdate()
    log.debug("Set {} message IDs for session: {}", ids.size, session)
  }

  override fun getSessionTime(user: User?, session: Session): Date? {
    log.debug("Fetching session time for session: {}, user: {}", session, user?.email)
    val statement = connection.prepareStatement(
      "SELECT value, timestamp FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'session_time'"
    )
    statement.setString(1, session.sessionId)
    statement.setString(2, user?.email ?: "")
    val resultSet = statement.executeQuery()
    return if (resultSet.next()) {
      val time = resultSet.getString("value")
      try {
        Date(time.toLong()).also {
          log.debug("Retrieved session time: {} for session: {}", it, session)
        }
      } catch (e: NumberFormatException) {
        log.warn("Invalid session time value: $time, falling back to timestamp for session: ${session}")
        resultSet.getTimestamp("timestamp")
      }
    } else {
      Date()
    }
  }

  override fun setSessionTime(user: User?, session: Session, time: Date) {
    log.debug("Setting session time for session: {}, user: {} to {}", session, user?.email, time)
    val statement = connection.prepareStatement(
      """
            MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
            ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
            WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
            WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
            """
    )
    statement.setString(1, session.sessionId)
    statement.setString(2, user?.email ?: "")
    statement.setString(3, "session_time")
    statement.setString(4, time.time.toString())
    statement.setTimestamp(5, Timestamp(time.time))
    statement.executeUpdate()
    log.info("Session time set to $time for session: ${session}")
  }

  override fun listSessions(path: String): List<String> {
    log.debug("Listing sessions for path: $path")
    val statement = connection.prepareStatement(
      "SELECT DISTINCT session_id FROM metadata WHERE value = ? AND key = 'path'"
    )
    statement.setString(1, path)
    val resultSet = statement.executeQuery()
    val sessions = mutableListOf<String>()
    while (resultSet.next()) {
      sessions.add(resultSet.getString("session_id"))
    }
    log.info("Found ${sessions.size} sessions for path: $path")
    return sessions
  }
   override fun getSessionOwner(session: Session): String? {
     log.debug("Fetching session owner for session: {}", session)
     val statement = connection.prepareStatement(
       "SELECT value FROM metadata WHERE session_id = ? AND key = 'owner_id'"
     )
     statement.setString(1, session.sessionId)
     val resultSet = statement.executeQuery()
     return if (resultSet.next()) {
       val ownerId = resultSet.getString("value")
       log.debug("Retrieved session owner: {} for session: {}", ownerId, session)
       ownerId
     } else {
       log.debug("No owner found for session: {}", session)
       null
     }
   }
   override fun setSessionOwner(session: Session, ownerId: String?) {
     log.debug("Setting session owner for session: {} to {}", session, ownerId)
     val statement = connection.prepareStatement(
       """
             MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
             ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
             WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
             WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
             """
     )
     statement.setString(1, session.sessionId)
     statement.setString(2, "")
     statement.setString(3, "owner_id")
     statement.setString(4, ownerId)
     statement.setTimestamp(5, Timestamp(System.currentTimeMillis()))
     statement.executeUpdate()
     log.info("Session owner set to $ownerId for session: ${session}")
   }


  override fun deleteSession(user: User?, session: Session) {
    log.debug("Deleting session: {}, user: {}", session, user?.email)
    val statement = connection.prepareStatement(
      "DELETE FROM metadata WHERE session_id = ? AND user_email = ?"
    )
    statement.setString(1, session.sessionId)
    statement.setString(2, user?.email ?: "")
    statement.executeUpdate()
    log.info("Deleted session: ${session} for user: ${user?.email ?: "anonymous"}")
  }

  companion object {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Optional fully-qualified JDBC URL to a remote HSQL service.
     * Example: "jdbc:hsqldb:hsql://my-host:9001/metadata"
     * If non-null, the storage operates in CLIENT mode and connects to this URL
     * instead of starting an embedded HSQL server.
     *
     * System property: `cognotik.hsql.metadata.serviceUrl`
     */
    @JvmStatic
    var serviceUrl: String? = System.getProperty("cognotik.hsql.metadata.serviceUrl")

    /**
     * Username used when connecting in CLIENT mode (see [serviceUrl]).
     *
     * System property: `cognotik.hsql.metadata.serviceUser` (default: `SA`)
     */
    @JvmStatic
    var serviceUser: String = System.getProperty("cognotik.hsql.metadata.serviceUser", "SA")

    /**
     * Password used when connecting in CLIENT mode (see [serviceUrl]).
     *
     * System property: `cognotik.hsql.metadata.servicePassword` (default: empty string)
     */
    @JvmStatic
    var servicePassword: String = System.getProperty("cognotik.hsql.metadata.servicePassword", "")

    /**
     * Host/interface the embedded HSQL server binds to (server mode).
     *
     * System property: `cognotik.hsql.metadata.serverHost` (default: `localhost`)
     */
    @JvmStatic
    var serverHost: String = System.getProperty("cognotik.hsql.metadata.serverHost", "localhost")

    /**
     * Port the embedded HSQL server listens on (server mode). 0 = pick automatically.
     *
     * System property: `cognotik.hsql.metadata.serverPort` (default: `9001`)
     */
    @JvmStatic
    var serverPort: Int = System.getProperty("cognotik.hsql.metadata.serverPort", "9001").toInt()

    /**
     * Whether the embedded HSQL server runs silently (no console logging).
     *
     * System property: `cognotik.hsql.metadata.serverSilent` (default: `true`)
     */
    @JvmStatic
    var serverSilent: Boolean = System.getProperty("cognotik.hsql.metadata.serverSilent", "true").toBoolean()

    /**
     * Optional override for the database name (both in-memory and file modes).
     *
     * System property: `cognotik.hsql.metadata.dbName` (default: `metadata`)
     */
    @JvmStatic
    var dbName: String = System.getProperty("cognotik.hsql.metadata.dbName", "metadata")

    fun getLocalServiceUrl(root: File = ApplicationServicesConfig.dataStorageRoot.resolve("metadatadb")): String {
      val server = ensureServerStarted(root)
      return "jdbc:hsqldb:hsql://${serverHost}:${server.port}/${dbName}"
    }


    @Volatile
    private var embeddedServer: Server? = null
     @Volatile
     private var driverLoaded: Boolean = false
     /**
      * Cached connections keyed by JDBC URL. Ensures we don't open multiple
      * connections (and don't run schema creation more than once) for the
      * same logical database.
      */
     private val connections = ConcurrentHashMap<String, Connection>()
     /**
      * Tracks JDBC URLs for which schema creation has already been performed,
      * so concurrent/repeated callers don't redundantly issue DDL.
      */
     private val schemasInitialized = ConcurrentHashMap.newKeySet<String>()
     /**
      * Static accessor: lazily starts the embedded DB (if needed) and returns
      * a shared [Connection]. Schema creation is deduplicated per JDBC URL.
      */
     @JvmStatic
     @JvmOverloads
     fun getConn(root: File? = null): Connection {
       if (!driverLoaded) {
         synchronized(this) {
           if (!driverLoaded) {
             Class.forName("org.hsqldb.jdbc.JDBCDriver")
             driverLoaded = true
           }
         }
       }
       val url: String
       val username: String
       val password: String
       val remoteUrl = serviceUrl
       if (remoteUrl != null) {
         log.info("Connecting to external HSQL service at: {}", remoteUrl)
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
           log.info("Opening HSQL connection to: {}", url)
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
         log.debug("Creating database schema if not exists for {}", url)
         connection.createStatement().executeUpdate(
           """
                 CREATE TABLE IF NOT EXISTS metadata (
                     session_id VARCHAR(255),
                     user_email VARCHAR(255),
                     key VARCHAR(255),
                     value LONGVARCHAR,
                     timestamp TIMESTAMP,
                     PRIMARY KEY (session_id, user_email, key)
                 )
                 """
         )
         schemasInitialized.add(url)
       }
     }

    @Synchronized
    private fun ensureServerStarted(root: File?): Server {
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
        "Started embedded HSQL server on {}:{} (db={})",
        serverHost, server.port, server.getDatabaseName(0, true)
      )
      Runtime.getRuntime().addShutdownHook(Thread {
        try {
          server.shutdown()
          log.info("Embedded HSQL server stopped")
        } catch (e: Exception) {
          log.warn("Error shutting down embedded HSQL server", e)
        }
      })
      embeddedServer = server
      return server
    }
  }

}