package com.simiacryptus.cognotik.diff

import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * A patch processor based on thermodynamic principles similar to DNA sequence binding.
 * 
 * This implementation treats patch matching as a molecular binding problem where:
 * - Each line has a "binding energy" based on its similarity to potential matches
 * - The system seeks the lowest free energy configuration (most stable binding)
 * - Temperature parameter controls tolerance for mismatches
 * - Entropy considerations favor contiguous matches
 * - Cooperative binding effects strengthen adjacent matches
 * 
 * Key thermodynamic concepts:
 * - **Binding Energy (ΔG):** Negative for favorable matches, positive for mismatches
 * - **Temperature (T):** Controls stringency - lower T requires better matches
 * - **Partition Function (Z):** Sum of Boltzmann factors for all possible configurations
 * - **Boltzmann Distribution:** Probability of a configuration ∝ exp(-ΔG/kT)
 * - **Cooperativity:** Adjacent matches stabilize each other (like base stacking in DNA)
 * 
 * @param temperature Controls matching stringency. Higher values allow more mismatches.
 * @param cooperativityBonus Energy bonus for adjacent matches (simulates base stacking).
 * @param entropyPenalty Energy penalty per gap (simulates entropic cost of loops).
 * @param contextSize Number of context lines in generated patches.
 * @param minBindingEnergy Minimum energy threshold for a match to be considered.
 */
