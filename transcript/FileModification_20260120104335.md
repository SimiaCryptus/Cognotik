# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/editor/CustomEditAction.kt

```
package cognotik.actions.editor

import cognotik.actions.SelectionAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.simiacryptus.cognotik.agents.ProxyAgent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.UITools
import javax.swing.JOptionPane

/**
 * Action that allows custom editing of code selections using AI.
 * Supports multiple languages and provides custom edit instructions.
 */
open class CustomEditAction : SelectionAction<String>(requiresSelection = true) {
    private val log = Logger.getInstance(CustomEditAction::class.java)
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    interface VirtualAPI {
        fun editCode(
            code: String,
            operation: String,
            computerLanguage: String,
            humanLanguage: String
        ): EditedText

        data class EditedText(
            var code: String? = null,
            var language: String? = null
        )
    }

    val proxy: VirtualAPI
        get() {
            val proxyAgent = ProxyAgent(
                clazz = VirtualAPI::class.java,
                model = AppSettingsState.instance.smartChatClient,
                temperature = AppSettingsState.instance.temperature,
            )
            proxyAgent.addExample(
                VirtualAPI.EditedText(
                    """

                println("Hello, World!")
                """.trimIndent(),
                    "java"
                )
            ) {
                it.editCode(
                    """println("Hello, World!")""",
                    "Add code comments",
                    "java",
                    "English"
                )
            }
            return proxyAgent.create()
        }

    override fun getConfig(project: Project?): String? {
        return UITools.showInputDialog(
            null,
            "Enter edit instruction:",
            "Edit Code",
            JOptionPane.QUESTION_MESSAGE
        ) as String?
    }

    override fun processSelection(state: SelectionState, config: String?, progress: ProgressIndicator): String {
        if (config.isNullOrBlank()) return state.selectedText ?: ""
        return try {
            progress.isIndeterminate = true
            progress.text = "Applying edit: $config"
            val settings = AppSettingsState.instance
            val outputHumanLanguage = ""
            settings.getRecentCommands("customEdits").addInstructionToHistory(config)
            val result = proxy.editCode(
                state.selectedText ?: "",
                config,
                state.language?.name ?: state.editor?.virtualFile?.extension ?: "unknown",
                outputHumanLanguage
            )
            result.code ?: state.selectedText ?: ""
        } catch (e: Exception) {
            log.error("Failed to process edit", e)
            UITools.showErrorDialog(
                "Failed to process edit: ${e.message}",
                "Edit Error"
            )
            state.selectedText ?: ""
        }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/editor/PasteAction.kt

```
package cognotik.actions.editor

import cognotik.actions.SelectionAction
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.simiacryptus.cognotik.agents.ProxyAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.ComputerLanguage
import com.simiacryptus.cognotik.util.LoggerFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.Logger
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor.*

/**
 * Base class for paste actions that convert clipboard content to appropriate code format
 * Supports both text and HTML clipboard content with automatic language detection
 */
