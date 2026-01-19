package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.util.FileGenerator.Companion.lastModified
import java.io.File

enum class OverwriteModes : FileGenerator.OverwriteMode {
  SkipExisting,
  OverwriteExisting,
  OverwriteToUpdate,
  PatchExisting,
  PatchToUpdate;

  override fun prepare(
    source: File,
    target: File,
    relatedFiles: List<File>,
  ): PatchProcessors? = when (this) {
    SkipExisting -> null
    PatchExisting -> PatchProcessors.Fuzzy

    OverwriteExisting -> {
      target.delete()
      PatchProcessors.FullReplacement
    }

    OverwriteToUpdate -> when {
      source.lastModified(relatedFiles) > target.lastModified() -> {
        target.delete()
        PatchProcessors.FullReplacement
      }

      else -> null
    }

    PatchToUpdate -> when {
      source.lastModified(relatedFiles) > target.lastModified() -> PatchProcessors.Fuzzy
      else -> null
    }
  }
//  when {
//    target.exists() ->
//
//    else -> PatchProcessors.FullReplacement
//  }
}