class ThermodynamicPatchMatcher(
    private val temperature: Double = DEFAULT_TEMPERATURE,
    private val cooperativityBonus: Double = COOPERATIVITY_BONUS,
    private val entropyPenalty: Double = ENTROPY_PENALTY,
    private val contextSize: Int = DEFAULT_CONTEXT_SIZE,
    private val minBindingEnergy: Double = MIN_BINDING_ENERGY
) : PatchProcessor {

    override val label: String = "Thermodynamic Patch Matcher"

    override val patchFormatPrompt = """
        Response should use one or more code patches in diff format within ```diff code blocks.
        Each diff should be preceded by a header that identifies the file being modified.
        The diff format should use + for line additions, - for line deletions.
        The diff should include 2 lines of context before and after every change.

        Example:

        Here are the patches:

        ### src/utils/exampleUtils.js
        ```diff
        
         const b = 2;
         function exampleFunction() {
        -   return b + 1;
        +   return b + 2;
         }
        ```

        Alternately, the patch can be provided as a snippet of updated code with context.
        """.trimIndent()

    /**
     * Generates a patch by finding the thermodynamically most stable alignment.
     */
    override fun generatePatch(oldCode: String, newCode: String): String {
        log.info("Starting thermodynamic patch generation")
        if (oldCode == newCode) return ""
        if (oldCode.isBlank() && newCode.isNotBlank()) {
            return newCode.lines().joinToString("\n") { "+ $it" }
        }
        if (newCode.isBlank() && oldCode.isNotBlank()) {
            return oldCode.lines().joinToString("\n") { "- $it" }
        }

        val oldLines = oldCode.lines()
        val newLines = newCode.lines()

        // Calculate binding energy matrix
        val energyMatrix = calculateBindingEnergyMatrix(oldLines, newLines)
        
        // Find optimal alignment using dynamic programming with thermodynamic scoring
        val alignment = findOptimalAlignment(oldLines, newLines, energyMatrix)
        
        // Generate diff from alignment
        return generateDiffFromAlignment(oldLines, newLines, alignment)
    }

    /**
     * Applies a patch using thermodynamic matching to find the best binding site.
     */
    override fun applyPatch(source: String, patch: String): String {
        if (patch.isBlank()) return source

        val patchLines = parsePatch(patch)
        if (patchLines.isEmpty()) return source

        val sourceLines = source.lines()
        if (sourceLines.isEmpty()) return patch

        // Calculate binding energies for all possible patch positions
        val bindingSites = findBindingSites(sourceLines, patchLines)
        
        if (bindingSites.isEmpty()) {
            log.warn("No suitable binding site found for patch")
            return source
        }

        // Select the most stable binding site (lowest free energy)
        val bestSite = bindingSites.minByOrNull { it.freeEnergy }!!
        
        log.info("Applying patch at position ${bestSite.position} with ΔG = ${bestSite.freeEnergy}")
        
        return applyPatchAtSite(sourceLines, patchLines, bestSite)
    }

    /**
     * Calculates the binding energy matrix between all pairs of lines.
     * Negative energy = favorable binding, positive = unfavorable.
     */
    private fun calculateBindingEnergyMatrix(
        oldLines: List<String>,
        newLines: List<String>
    ): Array<DoubleArray> {
        val matrix = Array(oldLines.size) { DoubleArray(newLines.size) }
        val levenshtein = LevenshteinDistance()

        for (i in oldLines.indices) {
            for (j in newLines.indices) {
                matrix[i][j] = calculateBindingEnergy(
                    oldLines[i],
                    newLines[j],
                    levenshtein
                )
            }
        }

        return matrix
    }

    /**
     * Calculates binding energy between two lines using thermodynamic principles.
     * 
     * Energy components:
     * - Base pairing: Negative energy for matching characters
     * - Mismatches: Positive energy penalty
     * - Length difference: Entropic penalty
     */
    private fun calculateBindingEnergy(
        line1: String,
        line2: String,
        levenshtein: LevenshteinDistance
    ): Double {
        val norm1 = normalizeLine(line1)
        val norm2 = normalizeLine(line2)

        // Perfect match has maximum negative energy (most stable)
        if (norm1 == norm2) {
            return -10.0 * max(norm1.length, 1)
        }

        // Empty lines have zero binding energy
        if (norm1.isEmpty() || norm2.isEmpty()) {
            return 0.0
        }

        val maxLen = max(norm1.length, norm2.length)
        val distance = levenshtein.apply(norm1, norm2)
        
        // Calculate similarity ratio (0 to 1)
        val similarity = 1.0 - (distance.toDouble() / maxLen)
        
        // Convert similarity to binding energy
        // High similarity → negative energy (favorable)
        // Low similarity → positive energy (unfavorable)
        val baseEnergy = -10.0 * similarity * maxLen
        
        // Add entropic penalty for length differences
        val lengthPenalty = entropyPenalty * kotlin.math.abs(norm1.length - norm2.length)
        
        return baseEnergy + lengthPenalty
    }

    /**
     * Finds the optimal alignment using dynamic programming with thermodynamic scoring.
     * This is similar to the Needleman-Wunsch algorithm but with energy-based scoring.
     */
    private fun findOptimalAlignment(
        oldLines: List<String>,
        newLines: List<String>,
        energyMatrix: Array<DoubleArray>
    ): Alignment {
        val m = oldLines.size
        val n = newLines.size

        // DP table: dp[i][j] = minimum free energy to align old[0..i] with new[0..j]
        val dp = Array(m + 1) { DoubleArray(n + 1) { Double.POSITIVE_INFINITY } }
        val backtrack = Array(m + 1) { Array(n + 1) { Move.NONE } }

        // Initialize: aligning with nothing has zero energy
        dp[0][0] = 0.0

        // Fill first row (deletions)
        for (i in 1..m) {
            dp[i][0] = dp[i - 1][0] + entropyPenalty
            backtrack[i][0] = Move.DELETE
        }

        // Fill first column (insertions)
        for (j in 1..n) {
            dp[0][j] = dp[0][j - 1] + entropyPenalty
            backtrack[0][j] = Move.INSERT
        }

        // Fill DP table
        for (i in 1..m) {
            for (j in 1..n) {
                // Option 1: Match/mismatch
                val matchEnergy = dp[i - 1][j - 1] + energyMatrix[i - 1][j - 1]
                
                // Add cooperativity bonus if previous was also a match
                val matchWithCooperativity = if (backtrack[i - 1][j - 1] == Move.MATCH) {
                    matchEnergy - cooperativityBonus
                } else {
                    matchEnergy
                }

                // Option 2: Delete from old
                val deleteEnergy = dp[i - 1][j] + entropyPenalty

                // Option 3: Insert from new
                val insertEnergy = dp[i][j - 1] + entropyPenalty

                // Choose minimum energy path
                val minEnergy = minOf(matchWithCooperativity, deleteEnergy, insertEnergy)
                dp[i][j] = minEnergy

                backtrack[i][j] = when (minEnergy) {
                    matchWithCooperativity -> Move.MATCH
                    deleteEnergy -> Move.DELETE
                    else -> Move.INSERT
                }
            }
        }

        // Backtrack to construct alignment
        return backtrackAlignment(backtrack, oldLines, newLines, m, n)
    }

    /**
     * Backtracks through the DP table to construct the optimal alignment.
     */
    private fun backtrackAlignment(
        backtrack: Array<Array<Move>>,
        oldLines: List<String>,
        newLines: List<String>,
        m: Int,
        n: Int
    ): Alignment {
        val operations = mutableListOf<AlignmentOp>()
        var i = m
        var j = n

        while (i > 0 || j > 0) {
            when (backtrack[i][j]) {
                Move.MATCH -> {
                    operations.add(AlignmentOp.Match(i - 1, j - 1))
                    i--
                    j--
                }
                Move.DELETE -> {
                    operations.add(AlignmentOp.Delete(i - 1))
                    i--
                }
                Move.INSERT -> {
                    operations.add(AlignmentOp.Insert(j - 1))
                    j--
                }
                Move.NONE -> break
            }
        }

        return Alignment(operations.reversed())
    }

    /**
     * Generates a diff string from an alignment.
     */
    private fun generateDiffFromAlignment(
        oldLines: List<String>,
        newLines: List<String>,
        alignment: Alignment
    ): String {
        val diff = mutableListOf<DiffLine>()

        for (op in alignment.operations) {
            when (op) {
                is AlignmentOp.Match -> {
                    diff.add(DiffLine.Context(oldLines[op.oldIndex]))
                }
                is AlignmentOp.Delete -> {
                    diff.add(DiffLine.Delete(oldLines[op.oldIndex]))
                }
                is AlignmentOp.Insert -> {
                    diff.add(DiffLine.Add(newLines[op.newIndex]))
                }
            }
        }

        // Truncate context
        val truncated = truncateContext(diff)

        return formatDiff(truncated)
    }

    /**
     * Truncates excessive context lines.
     */
    private fun truncateContext(diff: List<DiffLine>): List<DiffLine> {
        if (diff.isEmpty()) return emptyList()

        val result = mutableListOf<DiffLine>()
        val contextBuffer = mutableListOf<DiffLine.Context>()

        for (line in diff) {
            when (line) {
                is DiffLine.Context -> {
                    contextBuffer.add(line)
                }
                else -> {
                    // Flush context buffer with truncation
                    if (contextBuffer.size > contextSize * 2) {
                        if (result.isNotEmpty()) {
                            result.addAll(contextBuffer.take(contextSize))
                            result.add(DiffLine.Context("..."))
                        }
                        result.addAll(contextBuffer.takeLast(contextSize))
                    } else {
                        result.addAll(contextBuffer)
                    }
                    contextBuffer.clear()
                    result.add(line)
                }
            }
        }

        // Handle trailing context
        if (contextBuffer.size > contextSize) {
            result.addAll(contextBuffer.take(contextSize))
        } else {
            result.addAll(contextBuffer)
        }

        return result
    }

    /**
     * Formats a diff as a string.
     */
    private fun formatDiff(diff: List<DiffLine>): String {
        return diff.joinToString("\n") { line ->
            when (line) {
                is DiffLine.Context -> "  ${line.text}"
                is DiffLine.Add -> "+ ${line.text}"
                is DiffLine.Delete -> "- ${line.text}"
            }
        }
    }

    /**
     * Finds all possible binding sites for a patch in the source.
     */
    private fun findBindingSites(
        sourceLines: List<String>,
        patchLines: List<PatchLine>
    ): List<BindingSite> {
        val sites = mutableListOf<BindingSite>()
        val contextLines = patchLines.filter { it.type == PatchLineType.CONTEXT }

        if (contextLines.isEmpty()) {
            // No context - try to match the entire patch as a snippet
            return findSnippetBindingSites(sourceLines, patchLines)
        }

        // Try each possible position in source
        for (pos in 0..sourceLines.size) {
            val energy = calculateBindingSiteEnergy(sourceLines, patchLines, pos)
            
            if (energy < minBindingEnergy) {
                sites.add(BindingSite(pos, energy))
            }
        }

        return sites
    }

    /**
     * Finds binding sites for snippet patches (no context lines).
     */
    private fun findSnippetBindingSites(
        sourceLines: List<String>,
        patchLines: List<PatchLine>
    ): List<BindingSite> {
        val sites = mutableListOf<BindingSite>()
        val patchContent = patchLines.map { it.text }
        val levenshtein = LevenshteinDistance()

        // Slide window across source
        for (pos in 0..sourceLines.size - patchContent.size) {
            var totalEnergy = 0.0
            var cooperativityCount = 0

            for (i in patchContent.indices) {
                val energy = calculateBindingEnergy(
                    sourceLines[pos + i],
                    patchContent[i],
                    levenshtein
                )
                totalEnergy += energy

                // Add cooperativity bonus for consecutive matches
                if (energy < 0 && i > 0) {
                    cooperativityCount++
                }
            }

            // Apply cooperativity bonus
            totalEnergy -= cooperativityCount * cooperativityBonus

            if (totalEnergy < minBindingEnergy) {
                sites.add(BindingSite(pos, totalEnergy))
            }
        }

        return sites
    }

    /**
     * Calculates the free energy of binding a patch at a specific position.
     */
    private fun calculateBindingSiteEnergy(
        sourceLines: List<String>,
        patchLines: List<PatchLine>,
        position: Int
    ): Double {
        var totalEnergy = 0.0
        var sourceIdx = position
        val levenshtein = LevenshteinDistance()
        var lastWasMatch = false

        for (patchLine in patchLines) {
            when (patchLine.type) {
                PatchLineType.CONTEXT -> {
                    if (sourceIdx >= sourceLines.size) {
                        return Double.POSITIVE_INFINITY
                    }

                    val energy = calculateBindingEnergy(
                        sourceLines[sourceIdx],
                        patchLine.text,
                        levenshtein
                    )

                    // Add cooperativity bonus
                    if (lastWasMatch && energy < 0) {
                        totalEnergy -= cooperativityBonus
                    }

                    totalEnergy += energy
                    lastWasMatch = energy < 0
                    sourceIdx++
                }
                PatchLineType.DELETE -> {
                    if (sourceIdx >= sourceLines.size) {
                        return Double.POSITIVE_INFINITY
                    }
                    // Deletion has entropic cost
                    totalEnergy += entropyPenalty
                    sourceIdx++
                    lastWasMatch = false
                }
                PatchLineType.ADD -> {
                    // Addition has entropic cost
                    totalEnergy += entropyPenalty
                    lastWasMatch = false
                }
            }
        }

        return totalEnergy
    }

    /**
     * Applies a patch at a specific binding site.
     */
    private fun applyPatchAtSite(
        sourceLines: List<String>,
        patchLines: List<PatchLine>,
        site: BindingSite
    ): String {
        val result = mutableListOf<String>()
        var sourceIdx = 0
        var patchIdx = 0
        var siteIdx = site.position

        // Add lines before patch site
        while (sourceIdx < siteIdx) {
            result.add(sourceLines[sourceIdx])
            sourceIdx++
        }

        // Apply patch
        while (patchIdx < patchLines.size) {
            val patchLine = patchLines[patchIdx]
            when (patchLine.type) {
                PatchLineType.CONTEXT -> {
                    result.add(sourceLines[sourceIdx])
                    sourceIdx++
                }
                PatchLineType.DELETE -> {
                    // Skip source line
                    sourceIdx++
                }
                PatchLineType.ADD -> {
                    result.add(patchLine.text)
                }
            }
            patchIdx++
        }

        // Add remaining source lines
        while (sourceIdx < sourceLines.size) {
            result.add(sourceLines[sourceIdx])
            sourceIdx++
        }

        return result.joinToString("\n")
    }

    /**
     * Parses a patch string into structured patch lines.
     */
    private fun parsePatch(patch: String): List<PatchLine> {
        return patch.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val trimmed = line.trimStart()
                when {
                    trimmed.startsWith("+++") || trimmed.startsWith("---") || 
                    trimmed.startsWith("@@") -> null
                    trimmed.startsWith("+") -> 
                        PatchLine(trimmed.substring(1).trim(), PatchLineType.ADD)
                    trimmed.startsWith("-") -> 
                        PatchLine(trimmed.substring(1).trim(), PatchLineType.DELETE)
                    line.startsWith("  ") -> 
                        PatchLine(line.substring(2), PatchLineType.CONTEXT)
                    else -> 
                        PatchLine(line, PatchLineType.CONTEXT)
                }
            }
    }

  override fun getInitiatorPattern(): Regex {
    return FuzzyPatchMatcher.default.getInitiatorPattern()
  }
  override fun extractCodeBlocks(response: String): List<Pair<String, String>> {
    return FuzzyPatchMatcher.default.extractCodeBlocks(response)
  }

    /**
     * Normalizes a line for comparison.
     */
    private fun normalizeLine(line: String): String {
        return line.trimEnd().replace("\\s{2,}".toRegex(), " ")
    }

    // Data classes for alignment
    private enum class Move { NONE, MATCH, DELETE, INSERT }

    private sealed class AlignmentOp {
        data class Match(val oldIndex: Int, val newIndex: Int) : AlignmentOp()
        data class Delete(val oldIndex: Int) : AlignmentOp()
        data class Insert(val newIndex: Int) : AlignmentOp()
    }

    private data class Alignment(val operations: List<AlignmentOp>)

    private sealed class DiffLine {
        data class Context(val text: String) : DiffLine()
        data class Add(val text: String) : DiffLine()
        data class Delete(val text: String) : DiffLine()
    }

    private enum class PatchLineType { CONTEXT, ADD, DELETE }

    private data class PatchLine(val text: String, val type: PatchLineType)

    private data class BindingSite(val position: Int, val freeEnergy: Double)

    companion object {
        private val log = LoggerFactory.getLogger(ThermodynamicPatchMatcher::class.java)

        // Default thermodynamic parameters
        const val DEFAULT_TEMPERATURE = 1.0
        const val COOPERATIVITY_BONUS = 2.0  // Energy bonus for adjacent matches
        const val ENTROPY_PENALTY = 1.0      // Energy cost per gap
        const val DEFAULT_CONTEXT_SIZE = 3
        const val MIN_BINDING_ENERGY = 0.0   // Maximum energy for valid binding
    }
}