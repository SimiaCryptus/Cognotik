package com.simiacryptus.cognotik.ui.patch

import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import java.nio.file.Path

class DiffInstrumentor(
  private val processor: PatchProcessor,
  private val renderer: DiffUIRenderer,
  private val fs: FileSystem = RealFileSystem()
) {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(DiffInstrumentor::class.java)
  }

  private val parser = ResponseParser(processor)

  fun instrument(
    root: Path,
    response: String,
    handle: (Map<Path, String>) -> Unit = {},
    shouldAutoApply: (Path) -> Boolean = { false },
    defaultFile: String? = null,
    resolver: (Path, String) -> String? = ::resolveToRelativePath,
  ): String {
    val segments = parser.parse(response, defaultFile)
    if (segments.isEmpty()) return response

    val result = StringBuilder()
    for (segment in segments) {
      when (segment) {
        is ResponseSegment.Markdown -> result.append(segment.content)
        is ResponseSegment.NewFileBlock -> {
          val filename = prefilterFilename(segment.filename) ?: segment.filename
          val resolved = resolver(root, filename)
          if (resolved == null) {
            result.append("```${segment.language}\n${segment.code}\n```")
            continue
          }
          val filepath = fs.resolve(root, resolved)
          result.append(renderNewFile(filepath, segment.code, segment.language, handle, shouldAutoApply))
        }

        is ResponseSegment.DiffBlock -> {
          var filename = segment.filename
          if (filename.isBlank() || !filename.contains('.')) {
            if (defaultFile != null) filename = defaultFile
            else {
              result.append("```diff\n${segment.diff}\n```")
              continue
            }
          }
          val resolved = resolver(root, filename)
          if (resolved == null) {
            result.append("```diff\n${segment.diff}\n```")
            continue
          }
          val filepath = fs.resolve(root, resolved)
          val relativize = try {
            root.relativize(filepath)
          } catch (e: Throwable) {
            filepath
          }
          result.append(renderDiffBlock(filepath, relativize, segment.diff, handle, shouldAutoApply))
        }
      }
    }
    return result.toString()
  }

  private fun renderNewFile(
    filepath: Path,
    code: String,
    lang: String,
    handle: (Map<Path, String>) -> Unit,
    shouldAutoApply: (Path) -> Boolean
  ): String {
    val codeBlock = "\n```${lang}\n${code}\n```\n"
    if (shouldAutoApply(filepath) && !fs.exists(filepath)) {
      return try {
        fs.writeText(filepath, code)
        handle(mapOf(filepath to code))
        codeBlock + "\n" + renderer.renderAutoApplied(filepath, "") +
            renderer.recordPatch(mapOf("filename" to filepath.toString(), "code" to code, "action" to "save_auto"))
      } catch (e: Throwable) {
        codeBlock + "\n<div class=\"cmd-button\">Error Auto-Saving ${filepath}: ${e.message}</div>"
      }
    }
    val saveButton = renderer.renderSaveButton(filepath, code, lang) {
      fs.writeText(filepath, code)
      handle(mapOf(filepath to code))
    }
    return codeBlock + "\n" + saveButton +
        renderer.recordPatch(mapOf("filename" to filepath.toString(), "code" to code, "action" to "save"))
  }

  private fun renderDiffBlock(
    filepath: Path,
    relativePath: Path,
    diffVal: String,
    handle: (Map<Path, String>) -> Unit,
    shouldAutoApply: (Path) -> Boolean
  ): String {
    val controller = DiffApplyController(filepath, diffVal, processor, fs)
    val prevCode = fs.readText(filepath)

    // Validate the patch
    val testResult = try {
      processor.apply(prevCode, "```diff\n$diffVal\n```", filepath.fileName?.toString())
    } catch (e: Throwable) {
      null
    }
    val isValid = testResult?.isValid ?: false
    val errorMsg = testResult?.errors?.joinToString("; ") { it.message }

    val diffBlock = "\n```diff\n$diffVal\n```\n"

    // Auto-apply path
    if (isValid && shouldAutoApply(filepath)) {
      val state = controller.apply()
      return when (state) {
        is ApplyState.Applied -> {
          handle(mapOf(relativePath to state.newCode))
          val revertButton = renderer.renderApplyDiffButton(
            filepath, diffVal,
            onApply = {},
            onRevert = {
              controller.revert()
              handle(mapOf(relativePath to state.originalCode))
            }
          )
          diffBlock + renderer.renderAutoApplied(filepath, revertButton) +
              renderer.recordPatch(
                mapOf(
                  "filename" to filepath.toString(),
                  "originalCode" to prevCode,
                  "diff" to diffVal,
                  "newCode" to state.newCode,
                  "isValid" to true,
                  "action" to "auto_apply"
                )
              )
        }

        is ApplyState.Failed -> {
          diffBlock + "<div class=\"cmd-button\">Error Auto-Applying Diff to ${filepath}: ${state.error.message}</div>"
        }

        else -> diffBlock
      }
    }

    // Manual apply path
    val applyButton = renderer.renderApplyDiffButton(
      filepath, diffVal,
      onApply = {
        val state = controller.apply()
        if (state is ApplyState.Applied) {
          handle(mapOf(relativePath to state.newCode))
        } else if (state is ApplyState.Failed) {
          throw state.error
        }
      },
      onRevert = {
        val currentState = controller.currentState()
        if (currentState is ApplyState.Applied) {
          controller.revert()
          handle(mapOf(relativePath to currentState.originalCode))
        }
      }
    )

    val warning = if (!isValid && errorMsg != null) {
      renderer.renderWarning("The patch is not valid: $errorMsg")
    } else ""

    return diffBlock + warning + applyButton +
        renderer.recordPatch(
          mapOf(
            "filename" to filepath.toString(),
            "originalCode" to prevCode,
            "diff" to diffVal,
            "newCode" to (testResult?.newCode ?: ""),
            "isValid" to isValid,
            "errors" to errorMsg,
            "action" to "diff"
          )
        )
  }
}