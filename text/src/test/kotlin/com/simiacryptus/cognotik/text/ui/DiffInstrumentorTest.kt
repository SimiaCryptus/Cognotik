package com.simiacryptus.cognotik.text.ui

import com.simiacryptus.cognotik.text.patch.PatchParser.ResponseSegment
import com.simiacryptus.cognotik.text.patch.PatchProcessor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [DiffInstrumentor].
 *
 * Collaborators are mocked; the [FileSystem] mock is backed by an in-memory map so that
 * "writes" can be inspected (and made to fail) without touching disk, while *resolution*
 * still exercises the real temp-directory tree (needed by the filesystem-search strategy).
 */
class  DiffInstrumentorTest {

  @TempDir
  lateinit var tempDir: Path

  /** Project root passed to [DiffInstrumentor.instrument]; individual tests may reassign it. */
  private lateinit var root: Path

  private val processor = mockk<PatchProcessor>(relaxed = true)
  private val renderer = mockk<DiffUIRenderer>(relaxed = true)
  private val fs = mockk<FileSystem>(relaxed = true)

  /* ---------------- in-memory filesystem state ---------------- */
  private val written = linkedMapOf<Path, String>()
  private val failWritesFor = mutableSetOf<Path>()

  /* ---------------- captured renderer interactions ---------------- */
  private val saveActions = linkedMapOf<Path, () -> Unit>()
  private val applyButtons = linkedMapOf<Path, DiffButtons>()
  private val recordedPatches = mutableListOf<Map<*, *>>()
  private val warnings = mutableListOf<String>()
  private var summaries: List<FileChangeSummary> = emptyList()
  private var applyAll: (() -> Unit)? = null
  private var summaryRenderCount = 0

  /* ---------------- captured handle() invocations ---------------- */
  private val handled = mutableListOf<Map<Path, String>>()

  data class DiffButtons(
    val onApply: () -> Unit,
    val onRevert: () -> Unit,
    val onForceApply: () -> Unit
  )

  @BeforeEach
  fun setUp() {
    root = tempDir.resolve("project").also { Files.createDirectories(it) }
    stubFileSystem()
    stubRenderer()
  }

  /* =========================================================================
   *                                fixtures
   * ========================================================================= */

