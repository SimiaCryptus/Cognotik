package com.simiacryptus.cognotik.platform.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class SessionTest {

  private fun today(): String = LocalDate.now().toString().replace("-", "")

  // ---- Construction / validation ----

  @ParameterizedTest
  @ValueSource(
    strings = [
      "20240101-abcd",
      "G-20240101-abcd",
      "U-20240101-abcd",
      "20240101-abcdefghijkl",   // 12 chars (max)
      "U-20240101-a+b.c",
      "G-20240101-a_b-c",
      "20240101-ABCD1234",
    ]
  )
  fun `valid session ids are accepted`(id: String) {
    val session = Session(id)
    assertEquals(id, session.sessionId)
    assertTrue(Session.isValid(id))
    assertNotNull(Session.tryParse(id))
    assertEquals(session, Session.parseSessionID(id))
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "",
      "abc",
      "20240101-abc",            // suffix too short
      "20240101-abcdefghijklm",  // suffix too long (13)
      "2024010-abcd",            // 7 digits
      "202401011-abcd",          // 9 digits
      "X-20240101-abcd",         // bad prefix
      "g-20240101-abcd",         // lowercase prefix
      "20240101_abcd",           // wrong separator
      "20240101-ab cd",          // whitespace
      "20240101-ab/cd",          // path separator
      "../20240101-abcd",
      " 20240101-abcd",
    ]
  )
  fun `invalid session ids are rejected`(id: String) {
    assertFalse(Session.isValid(id))
    assertNull(Session.tryParse(id))
    assertThrows<IllegalArgumentException> { Session(id) }
    assertThrows<IllegalArgumentException> { Session.parseSessionID(id) }
  }

  @Test
  fun `invalid id exception message contains id`() {
    val ex = assertThrows<IllegalArgumentException> { Session("bogus") }
    assertTrue(ex.message!!.contains("Invalid session ID"))
  }

  @Test
  fun `string validateSessionId extension returns boolean without throwing`() {
    with(Session.Companion) {
      assertTrue("20240101-abcd".validateSessionId())
      assertTrue("G-20240101-abcd".validateSessionId())
      assertFalse("not-a-session".validateSessionId())
      assertFalse("".validateSessionId())
    }
  }

  @Test
  fun `companion validateSessionId accepts valid session`() {
    assertDoesNotThrow { Session.validateSessionId(Session("U-20240101-abcd")) }
  }

  // ---- Global / user helpers ----

  @Test
  fun `isGlobal reflects prefix`() {
    assertTrue(Session("G-20240101-abcd").isGlobal())
    assertFalse(Session("U-20240101-abcd").isGlobal())
    assertFalse(Session("20240101-abcd").isGlobal())
  }

  @Test
  fun `toGlobal converts user session`() {
    val global = Session("U-20240101-abcd").toGlobal()
    assertEquals("G-20240101-abcd", global.sessionId)
    assertTrue(global.isGlobal())
  }

  @Test
  fun `toGlobal converts unprefixed session`() {
    assertEquals("G-20240101-abcd", Session("20240101-abcd").toGlobal().sessionId)
  }

  @Test
  fun `toGlobal on global session returns same instance`() {
    val global = Session("G-20240101-abcd")
    assertSame(global, global.toGlobal())
  }

  @Test
  fun `newGlobalID produces valid global session for today`() {
    val before = today()
    val session = Session.newGlobalID()
    val after = today()
    assertTrue(session.isGlobal())
    assertTrue(Session.isValid(session.sessionId))
    val date = session.sessionId.removePrefix("G-").substringBefore("-")
    assertTrue(date == before || date == after, "unexpected date $date")
    assertEquals(8, session.sessionId.substringAfterLast("-").length)
  }

  @Test
  fun `newUserID produces valid user session`() {
    val session = Session.newUserID()
    assertTrue(session.sessionId.startsWith("U-"))
    assertFalse(session.isGlobal())
    assertTrue(Session.isValid(session.sessionId))
  }

  @Test
  fun `generated ids are unique`() {
    val ids = (1..500).map { Session.newUserID().sessionId }.toSet()
    assertEquals(500, ids.size)
  }

  // ---- randomId ----

  @Test
  fun `randomId has requested length and uses alphabet`() {
    for (len in listOf(0, 1, 4, 8, 12, 64)) {
      val id = Session.randomId(len)
      assertEquals(len, id.length)
      assertTrue(id.all { it in Session.ID_ALPHABET }, "bad chars in $id")
    }
  }

  @Test
  fun `randomId default length is 8`() {
    assertEquals(8, Session.randomId().length)
  }

  @Test
  fun `randomId suffix always forms a valid session id`() {
    repeat(200) {
      assertTrue(Session.isValid("U-20240101-${Session.randomId()}"))
    }
  }

  @Test
  fun `alphabet characters are all valid in regex`() {
    assertTrue(Session.isValid("20240101-${Session.ID_ALPHABET.take(12)}"))
    Session.ID_ALPHABET.chunked(12).filter { it.length >= 4 }.forEach {
      assertTrue(Session.isValid("20240101-$it"), it)
    }
  }

  // ---- NULL sentinel ----

  @Suppress("DEPRECATION")
  @Test
  fun `NULL sentinel has empty id and skips validation`() {
    val nul = Session.NULL
    assertEquals("", nul.sessionId)
    assertTrue(nul.isNull())
    assertFalse(nul.isGlobal())
    assertEquals("", nul.toString())
    assertDoesNotThrow { Session.validateSessionId(nul) }
  }

  @Test
  fun `regular session is not null`() {
    assertFalse(Session("20240101-abcd").isNull())
  }

  // ---- equality / misc ----

  @Test
  fun `equality and hashCode are based on sessionId`() {
    val a = Session("U-20240101-abcd")
    val b = Session("U-20240101-abcd")
    val c = Session("U-20240101-abce")
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertNotEquals(a, c)
    assertNotEquals(a, "U-20240101-abcd")
    assertNotEquals(a, null)
    assertEquals(1, setOf(a, b).size)
  }

  @Test
  fun `toString returns sessionId`() {
    assertEquals("G-20240101-abcd", Session("G-20240101-abcd").toString())
  }

  @Test
  fun `withUser creates UserSession`() {
    val session = Session("U-20240101-abcd")
    val user = User("alice@example.com")
    val us = session.withUser(user)
    assertSame(session, us.session)
    assertSame(user, us.user)
    assertEquals(UserSession(session, user), us)
  }
}