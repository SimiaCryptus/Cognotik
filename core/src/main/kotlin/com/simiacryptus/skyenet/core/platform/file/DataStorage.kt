package com.simiacryptus.skyenet.core.platform.file

import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.util.JsonUtil
import java.io.File
import java.util.*

open class DataStorage(
  private val dataDir: File,
) : StorageInterface {

  init {
    log.debug("Data directory: ${dataDir.absolutePath}")
  }

  override fun getMessages(
    user: User?,
    session: Session
  ): LinkedHashMap<String, String> {
    Session.validateSessionId(session)
    log.debug("Fetching messages for session: ${session.sessionId}, user: ${user?.email}")
    val messageDir =
      getDataDir(user, session).resolve("messages/")
        .apply { mkdirs() }
    val messages = LinkedHashMap<String, String>()
    getMessageIds(user, session).forEach { messageId ->
      val file = File(messageDir, "$messageId.json")
      if (file.exists()) {
        val message = JsonUtil.objectMapper().readValue(file, String::class.java)
        messages[messageId] = message
      }
    }
    log.debug("Loaded ${messages.size} messages for session: ${session.sessionId}")
    return messages
  }

  override fun getSessionDir(
    user: User?,
    session: Session
  ) = if (sessionPaths.containsKey(session)) {
    sessionPaths[session]!!
  } else {
    getDataDir(user, session).apply { mkdirs() }
  }

  override fun getDataDir(
    user: User?,
    session: Session
  ): File {
    Session.validateSessionId(session)
    log.debug("Getting data directory for session: ${session.sessionId}, user: ${user?.email}")
    val parts = session.sessionId.split("-")
    return when (parts.size) {
      3 -> {
        val root = when {
          parts[0] == "G" -> dataDir.resolve("global")
          parts[0] == "U" -> dataDir.resolve("user-sessions/$user")
          else -> throw IllegalArgumentException("Invalid session ID: $session")
        }
        val dateDir = File(root, parts[1])
        val sessionDir = File(dateDir, parts[2])
        log.debug("Session directory for session: ${session.sessionId} is ${sessionDir.absolutePath}")
        sessionDir
      }

      2 -> {
        val dateDir = dataDir.resolve("global").resolve(parts[0])
        val sessionDir = dateDir.resolve(parts[1])
        log.debug("Session directory for session: ${session.sessionId} is ${sessionDir.absolutePath}")
        sessionDir
      }

      else -> {
        throw IllegalArgumentException("Invalid session ID: $session")
      }
    }
  }

  override fun listSessions(
    user: User?,
    path: String
  ): List<Session> {
    log.debug("Listing sessions for user: ${user?.email}")
    val globalSessions = listSessions(dataDir.resolve("global"), path)
    val userSessions = if (user == null) listOf() else ApplicationServices.metadataStorageFactory(dataDir).listSessions(
      path
    )
    log.debug("Found ${globalSessions.size} global sessions and ${userSessions.size} user sessions for user: ${user?.email}")
    return ((globalSessions.map {
      try {
        Session("G-$it")
      } catch (e: Exception) {
        null
      }
    }).toList() + (userSessions.map {
      try {
        Session("U-$it")
      } catch (e: Exception) {
        null
      }
    }).toList()).filterNotNull()
  }

  override fun <T : Any> setJson(
    user: User?,
    session: Session,
    filename: String,
    settings: T
  ) = setJson(getDataDir(user, session), filename, settings)

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
    log.debug("Updating message for session: ${session.sessionId}, messageId: $messageId, user: ${user?.email}")
    val file =
      getDataDir(user, session).resolve("messages/$messageId.json")
        .apply { parentFile.mkdirs() }
    if (!file.exists()) {
      file.parentFile.mkdirs()
      addMessageID(user, session, messageId)
    }
    JsonUtil.objectMapper().writeValue(file, value)
  }

  protected open fun addMessageID(
    user: User?,
    session: Session,
    messageId: String
  ) {
    synchronized(this) {
      log.debug("Adding message ID for session: ${session.sessionId}, messageId: $messageId, user: ${user?.email}")
      setMessageIds(user, session, getMessageIds(user, session) + messageId)
    }
  }

  override fun userRoot(user: User?) = dataDir.resolve("users").resolve(
    if (user?.email != null) {
      user.email
    } else {
      throw IllegalArgumentException("User required for private session")
    }
  ).apply { mkdirs() }

  override fun deleteSession(user: User?, session: Session) {
    Session.validateSessionId(session)
    log.debug("Deleting session: ${session.sessionId}, user: ${user?.email}")
    val sessionDir = getDataDir(user, session)
    ApplicationServices.metadataStorageFactory(dataDir).deleteSession(user, session)
    sessionDir.deleteRecursively()
  }
  @Deprecated("Use metadataStorage instead")

  override fun listSessions(dir: File, path: String): List<String> = ApplicationServices.metadataStorageFactory(dataDir).listSessions(path)
  @Deprecated("Use metadataStorage instead")

  override fun getSessionName(
    user: User?,
    session: Session
  ): String = ApplicationServices.metadataStorageFactory(dataDir).getSessionName(user, session)
  @Deprecated("Use metadataStorage instead")

  override fun getMessageIds(
    user: User?,
    session: Session
  ): List<String> = ApplicationServices.metadataStorageFactory(dataDir).getMessageIds(user, session)
  @Deprecated("Use metadataStorage instead")

  override fun setMessageIds(
    user: User?,
    session: Session,
    ids: List<String>
  ) = ApplicationServices.metadataStorageFactory(dataDir).setMessageIds(user, session, ids)
  @Deprecated("Use metadataStorage instead")

  override fun getSessionTime(
    user: User?,
    session: Session
  ): Date? = ApplicationServices.metadataStorageFactory(dataDir).getSessionTime(user, session)

  companion object {

    val log = org.slf4j.LoggerFactory.getLogger(DataStorage::class.java)
    val sessionPaths = mutableMapOf<Session, File>()

  }
}