package com.simiacryptus.cognotik.plan

data class TaskBreakdownWithPrompt(
    val prompt: String,
    val plan: Map<String, TaskExecutionConfig>,
    val planText: String
)