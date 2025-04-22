package com.simiacryptus.jopenai

import com.simiacryptus.jopenai.describe.ApiFunctionDescriber
import com.simiacryptus.jopenai.describe.TypeDescriber

class ApiFunctionDescriberTest : TypeDescriberTestBase() {
    override val typeDescriber: TypeDescriber get() = ApiFunctionDescriber()
    override val classDescription: String
        get() = """
        data class DataClassExample (
            val a: int
          val b: string
          val c: List
          val d: Map
        )
        """.trimIndent()
    override val methodDescription
        get() = """
            methodExample(
              p1: int
              p2: string
            )
        """.trimIndent()

    override fun testDescribeRecursiveType() {
//        super.testDescribeRecursiveType()
    }
}