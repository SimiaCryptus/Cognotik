package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.ApiModel
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

interface Chatter {

    val modelType: ChatModel
    val workPool: ExecutorService
    val logStreams: MutableList<java.io.BufferedOutputStream> get() = mutableListOf()

    var budget: Number? // TODO: implement budget tracking
        get() = null
        set(value) {}

    fun chat(
        messages: List<ApiModel.ChatMessage> = listOf()
    ): ApiModel.ChatResponse

    fun getChildClient(): Chatter = ChildChatter(
        parent = this,
        modelType = this.modelType,
        workPool = this.workPool,
    )

    class ChildChatter(
        val parent: Chatter,
        override val modelType: ChatModel,
        override val workPool: ExecutorService,
        override val logStreams: MutableList<BufferedOutputStream> = parent.logStreams.toTypedArray().toMutableList(),
    ) : Chatter {
        override fun chat(messages: List<ApiModel.ChatMessage>): ApiModel.ChatResponse =
            this.modelType.instance(
                key = "",
                workPool = workPool,
            ).chat(messages)
    }
}