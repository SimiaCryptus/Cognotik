package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.ChatInterface
import com.simiacryptus.cognotik.platform.CognotikConfig
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.service.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.ChatSocketManager

open class CodeChatSocketManager(
    session: Session,
    val language: String,
    val filename: String,
    val codeSelection: String,
    model: ChatInterface,
    fastModel: ChatInterface,
    storage: StorageInterface,
) : ChatSocketManager(
    session = session,
    smartModel = model,
    fastModel = fastModel,
    userInterfacePrompt = "# `$filename`\n\n${codeBlock(language, codeSelection)}".renderMarkdown(),
    systemPrompt = """
        |You are a helpful AI that helps people with coding.
        |
        |You will be answering questions about the following code located in `$filename`:
        |
        |${codeBlock(language, codeSelection)}
        |
        |Responses may use markdown formatting, including code blocks.
        """.trimMargin(),
    applicationClass = ApplicationServer::class.java,
    storage = storage,
    budget = 2.0,
    owner = CognotikConfig.localUser,
) {
    override fun canWrite(user: User?): Boolean = true

    companion object {
        /** Wraps [code] in a fence longer than any backtick run it contains, so the block can't be broken. */
        fun codeBlock(language: String, code: String): String {
            val longestRun = Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0
            val fence = "`".repeat(maxOf(3, longestRun + 1))
            return "$fence$language\n$code\n$fence"
        }
    }
}