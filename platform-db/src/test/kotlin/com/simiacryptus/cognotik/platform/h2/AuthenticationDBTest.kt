package com.simiacryptus.cognotik.platform.h2

import com.simiacryptus.cognotik.platform.model.User
import org.junit.jupiter.api.AfterEach
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
import java.util.concurrent.atomic.AtomicInteger

class AuthenticationDBTest {

  private lateinit var db: AuthenticationDB

  private val alice = User(email = "alice@example.com", name = "Alice")
  private val bob = User(email = "bob@example.com", name = "Bob")

  private fun token() = "tok-" + UUID.randomUUID()

  /** The `access_tokens` table is shared JVM-wide via the lazy companion facet. */
  @BeforeEach
  fun setUp() {
    db = AuthenticationDB()
    db.clearAllSessions()
  }

  @AfterEach
  fun tearDown() {
    db.clearAllSessions()
  }

  @Nested
  @DisplayName("putUser / getUser")
  inner class PutAndGet {

    @Test
    fun `stores and retrieves a user`() {
      val t = token()
      assertEquals(alice, db.putUser(t, alice))
      assertEquals(alice, db.getUser(t))
      assertEquals(1, db.sessionCount())
    }

    @Test
    fun `returns null for an unknown token`() {
      assertNull(db.getUser(token()))
    }

    @Test
    fun `returns null for null or blank tokens`() {
      assertNull(db.getUser(""))
      assertNull(db.getUser("   "))
      assertNull(db.getUser("\t\n"))
    }

    @Test
    fun `rejects blank tokens on write`() {
      assertThrows(IllegalArgumentException::class.java) { db.putUser("", alice) }
      assertThrows(IllegalArgumentException::class.java) { db.putUser("   ", alice) }
      assertThrows(IllegalArgumentException::class.java) {
        db.putUser(" ", alice, Duration.ofMinutes(1))
      }
      assertEquals(0, db.sessionCount())
    }

    @Test
    fun `rejects tokens longer than the column width`() {
      val tooLong = "x".repeat(513)
      assertThrows(IllegalArgumentException::class.java) { db.putUser(tooLong, alice) }
      assertEquals(0, db.sessionCount())
    }

    @Test
    fun `accepts a token of exactly the maximum length`() {
      val maxLength = "y".repeat(512)
      db.putUser(maxLength, alice)
      assertEquals(alice, db.getUser(maxLength))
    }

    @Test
    fun `re-putting the same token overwrites the previous owner`() {
      val t = token()
      db.putUser(t, alice)
      db.putUser(t, bob)

      assertEquals(bob, db.getUser(t))
      assertEquals(1, db.sessionCount())
      assertTrue(db.listTokens(alice).isEmpty())
      assertEquals(1, db.listTokens(bob).size)
    }

    @Test
    fun `re-putting refreshes issuedAt and the ttl`() {
      val t = token()
      db.putUser(t, alice, Duration.ofSeconds(-1))
      assertNull(db.getUser(t), "the first session must already be expired")

      db.putUser(t, alice, Duration.ofMinutes(10))
      assertEquals(alice, db.getUser(t))
      assertTrue(db.listTokens(alice).single().expiresAt!!.isAfter(Instant.now()))
    }

    @Test
    fun `distinct tokens may map to the same user`() {
      val t1 = token()
      val t2 = token()
      db.putUser(t1, alice)
      db.putUser(t2, alice)

      assertEquals(alice, db.getUser(t1))
      assertEquals(alice, db.getUser(t2))
      assertEquals(2, db.listTokens(alice).size)
    }

    @Test
    fun `token lookup is case and whitespace sensitive`() {
      val t = "MixedCaseToken"
      db.putUser(t, alice)

      assertNull(db.getUser(t.lowercase()))
      assertNull(db.getUser(" $t"))
      assertEquals(alice, db.getUser(t))
    }
  }

  @Nested
  @DisplayName("persistence")
  inner class Persistence {

    @Test
    fun `a session survives into a brand new instance`() {
      val t = token()
      db.putUser(t, alice)

      // A different instance has an empty cache and must hydrate from the database.
      val other = AuthenticationDB()
      val loaded = other.getUser(t)

      assertNotNull(loaded)
      assertEquals(alice.email, loaded!!.email)
      assertEquals(alice.name, loaded.name)
    }

    @Test
    fun `a cache miss is recorded when hydrating from the database`() {
      val t = token()
      db.putUser(t, alice)

      val other = AuthenticationDB()
      assertEquals(Triple(0L, 0L, 0), other.cacheStats())

      assertNotNull(other.getUser(t))
      val (hits, misses, size) = other.cacheStats()
      assertEquals(0L, hits)
      assertEquals(1L, misses)
      assertEquals(1, size)

      assertNotNull(other.getUser(t))
      assertEquals(1L, other.cacheStats().first, "the second read must be served from cache")
    }

    @Test
    fun `an unknown token is not cached`() {
      assertNull(db.getUser(token()))
      assertEquals(0, db.cacheStats().third)
    }

    @Test
    fun `metadata is visible to other instances`() {
      val t = token()
      db.putUser(t, alice, Duration.ofHours(2))

      val meta = AuthenticationDB().listTokens(alice).single()
      assertEquals(t, meta.token)
      assertEquals(alice.id, meta.userId)
      assertEquals(Duration.ofHours(2), Duration.between(meta.issuedAt, meta.expiresAt))
    }
  }

