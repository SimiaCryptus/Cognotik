package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.models.ModelSchema

fun String.toContentList() = listOf(this).map { ModelSchema.ContentPart(text = it) }
fun String.toChatMessage(role: ModelSchema.Role = ModelSchema.Role.user) =
  ModelSchema.ChatMessage(role = role, content = toContentList())