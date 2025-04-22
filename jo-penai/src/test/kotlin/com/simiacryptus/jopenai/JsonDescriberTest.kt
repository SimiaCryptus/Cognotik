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
//    super.testDescribeMethod()
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
            //language=json
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
        val expectedDescription = // Expected YAML description for RecursiveDataClass
            """{
                 "type": "object",
                 "class": "com.simiacryptus.jopenai.TypeDescriberTestBase${"$"}RecursiveDataClass",
                 "allowed": false
               }"""
        val actualDescription = typeDescriber.describe(RecursiveDataClass::class.java)
        Assertions.assertEquals(expectedDescription, actualDescription)
    }

//  @Test
//  fun testDescribedTypesPreventRecursion() {
//    val describer = JsonDescriber()
//    val describedTypes = mutableSetOf<String>()
//    val description = describer.describe(RecursiveType::class.java, 10, describedTypes)
//    assertTrue(description.contains("..."), "Description should contain recursion prevention marker")
//    assertTrue(describedTypes.contains(RecursiveType::class.java.name), "Described types should contain RecursiveType")
//  }

//  @Test
//  fun testDescribedTypesTrackMultipleTypes() {
//    val describer = JsonDescriber()
//    val describedTypes = mutableSetOf<String>()
//    describer.describe(FirstType::class.java, 10, describedTypes)
//    describer.describe(SecondType::class.java, 10, describedTypes)
//    assertTrue(describedTypes.contains(FirstType::class.java.name), "Described types should contain FirstType")
//    assertTrue(describedTypes.contains(SecondType::class.java.name), "Described types should contain SecondType")
//  }

}