abstract class PasteActionBase(private val model: (AppSettingsState) -> ChatInterface) :
    SelectionAction<String>(false) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /**
     * API interface for code conversion
     */
    interface VirtualAPI {
        fun convert(text: String, to_language: String): ConvertedText

        @JsonDeserialize(using = ConvertedTextDeserializer::class)

        class ConvertedText {
            var converted_text: String? = null
        }

        /**
         * Custom deserializer for ConvertedText that can handle different response formats:
         * - Direct string values
         * - Objects with a single text attribute (regardless of attribute name)
         * - Standard objects with the expected "converted_text" attribute
         */
        class ConvertedTextDeserializer : JsonDeserializer<ConvertedText>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ConvertedText {
                val node: JsonNode = p.codec.readTree(p)
                val result = ConvertedText()
                when {

                    node.isTextual -> {
                        result.converted_text = node.asText()
                    }

                    node.has("converted_text") -> {
                        result.converted_text = node.get("converted_text").asText()
                    }

                    node.isObject && node.fields().hasNext() -> {
                        val fields = node.fields()
                        while (fields.hasNext()) {
                            val field = fields.next()
                            if (field.value.isTextual) {
                                result.converted_text = field.value.asText()
                                break
                            }
                        }
                    }
                }
                return result
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(PasteActionBase::class.java)
        fun scrubHtml(str: String, maxLength: Int = 100 * 1024): String {
            val document: Document = Jsoup.parse(str)

            document.apply {
                fun qTry(block: () -> Unit) {
                    return try {
                        block()
                    } catch (e: Exception) {
                        log.error("Error in scrubHtml", e)
                    }
                }
                if ((document.body()?.html()?.length ?: 0) > maxLength) return document.body()?.html()
                    ?.substring(0, maxLength) ?: ""
                select("script, style, link, meta, iframe, noscript").remove()

                outputSettings().prettyPrint(false)

                if ((document.body()?.html()?.length ?: 0) > maxLength) return document.body()?.html()
                    ?.substring(0, maxLength) ?: ""

                qTry { select("*").forEach { it.childNodes().removeAll { node -> node.nodeName() == "#comment" } } }
                if (document.body().html().length > maxLength) return@apply

                qTry {
                    select("*[data-*]").forEach {
                        it.attributes().removeAll { attr -> attr.key.startsWith("data-") }
                    }
                }
                if (document.body().html().length > maxLength) return@apply
                qTry {
                    select("*").forEach { element ->
                        val importantAttributes =
                            setOf("href", "src", "alt", "title", "width", "height", "style", "class", "id", "name")
                        element.attributes().removeAll { it.key !in importantAttributes }
                    }
                }
                if (document.body().html().length > maxLength) return@apply

                qTry {
                    select("*").forEach { element ->
                        if (element.childNodes().isEmpty() && element.attributes().isEmpty()) {
                            element.remove()
                        }
                    }
                }
                if (document.body().html().length > maxLength) return@apply

                qTry {
                    select("*").forEach { element ->
                        if (element.childNodes().size == 1 && element.childNodes()[0].nodeName() == "#text" && element.attributes()
                                .isEmpty()
                        ) {
                            element.unwrap()
                        }
                    }
                }
                if (document.body().html().length > maxLength) return@apply

                qTry {
                    select("[href],[src]").forEach { element ->
                        element.attr("href").let { href -> element.attr("href", href.makeAbsolute()) }
                        element.attr("src").let { src -> element.attr("src", src.makeAbsolute()) }
                    }
                }
                if (document.body().html().length > maxLength) return@apply

                qTry {
                    select("*").forEach { element ->
                        element.attributes().removeAll { it.value.isBlank() }
                    }
                }
            }

            val result = document.body().html()
            return if (result.length > maxLength) {
                result.substring(0, maxLength)
            } else {
                result
            }
        }

        fun getClipboard(): Any? {
            try {
                val toolkit = Toolkit.getDefaultToolkit()
                val systemClipboard = toolkit.systemClipboard
                return systemClipboard.getContents(null)?.let { contents ->
                    return when {
                        contents.isDataFlavorSupported(selectionHtmlFlavor) -> contents.getTransferData(
                            selectionHtmlFlavor
                        ).toString().trim().let { scrubHtml(it) }

                        contents.isDataFlavorSupported(fragmentHtmlFlavor) -> contents.getTransferData(
                            fragmentHtmlFlavor
                        ).toString().trim().let { scrubHtml(it) }

                        contents.isDataFlavorSupported(allHtmlFlavor) -> contents.getTransferData(allHtmlFlavor)
                            .toString().trim().let { scrubHtml(it) }

                        contents.isDataFlavorSupported(stringFlavor) -> contents.getTransferData(stringFlavor)
                        contents.isDataFlavorSupported(getTextPlainUnicodeFlavor()) -> contents.getTransferData(
                            getTextPlainUnicodeFlavor()
                        )

                        else -> null
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to access clipboard", e)
                return null
            }
        }

        fun hasClipboard() = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)?.let { contents ->
            return when {
                contents.isDataFlavorSupported(stringFlavor) -> true
                contents.isDataFlavorSupported(getTextPlainUnicodeFlavor()) -> true
                else -> false
            }
        } ?: false

        fun converter(chatModel: ChatInterface, temp: Double) = ProxyAgent(
            clazz = VirtualAPI::class.java,
            model = chatModel,
            temperature = temp
        ).create()
    }

    override fun getConfig(project: Project?): String {
        return ""
    }

    override fun processSelection(state: SelectionState, config: String?, progress: ProgressIndicator): String {
        val progress: ProgressIndicator? = state.progress
        progress?.text = "Reading clipboard content..."
        val clipboardContent = getClipboard() ?: return ""
        val text = clipboardContent.toString().trim()
        if (text.isEmpty()) return ""
        progress?.text = "Converting code format..."
        val converter = converter(model(AppSettingsState.instance), AppSettingsState.instance.temperature)
        val convert = converter.convert(text, state.language?.name ?: state.editor?.virtualFile?.extension ?: "")
        return convert.converted_text ?: ""
    }

    override fun isLanguageSupported(computerLanguage: ComputerLanguage?): Boolean {
        return true
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!hasClipboard()) return false
        return super.isEnabled(event)
    }

}

