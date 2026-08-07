package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
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
    userInterfacePrompt = "# `$filename`\n\n```$language\n$codeSelection\n```".renderMarkdown(),
    systemPrompt = "\nYou are a helpful AI that helps people with coding.\n\nYou will be answering questions about the following code located in `$filename`:\n\n```$language\n$codeSelection\n```\n\nResponses may use markdown formatting, including code blocks.",
    applicationClass = ApplicationServer::class.java,
    storage = storage,
    budget = 2.0,
    owner = localUser,
) {
    override fun canWrite(user: User?): Boolean = true
}