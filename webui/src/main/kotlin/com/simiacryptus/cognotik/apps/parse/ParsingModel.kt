package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.cognotik.API
import com.simiacryptus.cognotik.chat.ChatClientInterface

interface ParsingModel<T : ParsingModel.DocumentData> {
    val api: ChatClientInterface
    fun merge(runningDocument: T, newData: T): T
    fun getFastParser(api: API = this.api): (String) -> T = { prompt ->
        getSmartParser(this.api)(newDocument(), prompt)
    }

    fun getSmartParser(api: API = this.api): (T, String) -> T = { runningDocument, prompt ->
        getFastParser(this.api)(prompt)
    }

    fun newDocument(): T

    interface ContentData {
        val type: String
        val text: String?
        val content_list: List<ContentData>?
        val tags: List<String>?
    }

    interface DocumentData {
        val id: String?
        val content_list: List<ContentData>?
    }

    companion object {
    }
}