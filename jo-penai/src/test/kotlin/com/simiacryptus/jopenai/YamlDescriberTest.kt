package com.simiacryptus.util

import com.simiacryptus.jopenai.TypeDescriberTestBase
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.describe.YamlDescriber
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YamlDescriberTest : TypeDescriberTestBase() {

    @Test
    override fun testDescribeMethod() {
        super.testDescribeMethod()
    }

    override val typeDescriber: TypeDescriber get() = YamlDescriber()
    override val classDescription: String
        get() =
            //language=yaml
            """
             type: object
             class: com.simiacryptus.jopenai.TypeDescriberTestBase${"$"}DataClassExample
             properties:
               a:
                 description: "This is an integer"
                 type: int
               b:
                 type: string
               c:
                 type: array
                 items:
                   ...
               d:
                 type: map
                 keys:
                   ...
                 values:
                   type: integer
                """.trimIndent()

    override val methodDescription
        get() =
            //language=yaml
            """
            operationId: methodExample
            description: This is a method
            parameters:
              - name: p1
                description: This is a parameter
                type: int
              - name: p2
                type: string
            responses:
              application/json:
                schema:
                  type: string
            
            """.trimIndent()

    @Test
    override fun testDescribeRecursiveType() {
        val expectedDescription = // Expected YAML description for RecursiveDataClass
            """type: object
class: com.simiacryptus.jopenai.TypeDescriberTestBase${"$"}RecursiveDataClass
properties:
  name:
    type: string
  parent:
    description: "Recursive reference"
    ..."""
        val actualDescription = typeDescriber.describe(RecursiveDataClass::class.java)
        Assertions.assertEquals(expectedDescription, actualDescription)
    }

    @Test
    fun testDescribedTypesPreventRecursion() {
        val describer = YamlDescriber()
        val describedTypes = mutableSetOf<String>()
        val description = describer.describe(RecursiveType::class.java, 10, describedTypes)
        assertTrue(description.contains("..."), "Description should contain recursion prevention marker")
        assertTrue(
            describedTypes.contains(RecursiveType::class.java.name),
            "Described types should contain RecursiveType"
        )
    }

    @Test
    fun testDescribedTypesTrackMultipleTypes() {
        val describer = YamlDescriber()
        val describedTypes = mutableSetOf<String>()
        describer.describe(FirstType::class.java, 10, describedTypes)
        describer.describe(SecondType::class.java, 10, describedTypes)
        assertTrue(describedTypes.contains(FirstType::class.java.name), "Described types should contain FirstType")
        assertTrue(describedTypes.contains(SecondType::class.java.name), "Described types should contain SecondType")
    }

    data class RecursiveType(val self: RecursiveType?)
    data class FirstType(val name: String)
    data class SecondType(val id: Int)

    @Suppress("unused")
    enum class TestEnum {
        FIRST_OPTION, SECOND_OPTION, THIRD_OPTION
    }

    @Test
    fun testDescribeEnumType() {
        val expectedDescription = """
         type: enumeration
         values:
           - FIRST_OPTION
           - SECOND_OPTION
           - THIRD_OPTION
         """.trimIndent()
        val actualDescription = typeDescriber.describe(TestEnum::class.java)
        Assertions.assertEquals(expectedDescription, actualDescription)
    }
}
