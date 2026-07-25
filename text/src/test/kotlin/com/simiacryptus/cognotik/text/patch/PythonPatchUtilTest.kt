package com.simiacryptus.diff

import com.simiacryptus.cognotik.text.patch.PythonPatcher
import com.simiacryptus.diff.PatchTestCase.Companion.test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PythonPatchUtilTest {

  companion object {
    @JvmStatic
    fun patchTestCases() = listOf(
      "/patch_exact_match.json",
      "/patch_add_line.json",
      "/patch_append_line.json",
      "/patch_prepend_line.json",
      "/patch_modify_line.json",
//            "/yaml_min_repro.json",
      "/patch_remove_line.json",
//            "/patch_add_2_lines_variant_2.json",
//            "/patch_add_2_lines_variant_3.json",
      "/patch_inner_block.json",
      "/patch_append_to_empty_file.json",
//            "/patch_wrap_panel.json"
    )
  }

  @ParameterizedTest
  @MethodSource("patchTestCases")
  fun testPatchFromJson(resourceName: String) {
    test(resourceName, PythonPatcher())
  }

}