  @Nested
  @DisplayName("expiry")
  inner class Expiry {

    @Test
    fun `a null ttl never expires`() {
      val t = token()
      db.putUser(t, alice, null)

      assertNull(db.listTokens(alice).single().expiresAt)
      assertEquals(alice, db.getUser(t))
    }

    @Test
    fun `an unexpired ttl is honoured`() {
      val t = token()
      db.putUser(t, alice, Duration.ofMinutes(30))

      assertEquals(alice, db.getUser(t))
      val meta = db.listTokens(alice).single()
      assertNotNull(meta.expiresAt)
      assertTrue(meta.expiresAt!!.isAfter(Instant.now()))
    }

    @Test
    fun `an expired token is rejected`() {
      val t = token()
      db.putUser(t, alice, Duration.ofSeconds(-1))

      assertNull(db.getUser(t))
    }

    @Test
    fun `an expired token is deleted from the database and the cache on read`() {
      val t = token()
      db.putUser(t, alice, Duration.ofSeconds(-1))
      assertEquals(1, db.sessionCount())

      db.getUser(t) // triggers lazy eviction

      assertEquals(0, db.sessionCount())
      assertEquals(0, db.cacheStats().third)
      assertTrue(db.listTokens(alice).isEmpty())
    }

    @Test
    fun `an expired token is rejected by a cold instance too`() {
      val t = token()
      db.putUser(t, alice, Duration.ofSeconds(-1))

      val cold = AuthenticationDB()
      assertNull(cold.getUser(t))
      assertEquals(0, cold.sessionCount())
    }

    @Test
    fun `expiring one token does not affect a sibling token`() {
      val live = token()
      val dead = token()
      db.putUser(live, alice, Duration.ofMinutes(5))
      db.putUser(dead, alice, Duration.ofSeconds(-1))

      assertNull(db.getUser(dead))
      assertEquals(alice, db.getUser(live))
      assertEquals(1, db.listTokens(alice).size)
    }

    @Test
    fun `listTokens filters expired rows without deleting them`() {
      // Differs from the in-memory manager: the DB implementation sweeps on read.
      val t = token()
      db.putUser(t, alice, Duration.ofSeconds(-1))

      assertTrue(db.listTokens(alice).isEmpty())
      assertEquals(1, db.sessionCount(), "listTokens must not delete anything")
    }
  }

  @Nested
  @DisplayName("purgeExpired")
  inner class Purge {

    @Test
    fun `returns zero when there is nothing to purge`() {
      db.putUser(token(), alice)
      db.putUser(token(), bob, Duration.ofHours(1))

      assertEquals(0, db.purgeExpired())
      assertEquals(2, db.sessionCount())
    }

    @Test
    fun `removes only expired rows`() {
      val live = token()
      db.putUser(live, alice, Duration.ofMinutes(5))
      db.putUser(token(), alice, Duration.ofSeconds(-1))
      db.putUser(token(), bob, Duration.ofSeconds(-1))
      db.putUser(token(), bob, null)

      assertEquals(2, db.purgeExpired())
      assertEquals(2, db.sessionCount())
      assertEquals(alice, db.getUser(live))
    }

    @Test
    fun `drops the cached copies of purged sessions`() {
      val dead = token()
      db.putUser(dead, alice, Duration.ofSeconds(-1))
      assertEquals(1, db.cacheStats().third)

      assertEquals(1, db.purgeExpired())

      assertEquals(0, db.cacheStats().third)
      assertNull(db.getUser(dead))
    }

    @Test
    fun `is idempotent`() {
      db.putUser(token(), alice, Duration.ofSeconds(-1))
      assertEquals(1, db.purgeExpired())
      assertEquals(0, db.purgeExpired())
    }
  }

