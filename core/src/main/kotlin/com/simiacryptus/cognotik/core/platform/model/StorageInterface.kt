package com.simiacryptus.cognotik.core.platform.model

import com.simiacryptus.cognotik.core.platform.Session
import java.io.File
import java.util.*

interface StorageInterface {

  fun getMessages(
    user: User?,
    session: Session
  ): LinkedHashMap<String, String>

  fun getSessionDir(
    user: User?,
    session: Session
  ): File

  fun getDataDir(
    user: User?,
    session: Session
  ): File

  @Deprecated("Use metadataStorage instead")
  fun getSessionName(
    user: User?,
    session: Session
  ): String

  @Deprecated("Use metadataStorage instead")
  fun getSessionTime(
    user: User?,
    session: Session
  ): Date?

  fun listSessions(
    user: User?,
    path: String,
  ): List<Session>

  fun <T : Any> setJson(
    user: User?,
    session: Session,
    filename: String,
    settings: T
  ): T

  fun updateMessage(
    user: User?,
    session: Session,
    messageId: String,
    value: String
  )

  @Deprecated("Use metadataStorage instead")
  fun listSessions(dir: File, path: String): List<String>
  fun userRoot(user: User?): File
  fun deleteSession(user: User?, session: Session)

  @Deprecated("Use metadataStorage instead")
  fun getMessageIds(
    user: User?,
    session: Session
  ): List<String>

  @Deprecated("Use metadataStorage instead")
  fun setMessageIds(
    user: User?,
    session: Session,
    ids: List<String>
  )

  companion object {
    @Deprecated("Use Session.long64() instead", ReplaceWith("Session.long64()"))
    inline fun long64() = Session.long64()

    @Deprecated("Use Session.validateSessionId(session) instead", ReplaceWith("Session.validateSessionId(session)"))
    inline fun validateSessionId(session: Session) = Session.validateSessionId(session)

    @Deprecated("Use Session.newGlobalID() instead", ReplaceWith("Session.newGlobalID()"))
    inline fun newGlobalID(): Session = Session.newGlobalID()

    @Deprecated("Use Session.newUserID() instead", ReplaceWith("Session.newUserID()"))
    inline fun newUserID(): Session = Session.newUserID()


    @Deprecated("Use Session.parseSessionID(sessionID) instead", ReplaceWith("Session.parseSessionID(sessionID)"))
    inline fun parseSessionID(sessionID: String): Session = Session.parseSessionID(sessionID)

    @Deprecated("Use Session.id2() instead")
    private inline fun id2() = Session.long64().filter {
      it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9'
    }.take(4)

  }
}