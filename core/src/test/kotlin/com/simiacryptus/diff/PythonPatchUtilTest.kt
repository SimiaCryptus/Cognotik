package com.simiacryptus.diff
import com.simiacryptus.skyenet.core.util.PythonPatchUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
class PythonPatchUtilTest {
    // This helper normalizes line endings and trims the overall string.
    private fun normalize(text: String) = text.trim().replace("\r\n", "\n")
    @Test
    fun testPatchExactMatch() {
        val source = """
            line1
            line2
            line3
        """.trimIndent()
        val patch = """
            line1
            line2
            line3
        """.trimIndent()
        val result = PythonPatchUtil.applyPatch(source, patch)
        Assertions.assertEquals(normalize(source), normalize(result))
    }
    @Test
    fun testPatchAddLine() {
        val source = """
            line1
            line2
            line3
        """.trimIndent()
        val patch = """
            line1
            line2
            +newLine
            line3
        """.trimIndent()
        val expected = """
            line1
            line2
            newLine
            line3
        """.trimIndent()
        val result = PythonPatchUtil.applyPatch(source, patch)
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
    @Test
    fun testPatchModifyLine() {
        val source = """
            line1
            line2
            line3
        """.trimIndent()
        val patch = """
            line1
            -line2
            +modifiedLine2
            line3
        """.trimIndent()
        val expected = """
            line1
            modifiedLine2
            line3
        """.trimIndent()
        val result = PythonPatchUtil.applyPatch(source, patch)
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
//    @Test
    fun testPatchRemoveLine() {
        val source = """
            line1
            line2
            line3
        """.trimIndent()
        val patch = """
            line1
          - line2
            line3
        """.trimIndent()
        val expected = """
            line1
            line3
        """.trimIndent()
        val result = PythonPatchUtil.applyPatch(source, patch)
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
//    @Test
    fun testPatchAdd2Lines() {
        val source = """
            line1
            line2
            line3
        """.trimIndent()
        val patch = """
            line1
          + lineA
          + lineB
            line2
            line3
        """.trimIndent()
        val expected = """
           line1
            lineA
            lineB
            line2
            line3
        """.trimIndent()
        val result = PythonPatchUtil.applyPatch(source, patch)
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
    @Test
    fun testGeneratePatchNoChanges() {
        val oldCode = "line1\nline2\nline3"
        val newCode = oldCode
        val result = PythonPatchUtil.generatePatch(oldCode, newCode)
        val expected = ""
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
    @Test
    fun testGeneratePatchAddLine() {
        val oldCode = "line1\nline2\nline3"
        val newCode = "line1\nline2\nnewLine\nline3"
        val result = PythonPatchUtil.generatePatch(oldCode, newCode)
        val expected = "  line1\n  line2\n+ newLine\n  line3"
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
    @Test
    fun testGeneratePatchRemoveLine() {
        val oldCode = "line1\nline2\nline3"
        val newCode = "line1\nline3"
        val result = PythonPatchUtil.generatePatch(oldCode, newCode)
        val expected = "  line1\n- line2\n  line3"
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
    @Test
    fun testGeneratePatchModifyLine() {
        val oldCode = "line1\nline2\nline3"
        val newCode = "line1\nmodifiedLine2\nline3"
        val result = PythonPatchUtil.generatePatch(oldCode, newCode)
        val expected = "  line1\n- line2\n+ modifiedLine2\n  line3"
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
    @Test
    fun testGeneratePatchComplexChanges() {
        val oldCode = """
            function example() {
                console.log("Hello");
                // Some comment
                return true;
            }
        """.trimIndent()
        val newCode = """
            function example() {
                console.log("Hello, World!");
                // Modified comment
                let x = 5;
                return x > 0;
            }
        """.trimIndent()
        val result = PythonPatchUtil.generatePatch(oldCode, newCode)
        val expected = """
              function example() {
            -     console.log("Hello");
            -     // Some comment
            -     return true;
            +     console.log("Hello, World!");
            +     // Modified comment
            +     let x = 5;
            +     return x > 0;
              }
        """.trimIndent()
        Assertions.assertEquals(normalize(expected), normalize(result))
    }
}