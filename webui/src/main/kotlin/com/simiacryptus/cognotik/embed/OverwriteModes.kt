package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.util.FileGenerator.Companion.lastModified
import java.io.File

enum class OverwriteModes : OverwriteMode {

  SkipExisting {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? = null
  },
  
  OverwriteExisting {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? {
      target.delete()
      return PatchProcessors.FullReplacement
    }
  },
  
  OverwriteToUpdate {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? {
      return if (source.lastModified(relatedFiles) > target.lastModified()) {
        target.delete()
        PatchProcessors.FullReplacement
      } else {
        null
      }
    }
  },
  
  PatchExisting {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? = PatchProcessors.Fuzzy
  },
  
  PatchToUpdate {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? {
      return if (source.lastModified(relatedFiles) > target.lastModified()) {
        PatchProcessors.Fuzzy
      } else {
        null
      }
    }
  };
}


interface OverwriteMode {
  fun prepare(
    source: File,
    target: File,
    relatedFiles: List<File> = emptyList(),
  ): PatchProcessors?
}