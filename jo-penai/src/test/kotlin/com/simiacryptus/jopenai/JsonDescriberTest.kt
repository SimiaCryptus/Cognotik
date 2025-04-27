package com.simiacryptus.util

import com.simiacryptus.jopenai.TypeDescriberTestBase
import com.simiacryptus.jopenai.describe.JsonDescriber
import com.simiacryptus.jopenai.describe.TypeDescriber
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JsonDescriberTest : TypeDescriberTestBase() {
    @Test
    override fun testDescribeType() {
        super.testDescribeType()
    }

    @Test
    override fun testDescribeOpenAIClient() {
        super.testDescribeOpenAIClient()
    }

    @Test
    override fun testDescribeMethod() {

    }

    override val typeDescriber: TypeDescriber get() = JsonDescriber()
    override val classDescription: String
        @Language("TEXT")
        get() =
            """{
                 "type": "object",
                 "class": "com.simiacryptus.jopenai.TypeDescriberTestBase${"$"}DataClassExample",
                 "allowed": false
               }"""

    override val methodDescription
        get() =

            """
            {
              "operationId": "methodExample",
              "description": "This is a method",
              "parameters": [
                {
                  "name": "p1",
                  "description": "This is a parameter",
                  "type": "int"
                },
                {
                  "name": "p2",
                  "type": "string"
                }
              ],
              "responses": {
                "application/json": {
                  "schema": {
                    "type": "string"
                  }
                }
              }
            }
            """.trimIndent()

    @Test
    override fun testDescribeRecursiveType() {
        val expectedDescription =

            """{
                 "type": "object",
                 "class": "com.simiacryptus.jopenai.TypeDescriberTestBase${"$"}RecursiveDataClass",
                 "allowed": false
               }"""
        val actualDescription = typeDescriber.describe(RecursiveDataClass::class.java)
        Assertions.assertEquals(expectedDescription, actualDescription)
    }


















}