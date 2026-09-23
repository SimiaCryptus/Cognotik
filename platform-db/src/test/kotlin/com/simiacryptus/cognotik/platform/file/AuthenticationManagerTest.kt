package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthenticationManagerTest {

  private lateinit var manager: AuthenticationManager

  private val alice = User(email = "alice@example.com", name = "Alice")
  private val bob = User(email = "bob@example.com", name = "Bob")

  private fun token() = "tok-" + UUID.randomUUID()

  @BeforeEach
  fun setUp() {
    // `sessions` is held in the companion object, i.e. it is shared JVM-wide.
    AuthenticationManager.clearAllSessions()
    manager = AuthenticationManager()
  }

  @Nested
  @DisplayName("putUser / getUser")
  inner class PutAndGet {

    @Test
    fun `stores and retrieves a user`() {
      val t = token()
      assertEquals(alice, manager.putUser(t, alice))
      assertEquals(alice, manager.getUser(t))
    }

    @Test
    fun `returns null for an unknown token`() {
      assertNull(manager.getUser(token()))
    }

    @Test
    fun `returns null for null or blank tokens`() {
      assertNull(manager.getUser(""))
      assertNull(manager.getUser("   "))
      assertNull(manager.getUser("\t\n"))
    }

    @Test
    fun `rejects blank tokens on write`() {
      assertThrows(IllegalArgumentException::class.java) { manager.putUser("", alice) }
      assertThrows(IllegalArgumentException::class.java) { manager.putUser("   ", alice) }
      assertThrows(IllegalArgumentException::class.java) {
        manager.putUser(" ", alice, Duration.ofMinutes(1))
      }
      assertEquals(0, AuthenticationManager.sessionCount())
    }

    @Test
    fun `re-putting the same token overwrites the previous owner`() {
      val t = token()
      manager.putUser(t, alice)
      manager.putUser(t, bob)

      assertEquals(bob, manager.getUser(t))
      assertEquals(1, AuthenticationManager.sessionCount())
      assertTrue(manager.listTokens(alice).isEmpty())
      assertEquals(1, manager.listTokens(bob).size)
    }

    @Test
    fun `distinct tokens may map to the same user`() {
      val t1 = token()
      val t2 = token()
      manager.putUser(t1, alice)
      manager.putUser(t2, alice)

      assertEquals(alice, manager.getUser(t1))
      assertEquals(alice, manager.getUser(t2))
      assertEquals(2, manager.listTokens(alice).size)
    }

    @Test
    fun `token lookup is case and whitespace sensitive`() {
      val t = "MixedCaseToken"
      manager.putUser(t, alice)

      assertNull(manager.getUser(t.lowercase()))
      assertNull(manager.getUser(" $t"))
      assertEquals(alice, manager.getUser(t))
    }
  }

  @Nested
  @DisplayName("expiry")
  inner class Expiry {

    @Test
    fun `a null ttl never expires`() {
      val t = token()
      manager.putUser(t, alice, null)

      val meta = manager.listTokens(alice).single()
      assertNull(meta.expiresAt)
      assertEquals(alice, manager.getUser(t))
    }

    @Test
    fun `an unexpired ttl is honoured`() {
      val t = token()
      manager.putUser(t, alice, Duration.ofMinutes(30))

      assertEquals(alice, manager.getUser(t))
      val meta = manager.listTokens(alice).single()
      assertNotNull(meta.expiresAt)
      assertTrue(meta.expiresAt!!.isAfter(Instant.now()))
    }

    @Test
    fun `an expired token is rejected`() {
      val t = token()
      manager.putUser(t, alice, Duration.ofSeconds(-1))

      assertNull(manager.getUser(t))
    }

    @Test
    fun `an expired token is evicted from the store`() {
      val t = token()
      manager.putUser(t, alice, Duration.ofSeconds(-1))
      assertEquals(1, AuthenticationManager.sessionCount())

      manager.getUser(t) // triggers lazy eviction

      assertEquals(0, AuthenticationManager.sessionCount())
      assertTrue(manager.listTokens(alice).isEmpty())
    }

    @Test
    fun `expiring one token does not affect a sibling token`() {
      val live = token()
      val dead = token()
      manager.putUser(live, alice, Duration.ofMinutes(5))
      manager.putUser(dead, alice, Duration.ofSeconds(-1))

      assertNull(manager.getUser(dead))
      assertEquals(alice, manager.getUser(live))
      assertEquals(1, manager.listTokens(alice).size)
    }

    @Test
    fun `expired tokens are still listed until they are touched`() {
      // Documents current behaviour: listTokens performs no expiry sweep.
      val t = token()
      manager.putUser(t, alice, Duration.ofSeconds(-1))

      assertEquals(1, manager.listTokens(alice).size)
      manager.getUser(t)
      assertEquals(0, manager.listTokens(alice).size)
    }
  }

  @Nested
  @DisplayName("listTokens")
  inner class ListTokens {

    @Test
    fun `returns an empty list for a user with no sessions`() {
      assertTrue(manager.listTokens(alice).isEmpty())
    }

    @Test
    fun `only returns the requested user's tokens`() {
      val aliceToken = token()
      manager.putUser(aliceToken, alice)
      manager.putUser(token(), bob)
      manager.putUser(token(), bob)

      val aliceTokens = manager.listTokens(alice)
      assertEquals(1, aliceTokens.size)
      assertEquals(aliceToken, aliceTokens.single().token)
      assertEquals(2, manager.listTokens(bob).size)
    }

    @Test
    fun `metadata reflects the stored session`() {
      val t = token()
      val before = Instant.now()
      manager.putUser(t, alice, Duration.ofHours(1))
      val after = Instant.now()

      val meta = manager.listTokens(alice).single()
      assertEquals(alice.id, meta.userId)
      assertEquals(t, meta.token)
      assertFalse(meta.issuedAt!!.isBefore(before.minusMillis(1)))
      assertFalse(meta.issuedAt!!.isAfter(after.plusMillis(1)))
      assertEquals(meta.issuedAt!!.plus(Duration.ofHours(1)), meta.expiresAt)
    }

    @Test
    fun `lastUsedAt advances on a successful getUser`() {
      val t = token()
      manager.putUser(t, alice)
      val issued = manager.listTokens(alice).single().lastUsedAt

      Thread.sleep(10)
      assertEquals(alice, manager.getUser(t))

      val touched = manager.listTokens(alice).single().lastUsedAt
      assertTrue(touched!!.isAfter(issued), "expected $touched to be after $issued")
    }

    @Test
    fun `lastUsedAt does not advance for a failed lookup`() {
      val t = token()
      manager.putUser(t, alice)
      val issued = manager.listTokens(alice).single().lastUsedAt

      Thread.sleep(10)
      assertNull(manager.getUser("not-a-real-token"))

      assertEquals(issued, manager.listTokens(alice).single().lastUsedAt)
    }
  }

  @Nested
  @DisplayName("logoutIfMatching")
  inner class Logout {

    @Test
    fun `removes a matching session`() {
      val t = token()
      manager.putUser(t, alice)

      assertTrue(manager.logoutIfMatching(t, alice))
      assertNull(manager.getUser(t))
      assertEquals(0, AuthenticationManager.sessionCount())
    }

    @Test
    fun `is idempotent`() {
      val t = token()
      manager.putUser(t, alice)

      assertTrue(manager.logoutIfMatching(t, alice))
      assertFalse(manager.logoutIfMatching(t, alice))
    }

    @Test
    fun `refuses to log out another user's token`() {
      val t = token()
      manager.putUser(t, alice)

      assertFalse(manager.logoutIfMatching(t, bob))
      assertEquals(alice, manager.getUser(t), "Alice's session must survive Bob's attempt")
    }

    @Test
    fun `returns false for unknown or blank tokens`() {
      assertFalse(manager.logoutIfMatching(token(), alice))
      assertFalse(manager.logoutIfMatching("", alice))
      assertFalse(manager.logoutIfMatching("   ", alice))
    }

    @Test
    fun `only removes the token that was presented`() {
      val t1 = token()
      val t2 = token()
      manager.putUser(t1, alice)
      manager.putUser(t2, alice)

      assertTrue(manager.logoutIfMatching(t1, alice))
      assertNull(manager.getUser(t1))
      assertEquals(alice, manager.getUser(t2))
    }
  }

  @Nested
  @DisplayName("revokeAll")
  inner class RevokeAll {

    @Test
    fun `returns zero when the user has no sessions`() {
      assertEquals(0, manager.revokeAll(alice))
    }

    @Test
    fun `removes every session of the user and only that user`() {
      val a1 = token()
      val a2 = token()
      val b1 = token()
      manager.putUser(a1, alice)
      manager.putUser(a2, alice)
      manager.putUser(b1, bob)

      assertEquals(2, manager.revokeAll(alice))

      assertNull(manager.getUser(a1))
      assertNull(manager.getUser(a2))
      assertEquals(bob, manager.getUser(b1))
      assertEquals(1, AuthenticationManager.sessionCount())
    }

    @Test
    fun `counts expired-but-not-yet-evicted sessions`() {
      manager.putUser(token(), alice, Duration.ofSeconds(-1))
      manager.putUser(token(), alice)

      assertEquals(2, manager.revokeAll(alice))
      assertTrue(manager.listTokens(alice).isEmpty())
    }

    @Test
    fun `is idempotent`() {
      manager.putUser(token(), alice)
      assertEquals(1, manager.revokeAll(alice))
      assertEquals(0, manager.revokeAll(alice))
    }
  }

  @Nested
  @DisplayName("hash")
  inner class Hash {

    @Test
    fun `matches the known SHA-256 vector for abc`() {
      assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        AuthenticationManager.hash("abc"),
      )
    }

    @Test
    fun `matches the known SHA-256 vector for the empty string`() {
      assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        AuthenticationManager.hash(""),
      )
    }

    @Test
    fun `is deterministic and lower-case hex of fixed width`() {
      val h1 = AuthenticationManager.hash("some-token")
      val h2 = AuthenticationManager.hash("some-token")

      assertEquals(h1, h2)
      assertEquals(64, h1.length)
      assertTrue(h1.matches(Regex("[0-9a-f]{64}")), "unexpected digest format: $h1")
    }

    @Test
    fun `is sensitive to a single character change`() {
      assertFalse(AuthenticationManager.hash("token-a") == AuthenticationManager.hash("token-b"))
    }

    @Test
    fun `handles non-ascii input via utf-8`() {
      val expected = AuthenticationManager.hash("naïve-tøken-☃")
      assertEquals(64, expected.length)
      assertEquals(expected, AuthenticationManager.hash("naïve-tøken-☃"))
    }

    @Test
    fun `hashed tokens round-trip through the store`() {
      val raw = token()
      val hashed = AuthenticationManager.hash(raw)
      manager.putUser(hashed, alice)

      assertNull(manager.getUser(raw), "raw token must not resolve when a hash was stored")
      assertEquals(alice, manager.getUser(hashed))
    }
  }

  @Nested
  @DisplayName("shared state and concurrency")
  inner class SharedState {

    @Test
    fun `the session store is shared across instances`() {
      val t = token()
      AuthenticationManager().putUser(t, alice)

      // Documents the companion-object (static) store: a second instance sees the session.
      assertEquals(alice, AuthenticationManager().getUser(t))
    }

    @Test
    fun `concurrent writes and reads are consistent`() {
      val threads = 8
      val perThread = 200
      val pool = Executors.newFixedThreadPool(threads)
      val start = CountDownLatch(1)
      val tokens = ConcurrentHashMap.newKeySet<String>()

      try {
        repeat(threads) { i ->
          pool.submit {
            start.await()
            repeat(perThread) { j ->
              val t = "t-$i-$j"
              tokens.add(t)
              manager.putUser(t, alice)
              assertEquals(alice, manager.getUser(t))
            }
          }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers did not finish in time")
      } finally {
        pool.shutdownNow()
      }

      assertEquals(threads * perThread, tokens.size)
      assertEquals(threads * perThread, manager.listTokens(alice).size)
      assertEquals(threads * perThread, manager.revokeAll(alice))
    }

    @Test
    fun `concurrent logout of the same token succeeds exactly once`() {
      val t = token()
      manager.putUser(t, alice)

      val threads = 16
      val pool = Executors.newFixedThreadPool(threads)
      val start = CountDownLatch(1)
      val successes = java.util.concurrent.atomic.AtomicInteger()

      try {
        repeat(threads) {
          pool.submit {
            start.await()
            if (manager.logoutIfMatching(t, alice)) successes.incrementAndGet()
          }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
      } finally {
        pool.shutdownNow()
      }

      assertEquals(1, successes.get())
      assertNull(manager.getUser(t))
    }
  }
}