private fun String.makeAbsolute(): String {
    return if (startsWith("http://") || startsWith("https://") || startsWith("//")) {
        this
    } else {
        "https://$this"
    }
}

/**
 * Fast paste action using faster but simpler model
 */
class FastPasteAction : PasteActionBase({ it.fastChatClient }) {
    companion object {
    }

    protected var progress: ProgressIndicator? = null
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Based on the provided source code and the transformation rules, here is the new `README.md` file for the `cognotik.actions.editor` package.

### intellij/src/main/kotlin/cognotik/actions/editor/README.md
```markdown
# Editor Actions

This package contains AI-powered IntelliJ actions designed to enhance the code editing experience through intelligent transformations and content insertion.

## Core Actions

### Custom Edit Action (`CustomEditAction`)
The `CustomEditAction` provides a flexible way to modify code using natural language instructions.
- **Usage**: Requires an active code selection. When triggered, it prompts the user for an edit instruction (e.g., "Add documentation," "Refactor to use streams," or "Add error handling").
- **AI Integration**: Uses a `ProxyAgent` to communicate with a `VirtualAPI`. It passes the selected code, the user's instruction, and the detected programming language to the AI model.
- **Features**: 
    - Maintains a history of recent edit commands.
    - Automatically detects the computer language from the editor context.
    - Provides visual progress feedback during the AI processing phase.

### Smart Paste Actions (`PasteActionBase`, `FastPasteAction`)
These actions facilitate "intelligent pasting," allowing users to paste content from the clipboard (including HTML from web pages) and have it automatically converted into the target programming language.
- **Functionality**: 
    - **HTML Scrubbing**: Includes a sophisticated `scrubHtml` utility that strips unnecessary tags (scripts, styles, metadata), comments, and attributes from clipboard HTML to minimize token usage and focus on relevant content.
    - **Format Conversion**: Uses AI to translate the scrubbed clipboard content into the language of the current file.
- **Implementations**:
    - `FastPasteAction`: Optimized for speed, utilizing a "fast" chat model for quick conversions.
- **Clipboard Support**: Handles multiple data flavors including HTML fragments, full HTML documents, and plain text.

## Technical Implementation Details

### ProxyAgent and VirtualAPI
Both actions leverage the `ProxyAgent` pattern. This allows the code to define a clean Kotlin interface (`VirtualAPI`) for the desired AI operations, which the `ProxyAgent` then implements dynamically by generating prompts and parsing responses from the configured LLM.

### Language Detection
The actions automatically determine the target language using the `SelectionState`, which inspects the active editor's virtual file extension or internal language settings.

### Error Handling
Actions are designed to be resilient; if an AI transformation fails, they typically fall back to the original text and display a descriptive error dialog to the user via `UITools`.
```

### Summary of Changes
- **Created `intellij/src/main/kotlin/cognotik/actions/editor/README.md`**: This file provides documentation for the classes found in that directory, as specified by the transform rule in `index_docs.md`.
- **Documented `CustomEditAction`**: Explained its purpose as a natural-language-driven code editor and its use of `ProxyAgent`.
- **Documented Paste Actions**: Detailed the smart pasting logic, specifically the HTML scrubbing capabilities and the distinction between the base class and the `FastPasteAction`.
- **Technical Overview**: Included sections on the `ProxyAgent` pattern, language detection, and error handling strategies used in these editor actions.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/editor/README.md'>intellij/src/main/kotlin/cognotik/actions/editor/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/editor/README.md'>intellij/src/main/kotlin/cognotik/actions/editor/README.md</a> Updated
