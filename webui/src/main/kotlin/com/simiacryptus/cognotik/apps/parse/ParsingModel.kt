package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.jopenai.API

interface ParsingModel<T : ParsingModel.DocumentData> {
    fun merge(runningDocument: T, newData: T): T
    fun getFastParser(api: API): (String) -> T = { prompt ->
        getSmartParser(api)(newDocument(), prompt)
    }

    fun getSmartParser(api: API): (T, String) -> T = { runningDocument, prompt ->
        getFastParser(api)(prompt)
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
        private val log = org.slf4j.LoggerFactory.getLogger(ParsingModel::class.java)
    }
}