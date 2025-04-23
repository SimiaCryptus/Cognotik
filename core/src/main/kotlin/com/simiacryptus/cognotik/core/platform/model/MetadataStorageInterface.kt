package com.simiacryptus.cognotik.core.platform.model

import com.simiacryptus.cognotik.core.platform.Session
import java.util.*

interface MetadataStorageInterface {
  fun getSessionName(user: User?, session: Session): String
  fun setSessionName(user: User?, session: Session, name: String)
  fun getMessageIds(user: User?, session: Session): List<String>
  fun setMessageIds(user: User?, session: Session, ids: List<String>)
  fun getSessionTime(user: User?, session: Session): Date?
  fun setSessionTime(user: User?, session: Session, time: Date)
  fun listSessions(path: String): List<String>
  fun deleteSession(user: User?, session: Session)
}