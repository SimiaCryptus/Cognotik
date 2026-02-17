package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.util.LoggerFactory

/**
 * A processor that handles full file replacement instead of patching.
 * This is useful when changes are extensive or when patching would be more complex.
 */
class FullReplacementProcessor : PatchProcessor {
    override val label = "FullReplacement"

    override val patchFormatPrompt = """
      Response should provide the complete updated file content within ```code blocks.
      Each code block should be preceded by a header that identifies the file being modified.
      The entire file content should be provided, not just the changes.
      
      Example:
      
      Here is the updated file:
      
      ### src/utils/exampleUtils.js
      ```javascript
      const a = 1;
      const b = 2;
      
      function exampleFunction() {
     return a + b;
      }
      
      module.exports = { exampleFunction };
      ```
      
      ### tests/exampleUtils.test.js
      ```javascript
      const assert = require('assert');
      const { exampleFunction } = require('../src/utils/exampleUtils');
      
      describe('exampleFunction', () => {
        it('should return 4', () => {
          assert.equal(exampleFunction(), 4);
        });
      });
      ```
      """.trimIndent()

    override fun getInitiatorPattern(): Regex {
        return "(?s)```\\w*\n".toRegex()
    }

    override fun extractCodeBlocks(response: String): List<Pair<String, String>> {
        val codeblockPattern = """(?s)(?<![^\n])```([^\n]*)\n(.*?)\n```""".toRegex()
        return codeblockPattern.findAll(response).map { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2]
            language to code
        }.toList()
    }

    override fun generatePatch(oldCode: String, newCode: String): String {
        log.debug("Generating full replacement patch")
        // For full replacement, the "patch" is just the new code
        return newCode
    }

    override fun applyPatch(source: String, patch: String): String {
        log.debug("Applying full replacement patch")
        // For full replacement, we simply return the patch as the new content
        return patch.trim()
    }

    companion object {
        private val log = LoggerFactory.getLogger(FullReplacementProcessor::class.java)
    }
}