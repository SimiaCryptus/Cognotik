package com.simiacryptus.cognotik.platform.test

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.model.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.*

abstract class MetadataStorageInterfaceTest(val storage: MetadataStorageInterface) {
    companion object {
        private val log = LoggerFactory.getLogger(MetadataStorageInterfaceTest::class.java)
    }


    @Test
    fun testGetSessionName() {
        log.info("Starting testGetSessionName")
        // Arrange
        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        // Act
        log.debug("Retrieving session name for user {} and session {}", user.email, session.sessionId)
        val sessionName = storage.getSessionName(user, session)

        // Assert
        log.debug("Retrieved session name: {}", sessionName)
        assertNotNull(sessionName)
        assertTrue(sessionName is String)
        log.info("Completed testGetSessionName successfully")
    }

    @Test
    fun testSetSessionName() {
        log.info("Starting testSetSessionName")
        // Arrange
        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")
        val newName = "Test Session"

        // Act
        log.debug("Setting session name to '{}' for user {} and session {}", newName, user.email, session.sessionId)
        storage.setSessionName(user, session, newName)
        log.debug("Retrieving session name for verification")
        val retrievedName = storage.getSessionName(user, session)

        // Assert
        log.debug("Retrieved session name: {}", retrievedName)
        assertEquals(newName, retrievedName)
        log.info("Completed testSetSessionName successfully")
    }

    @Test
    fun testGetMessageIds() {
        log.info("Starting testGetMessageIds")
        // Arrange
        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        // Act
        log.debug("Retrieving message IDs for user {} and session {}", user.email, session.sessionId)
        val messageIds = storage.getMessageIds(user, session)

        // Assert
        log.debug("Retrieved message IDs: {}", messageIds)
        assertNotNull(messageIds)
        assertTrue(messageIds is List<*>)
        log.info("Completed testGetMessageIds successfully")
    }

    @Test
    fun testSetMessageIds() {
        log.info("Starting testSetMessageIds")
        // Arrange
        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")
        val newIds = listOf("msg001", "msg002", "msg003")

        // Act
        log.debug("Setting message IDs {} for user {} and session {}", newIds, user.email, session.sessionId)
        storage.setMessageIds(user, session, newIds)
        log.debug("Retrieving message IDs for verification")
        val retrievedIds = storage.getMessageIds(user, session)

        // Assert
        log.debug("Retrieved message IDs: {}", retrievedIds)
        assertEquals(newIds, retrievedIds)
        log.info("Completed testSetMessageIds successfully")
    }

    //    @Test
    fun testGetSessionTime() {
        log.info("Starting testGetSessionTime")
        // Arrange
        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        // Act
        log.debug("Retrieving session time for user {} and session {}", user.email, session.sessionId)
        val sessionTime = storage.getSessionTime(user, session)

        // Assert
        log.debug("Retrieved session time: {}", sessionTime)
        assertNotNull(sessionTime)
        assertTrue(sessionTime is Date)
        log.info("Completed testGetSessionTime successfully")
    }

    @Test
    fun testSetSessionTime() {
        log.info("Starting testSetSessionTime")
        // Arrange
        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")
        val newTime = Date()

        // Act
        log.debug("Setting session time to {} for user {} and session {}", newTime, user.email, session.sessionId)
        storage.setSessionTime(user, session, newTime)
        log.debug("Retrieving session time for verification")
        val retrievedTime = storage.getSessionTime(user, session)

        // Assert
        log.debug("Retrieved session time: {}", retrievedTime)
        assertEquals(newTime.toString(), retrievedTime.toString())
        log.info("Completed testSetSessionTime successfully")
    }

    @Test
    fun testListSessions() {
        log.info("Starting testListSessions")
        // Arrange
        val path = ""

        // Act
        log.debug("Listing sessions for path: {}", path)
        val sessions = storage.listSessions(path)

        // Assert
        log.debug("Retrieved sessions: {}", sessions)
        assertNotNull(sessions)
        assertTrue(sessions is List<*>)
        log.info("Completed testListSessions successfully")
    }

    @Test
    fun testDeleteSession() {
        log.info("Starting testDeleteSession")
        // Arrange
        val user = User(email = "test@example.com")
        val session = Session("G-20230101-1234")

        // Act and Assert
        try {
            log.debug("Attempting to delete session {} for user {}", session.sessionId, user.email)
            storage.deleteSession(user, session)
            log.info("Session deleted successfully")
            // If no exception is thrown, the test passes.
        } catch (e: Exception) {
            log.error("Failed to delete session: {}", e.message, e)
            fail("Exception should not be thrown")
        }
        log.info("Completed testDeleteSession successfully")
    }
}