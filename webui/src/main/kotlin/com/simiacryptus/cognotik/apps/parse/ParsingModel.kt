package com.simiacryptus.cognotik.apps.parse

interface ParsingModel<T : ParsingModel.DocumentData> {
    fun merge(runningDocument: T, newData: T): T
    fun getFastParser(): (String) -> T = { prompt ->
        getSmartParser()(newDocument(), prompt)
    }

    fun getSmartParser(): (T, String) -> T = { runningDocument, prompt ->
        getFastParser()(prompt)
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