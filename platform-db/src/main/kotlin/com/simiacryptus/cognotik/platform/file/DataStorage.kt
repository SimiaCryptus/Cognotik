@file:Suppress("DEPRECATION")

package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.StorageInterface
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

class DataStorage(
  private val dataDir: File,
  override val metadataStorage: MetadataStorageInterface = ApplicationServicesImpl.fileApplicationServices(dataDir.parentFile).metadataDB
) : StorageInterface {

  init {
    log.info("Data storage directory: ${dataDir.absolutePath}")
  }

  override fun getMessageMap(
    user: User?,
    session: Session
  ): LinkedHashMap<String, String> {
    Session.validateSessionId(session)
    log.debug("Fetching messages for session: {}, user: {}", session, user?.email)
    val messageDir =
      getSystemDir(user, session).resolve("messages/")
        .apply { mkdirs() }
    val messages = LinkedHashMap<String, String>()
    getMessageIds(user, session).forEach { messageId ->
      val file = File(messageDir, "$messageId.json")
      if (file.exists()) {
        val message = JsonUtil.objectMapper().readValue(file, SecureString::class.java)?.decrypt ?: ""
        messages[messageId] = message
      }
    }
    log.debug("Loaded {} messages for session: {}", messages.size, session)
    return messages
  }

  @Deprecated("Use getMessageMap", ReplaceWith("getMessageMap(user, session)"))
  fun getMessages(user: User?, session: Session): LinkedHashMap<String, String> =
    getMessageMap(user, session)

  override fun <T : Any> getJson(
    user: User?,
    session: Session,
    filename: String,
    type: Class<T>
  ): T? {
    val file = getSystemDir(user, session).resolve(filename)
    if (!file.exists()) return null
    return try {
      JsonUtil.objectMapper().readValue(file, type)
    } catch (e: Exception) {
      log.warn("Failed to read JSON '{}' for session: {}", filename, session, e)
      null
    }
  }


  @Deprecated("Exposes the local filesystem and grants callers unrestricted authority over the directory; use (openRead/openWrite/list/delete).")
  override fun getUserDir(
    user: User?,
    session: Session
  ) = if (userPaths.containsKey(session)) {
    userPaths[session]!!
  } else {
    getSystemDir(user, session).apply { mkdirs() }
  }

  @Deprecated("Exposes the local filesystem; use (openRead/openWrite/list/delete) for content access.")
  override fun getSystemDir(
    user: User?,
    session: Session
  ): File {
    // The Session.NULL sentinel has an empty id; deriving a directory from it
    // would silently collapse every session onto one path (REVIEW.md §3.11).
    require(!session.isNull()) { "A valid session ID is required for storage access" }
    if (systemPaths.containsKey(session)) {
      return systemPaths[session]!!
    }
    Session.validateSessionId(session)
    log.debug("Getting data directory for session: {}, user: {}", session, user?.id)
    val parts = session.sessionId.split("-")
    return when (parts.size) {
      3 -> {
        val root = when {
          parts[0] == "G" -> dataDir.resolve("global")
          parts[0] == "U" -> dataDir.resolve("user-sessions/${user?.id}")
          else -> throw IllegalArgumentException("Invalid session ID: $session")
        }
        val dateDir = File(root, parts[1])
        val sessionDir = File(dateDir, parts[2])
        log.debug("Session directory for session: {} is {}", session, sessionDir.absolutePath)
        sessionDir
      }

      2 -> {
        val dateDir = dataDir.resolve("global").resolve(parts[0])
        val sessionDir = dateDir.resolve(parts[1])
        log.debug("Session directory for session: {} is {}", session, sessionDir.absolutePath)
        sessionDir
      }

      else -> {
        throw IllegalArgumentException("Invalid session ID: $session")
      }
    }
  }

  override fun listSessionsForUser(
    user: User?,
    path: String
  ): List<Session> {
    log.debug("Listing sessions for user: ${user?.email}")
    val globalSessions = listSessions(path)
    val userSessions =
      if (user == null) listOf() else metadataStorage.listSessionsByPath(
        path
      )
    log.debug("Found ${globalSessions.size} global sessions and ${userSessions.size} user sessions for user: ${user?.email}")
    return ((globalSessions.map {
      try {
        Session("G-$it")
      } catch (_: Exception) {
        null
      }
    }).toList() + (userSessions.map {
      try {
        Session("U-$it")
      } catch (_: Exception) {
        null
      }
    }).toList()).filterNotNull()
  }

  @Deprecated("Use listSessionsForUser", ReplaceWith("listSessionsForUser(user, path)"))
  fun listSessions(user: User?, path: String): List<Session> = listSessionsForUser(user, path)


  override fun <T : Any> setJson(
    user: User?,
    session: Session,
    filename: String,
    settings: T
  ) = setJson(getSystemDir(user, session), filename, settings)

  private fun <T : Any> setJson(sessionDir: File, filename: String, settings: T): T {
    log.debug("Setting JSON for session directory: ${sessionDir.absolutePath}, filename: $filename")
    val settingsFile = sessionDir.resolve(filename).apply { parentFile.mkdirs() }
    JsonUtil.objectMapper().writeValue(settingsFile, settings)
    return settings
  }

  override fun updateMessage(
    user: User?,
    session: Session,
    messageId: String,
    value: String
  ) {
    Session.validateSessionId(session)
    log.debug("Updating message for session: {}, messageId: {}, user: {}", session, messageId, user?.email)
    val file =
      getSystemDir(user, session).resolve("messages/$messageId.json")
        .apply { parentFile.mkdirs() }
    if (!file.exists()) {
      file.parentFile.mkdirs()
      addMessageID(user, session, messageId)
    }
    JsonUtil.objectMapper().writeValue(file, SecureString(value))
  }

  private fun addMessageID(
    user: User?,
    session: Session,
    messageId: String
  ) {
    synchronized(this) {
      log.debug("Adding message ID for session: {}, messageId: {}, user: {}", session, messageId, user?.email)
      setMessageIds(user, session, getMessageIds(user, session) + messageId)
    }
  }

  override fun userRootFor(user: User): File =
    dataDir.resolve("users").resolve(user.email).apply { mkdirs() }

  @Deprecated("Use userRootFor(user)", ReplaceWith("userRootFor(user!!)"))
  fun userRoot(user: User?): File =
    userRootFor(user ?: throw IllegalArgumentException("User required for private session"))

  override fun deleteSession(user: User?, session: Session) {
    Session.validateSessionId(session)
    log.debug("Deleting session: {}, user: {}", session, user?.email)
    val sessionDir = getSystemDir(user, session)
    metadataStorage.deleteSession(user, session)
    sessionDir.deleteRecursively()
  }

  override fun deleteSessionIfExists(user: User?, session: Session): Boolean {
    Session.validateSessionId(session)
    val sessionDir = getSystemDir(user, session)
    if (!sessionDir.exists() && !metadataStorage.exists(user, session)) {
      log.debug("Session {} does not exist; nothing to delete", session)
      return false
    }
    deleteSession(user, session)
    return true
  }


  @Deprecated("Use metadataStorage instead")
  fun listSessions(path: String): List<String> =
    metadataStorage.listSessionsByPath(path)

  @Deprecated("Use metadataStorage instead")
  fun getSessionName(
    user: User?,
    session: Session
  ): String =
    metadataStorage.getSessionName(user, session)

  @Deprecated("Use metadataStorage instead")
  fun getMessageIds(
    user: User?,
    session: Session
  ): List<String> =
    metadataStorage.getMessageIds(user, session)

  @Deprecated("Use metadataStorage instead")
  fun setMessageIds(
    user: User?,
    session: Session,
    ids: List<String>
  ) = metadataStorage.setMessageIds(user, session, ids)

  @Deprecated("Use metadataStorage instead")
  fun getSessionTime(
    user: User?,
    session: Session
  ): Instant? = metadataStorage.getSessionTimestamp(user, session)

  companion object {
    val log = LoggerFactory.getLogger(StorageInterface::class.java)
    val userPaths = mutableMapOf<Session, File>()
    val systemPaths = mutableMapOf<Session, File>()
  }
}