package com.simiacryptus.diff

import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.util.JsonUtil
import org.junit.jupiter.api.Assertions

data class PatchTestCase(
    val filename: String,
    val originalCode: String,
    val diff: String,
    val newCode: String,
    val isValid: Boolean?,
    val errors: String?
) {
    companion object {
        fun test(resourceName: String, patcher: PatchProcessor) {
            fun normalize(text: String) = text.trim().replace("\r\n", "\n")
            val stream = patcher.javaClass.getResourceAsStream(resourceName)
                ?: throw IllegalArgumentException("Resource not found: $resourceName")
            val testCase: PatchTestCase = JsonUtil.fromJson(String(stream.readAllBytes()), PatchTestCase::class.java)
            if (false == testCase.isValid) return
            val result = patcher.applyPatch(testCase.originalCode, testCase.diff)
            Assertions.assertEquals(normalize(testCase.newCode), normalize(result))
        }
    }
}