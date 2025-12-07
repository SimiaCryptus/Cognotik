package com.simiacryptus.diff

import com.simiacryptus.cognotik.diff.FuzzyPatchMatcher
import com.simiacryptus.cognotik.diff.ThermodynamicPatchMatcher
import com.simiacryptus.diff.PatchTestCase.Companion.test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ThermodynamicPatchMatcherTest {

    companion object {
        @JvmStatic
        fun testCases() = listOf(
            "/patch_exact_match.json",
            "/patch_add_line.json",
            "/patch_append_line.json",
            "/patch_prepend_line.json",
            "/patch_modify_line.json",
            "/yaml_min_repro.json",
            "/patch_remove_line.json",
//            "/patch_add_2_lines_variant_2.json",
//            "/patch_add_2_lines_variant_3.json",
//            "/patch_inner_block.json",
//            "/patch_append_to_empty_file.json",
//            "/patch_wrap_panel.json"
        )
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun testPatchApplication(resourceName: String) {
        test(resourceName, ThermodynamicPatchMatcher())
    }

}

