package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.models.ApiModel

fun String.toContentList() = listOf(this).map { ApiModel.ContentPart(text = it, type = "text") }
fun String.toChatMessage(role: ApiModel.Role = ApiModel.Role.user) =
    ApiModel.ChatMessage(role = role, content = toContentList())