  @Nested
  @DisplayName("listTokens")
  inner class ListTokens {

    @Test
    fun `returns an empty list for a user with no sessions`() {
      assertTrue(db.listTokens(alice).isEmpty())
    }

    @Test
    fun `only returns the requested user's tokens`() {
      val aliceToken = token()
      db.putUser(aliceToken, alice)
      db.putUser(token(), bob)
      db.putUser(token(), bob)

      val aliceTokens = db.listTokens(alice)
      assertEquals(1, aliceTokens.size)
      assertEquals(aliceToken, aliceTokens.single().token)
      assertEquals(2, db.listTokens(bob).size)
    }

    @Test
    fun `metadata reflects the stored session`() {
      val t = token()
      val before = Instant.now()
      db.putUser(t, alice, Duration.ofHours(1))
      val after = Instant.now()

      val meta = db.listTokens(alice).single()
      assertEquals(alice.id, meta.userId)
      assertEquals(t, meta.token)
      assertFalse(meta.issuedAt!!.isBefore(before.minusMillis(1)))
      assertFalse(meta.issuedAt!!.isAfter(after.plusMillis(1)))
      assertEquals(Duration.ofHours(1), Duration.between(meta.issuedAt, meta.expiresAt))
    }

    @Test
    fun `lastUsedAt is not flushed inside the touch interval`() {
      // Default interval is 60s, so a hot token causes no write amplification.
      val t = token()
      db.putUser(t, alice)
      val issued = db.listTokens(alice).single().lastUsedAt

      Thread.sleep(10)
      assertEquals(alice, db.getUser(t))

      assertEquals(issued, db.listTokens(alice).single().lastUsedAt)
    }

    @Test
    fun `lastUsedAt is flushed once the touch interval elapses`() {
      val key = "cognotik.auth.touchIntervalMillis"
      val previous = System.getProperty(key)
      System.setProperty(key, "1")
      try {
        val eager = AuthenticationDB() // reads the property at construction time
        val t = token()
        eager.putUser(t, alice)
        val issued = eager.listTokens(alice).single().lastUsedAt

        Thread.sleep(25)
        assertEquals(alice, eager.getUser(t))

        val touched = eager.listTokens(alice).single().lastUsedAt
        assertTrue(touched!!.isAfter(issued), "expected $touched to be after $issued")
      } finally {
        if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
      }
    }

    @Test
    fun `lastUsedAt does not advance for a failed lookup`() {
      val t = token()
      db.putUser(t, alice)
      val issued = db.listTokens(alice).single().lastUsedAt

      Thread.sleep(10)
      assertNull(db.getUser("not-a-real-token"))

      assertEquals(issued, db.listTokens(alice).single().lastUsedAt)
    }
  }

  @Nested
  @DisplayName("logoutIfMatching")
  inner class Logout {

    @Test
    fun `removes a matching session`() {
      val t = token()
      db.putUser(t, alice)

      assertTrue(db.logoutIfMatching(t, alice))
      assertNull(db.getUser(t))
      assertEquals(0, db.sessionCount())
      assertEquals(0, db.cacheStats().third)
    }

    @Test
    fun `is idempotent`() {
      val t = token()
      db.putUser(t, alice)

      assertTrue(db.logoutIfMatching(t, alice))
      assertFalse(db.logoutIfMatching(t, alice))
    }

    @Test
    fun `refuses to log out another user's token`() {
      val t = token()
      db.putUser(t, alice)

      assertFalse(db.logoutIfMatching(t, bob))
      assertEquals(alice, db.getUser(t), "Alice's session must survive Bob's attempt")
      assertEquals(1, db.sessionCount())
    }

    @Test
    fun `returns false for unknown or blank tokens`() {
      assertFalse(db.logoutIfMatching(token(), alice))
      assertFalse(db.logoutIfMatching("", alice))
      assertFalse(db.logoutIfMatching("   ", alice))
    }

    @Test
    fun `only removes the token that was presented`() {
      val t1 = token()
      val t2 = token()
      db.putUser(t1, alice)
      db.putUser(t2, alice)

      assertTrue(db.logoutIfMatching(t1, alice))
      assertNull(db.getUser(t1))
      assertEquals(alice, db.getUser(t2))
    }

    @Test
    fun `a logout performed elsewhere is visible to a cold instance`() {
      val t = token()
      db.putUser(t, alice)
      assertTrue(AuthenticationDB().logoutIfMatching(t, alice))

      assertNull(AuthenticationDB().getUser(t))
    }
  }

