package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.util.JsonUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf


class MetadataStorage(private val dataDir: File) : MetadataStorageInterface {
    private val log = org.slf4j.LoggerFactory.getLogger(MetadataStorage::class.java)

    override fun getSessionName(user: User?, session: Session): String {
        log.debug("Fetching session name for session: ${session.sessionId}, user: ${user?.email}")
        val sessionDir: File = ApplicationServices.dataStorageFactory.invoke(dataDir).getDataDir(user, session)
        val settings = getSettings(sessionDir, "settings.json")
        if (settings.containsKey("name")) return settings["name"] as String
        val userMessage = messageFiles(session, sessionDir).entries.minByOrNull { it.key.lastModified() }?.value
        return if (null != userMessage) {
            setJson(sessionDir, "settings.json", settings.plus("name" to userMessage))
            log.debug("Session name for session: ${session.sessionId} is $userMessage")
            userMessage
        } else {
            log.debug("Session ${session.sessionId} has no messages")
            session.sessionId
        }
    }

    override fun setSessionName(user: User?, session: Session, name: String) {
        log.debug("Setting session name for session: ${session.sessionId}, user: ${user?.email} to $name")
        val sessionDir: File = ApplicationServices.dataStorageFactory.invoke(dataDir).getDataDir(user, session)
        val settings = getSettings(sessionDir, "settings.json")
        setJson(sessionDir, "settings.json", settings.plus("name" to name))
    }


    override fun getMessageIds(user: User?, session: Session): List<String> {
        log.debug("Fetching message IDs for session: ${session.sessionId}, user: ${user?.email}")
        val sessionDir: File = ApplicationServices.dataStorageFactory.invoke(dataDir).getDataDir(user, session)
        val settings = getSettings(sessionDir, "internal.json")
        if (settings.containsKey("ids")) return settings["ids"].toString().split(",").toList()
        val ids = messageFiles(session, sessionDir).entries.sortedBy { it.key.lastModified() }
            .map { it.key.nameWithoutExtension }.toList()
        setJson(sessionDir, "internal.json", settings.plus("ids" to ids.joinToString(",")))
        log.debug("Message IDs for session: ${session.sessionId} are $ids")
        return ids
    }

    override fun setMessageIds(user: User?, session: Session, ids: List<String>) {
        log.debug("Setting message IDs for session: ${session.sessionId}, user: ${user?.email} to $ids")
        val sessionDir: File = ApplicationServices.dataStorageFactory.invoke(dataDir).getDataDir(user, session)
        val settings = getSettings(sessionDir, "internal.json")
        setJson(sessionDir, "internal.json", settings.plus("ids" to ids.joinToString(",")))
    }

    override fun getSessionTime(user: User?, session: Session): Date? {
        log.debug("Fetching session time for session: ${session.sessionId}, user: ${user?.email}")
        val sessionDir: File = ApplicationServices.dataStorageFactory.invoke(dataDir).getDataDir(user, session)
        val settings = getSettings(sessionDir, "internal.json")
        val dateFormat = SimpleDateFormat.getDateTimeInstance()
        if (settings.containsKey("time")) return dateFormat.parse(settings["time"] as String)
        val messageFiles = messageFiles(session, sessionDir)
        val file = messageFiles.entries.minByOrNull { it.key.lastModified() }?.key
        return if (null != file) {
            val date = Date(file.lastModified())
            setJson(sessionDir, "internal.json", settings.plus("time" to dateFormat.format(date)))
            log.debug("Session time for session: ${session.sessionId} is $date")
            date
        } else {
            log.debug("Session ${session.sessionId} has no messages")
            null
        }
    }

    override fun setSessionTime(user: User?, session: Session, time: Date) {
        log.debug("Setting session time for session: ${session.sessionId}, user: ${user?.email} to $time")
        val sessionDir: File = ApplicationServices.dataStorageFactory.invoke(dataDir).getDataDir(user, session)
        val settings = getSettings(sessionDir, "internal.json")
        val dateFormat = SimpleDateFormat.getDateTimeInstance()
        setJson(sessionDir, "internal.json", settings.plus("time" to dateFormat.format(time)))
    }


    override fun listSessions(path: String): List<String> {
        log.debug("Listing sessions in dataDir.absolutePath}")
        val files = dataDir.listFiles()
            ?.flatMap { it.listFiles()?.toList() ?: listOf() }
            ?.filter { sessionDir ->
                val resolve = sessionDir.resolve("info.json")
                if (!resolve.exists()) return@filter false
                val infoJson = resolve.readText()
                val infoData = JsonUtil.fromJson<Map<String, String>>(infoJson, typeOf<Map<String, String>>().javaType)
                path == infoData["path"]
            }?.sortedBy { it.lastModified() } ?: listOf()
        log.debug("Found ${files.size} sessions in directory: ${dataDir.absolutePath}")
        return files.map { it.parentFile.name + "-" + it.name }
    }

    private fun getSettings(sessionDir: File, filename: String): Map<*, *> {
        val settingsFile = sessionDir.resolve(filename)
        return if (!settingsFile.exists()) mapOf<String, String>()
        else JsonUtil.objectMapper().readValue(settingsFile, Map::class.java) as Map<*, *>
    }

    private fun <T : Any> setJson(sessionDir: File, filename: String, settings: T): T {
        log.debug("Setting JSON for session directory: ${sessionDir.absolutePath}, filename: $filename")
        val settingsFile = sessionDir.resolve(filename).apply { parentFile.mkdirs() }
        JsonUtil.objectMapper().writeValue(settingsFile, settings)
        return settings
    }

    private fun messageFiles(session: Session, sessionDir: File): Map<File, String> {
        return sessionDir.resolve("messages")
            .apply { mkdirs() }.listFiles()
            ?.filter { file -> file.isFile }
            ?.map { messageFile ->
                val fileText = messageFile.readText()
                val split = fileText.split("<p>")
                if (split.size < 2) {
                    log.debug("Session ${session.sessionId} has no messages in file ${messageFile.name}")
                    messageFile to ""
                } else {
                    val stringList = split[1].split("</p>")
                    if (stringList.isEmpty()) {
                        log.debug("Session ${session.sessionId} has no messages in file ${messageFile.name}")
                        messageFile to ""
                    } else {
                        messageFile to stringList.first()
                    }
                }
            }?.filter { it.second.isNotEmpty() }?.toList()?.toMap() ?: mapOf()
    }

    override fun deleteSession(user: User?, session: Session) {}
}