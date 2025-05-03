package com.simiacryptus.cognotik.platform.file

import java.nio.file.Files

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.util.JsonUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*

abstract class StorageInterfaceTest(val storage: StorageInterface) {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(StorageInterfaceTest::class.java)
    }

    @Test
    fun testGetJson() {
        log.info("Starting testGetJson")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")
        val filename = "test.json"

        log.debug("Attempting to read JSON file: {}", filename)
        val settingsFile = File(storage.getSessionDir(user, session), filename)
        val result = if (!settingsFile.exists()) null else {
            JsonUtil.objectMapper().readValue(settingsFile, Any::class.java) as Any
        }

        log.info("Asserting result is null for non-existing JSON file")
        Assertions.assertNull(result, "Expected null result for non-existing JSON file")
        log.info("testGetJson completed successfully")
    }

    @Test
    fun testGetMessages() {
        log.info("Starting testGetMessages")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        log.debug("Retrieving messages for user: {} and session: {}", user.email, session.sessionId)
        val messages = storage.getMessages(user, session)

        log.info("Asserting messages type is LinkedHashMap")
        assertTrue(messages is LinkedHashMap<*, *>, "Expected LinkedHashMap type for messages")
        log.info("testGetMessages completed successfully")
    }

    @Test
    fun testGetSessionDir() {
        log.info("Starting testGetSessionDir")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        log.debug("Getting session directory for user: {} and session: {}", user.email, session.sessionId)
        val sessionDir = storage.getSessionDir(user, session)

        log.info("Asserting session directory is of type File")
        assertTrue(sessionDir is File, "Expected File type for session directory")
        log.info("testGetSessionDir completed successfully")
    }

    @Test
    fun testGetSessionName() {
        log.info("Starting testGetSessionName")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        log.debug("Getting session name for user: {} and session: {}", user.email, session.sessionId)
        val sessionName = storage.getSessionName(user, session)

        log.info("Asserting session name is not null and is of type String")
        Assertions.assertNotNull(sessionName)
        assertTrue(sessionName is String)
        log.info("testGetSessionName completed successfully")
    }

    @Test
    fun testGetSessionTime() {
        log.info("Starting testGetSessionTime")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")
        log.debug("Updating message for user: {} and session: {}", user.email, session.sessionId)
        storage.updateMessage(user, session, "msg001", "<p>Hello, World!</p><p>Hello, World!</p>")

        log.debug("Getting session time for user: {} and session: {}", user.email, session.sessionId)
        val sessionTime = storage.getSessionTime(user, session)

        log.info("Asserting session time is not null and is of type Date")
        Assertions.assertNotNull(sessionTime)
        assertTrue(sessionTime is Date)
        log.info("testGetSessionTime completed successfully")
    }

    @Test
    fun testListSessions() {
        log.info("Starting testListSessions")

        val user = User(email = "test@example.com")

        log.debug("Listing sessions for user: {}", user.email)
        val sessions = storage.listSessions(user, "")

        log.info("Asserting sessions list is not null and is of type List")
        Assertions.assertNotNull(sessions)
        assertTrue(sessions is List<*>)
        log.info("testListSessions completed successfully")
    }

    @Test
    fun testSetJson() {
        log.info("Starting testSetJson")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")
        val filename = "settings.json"
        val settings = mapOf("theme" to "dark")

        log.debug("Setting JSON for user: {} and session: {}", user.email, session.sessionId)
        val result = storage.setJson(user, session, filename, settings)

        log.info("Asserting JSON setting result is not null and matches input")
        Assertions.assertNotNull(result)
        assertEquals(settings, result)
        log.info("testSetJson completed successfully")
    }

    @Test
    fun testUpdateMessage() {
        log.info("Starting testUpdateMessage")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")
        val messageId = "msg001"
        val value = "Hello, World!"

        try {
            log.debug("Updating message for user: {} and session: {}", user.email, session.sessionId)
            storage.updateMessage(user, session, messageId, value)
            log.info("Message updated successfully")

        } catch (e: Exception) {
            log.error("Exception thrown while updating message", e)
            Assertions.fail("Exception should not be thrown")
        }
        log.info("testUpdateMessage completed successfully")
    }

    @Test
    fun testListSessionsWithDir() {
        log.info("Starting testListSessionsWithDir")

        val directory = File(System.getProperty("user.dir"))


        log.debug("Listing sessions for directory: {}", directory.absolutePath)
        val sessionList = storage.listSessions(directory, "")

        log.info("Asserting session list is not null and is of type List")
        Assertions.assertNotNull(sessionList)
        assertTrue(sessionList is List<*>)
        log.info("testListSessionsWithDir completed successfully")
    }

    @Test
    fun testUserRoot() {
        log.info("Starting testUserRoot")

        val user = User(email = "test@example.com")

        log.debug("Getting user root for user: {}", user.email)
        val userRoot = storage.userRoot(user)

        log.info("Asserting user root is not null and is of type File")
        Assertions.assertNotNull(userRoot)
        assertTrue(userRoot is File)
        log.info("testUserRoot completed successfully")
    }

    @Test
    fun testDeleteSession() {
        log.info("Starting testDeleteSession")

        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        try {
            log.debug("Deleting session for user: {} and session: {}", user.email, session.sessionId)
            storage.deleteSession(user, session)
            log.info("Session deleted successfully")

        } catch (e: Exception) {
            log.error("Exception thrown while deleting session", e)
            Assertions.fail("Exception should not be thrown")
        }
        log.info("testDeleteSession completed successfully")
    }


}
class DataStorageTest : StorageInterfaceTest(DataStorage(Files.createTempDirectory("sessionDataTest").toFile()))