  @Nested
  @DisplayName("revokeAll")
  inner class RevokeAll {

    @Test
    fun `returns zero when the user has no sessions`() {
      assertEquals(0, db.revokeAll(alice))
    }

    @Test
    fun `removes every session of the user and only that user`() {
      val a1 = token()
      val a2 = token()
      val b1 = token()
      db.putUser(a1, alice)
      db.putUser(a2, alice)
      db.putUser(b1, bob)

      assertEquals(2, db.revokeAll(alice))

      assertNull(db.getUser(a1))
      assertNull(db.getUser(a2))
      assertEquals(bob, db.getUser(b1))
      assertEquals(1, db.sessionCount())
      assertEquals(1, db.cacheStats().third, "only Bob's entry may remain cached")
    }

    @Test
    fun `counts expired-but-not-yet-purged sessions`() {
      db.putUser(token(), alice, Duration.ofSeconds(-1))
      db.putUser(token(), alice)

      assertEquals(2, db.revokeAll(alice))
      assertTrue(db.listTokens(alice).isEmpty())
    }

    @Test
    fun `is idempotent`() {
      db.putUser(token(), alice)
      assertEquals(1, db.revokeAll(alice))
      assertEquals(0, db.revokeAll(alice))
    }
  }

  @Nested
  @DisplayName("cache management")
  inner class CacheManagement {

    @Test
    fun `invalidate forces the next read to hit the database`() {
      val t = token()
      db.putUser(t, alice)
      assertEquals(1, db.cacheStats().third)

      db.invalidate(t)
      assertEquals(0, db.cacheStats().third)

      assertNotNull(db.getUser(t))
      assertEquals(1L, db.cacheStats().second, "expected exactly one cache miss")
    }

    @Test
    fun `invalidate of an unknown token is a no-op`() {
      db.putUser(token(), alice)
      db.invalidate("nothing-here")
      assertEquals(1, db.cacheStats().third)
    }

    @Test
    fun `invalidateAll empties the cache but keeps the rows`() {
      db.putUser(token(), alice)
      db.putUser(token(), bob)

      db.invalidateAll()

      assertEquals(0, db.cacheStats().third)
      assertEquals(2, db.sessionCount())
    }

    @Test
    fun `a stale cache can still serve a session revoked by another instance`() {
      // Documents the current (eventually consistent) caching contract.
      val t = token()
      db.putUser(t, alice)
      assertEquals(1, AuthenticationDB().revokeAll(alice))

      assertEquals(alice, db.getUser(t), "the stale cache still answers")
      db.invalidate(t)
      assertNull(db.getUser(t), "after invalidation the revocation is observed")
    }

    @Test
    fun `clearAllSessions wipes both the table and the cache`() {
      db.putUser(token(), alice)
      db.putUser(token(), bob)

      db.clearAllSessions()

      assertEquals(0, db.sessionCount())
      assertEquals(0, db.cacheStats().third)
      assertTrue(db.listTokens(alice).isEmpty())
    }
  }

  @Nested
  @DisplayName("concurrency")
  inner class Concurrency {

    @Test
    fun `concurrent writes and reads are consistent`() {
      val threads = 4
      val perThread = 25
      val pool = Executors.newFixedThreadPool(threads)
      val start = CountDownLatch(1)
      val tokens = ConcurrentHashMap.newKeySet<String>()
      val failures = ConcurrentHashMap.newKeySet<String>()

      try {
        repeat(threads) { i ->
          pool.submit {
            start.await()
            repeat(perThread) { j ->
              val t = "t-$i-$j"
              tokens.add(t)
              db.putUser(t, alice)
              if (db.getUser(t) == null) failures.add(t)
            }
          }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "workers did not finish in time")
      } finally {
        pool.shutdownNow()
      }

      assertTrue(failures.isEmpty(), "tokens that failed to read back: $failures")
      assertEquals(threads * perThread, tokens.size)
      assertEquals(threads * perThread, db.sessionCount())
      assertEquals(threads * perThread, db.listTokens(alice).size)
      assertEquals(threads * perThread, db.revokeAll(alice))
    }

    @Test
    fun `concurrent writes of the same token leave exactly one row`() {
      val t = token()
      val threads = 8
      val pool = Executors.newFixedThreadPool(threads)
      val start = CountDownLatch(1)

      try {
        repeat(threads) { i ->
          pool.submit {
            start.await()
            db.putUser(t, if (i % 2 == 0) alice else bob)
          }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
      } finally {
        pool.shutdownNow()
      }

      assertEquals(1, db.sessionCount())
      assertNotNull(db.getUser(t))
    }

    @Test
    fun `concurrent logout of the same token succeeds exactly once`() {
      val t = token()
      db.putUser(t, alice)

      val threads = 8
      val pool = Executors.newFixedThreadPool(threads)
      val start = CountDownLatch(1)
      val successes = AtomicInteger()

      try {
        repeat(threads) {
          pool.submit {
            start.await()
            if (db.logoutIfMatching(t, alice)) successes.incrementAndGet()
          }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))
      } finally {
        pool.shutdownNow()
      }

      assertEquals(1, successes.get())
      assertNull(db.getUser(t))
      assertEquals(0, db.sessionCount())
    }
  }
}