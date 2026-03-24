package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.DiffApplicationResult
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.util.ParenMatchingValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DiffInstrumentorResolveNewFilePathTest {

  /**
   * When the filename "generated_app/ops/profile_analysis_op.md" is resolved against
   * root "/home/andrew/.cognotik/data/.../generated_app/ops", the filename's leading
   * components overlap with the root's trailing components. The resolved path should
   * NOT duplicate those components.
   *
   * Expected: "profile_analysis_op.md" (relative to root)
   * NOT: "generated_app/ops/profile_analysis_op.md" (which would duplicate the suffix)
   */
  @Test
  fun `resolveNewFilePath should not duplicate path when filename is suffix of root`() {
    val root = Path.of("/home/andrew/.cognotik/data/user-sessions/acharneski@gmail.com/20260322/rfbn/generated_app/ops")
    val filename = "generated_app/ops/profile_analysis_op.md"

    val mockFs = object : FileSystem {
      override fun exists(path: Path): Boolean = false
      override fun readText(path: Path): String = ""
      override fun writeText(path: Path, content: String) {}
      override fun resolve(root: Path, relative: String): Path = root.resolve(relative)
    }

    val processor = object : com.simiacryptus.cognotik.diff.PatchProcessor {
      override fun parse(response: String, defaultFile: String?): List<com.simiacryptus.cognotik.diff.PatchParser.ResponseSegment> = emptyList()
      override val label: String get() = "TestProcessor"
      override fun generatePatch(oldCode: String, newCode: String): String = ""
      override fun applyPatch(source: String, patch: String): String = ""
      override fun apply(prevCode: String, patch: String, filename: String?): DiffApplicationResult = DiffApplicationResult("", emptyList(), validator = ParenMatchingValidator())
    }

    val renderer = object : DiffUIRenderer {
      override fun renderSaveButton(filepath: Path, code: String, lang: String, onSave: () -> Unit): String = ""
      override fun renderApplyDiffButton(filepath: Path, diff: String, onApply: () -> Unit, onRevert: () -> Unit): String = ""
      override fun renderAutoApplied(filepath: Path, revertButton: String): String = ""
      override fun renderWarning(message: String): String = ""
      override fun recordPatch(data: Map<String, Any?>): String = ""
    }

    val instrumentor = DiffInstrumentor(processor, renderer, mockFs)




    val method = DiffInstrumentor::class.java.getDeclaredMethod(
      "resolveNewFilePath",
      Path::class.java,
      String::class.java,
      Function2::class.java
    )
    method.isAccessible = true
    val resolver: (Path, String) -> String? = ::resolveToRelativePath
    val result = method.invoke(instrumentor, root, filename, resolver) as String?

    // The resolved relative path should be just "profile_analysis_op.md"
    // so that root.resolve(result) = .../generated_app/ops/profile_analysis_op.md
    assertEquals("profile_analysis_op.md", result,
      "When filename 'generated_app/ops/profile_analysis_op.md' overlaps with root ending in 'generated_app/ops', " +
          "the resolved relative path should strip the overlapping prefix")
  }

  @Test
  fun `resolveNewFilePath should handle non-overlapping filename normally`() {
    val root = Path.of("/home/user/project")
    val filename = "src/main/Foo.kt"

    val mockFs = object : FileSystem {
      override fun exists(path: Path): Boolean = false
      override fun readText(path: Path): String = ""
      override fun writeText(path: Path, content: String) {}
      override fun resolve(root: Path, relative: String): Path = root.resolve(relative)
    }

    val processor = object : com.simiacryptus.cognotik.diff.PatchProcessor {
      override fun parse(response: String, defaultFile: String?): List<com.simiacryptus.cognotik.diff.PatchParser.ResponseSegment> = emptyList()
      override val label: String get() = "TestProcessor"
      override fun generatePatch(oldCode: String, newCode: String): String = ""
      override fun applyPatch(source: String, patch: String): String = ""
      override fun apply(prevCode: String, patch: String, filename: String?): DiffApplicationResult = DiffApplicationResult("", emptyList(), validator = ParenMatchingValidator())
    }

    val renderer = object : DiffUIRenderer {
      override fun renderSaveButton(filepath: Path, code: String, lang: String, onSave: () -> Unit): String = ""
      override fun renderApplyDiffButton(filepath: Path, diff: String, onApply: () -> Unit, onRevert: () -> Unit): String = ""
      override fun renderAutoApplied(filepath: Path, revertButton: String): String = ""
      override fun renderWarning(message: String): String = ""
      override fun recordPatch(data: Map<String, Any?>): String = ""
    }

    val instrumentor = DiffInstrumentor(processor, renderer, mockFs)

    val method = DiffInstrumentor::class.java.getDeclaredMethod(
      "resolveNewFilePath",
      Path::class.java,
      String::class.java,
      Function2::class.java
    )
    method.isAccessible = true
    val resolver: (Path, String) -> String? = ::resolveToRelativePath
    val result = method.invoke(instrumentor, root, filename, resolver) as String?

    // No overlap, so the full filename should be the relative path
    assertEquals("src/main/Foo.kt", result)
  }

  @Test
  fun `resolveNewFilePath should handle partial overlap`() {
    val root = Path.of("/home/user/project/src/main")
    val filename = "main/utils/Helper.kt"

    val mockFs = object : FileSystem {
      override fun exists(path: Path): Boolean = false
      override fun readText(path: Path): String = ""
      override fun writeText(path: Path, content: String) {}
      override fun resolve(root: Path, relative: String): Path = root.resolve(relative)
    }

    val processor = object : com.simiacryptus.cognotik.diff.PatchProcessor {
      override fun parse(response: String, defaultFile: String?): List<com.simiacryptus.cognotik.diff.PatchParser.ResponseSegment> = emptyList()
      override val label: String get() = "TestProcessor"
      override fun generatePatch(oldCode: String, newCode: String): String = ""
      override fun applyPatch(source: String, patch: String): String = ""
      override fun apply(prevCode: String, patch: String, filename: String?): DiffApplicationResult = DiffApplicationResult("", emptyList(), validator = ParenMatchingValidator())
    }

    val renderer = object : DiffUIRenderer {
      override fun renderSaveButton(filepath: Path, code: String, lang: String, onSave: () -> Unit): String = ""
      override fun renderApplyDiffButton(filepath: Path, diff: String, onApply: () -> Unit, onRevert: () -> Unit): String = ""
      override fun renderAutoApplied(filepath: Path, revertButton: String): String = ""
      override fun renderWarning(message: String): String = ""
      override fun recordPatch(data: Map<String, Any?>): String = ""
    }

    val instrumentor = DiffInstrumentor(processor, renderer, mockFs)

    val method = DiffInstrumentor::class.java.getDeclaredMethod(
      "resolveNewFilePath",
      Path::class.java,
      String::class.java,
      Function2::class.java
    )
    method.isAccessible = true
    val resolver: (Path, String) -> String? = ::resolveToRelativePath
    val result = method.invoke(instrumentor, root, filename, resolver) as String?

    // "main/utils/Helper.kt" - "main" overlaps with root ending "main"
    // Should resolve to "utils/Helper.kt"
    assertEquals("utils/Helper.kt", result)
  }
}