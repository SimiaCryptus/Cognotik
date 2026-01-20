# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/txt/BlockComment.kt

```
﻿@file:Suppress("NAME_SHADOWING")

package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.util.TextBlock.Companion.DELIMITER
import com.simiacryptus.cognotik.util.TextBlock.Companion.TAB_REPLACEMENT
import java.util.*
import java.util.stream.Collectors

class BlockComment(
    private val blockPrefix: CharSequence,
    private val linePrefix: CharSequence,
    private val blockSuffix: CharSequence,
    indent: CharSequence,
    vararg textBlock: CharSequence
) :
    IndentedText(indent, *textBlock) {
    class Factory(private val blockPrefix: String, private val linePrefix: String, private val blockSuffix: String) :
        TextBlockFactory<BlockComment?> {
        override fun fromString(text: String?): BlockComment {
            var text = text!!
            text = StringUtil.stripSuffix(
                StringUtil.trimSuffix(text.replace("\t", TAB_REPLACEMENT.toString(), false)),
                blockSuffix.trim { it <= ' ' })
            val indent = StringUtil.getWhitespacePrefix(*text.split(DELIMITER.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
            return BlockComment(
                blockPrefix,
                linePrefix,
                blockSuffix,
                indent,
                *(Arrays.stream(text.split(DELIMITER.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                    .map { s: CharSequence? -> StringUtil.stripPrefix(s!!, indent) }
                    .map { text: CharSequence? -> StringUtil.trimPrefix(text!!) }
                    .map { s: CharSequence? -> StringUtil.stripPrefix(s!!, blockPrefix.trim { it <= ' ' }) }
                    .map { s: CharSequence? -> StringUtil.stripPrefix(s!!, linePrefix.trim { it <= ' ' }) }
                    .collect(Collectors.toList()).toTypedArray()))
        }

        override fun looksLike(text: String?): Boolean {
            return text!!.trim { it <= ' ' }.startsWith(blockPrefix) && text.trim { it <= ' ' }.endsWith(blockSuffix)
        }
    }

    override fun toString(): String {
        val indent = this.indent
        val delimiter: CharSequence = DELIMITER + indent
        val joined: CharSequence = Arrays.stream(rawString()).map { x: CharSequence -> "$linePrefix $x" }
            .collect(Collectors.joining(delimiter))
        return blockPrefix.toString() + delimiter + joined + delimiter + blockSuffix
    }

    override fun withIndent(indent: CharSequence): IndentedText {
        return BlockComment(blockPrefix, linePrefix, blockSuffix, indent, *lines)
    }
}

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/txt/IndentedText.kt

```
package com.simiacryptus.cognotik.util

/**
 * This class provides a way to store and manipulate indented text blocks.
 *
 * The text block is stored as a single string, with each line separated by a newline character.
 * The indentation is stored as a separate string, which is prepended to each line when the text block is converted to a string.
 *
 * The class provides a companion object method to convert a string to an IndentedText object.
 * This method replaces all tab characters with two spaces, and then finds the minimum indentation of all lines.
 * This indentation is then used as the indentation for the IndentedText object.
 *
 * The class also provides a method to create a new IndentedText object with a different indentation.
 */
open class IndentedText(var indent: CharSequence, vararg val lines: CharSequence) : TextBlock {

    override fun toString(): String {
        return rawString().joinToString(TextBlock.DELIMITER + indent)
    }

    override fun withIndent(indent: CharSequence): IndentedText {
        return IndentedText(indent, *lines)
    }

    override fun rawString(): Array<out CharSequence> {
        return lines
    }

