package com.simiacryptus.cognotik.platform.hsql

    import com.simiacryptus.cognotik.platform.Session
    import com.simiacryptus.cognotik.platform.model.User
    import org.junit.jupiter.api.AfterEach
    import org.junit.jupiter.api.Assertions.*
    import org.junit.jupiter.api.BeforeEach
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.assertDoesNotThrow
    import java.io.File
    import java.util.*

    class MetadataStorageDBTest {


        private lateinit var storage: MetadataStorageDB

        private val testUser = User(
            id = "user-123",
            email = "test@example.com",
            name = "Test User"
        )

        private val anotherUser = User(
            id = "user-456",
            email = "another@example.com",
            name = "Another User"
        )

        private val testSession = Session("G-20240101-test1234")
        private val anotherSession = Session("G-20240101-test5678")

        @BeforeEach
        fun setUp() {
             // Use null root => in-memory ephemeral HSQL database (mem:<dbName>).
             storage = MetadataStorageDB(null)
             // Clean DB between tests to ensure isolation
             try {
                 MetadataStorageDB.getConn(null).use { conn ->
                     conn.createStatement().use { stmt ->
                         stmt.execute("DELETE FROM metadata")
                     }
                 }
             } catch (e: Exception) {
                 // ignore - table may not exist yet on first run
             }
        }

        @AfterEach
        fun tearDown() {
            try {
                 MetadataStorageDB.getConn(null).use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("DELETE FROM metadata")
                    }
                }
            } catch (e: Exception) {

            }
        }


        @Test
        fun `init should create root directory if it does not exist`() {
             // Create a uniquely-named directory under java.io.tmpdir so the test
             // verifies directory-creation behavior without using a persistent DB.
             val newDir = File(
                 System.getProperty("java.io.tmpdir"),
                 "hsql-metadata-new-dir-${UUID.randomUUID()}"
             )
             assertFalse(newDir.exists())
             try {
                 MetadataStorageDB(newDir)
                 assertTrue(newDir.exists())
             } finally {
                 newDir.deleteRecursively()
             }
        }

        @Test
        fun `init should accept null root`() {
            assertDoesNotThrow {
                MetadataStorageDB(null)
            }
        }

        @Test
        fun `getSessionName should return sessionId when no name is set`() {
            val name = storage.getSessionName(testUser, testSession)
            assertEquals(testSession.sessionId, name)
        }

        @Test
        fun `setSessionName and getSessionName should round-trip`() {
            storage.setSessionName(testUser, testSession, "My Session")
            val name = storage.getSessionName(testUser, testSession)
            assertEquals("My Session", name)
        }

        @Test
        fun `setSessionName should overwrite previous name`() {
            storage.setSessionName(testUser, testSession, "First Name")
            storage.setSessionName(testUser, testSession, "Second Name")
            val name = storage.getSessionName(testUser, testSession)
            assertEquals("Second Name", name)
        }

        @Test
        fun `getSessionName should work with null user`() {
            storage.setSessionName(null, testSession, "Anonymous Session")
            val name = storage.getSessionName(null, testSession)
            assertEquals("Anonymous Session", name)
        }

        @Test
        fun `getSessionName should isolate names by user`() {
            storage.setSessionName(testUser, testSession, "User1 Name")
            storage.setSessionName(anotherUser, testSession, "User2 Name")

            assertEquals("User1 Name", storage.getSessionName(testUser, testSession))
            assertEquals("User2 Name", storage.getSessionName(anotherUser, testSession))
        }

        @Test
        fun `getMessageIds should return empty list when no ids are set`() {
            val ids = storage.getMessageIds(testUser, testSession)
            assertTrue(ids.isEmpty())
        }

        @Test
        fun `setMessageIds and getMessageIds should round-trip`() {
            val ids = listOf("msg1", "msg2", "msg3")
            storage.setMessageIds(testUser, testSession, ids)
            val retrieved = storage.getMessageIds(testUser, testSession)
            assertEquals(ids, retrieved)
        }

        @Test
        fun `setMessageIds with empty list should return empty list`() {
            storage.setMessageIds(testUser, testSession, emptyList())
            val retrieved = storage.getMessageIds(testUser, testSession)
            assertTrue(retrieved.isEmpty())
        }

        @Test
        fun `setMessageIds should overwrite previous ids`() {
            storage.setMessageIds(testUser, testSession, listOf("a", "b"))
            storage.setMessageIds(testUser, testSession, listOf("c", "d", "e"))
            val retrieved = storage.getMessageIds(testUser, testSession)
            assertEquals(listOf("c", "d", "e"), retrieved)
        }

        @Test
        fun `setMessageIds should work with null user`() {
            val ids = listOf("anon1", "anon2")
            storage.setMessageIds(null, testSession, ids)
            val retrieved = storage.getMessageIds(null, testSession)
            assertEquals(ids, retrieved)
        }

        @Test
        fun `getMessageIds should isolate by user`() {
            storage.setMessageIds(testUser, testSession, listOf("user1-msg"))
            storage.setMessageIds(anotherUser, testSession, listOf("user2-msg"))

            assertEquals(listOf("user1-msg"), storage.getMessageIds(testUser, testSession))
            assertEquals(listOf("user2-msg"), storage.getMessageIds(anotherUser, testSession))
        }

        @Test
        fun `getSessionTime should return null when no time is set`() {
            val time = storage.getSessionTime(testUser, testSession)
            assertNull(time)
        }

        @Test
        fun `setSessionTime and getSessionTime should round-trip`() {
            val now = Date()
            storage.setSessionTime(testUser, testSession, now)
            val retrieved = storage.getSessionTime(testUser, testSession)
            assertEquals(now.time, retrieved?.time)
        }

        @Test
        fun `setSessionTime should overwrite previous time`() {
            val time1 = Date(1000000L)
            val time2 = Date(2000000L)
            storage.setSessionTime(testUser, testSession, time1)
            storage.setSessionTime(testUser, testSession, time2)
            val retrieved = storage.getSessionTime(testUser, testSession)
            assertEquals(time2.time, retrieved?.time)
        }

        @Test
        fun `getSessionTime should work with null user`() {
            val now = Date()
            storage.setSessionTime(null, testSession, now)
            val retrieved = storage.getSessionTime(null, testSession)
            assertEquals(now.time, retrieved?.time)
        }

        @Test
        fun `getSessionOwner should return null when not set`() {
            val owner = storage.getSessionOwner(testSession)
            assertNull(owner)
        }

        @Test
        fun `setSessionOwner and getSessionOwner should round-trip`() {
            storage.setSessionOwner(testSession, "owner-123")
            val owner = storage.getSessionOwner(testSession)
            assertEquals("owner-123", owner)
        }

        @Test
        fun `setSessionOwner should overwrite previous owner`() {
            storage.setSessionOwner(testSession, "owner-1")
            storage.setSessionOwner(testSession, "owner-2")
            assertEquals("owner-2", storage.getSessionOwner(testSession))
        }

        @Test
        fun `setSessionOwner with null should set null value`() {
            storage.setSessionOwner(testSession, "owner-1")
            storage.setSessionOwner(testSession, null)
            val owner = storage.getSessionOwner(testSession)
            assertNull(owner)
        }

        @Test
        fun `listSessions should return empty list when no sessions match path`() {
            val sessions = storage.listSessions("/nonexistent/path")
            assertTrue(sessions.isEmpty())
        }

        @Test
        fun `listSessions should return sessions matching path`() {
            MetadataStorageDB.getConn(null).use { conn ->
                conn.prepareStatement(
                    "INSERT INTO metadata (session_id, user_email, key, value, timestamp) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)"
                ).use { stmt ->
                    stmt.setString(1, testSession.sessionId)
                    stmt.setString(2, "")
                    stmt.setString(3, "path")
                    stmt.setString(4, "/my/path")
                    stmt.executeUpdate()

                    stmt.setString(1, anotherSession.sessionId)
                    stmt.setString(2, "")
                    stmt.setString(3, "path")
                    stmt.setString(4, "/my/path")
                    stmt.executeUpdate()
                }
            }

            val sessions = storage.listSessions("/my/path")
            assertEquals(2, sessions.size)
            assertTrue(sessions.contains(testSession.sessionId))
            assertTrue(sessions.contains(anotherSession.sessionId))
        }

        @Test
        fun `listSessions should not return sessions with different paths`() {
            MetadataStorageDB.getConn(null).use { conn ->
                conn.prepareStatement(
                    "INSERT INTO metadata (session_id, user_email, key, value, timestamp) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)"
                ).use { stmt ->
                    stmt.setString(1, testSession.sessionId)
                    stmt.setString(2, "")
                    stmt.setString(3, "path")
                    stmt.setString(4, "/path/a")
                    stmt.executeUpdate()

                    stmt.setString(1, anotherSession.sessionId)
                    stmt.setString(2, "")
                    stmt.setString(3, "path")
                    stmt.setString(4, "/path/b")
                    stmt.executeUpdate()
                }
            }

            val sessions = storage.listSessions("/path/a")
            assertEquals(1, sessions.size)
            assertEquals(testSession.sessionId, sessions[0])
        }

        @Test
        fun `deleteSession should remove all metadata for session and user`() {
            storage.setSessionName(testUser, testSession, "Name to delete")
            storage.setMessageIds(testUser, testSession, listOf("msg1", "msg2"))
            storage.setSessionTime(testUser, testSession, Date())

            storage.deleteSession(testUser, testSession)

            assertEquals(testSession.sessionId, storage.getSessionName(testUser, testSession))
            assertTrue(storage.getMessageIds(testUser, testSession).isEmpty())
            assertNull(storage.getSessionTime(testUser, testSession))
        }

        @Test
        fun `deleteSession should not affect other users' data`() {
            storage.setSessionName(testUser, testSession, "User1 Name")
            storage.setSessionName(anotherUser, testSession, "User2 Name")

            storage.deleteSession(testUser, testSession)

            assertEquals(testSession.sessionId, storage.getSessionName(testUser, testSession))
            assertEquals("User2 Name", storage.getSessionName(anotherUser, testSession))
        }

        @Test
        fun `deleteSession should not affect other sessions`() {
            storage.setSessionName(testUser, testSession, "Session1 Name")
            storage.setSessionName(testUser, anotherSession, "Session2 Name")

            storage.deleteSession(testUser, testSession)

            assertEquals(testSession.sessionId, storage.getSessionName(testUser, testSession))
            assertEquals("Session2 Name", storage.getSessionName(testUser, anotherSession))
        }

        @Test
        fun `deleteSession should work with null user`() {
            storage.setSessionName(null, testSession, "Anon Name")
            storage.deleteSession(null, testSession)
            assertEquals(testSession.sessionId, storage.getSessionName(null, testSession))
        }

        @Test
        fun `multiple operations should work on same session`() {
            storage.setSessionName(testUser, testSession, "My Session")
            storage.setMessageIds(testUser, testSession, listOf("m1", "m2"))
            storage.setSessionTime(testUser, testSession, Date(1234567890L))
            storage.setSessionOwner(testSession, "owner-x")

            assertEquals("My Session", storage.getSessionName(testUser, testSession))
            assertEquals(listOf("m1", "m2"), storage.getMessageIds(testUser, testSession))
            assertEquals(1234567890L, storage.getSessionTime(testUser, testSession)?.time)
            assertEquals("owner-x", storage.getSessionOwner(testSession))
        }

        @Test
        fun `setMessageIds should handle special characters in ids`() {
            val ids = listOf("msg-1", "msg_2", "msg.3")
            storage.setMessageIds(testUser, testSession, ids)
            val retrieved = storage.getMessageIds(testUser, testSession)
            assertEquals(ids, retrieved)
        }

        @Test
        fun `setSessionName should handle long names`() {
            val longName = "A".repeat(1000)
            storage.setSessionName(testUser, testSession, longName)
            assertEquals(longName, storage.getSessionName(testUser, testSession))
        }

        @Test
        fun `getConn should return a valid connection`() {
            MetadataStorageDB.getConn(null).use { conn ->
                assertNotNull(conn)
                assertFalse(conn.isClosed)
            }
        }

        @Test
        fun `getLocalServiceUrl should return non-empty url`() {
            // getLocalServiceUrl requires a non-null root because it provisions a
            // file-backed embedded server. Use a temporary directory that is
            // cleaned up immediately after.
            val tmp = File(
                System.getProperty("java.io.tmpdir"),
                "hsql-metadata-url-${UUID.randomUUID()}"
            ).also { it.mkdirs() }
            try {
                val url = MetadataStorageDB.getLocalServiceUrl(tmp)
                assertNotNull(url)
                assertTrue(url.isNotEmpty())
            } finally {
                tmp.deleteRecursively()
            }
        }
    }