package com.simiacryptus.diff

import com.simiacryptus.cognotik.diff.FuzzyPatchMatcher
import com.simiacryptus.diff.PatchTestCase.Companion.test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class FuzzyPatchMatcherTest {

    companion object {
        @JvmStatic
        fun testCases() = listOf(
            "/patch_exact_match.json",
            "/patch_add_line.json",
            "/patch_modify_line.json",
            "/patch_remove_line.json",
//            "/patch_add_2_lines_variant_2.json",
//            "/patch_add_2_lines_variant_3.json",
            "/patch_from_data_1.json",
            "/patch_from_data_2.json",
            "/yaml_1.json"
        )
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun testPatchApplication(resourceName: String) {
        test(resourceName, FuzzyPatchMatcher.default)
    }

}

