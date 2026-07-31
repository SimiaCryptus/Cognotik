package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.hsql.UsageTest.Companion.tempDBDir
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.util.SecureString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UserSettingsDBTest {

  companion object {

    @BeforeAll
    @JvmStatic
    fun setupAll() {
      DatabaseFacet.root = tempDBDir
    }

  }

  private lateinit var manager: UserSettingsDB

  private val testUser = User(
    email = "test@example.com",
    name = "Test User",
    id = "test-user-id"
  )

  private val otherUser = User(
    email = "other@example.com",
    name = "Other User",
    id = "other-user-id"
  )

  @BeforeEach
  fun setUp() {
    SecureString.key = SecureString.randomKey()
    // Use null root => in-memory ephemeral HSQL database (mem:<dbName>).
    manager = UserSettingsDB()
    // Clean DB between tests to ensure isolation.
    try {
      UserSettingsDB.facet.withConnection { conn ->
        conn.createStatement().use { stmt ->
          stmt.execute("DELETE FROM user_settings")
        }
      }
    } catch (_: Exception) {
    }
  }

  @AfterEach
  fun tearDown() {
    try {
      UserSettingsDB.facet.withConnection { conn ->
        conn.createStatement().use { stmt ->
          stmt.execute("DELETE FROM user_settings")
        }
      }
    } catch (_: Exception) {
    }
  }

  private fun apiData(name: String, baseUrl: String, keyValue: String = "test-key"): ApiData =
    ApiData(
      name = name,
      key = SecureString(keyValue),
      baseUrl = baseUrl,
      provider = null
    )

  @Test
  fun `updateUserSettings persists settings`() {
    val newSettings = UserSettings(
      apis = mutableListOf(apiData("openai", "https://api.openai.com"))
    )
    manager.updateUserSettings(testUser, newSettings)

    val retrieved = manager.getUserSettings(testUser)
    assertEquals("https://api.openai.com", retrieved.apis.firstOrNull { it.name == "openai" }?.baseUrl)
  }

  @Test
  fun `getUserSettings uses cache after first load`() {
    val settings = UserSettings(apis = mutableListOf(apiData("provider", "url")))
    manager.updateUserSettings(testUser, settings)

    val first = manager.getUserSettings(testUser)
    val second = manager.getUserSettings(testUser)

    // Same instance from cache
    assertSame(first, second)
  }

  @Test
  fun `settings persist across manager instances`() {
    val settings = UserSettings(apis = mutableListOf(apiData("key", "value")))
    manager.updateUserSettings(testUser, settings)

    // Create a new manager pointing to the same in-memory DB. The DB is
    // backed by the static HSQLFacet, so the data persists for the JVM
    // lifetime within the in-memory store.
    val manager2 = UserSettingsDB()
    val retrieved = manager2.getUserSettings(testUser)
    assertEquals("value", retrieved.apis.firstOrNull { it.name == "key" }?.baseUrl)
  }

  @Test
  fun `updateUserSettings preserves existing passwordHash when new one is null`() {
    val originalHash = "original-hash-123"
    val initial = UserSettings(passwordHash = originalHash)
    manager.updateUserSettings(testUser, initial)

    // Update with null password hash
    val update = UserSettings(
      passwordHash = null,
      apis = mutableListOf(apiData("a", "b"))
    )
    manager.updateUserSettings(testUser, update)

    // Need fresh manager to bypass cache
    val manager2 = UserSettingsDB()
    val retrieved = manager2.getUserSettings(testUser)
    assertEquals(originalHash, retrieved.passwordHash)
    assertEquals("b", retrieved.apis.firstOrNull { it.name == "a" }?.baseUrl)
  }

  @Test
  fun `updateUserSettings preserves existing passwordHash when new one is blank`() {
    val originalHash = "original-hash-456"
    val initial = UserSettings(passwordHash = originalHash)
    manager.updateUserSettings(testUser, initial)

    val update = UserSettings(passwordHash = "")
    manager.updateUserSettings(testUser, update)

    val manager2 = UserSettingsDB()
    val retrieved = manager2.getUserSettings(testUser)
    assertEquals(originalHash, retrieved.passwordHash)
  }

  @Test
  fun `updateUserSettings overwrites passwordHash when new one is set`() {
    val initial = UserSettings(passwordHash = "old-hash")
    manager.updateUserSettings(testUser, initial)

    val newHash = "new-hash"
    val update = UserSettings(passwordHash = newHash)
    manager.updateUserSettings(testUser, update)

    val manager2 = UserSettingsDB()
    val retrieved = manager2.getUserSettings(testUser)
    assertEquals(newHash, retrieved.passwordHash)
  }

  @Test
  fun `different users have independent settings`() {
    val settings1 = UserSettings(apis = mutableListOf(apiData("a", "1")))
    val settings2 = UserSettings(apis = mutableListOf(apiData("b", "2")))

    manager.updateUserSettings(testUser, settings1)
    manager.updateUserSettings(otherUser, settings2)

    val retrieved1 = manager.getUserSettings(testUser)
    val retrieved2 = manager.getUserSettings(otherUser)

    assertEquals("1", retrieved1.apis.firstOrNull { it.name == "a" }?.baseUrl)
    assertNull(retrieved1.apis.firstOrNull { it.name == "b" })
    assertEquals("2", retrieved2.apis.firstOrNull { it.name == "b" }?.baseUrl)
    assertNull(retrieved2.apis.firstOrNull { it.name == "a" })
  }

  @Test
  fun `updateUserSettings is idempotent`() {
    val settings = UserSettings(apis = mutableListOf(apiData("k", "v")))
    manager.updateUserSettings(testUser, settings)
    manager.updateUserSettings(testUser, settings)
    manager.updateUserSettings(testUser, settings)

    val manager2 = UserSettingsDB()
    val retrieved = manager2.getUserSettings(testUser)
    assertEquals("v", retrieved.apis.firstOrNull { it.name == "k" }?.baseUrl)
  }

  @Test
  fun `user with blank email uses fallback key`() {
    val userBlankEmail = User(
      email = "",
      name = "Blank",
      id = "blank-id"
    )
    val settings = UserSettings(apis = mutableListOf(apiData("x", "y")))
    manager.updateUserSettings(userBlankEmail, settings)

    val manager2 = UserSettingsDB()
    val retrieved = manager2.getUserSettings(userBlankEmail)
    assertEquals("y", retrieved.apis.firstOrNull { it.name == "x" }?.baseUrl)
  }

  @Test
  fun `concurrent getUserSettings returns consistent results`() {
    val settings = UserSettings(apis = mutableListOf(apiData("k", "v")))
    manager.updateUserSettings(testUser, settings)

    val threadCount = 20
    val executor = Executors.newFixedThreadPool(threadCount)
    val latch = CountDownLatch(threadCount)
    val results = mutableListOf<UserSettings>()

    repeat(threadCount) {
      executor.submit {
        try {
          val result = manager.getUserSettings(testUser)
          synchronized(results) {
            results.add(result)
          }
        } finally {
          latch.countDown()
        }
      }
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS))
    executor.shutdown()

    assertEquals(threadCount, results.size)
    results.forEach {
      assertEquals("v", it.apis.firstOrNull { a -> a.name == "k" }?.baseUrl)
    }
  }

  @Test
  fun `concurrent updates do not corrupt data`() {
    val threadCount = 10
    val executor = Executors.newFixedThreadPool(threadCount)
    val latch = CountDownLatch(threadCount)

    repeat(threadCount) { i ->
      executor.submit {
        try {
          val settings = UserSettings(
            apis = mutableListOf(apiData("idx", i.toString()))
          )
          manager.updateUserSettings(testUser, settings)
        } finally {
          latch.countDown()
        }
      }
    }

    assertTrue(latch.await(30, TimeUnit.SECONDS))
    executor.shutdown()

    val manager2 = UserSettingsDB()
    val retrieved = manager2.getUserSettings(testUser)
    // The final value should be one of the writes (0..threadCount-1)
    val idx = retrieved.apis.firstOrNull { it.name == "idx" }?.baseUrl?.toIntOrNull()
    assertNotNull(idx)
    assertTrue(idx!! in 0 until threadCount)
  }

  @Test
  fun `constructor accepts null root`() {
    // Should not throw
    val mgr = UserSettingsDB()
    assertNotNull(mgr)
  }

  @Test
  fun `updating cached user returns merged settings from cache`() {
    val initial = UserSettings(
      passwordHash = "hash1",
      apis = mutableListOf(apiData("a", "1"))
    )
    manager.updateUserSettings(testUser, initial)

    val update = UserSettings(
      passwordHash = null,
      apis = mutableListOf(apiData("b", "2"))
    )
    manager.updateUserSettings(testUser, update)

    // Cache should be updated with merged result
    val retrieved = manager.getUserSettings(testUser)
    assertEquals("hash1", retrieved.passwordHash)
    assertEquals("2", retrieved.apis.firstOrNull { it.name == "b" }?.baseUrl)
  }
}