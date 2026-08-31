package com.simiacryptus.cognotik.webui.servlet.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MimeTypeResolverTest {

  @Test
  fun `explicit javascript overrides mapped default`() {
    assertEquals("application/javascript", MimeTypeResolver.getMimeType("app.js"))
    assertEquals("application/javascript", MimeTypeResolver.getMimeType("module.mjs"))
  }

  @Test
  fun `log files are treated as plain text`() {
    assertEquals("text/plain", MimeTypeResolver.getMimeType("server.log"))
  }

  @Test
  fun `known source extensions fall back to text plain`() {
    assertEquals("text/plain", MimeTypeResolver.getMimeType("Main.kt"))
    assertEquals("text/plain", MimeTypeResolver.getMimeType("build.gradle"))
    assertEquals("text/plain", MimeTypeResolver.getMimeType("script.py"))
    assertEquals("text/plain", MimeTypeResolver.getMimeType("notes.md"))
  }

  @Test
  fun `well known dotfiles are treated as plain text`() {
    assertEquals("text/plain", MimeTypeResolver.getMimeType(".gitignore"))
    assertEquals("text/plain", MimeTypeResolver.getMimeType(".editorconfig"))
  }

  @Test
  fun `unlisted dotfile without further dot is treated as plain text`() {
    assertEquals("text/plain", MimeTypeResolver.getMimeType(".customrc"))
  }

  @Test
  fun `well known extensionless filenames are treated as plain text`() {
    assertEquals("text/plain", MimeTypeResolver.getMimeType("Dockerfile"))
    assertEquals("text/plain", MimeTypeResolver.getMimeType("Makefile"))
    assertEquals("text/plain", MimeTypeResolver.getMimeType("README"))
  }

  @Test
  fun `file with no extension defaults to plain text`() {
    assertEquals("text/plain", MimeTypeResolver.getMimeType("randomfilewithnoext"))
  }

  @Test
  fun `unknown binary extension falls back to octet stream`() {
    assertEquals("application/octet-stream", MimeTypeResolver.getMimeType("archive.xyzabc"))
  }

  @Test
  fun `path with directories resolves based on file name`() {
    assertEquals("text/plain", MimeTypeResolver.getMimeType("some/dir/.gitignore"))
  }
}