  @Suppress("UNCHECKED_CAST")
  private fun stubFileSystem() {
    every { fs.resolve(any(), any()) } answers { call ->
      val base = call.invocation.args[0] as Path
      val child = call.invocation.args[1] as String
      base.resolve(child).normalize()
    }
    every { fs.exists(any()) } answers { call ->
      val p = call.invocation.args[0] as Path
      written.containsKey(p) || Files.exists(p)
    }
    every { fs.readText(any()) } answers { call ->
      val p = call.invocation.args[0] as Path
      written[p] ?: if (Files.isRegularFile(p)) Files.readString(p) else ""
    }
    every { fs.writeText(any(), any()) } answers { call ->
      val p = call.invocation.args[0] as Path
      val c = call.invocation.args[1] as String
      if (p in failWritesFor) throw RuntimeException("disk full: $p")
      written[p] = c
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun stubRenderer() {
    every { renderer.renderWarning(any()) } answers { call ->
      val msg = call.invocation.args[0] as String
      warnings.add(msg)
      "[WARN:$msg]"
    }
    every { renderer.renderAutoApplied(any(), any()) } answers { call ->
      "[AUTO:${call.invocation.args[0]}]${call.invocation.args[1]}"
    }
    every { renderer.recordPatch(any()) } answers { call ->
      recordedPatches.add(call.invocation.args[0] as Map<*, *>)
      ""
    }
    every { renderer.renderSaveButton(any(), any(), any(), any()) } answers { call ->
      val args = call.invocation.args
      saveActions[args[0] as Path] = args[3] as () -> Unit
      "[SAVE:${args[0]}]"
    }
    every { renderer.renderApplyDiffButton(any(), any(), any(), any(), any()) } answers { call ->
      val args = call.invocation.args
      applyButtons[args[0] as Path] = DiffButtons(
        onApply = args[2] as () -> Unit,
        onRevert = args[3] as () -> Unit,
        onForceApply = args[4] as () -> Unit
      )
      "[APPLY:${args[0]}]"
    }
    every { renderer.renderChangeSummary(any(), any()) } answers { call ->
      summaryRenderCount++
      summaries = call.invocation.args[0] as List<FileChangeSummary>
      applyAll = call.invocation.args[1] as (() -> Unit)?
      "[SUMMARY]"
    }
  }

  /** Only resolves names that already exist on disk, like a real project resolver. */
  private val existingFileResolver: (Path, String) -> String? = { r, f ->
    val candidate = try {
      r.resolve(f).normalize()
    } catch (e: Throwable) {
      null
    }
    if (candidate != null && Files.isRegularFile(candidate)) {
      try {
        r.relativize(candidate).toString()
      } catch (e: Throwable) {
        null
      }
    } else null
  }

  private fun file(relative: String, content: String = "ORIGINAL\n"): Path {
    val p = root.resolve(relative)
    Files.createDirectories(p.parent)
    Files.writeString(p, content)
    return p
  }

  private fun markdown(text: String): ResponseSegment.Markdown {
    val seg = mockk<ResponseSegment.Markdown>()
    every { seg.removeCodeFences() } returns text
    return seg
  }

  private fun newFileSegment(
    filename: String?,
    code: String,
    language: String = "kt"
  ): ResponseSegment.NewFileBlock {
    val seg = mockk<ResponseSegment.NewFileBlock>()
    every { seg.filename } returns filename
    every { seg.language } returns language
    every { seg.removeCodeFences() } returns code
    return seg
  }

  private fun diffSegment(
    filename: String?,
    diff: String = DIFF_TEXT
  ): ResponseSegment.DiffBlock {
    val seg = mockk<ResponseSegment.DiffBlock>()
    every { seg.filename } returns filename
    every { seg.calcFilename(any()) } returns filename?.let { Path.of(it) }
    every { seg.removeCodeFences() } returns diff
    return seg
  }

  /** Stubs the (unnamed) patch-apply result type via chained MockK stubs. */
  private fun stubApply(newCode: String, isValid: Boolean) {
    every { processor.apply(any(), any(), any()).newCode } returns newCode
    every { processor.apply(any(), any(), any()).isValid } returns isValid
    every { processor.apply(any(), any(), any()).errors } returns emptyList()
  }

  private fun instrumentor(
    allowInvalid: Boolean = true,
    forceChildPaths: Boolean = false,
    fileSystem: FileSystem = fs
  ) = DiffInstrumentor(processor, renderer, fileSystem, allowInvalid, forceChildPaths)

  private fun instrument(
    vararg segments: ResponseSegment,
    response: String = "RESPONSE BODY",
    defaultFile: String? = null,
    autoApply: (Path) -> Boolean = { false },
    resolver: (Path, String) -> String? = existingFileResolver,
    prefilter: (String) -> String? = { it },
    subject: DiffInstrumentor = instrumentor()
  ): String {
    every { processor.parse(any(), any()) } returns segments.toList()
    return subject.instrument(
      root = root,
      response = response,
      handle = { handled.add(it) },
      shouldAutoApply = autoApply,
      defaultFile = defaultFile,
      resolver = resolver,
      prefilterFilename = prefilter
    )
  }

  companion object {
    private val DIFF_TEXT = """
        |  context one
        |- removed line
        |+ added line one
        |+ added line two
        |  context two
      """.trimMargin()
  }

  /* =========================================================================
   *                            short-circuit paths
   * ========================================================================= */

  @Nested
  @DisplayName("early exits")
  inner class EarlyExits {

    @Test
    fun `empty response is returned verbatim and is never parsed`() {
      val result = instrumentor().instrument(
        root = root, response = "", resolver = existingFileResolver, prefilterFilename = { it }
      )
      assertEquals("", result)
      verify(exactly = 0) { processor.parse(any(), any()) }
    }

    @Test
    fun `whitespace-only response is returned verbatim`() {
      val response = "   \n\t\n "
      val result = instrumentor().instrument(
        root = root, response = response, resolver = existingFileResolver, prefilterFilename = { it }
      )
      assertEquals(response, result)
      verify(exactly = 0) { processor.parse(any(), any()) }
    }

    @Test
    fun `response with no segments is returned verbatim`() {
      val result = instrument(response = "nothing parseable")
      assertEquals("nothing parseable", result)
      assertEquals(0, summaryRenderCount)
    }

    @Test
    fun `default file is forwarded to the parser`() {
      instrument(markdown("hi"), defaultFile = "src/App.kt")
      verify { processor.parse("RESPONSE BODY", "src/App.kt") }
    }
  }

  /* =========================================================================
   *                                 markdown
   * ========================================================================= */

  @Nested
  @DisplayName("markdown segments")
  inner class Markdown {

    @Test
    fun `markdown is trimmed and separated by a blank line`() {
      val result = instrument(markdown("first paragraph   \n\n"), markdown("second paragraph\n"))
      assertEquals("first paragraph\n\nsecond paragraph\n\n".trim(), result.trim())
    }

    @Test
    fun `markdown alone produces no change summary`() {
      instrument(markdown("just prose"))
      assertEquals(0, summaryRenderCount)
      verify(exactly = 0) { renderer.renderChangeSummary(any(), any()) }
    }
  }

  /* =========================================================================
   *                              new file blocks
   * ========================================================================= */

  @Nested
  @DisplayName("new file blocks")
  inner class NewFiles {

    @Test
    fun `blank filename renders code block plus warning and records no change`() {
      val result = instrument(newFileSegment(filename = null, code = "val x = 1"))
      assertTrue(result.contains("```kt\nval x = 1\n```"), result)
      assertTrue(warnings.single().contains("could not be associated with a valid filename"), warnings.toString())
      assertEquals(0, summaryRenderCount)
      assertTrue(saveActions.isEmpty())
    }

    @Test
    fun `prefilter may rewrite the filename`() {
      instrument(
        newFileSegment("Weird Name.kt", "val x = 1"),
        prefilter = { "clean/Renamed.kt" }
      )
      assertEquals(setOf(root.resolve("clean/Renamed.kt")), saveActions.keys)
    }

    @Test
    fun `prefilter returning null falls back to the raw filename`() {
      instrument(newFileSegment("Kept.kt", "val x = 1"), prefilter = { null })
      assertEquals(setOf(root.resolve("Kept.kt")), saveActions.keys)
    }

    @Test
    fun `save button action writes the file and notifies the handler`() {
      val result = instrument(newFileSegment("pkg/New.kt", "val x = 1"))
      val target = root.resolve("pkg/New.kt")
      assertTrue(result.contains("[SAVE:$target]"), result)
      assertTrue(written.isEmpty(), "nothing must be written before the button is pressed")

      saveActions.getValue(target).invoke()

      assertEquals("val x = 1", written[target])
      assertEquals(listOf(mapOf(target to "val x = 1")), handled)
    }

    @Test
    fun `code is unindented and stray fences are stripped`() {
      instrument(newFileSegment("A.kt", "```kt\n  val x = 1\n```"))
      val target = root.resolve("A.kt")
      saveActions.getValue(target).invoke()
      assertEquals("val x = 1", written[target])
    }

    @Test
    fun `code block in the output is indented by two spaces`() {
      val result = instrument(newFileSegment("A.kt", "val x = 1"))
      assertTrue(result.contains("```kt\n  val x = 1\n```"), result)
    }

    @Test
    fun `summary describes a new file change`() {
      instrument(newFileSegment("pkg/New.kt", "line1\nline2\nline3"))
      val summary = summaries.single()
      assertEquals(root.resolve("pkg/New.kt"), summary.path)
      assertEquals(ChangeType.NEW_FILE, summary.changeType)
      assertEquals(3, summary.linesAdded)
      assertEquals(0, summary.linesRemoved)
      assertTrue(summary.isValid)
      assertFalse(summary.applied)
      assertNotNull(applyAll)
    }

    @Test
    fun `blank content still yields a save control`() {
      instrument(newFileSegment("Blank.kt", "   "))
      assertEquals(setOf(root.resolve("Blank.kt")), saveActions.keys)
      assertEquals(0, summaries.single().linesAdded)
    }

    @Test
    fun `auto apply writes immediately and marks the change as applied`() {
      val target = root.resolve("Auto.kt")
      val result = instrument(newFileSegment("Auto.kt", "val x = 1"), autoApply = { it == target })

      assertEquals("val x = 1", written[target])
      assertEquals(listOf(mapOf(target to "val x = 1")), handled)
      assertTrue(result.contains("[AUTO:$target]"), result)
      assertTrue(summaries.single().applied)
      assertNull(applyAll, "nothing is pending, so apply-all must be unavailable")
      assertEquals("save_auto", recordedPatches.single()["action"])
    }

    @Test
    fun `auto apply overwrites an existing file`() {
      val target = file("Auto.kt", "OLD")
      instrument(newFileSegment("Auto.kt", "NEW"), autoApply = { true })
      assertEquals("NEW", written[target])
    }

    @Test
    fun `auto apply failure surfaces an error and leaves the change pending`() {
      val target = root.resolve("Auto.kt")
      failWritesFor.add(target)

      val result = instrument(newFileSegment("Auto.kt", "val x = 1"), autoApply = { true })

      assertTrue(result.contains("Error Auto-Saving"), result)
      assertEquals("save_auto_failed", recordedPatches.single()["action"])
      assertFalse(summaries.single().applied)
      assertNotNull(applyAll, "the failed change must remain retryable")
    }

    @Test
    fun `recorded patch carries filename, code and trace`() {
      instrument(newFileSegment("A.kt", "val x = 1"))
      val patch = recordedPatches.single()
      assertEquals(root.resolve("A.kt").toString(), patch["filename"])
      assertEquals("val x = 1", patch["code"])
      assertEquals("save", patch["action"])
      assertNotNull(patch["trace"])
    }

    @Test
    fun `real filesystem implementation actually persists the file`() {
      instrument(
        newFileSegment("Top.kt", "val x = 1"),
        subject = instrumentor(fileSystem = RealFileSystem())
      )
      saveActions.getValue(root.resolve("Top.kt")).invoke()
      assertEquals("val x = 1", Files.readString(root.resolve("Top.kt")))
    }
  }

  /* =========================================================================
   *                        new file path resolution
   * ========================================================================= */

  @Nested
  @DisplayName("new file path resolution")
  inner class NewFileResolution {

    @Test
    fun `absolute path inside root is relativized instead of being duplicated`() {
      val absolute = root.resolve("active/foo.json").toString()
      instrument(newFileSegment(absolute, "{}", language = "json"))
      assertEquals(setOf(root.resolve("active/foo.json")), saveActions.keys)
    }

    @Test
    fun `absolute path outside root is used as-is when child paths are not forced`() {
      val outside = tempDir.resolve("outside/foo.kt")
      instrument(newFileSegment(outside.toString(), "val x = 1"))
      assertEquals(setOf(outside.normalize()), saveActions.keys)
    }

    @Test
    fun `absolute path outside root is rejected when child paths are forced`() {
      val outside = tempDir.resolve("outside/foo.kt")
      val result = instrument(
        newFileSegment(outside.toString(), "val x = 1"),
        subject = instrumentor(forceChildPaths = true)
      )
      assertTrue(saveActions.isEmpty())
      assertTrue(warnings.single().contains("could not be resolved to a valid path"), warnings.toString())
      assertTrue(result.contains("```kt"), result)
    }

    @Test
    fun `relative escape above root is rejected when child paths are forced`() {
      val result = instrument(
        newFileSegment("../../escape.kt", "val x = 1"),
        subject = instrumentor(forceChildPaths = true)
      )
      assertTrue(saveActions.isEmpty())
      assertTrue(warnings.single().contains("could not be resolved"), warnings.toString())
      assertFalse(result.isBlank())
    }

    @Test
    fun `updir prefix naming the root directory is stripped`() {
      instrument(newFileSegment("../${root.fileName}/x.kt", "val x = 1"))
      assertEquals(setOf(root.resolve("x.kt")), saveActions.keys)
    }

    @Test
    fun `overlap between filename prefix and root suffix is not duplicated`() {
      root = tempDir.resolve("generated_app/ops").also { Files.createDirectories(it) }
      instrument(newFileSegment("generated_app/ops/file.md", "# hi", language = "md"))
      assertEquals(setOf(root.resolve("file.md")), saveActions.keys)
    }

    @Test
    fun `git style prefix is stripped when the stripped path already exists`() {
      val existing = file("lib/x.kt", "OLD")
      instrument(newFileSegment("a/lib/x.kt", "NEW"))
      assertEquals(setOf(existing), saveActions.keys)
    }

    @Test
    fun `unknown nested path is treated as a brand new file under root`() {
      instrument(newFileSegment("brand/new/deep/File.kt", "val x = 1"))
      assertEquals(setOf(root.resolve("brand/new/deep/File.kt")), saveActions.keys)
    }
  }

  /* =========================================================================
   *                                diff blocks
   * ========================================================================= */

  @Nested
  @DisplayName("diff blocks")
  inner class Diffs {

    @Test
    fun `extensionless filename without default file yields a warning`() {
      val result = instrument(diffSegment("Makefile"))
      assertTrue(result.contains("```diff"), result)
      assertTrue(warnings.single().contains("could not be associated with a valid filename"), warnings.toString())
      assertEquals(0, summaryRenderCount)
    }

    @Test
    fun `extensionless filename falls back to the default file`() {
      val target = file("src/App.kt")
      stubApply(newCode = "PATCHED", isValid = true)
      instrument(diffSegment("Makefile"), defaultFile = "src/App.kt")
      assertTrue(applyButtons.containsKey(target), applyButtons.keys.toString())
    }

    @Test
    fun `missing filename and missing default file is a programming error`() {
      assertThrows(NullPointerException::class.java) {
        instrument(diffSegment(null))
      }
    }

    @Test
    fun `empty diff content renders a warning and records diff_empty`() {
      file("src/App.kt")
      val result = instrument(diffSegment("src/App.kt", diff = "   "))
      assertTrue(warnings.single().contains("Empty diff content"), warnings.toString())
      assertEquals("diff_empty", recordedPatches.single()["action"])
      assertEquals(0, summaryRenderCount)
      assertTrue(result.contains("```diff"), result)
    }

    @Test
    fun `invalid patch renders a validation warning plus the apply controls`() {
      val target = file("src/App.kt")
      stubApply(newCode = "", isValid = false)

      val result = instrument(diffSegment("src/App.kt"))

      assertTrue(warnings.any { it.startsWith("The patch is not valid") }, warnings.toString())
      assertTrue(result.contains("[APPLY:$target]"), result)
      val summary = summaries.single()
      assertEquals(ChangeType.MODIFIED, summary.changeType)
      assertFalse(summary.isValid)
      assertFalse(summary.applied)
    }

    @Test
    fun `valid patch is not applied until the button is pressed`() {
      val target = file("src/App.kt")
      stubApply(newCode = "PATCHED", isValid = true)

      val result = instrument(diffSegment("src/App.kt"))

      assertTrue(result.contains("[APPLY:$target]"), result)
      assertTrue(handled.isEmpty())
      assertFalse(warnings.any { it.startsWith("The patch is not valid") }, warnings.toString())
      assertTrue(summaries.single().isValid)
    }

    @Test
    fun `apply button applies the patch and reports the relative path`() {
      val target = file("src/App.kt")
      stubApply(newCode = "PATCHED", isValid = true)
      instrument(diffSegment("src/App.kt"))

      applyButtons.getValue(target).onApply()

      assertEquals(1, handled.size)
      assertEquals(setOf(Path.of("src/App.kt")), handled.single().keys)
      assertEquals("PATCHED", handled.single().values.single())
    }

    @Test
    fun `revert after apply restores the original content`() {
      val target = file("src/App.kt", "ORIGINAL")
      stubApply(newCode = "PATCHED", isValid = true)
      instrument(diffSegment("src/App.kt"))

      val buttons = applyButtons.getValue(target)
      buttons.onApply()
      buttons.onRevert()

      assertEquals(2, handled.size)
      assertEquals("ORIGINAL", handled.last().values.single())
    }

    @Test
    fun `revert without a previous apply is a no-op`() {
      val target = file("src/App.kt", "ORIGINAL")
      stubApply(newCode = "PATCHED", isValid = true)
      instrument(diffSegment("src/App.kt"))

      applyButtons.getValue(target).onRevert()

      assertTrue(handled.isEmpty())
    }

    @Test
    fun `valid patch is auto applied when requested`() {
      val target = file("src/App.kt")
      stubApply(newCode = "PATCHED", isValid = true)

      val result = instrument(diffSegment("src/App.kt"), autoApply = { it == target })

      assertTrue(result.contains("[AUTO:$target]"), result)
      assertEquals("PATCHED", handled.single().values.single())
      assertTrue(summaries.single().applied)
      assertNull(applyAll)
      assertEquals("auto_apply", recordedPatches.single()["action"])
    }

    @Test
    fun `invalid patch is never auto applied`() {
      val target = file("src/App.kt")
      stubApply(newCode = "", isValid = false)

      val result = instrument(diffSegment("src/App.kt"), autoApply = { true })

      assertTrue(handled.isEmpty())
      assertTrue(result.contains("[APPLY:$target]"), result)
      assertEquals("diff", recordedPatches.single()["action"])
    }

    @Test
    fun `summary uses DiffStats for line counts and a root relative path`() {
      file("src/App.kt")
      stubApply(newCode = "PATCHED", isValid = true)
      instrument(diffSegment("src/App.kt"))

      val summary = summaries.single()
      assertEquals(Path.of("src/App.kt"), summary.relativePath)
      assertEquals(DiffStats.linesAdded(DIFF_TEXT), summary.linesAdded)
      assertEquals(DiffStats.linesRemoved(DIFF_TEXT), summary.linesRemoved)
    }

    @Test
    fun `recorded patch carries original code, diff and validity`() {
      file("src/App.kt", "ORIGINAL")
      stubApply(newCode = "PATCHED", isValid = true)
      instrument(diffSegment("src/App.kt"))

      val patch = recordedPatches.single()
      assertEquals(root.resolve("src/App.kt").toString(), patch["filename"])
      assertEquals("ORIGINAL", patch["originalCode"])
      assertEquals(DIFF_TEXT, patch["diff"])
      assertEquals(true, patch["isValid"])
      assertNotNull(patch["trace"])
    }

    @Test
    fun `patch validation exceptions are treated as invalid rather than propagated`() {
      val target = file("src/App.kt")
      every { processor.apply(any(), any(), any()) } throws RuntimeException("parser blew up")

      val result = instrument(diffSegment("src/App.kt"))

      assertTrue(result.contains("[APPLY:$target]"), result)
      assertFalse(summaries.single().isValid)
    }
  }

  /* =========================================================================
   *                 diffs for files that do not exist yet
   * ========================================================================= */

  @Nested
  @DisplayName("diff for an unresolvable file")
  inner class DiffCreatesFile {

    @Test
    fun `diff applied to blank content creates the file`() {
      stubApply(newCode = "CREATED CONTENT", isValid = true)

      val result = instrument(diffSegment("brand/new/File.kt"))

      val target = root.resolve("brand/new/File.kt")
      assertTrue(result.contains("[SAVE:$target]"), result)
      assertEquals(ChangeType.NEW_FILE, summaries.single().changeType)

      saveActions.getValue(target).invoke()
      assertEquals("CREATED CONTENT", written[target])
      verify { processor.apply("", match { it.contains("```diff") }, "brand/new/File.kt") }
    }

    @Test
    fun `language for the created file is derived from its extension`() {
      stubApply(newCode = "CREATED", isValid = true)
      val result = instrument(diffSegment("brand/new/File.kt"))
      assertTrue(result.contains("```kt"), result)
    }

    @Test
    fun `invalid result is accepted when allowInvalid is true`() {
      stubApply(newCode = "CREATED", isValid = false)
      instrument(diffSegment("brand/new/File.kt"), subject = instrumentor(allowInvalid = true))
      assertEquals(setOf(root.resolve("brand/new/File.kt")), saveActions.keys)
    }

    @Test
    fun `invalid result is rejected when allowInvalid is false`() {
      stubApply(newCode = "CREATED", isValid = false)
      val result = instrument(diffSegment("brand/new/File.kt"), subject = instrumentor(allowInvalid = false))

      assertTrue(saveActions.isEmpty())
      assertTrue(warnings.single().contains("could not be applied to create the file"), warnings.toString())
      assertTrue(result.contains("```diff"), result)
      assertEquals(0, summaryRenderCount)
    }

    @Test
    fun `blank result is rejected`() {
      stubApply(newCode = "   ", isValid = true)
      instrument(diffSegment("brand/new/File.kt"))
      assertTrue(saveActions.isEmpty())
      assertTrue(warnings.single().contains("could not be applied to create the file"), warnings.toString())
    }

    @Test
    fun `exception while creating the file is reported as a warning`() {
      every { processor.apply(any(), any(), any()) } throws RuntimeException("boom")

      val result = instrument(diffSegment("brand/new/File.kt"))

      assertTrue(warnings.any { it.contains("Error applying diff to create new file") }, warnings.toString())
      assertTrue(warnings.any { it.contains("boom") }, warnings.toString())
      assertTrue(result.contains("```diff"), result)
      assertTrue(saveActions.isEmpty())
    }

    @Test
    fun `path outside root is rejected when child paths are forced`() {
      stubApply(newCode = "CREATED", isValid = true)
      val result = instrument(
        diffSegment("../../escape.kt"),
        subject = instrumentor(forceChildPaths = true)
      )
      assertTrue(saveActions.isEmpty())
      assertTrue(warnings.any { it.contains("could not be resolved to a valid path") }, warnings.toString())
      assertTrue(result.contains("```diff"), result)
    }
  }

  /* =========================================================================
   *                    best-effort resolution strategies
   * ========================================================================= */

  @Nested
  @DisplayName("best effort diff resolution")
  inner class BestEffortResolution {

    @BeforeEach
    fun validPatch() = stubApply(newCode = "PATCHED", isValid = true)

    @Test
    fun `absolute path under root is relativized before resolution`() {
      val target = file("src/App.kt")
      instrument(diffSegment(target.toString()))
      assertTrue(applyButtons.containsKey(target), applyButtons.keys.toString())
    }

    @Test
    fun `git prefix is stripped`() {
      val target = file("src/App.kt")
      instrument(diffSegment("b/src/App.kt"))
      assertTrue(applyButtons.containsKey(target), applyButtons.keys.toString())
    }

    @Test
    fun `progressively shorter path suffixes are attempted`() {
      val target = file("src/main/App.kt")
      instrument(diffSegment("some/other/project/src/main/App.kt"))
      assertTrue(applyButtons.containsKey(target), applyButtons.keys.toString())
    }

    @Test
    fun `unique filesystem match with two matching components is accepted`() {
      val target = file("deep/nested/Uniq.kt")
      instrument(diffSegment("elsewhere/nested/Uniq.kt"))
      assertTrue(applyButtons.containsKey(target), applyButtons.keys.toString())
    }

    @Test
    fun `filesystem match with only the filename in common is rejected`() {
      file("deep/Only.kt")
      instrument(diffSegment("foo/bar/Only.kt"))
      assertFalse(applyButtons.containsKey(root.resolve("deep/Only.kt")))
      // falls through to "create new file from diff"
      assertEquals(setOf(root.resolve("foo/bar/Only.kt")), saveActions.keys)
    }

    @Test
    fun `ambiguous filesystem matches are disambiguated by matching components`() {
      val expected = file("one/deep/Dup.kt")
      file("two/other/Dup.kt")
      instrument(diffSegment("zzz/deep/Dup.kt"))
      assertTrue(applyButtons.containsKey(expected), applyButtons.keys.toString())
    }

    @Test
    fun `windows style separators are normalized`() {
      val target = file("src/App.kt")
      instrument(diffSegment("src\\App.kt"))
      assertTrue(applyButtons.containsKey(target), applyButtons.keys.toString())
    }

    @Test
    fun `custom resolver result is honoured`() {
      val target = file("actual/Location.kt")
      instrument(diffSegment("whatever.kt"), resolver = { _, _ -> "actual/Location.kt" })
      assertTrue(applyButtons.containsKey(target), applyButtons.keys.toString())
    }
  }

  /* =========================================================================
   *                            change summary
   * ========================================================================= */

  @Nested
  @DisplayName("change summary")
  inner class Summary {

    @Test
    fun `summary is appended once and lists every touched file`() {
      val existing = file("src/App.kt")
      stubApply(newCode = "PATCHED", isValid = true)

      val result = instrument(
        markdown("intro"),
        newFileSegment("New.kt", "val x = 1"),
        diffSegment("src/App.kt")
      )

      assertEquals(1, summaryRenderCount)
      assertTrue(result.trimEnd().contains("[SUMMARY]"), result)
      assertEquals(
        listOf(root.resolve("New.kt"), existing),
        summaries.map { it.path }
      )
      assertEquals(listOf(ChangeType.NEW_FILE, ChangeType.MODIFIED), summaries.map { it.changeType })
    }

    @Test
    fun `apply all applies every pending change`() {
      instrument(
        newFileSegment("A.kt", "AAA"),
        newFileSegment("B.kt", "BBB")
      )

      applyAll!!.invoke()

      assertEquals("AAA", written[root.resolve("A.kt")])
      assertEquals("BBB", written[root.resolve("B.kt")])
      assertEquals(2, handled.size)
    }

    @Test
    fun `apply all continues past failures and then reports them`() {
      failWritesFor.add(root.resolve("A.kt"))
      instrument(
        newFileSegment("A.kt", "AAA"),
        newFileSegment("B.kt", "BBB")
      )

      val error = assertThrows(RuntimeException::class.java) { applyAll!!.invoke() }

      assertTrue(error.message!!.contains("1 of 2"), error.message)
      assertEquals("BBB", written[root.resolve("B.kt")], "the healthy change must still be applied")
    }

    @Test
    fun `apply all is unavailable when nothing is pending`() {
      instrument(newFileSegment("A.kt", "AAA"), autoApply = { true })
      assertEquals(1, summaryRenderCount)
      assertNull(applyAll)
    }

    @Test
    fun `apply all is available when at least one change is pending`() {
      stubApply(newCode = "PATCHED", isValid = true)
      val existing = file("src/App.kt")
      instrument(
        newFileSegment("A.kt", "AAA"),
        diffSegment("src/App.kt"),
        autoApply = { it == existing }
      )
      assertNotNull(applyAll)
      assertEquals(listOf(false, true), summaries.map { it.applied })
    }
  }
}