    companion object {
        /**
         * This method is used to convert a string into an IndentedText object.
         *
         * @param text The string to be converted into an IndentedText object.
         * @return IndentedText object created from the input string.
         */
        fun fromString(text: String?): IndentedText {
            val processedText = (text ?: "").replace("\t", TextBlock.TAB_REPLACEMENT.toString())
            val lines = processedText.split(TextBlock.DELIMITER)
            val indent = StringUtil.getWhitespacePrefix(*lines.toTypedArray())
            return IndentedText(indent, *lines.map { StringUtil.stripPrefix(it, indent) }.toTypedArray())
        }
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/txt/LineComment.kt

```
﻿package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.util.StringUtil.getWhitespacePrefix
import com.simiacryptus.cognotik.util.StringUtil.stripPrefix
import com.simiacryptus.cognotik.util.StringUtil.trimPrefix
import java.util.*
import java.util.stream.Collectors

class LineComment(private val commentPrefix: CharSequence, indent: CharSequence?, vararg lines: CharSequence) :
    IndentedText(indent!!, *lines) {
    class Factory(private val commentPrefix: String) : TextBlockFactory<LineComment?> {
        override fun fromString(text: String?): LineComment {
            var textVar = text
            textVar = textVar!!.replace(Regex("\t"), TextBlock.TAB_REPLACEMENT.toString())
            val indent = getWhitespacePrefix(
                *textVar.split(TextBlock.DELIMITER.toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
            )
            return LineComment(
                commentPrefix,
                indent,
                *Arrays.stream(textVar.split(TextBlock.DELIMITER.toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray())
                    .map { s: String? ->
                        stripPrefix(
                            s!!, indent
                        )
                    }
                    .map { obj: CharSequence -> trimPrefix(obj) }
                    .map { s: CharSequence? ->
                        stripPrefix(
                            s!!,
                            commentPrefix
                        )
                    }
                    .collect(Collectors.toList()).toTypedArray())
        }

        override fun looksLike(text: String?): Boolean {
            return Arrays.stream(text!!.split(TextBlock.DELIMITER.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()).allMatch { x: String ->
                x.trim { it <= ' ' }.startsWith(
                    commentPrefix
                )
            }
        }
    }

    override fun toString(): String {
        return "$commentPrefix " + Arrays.stream(rawString())
            .collect(Collectors.joining(TextBlock.DELIMITER + indent + commentPrefix + " "))
    }

    override fun withIndent(indent: CharSequence): LineComment {
        return LineComment(commentPrefix, indent, *lines)
    }
}

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/txt/TextBlock.kt

```
package com.simiacryptus.cognotik.util

interface TextBlock {
    companion object {
        val TAB_REPLACEMENT: CharSequence = "  "
        const val DELIMITER: String = "\n"
    }

    fun rawString(): Array<out CharSequence>

    fun withIndent(indent: CharSequence): TextBlock

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/txt/TextBlockFactory.kt

```
﻿package com.simiacryptus.cognotik.util

interface TextBlockFactory<T : TextBlock?> {
    fun fromString(text: String?): T

    fun toString(text: T): CharSequence? {
        return text.toString()
    }

    fun looksLike(text: String?): Boolean
}

```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the generated `README.md` for the text block utility package, based on the provided source code.

### core/src/main/kotlin/com/simiacryptus/cognotik/txt/README.md
```markdown
# Text Block Utilities

This package provides a robust framework for parsing, manipulating, and formatting structured text blocks, specifically focusing on indentation management and code comment styles (line and block comments).

## Core Components

### [TextBlock.kt](./TextBlock.kt)
The base interface for all text block types. It defines the fundamental contract for text that can be represented as a series of lines and re-indented.
- **Constants**: 
    - `TAB_REPLACEMENT`: Defaults to two spaces ("  ").
    - `DELIMITER`: The standard newline character (`\n`).
- **Methods**:
    - `rawString()`: Returns the lines of text without the block-level indentation.
    - `withIndent(indent)`: Returns a new instance with the specified indentation.

### [IndentedText.kt](./IndentedText.kt)
A foundational implementation of `TextBlock` that manages a collection of lines with a shared whitespace prefix.
- **Functionality**: Automatically detects the common indentation prefix when created via `fromString`.
- **Indentation Management**: Provides tools to strip and re-apply indentation consistently across multiple lines.

### [BlockComment.kt](./BlockComment.kt)
Specialized class for handling multi-line block comments (e.g., `/* ... */`).
- **Structure**: Manages a `blockPrefix`, a `linePrefix` (applied to each line within the block), and a `blockSuffix`.
- **Factory**: Includes a `Factory` that can identify if a string "looks like" a block comment and parse it into its constituent parts, stripping away the comment syntax to retrieve the raw text.

### [LineComment.kt](./LineComment.kt)
Specialized class for handling sequences of line comments (e.g., `// ...`).
- **Structure**: Manages a `commentPrefix` that is expected at the start of every line.
- **Factory**: Includes a `Factory` that validates if all lines in a block start with the specified prefix and parses them into a single logical text block.

### [TextBlockFactory.kt](./TextBlockFactory.kt)
A generic interface for creating `TextBlock` instances from raw strings.
- **Methods**:
    - `fromString(text)`: Parses a string into a specific `TextBlock` implementation.
    - `looksLike(text)`: A heuristic method used to determine if a given string matches the format handled by the factory (e.g., checking for comment delimiters).

## Usage Patterns

### Parsing and Re-indenting
The utilities are designed to handle the common task of taking a block of code or text, identifying its current indentation level, and shifting it to a new level while preserving internal formatting.

```kotlin
val rawText = """
    /**
     * This is a comment
     * with multiple lines.
     */
""".trimIndent()

val factory = BlockComment.Factory("/**", "*", "*/")
if (factory.looksLike(rawText)) {
    val comment = factory.fromString(rawText)
    val reIndented = comment.withIndent("  ")
    println(reIndented.toString())
}
```

### Implementation Details
- **Tab Handling**: All implementations consistently replace tab characters with `TextBlock.TAB_REPLACEMENT` to ensure predictable indentation calculations.
- **Whitespace Preservation**: The logic distinguishes between the "indentation" (the common prefix shared by all lines) and the "content" of the lines.
```

### Summary of Changes
- Created a new `README.md` in the `core/src/main/kotlin/com/simiacryptus/cognotik/txt/` directory.
- Documented the purpose and functionality of `TextBlock`, `IndentedText`, `BlockComment`, `LineComment`, and `TextBlockFactory`.
- Included technical details regarding tab replacement and indentation detection logic found in the source.
- Provided a conceptual usage example based on the `Factory` patterns implemented in the Kotlin files.
- Aligned the documentation with the package structure and naming conventions observed in the provided code.
</details>

                - <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/txt/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/txt/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/txt/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/txt/README.md</a> Updated
