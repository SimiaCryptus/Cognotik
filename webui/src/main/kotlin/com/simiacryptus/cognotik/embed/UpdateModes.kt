package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.diff.PatchProcessors
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Represents the result of an update mode's preparation step.
 * @param patchProcessor The patch processor to use for the update
 * @param shouldDeleteTarget Whether the target file should be deleted before processing
 */
data class UpdatePrepareResult(
    val patchProcessor: PatchProcessor,
    val shouldDeleteTarget: Boolean = false
)


enum class UpdateModes : UpdateMode {

    SkipExisting {
        override fun prepare(
            source: File,
            target: File,
            relatedFiles: List<File>,
        ): UpdatePrepareResult? {
            return if (target.exists()) {
                log.debug("Skipping existing file: ${target.absolutePath}")
                null
            } else {
                log.debug("Creating new file (target does not exist): ${target.absolutePath}")
                UpdatePrepareResult(PatchProcessors.FullReplacement)
            }
        }
    },

    OverwriteExisting {
        override fun prepare(
            source: File,
            target: File,
            relatedFiles: List<File>,
        ): UpdatePrepareResult {
            if (target.exists()) {
                log.debug("Will overwrite existing file: ${target.absolutePath}")
            } else {
                log.debug("Creating new file (target does not exist): ${target.absolutePath}")
            }
            return UpdatePrepareResult(
                patchProcessor = PatchProcessors.FullReplacement,
                shouldDeleteTarget = target.exists()
            )
        }
    },

    OverwriteToUpdate {
        override fun prepare(
            source: File,
            target: File,
            relatedFiles: List<File>,
        ): UpdatePrepareResult? {
            if (!target.exists()) {
                log.debug("Creating new file (target does not exist): ${target.absolutePath}")
                return UpdatePrepareResult(PatchProcessors.FullReplacement)
            }
            val sourceModified = lastModifiedOfSources(source, relatedFiles)
            val targetModified = target.lastModified()
            return if (sourceModified > targetModified) {
                log.debug("Source newer than target (${sourceModified} > ${targetModified}), overwriting: ${target.absolutePath}")
                UpdatePrepareResult(
                    patchProcessor = PatchProcessors.FullReplacement,
                    shouldDeleteTarget = true
                )
            } else {
                log.debug("Target is up-to-date (${targetModified} >= ${sourceModified}), skipping: ${target.absolutePath}")
                null
            }
        }
    },

    PatchExisting {
        override fun prepare(
            source: File,
            target: File,
            relatedFiles: List<File>,
        ): UpdatePrepareResult? {
            return if (target.exists()) {
                log.debug("Patching existing file: ${target.absolutePath}")
                UpdatePrepareResult(PatchProcessors.Fuzzy)
            } else {
                log.debug("Creating new file via full replacement (target does not exist): ${target.absolutePath}")
                UpdatePrepareResult(PatchProcessors.FullReplacement)
            }
        }
    },

    PatchToUpdate {
        override fun prepare(
            source: File,
            target: File,
            relatedFiles: List<File>,
        ): UpdatePrepareResult? {
            if (!target.exists()) {
                log.debug("Creating new file (target does not exist): ${target.absolutePath}")
                return UpdatePrepareResult(PatchProcessors.FullReplacement)
            }
            val sourceModified = lastModifiedOfSources(source, relatedFiles)
            val targetModified = target.lastModified()
            return if (sourceModified > targetModified) {
                log.debug("Source newer than target (${sourceModified} > ${targetModified}), patching: ${target.absolutePath}")
                UpdatePrepareResult(PatchProcessors.Fuzzy)
            } else {
                log.debug("Target is up-to-date (${targetModified} >= ${sourceModified}), skipping: ${target.absolutePath}")
                null
            }
        }
    },

    /**
     * Always patches the target file regardless of timestamps.
     * Uses fuzzy patching for existing files, full replacement for new files.
     */
    ForceUpdate {
        override fun prepare(
            source: File,
            target: File,
            relatedFiles: List<File>,
        ): UpdatePrepareResult? {
            return if (target.exists()) {
                log.debug("Force updating existing file: ${target.absolutePath}")
                UpdatePrepareResult(PatchProcessors.Fuzzy)
            } else {
                log.debug("Creating new file (target does not exist): ${target.absolutePath}")
                UpdatePrepareResult(PatchProcessors.FullReplacement)
            }
        }
    },

    /**
     * Always overwrites the target file regardless of timestamps.
     * Deletes existing files before processing to ensure a clean replacement.
     */
    ForceOverwrite {
        override fun prepare(
            source: File,
            target: File,
            relatedFiles: List<File>,
        ): UpdatePrepareResult {
            if (target.exists()) {
                log.debug("Force overwriting existing file: ${target.absolutePath}")
            } else {
                log.debug("Creating new file (target does not exist): ${target.absolutePath}")
            }
            return UpdatePrepareResult(
                patchProcessor = PatchProcessors.FullReplacement,
                shouldDeleteTarget = target.exists()
            )
        }
    };

    companion object {
        private val log = LoggerFactory.getLogger(UpdateModes::class.java)

      /**
         * Get the maximum last-modified time considering both the primary source
         * and all related files. This avoids issues where the source file is
         * already included in the related files list.
         *
         * @param source The primary source file
         * @param relatedFiles Additional related files (may include the source)
         * @return The maximum last-modified timestamp across all inputs
         */
        fun lastModifiedOfSources(source: File, relatedFiles: List<File>): Long {
            val allFiles = (listOf(source) + relatedFiles).distinct()
            return allFiles
                .filter { it.exists() }
                .maxOfOrNull { it.lastModified() } ?: 0L
        }
        /**
         * Resolve an UpdateMode by name (case-insensitive).
         * Returns null if the name doesn't match any known mode.
         */
        fun fromName(name: String): UpdateModes? {
            return try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                // Try case-insensitive match
                entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
        }
    }
}


interface UpdateMode {
    /**
     * Determine how (or whether) to process the target file.
     *
     * @param source The primary source file (doc file or transform source)
     * @param target The target file to be created or updated
     * @param relatedFiles Additional files that contribute to the target's content
     * @return An [UpdatePrepareResult] describing how to process, or null to skip this target
     */
    fun prepare(
        source: File,
        target: File,
        relatedFiles: List<File> = emptyList(),
    ): UpdatePrepareResult?
}