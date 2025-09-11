package com.simiacryptus.util

import com.simiacryptus.cognotik.TypeDescriberTestBase
import com.simiacryptus.cognotik.describe.JsonDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JsonDescriberTest : TypeDescriberTestBase() {
    @Test
    override fun testDescribeType() {
        super.testDescribeType()
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
                 "class": "com.simiacryptus.cognotik.TypeDescriberTestBase${"$"}DataClassExample",
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
                 "class": "com.simiacryptus.cognotik.TypeDescriberTestBase${"$"}RecursiveDataClass",
                 "allowed": false
               }"""
        val actualDescription = typeDescriber.describe(RecursiveDataClass::class.java)
        Assertions.assertEquals(expectedDescription, actualDescription)
    }


}