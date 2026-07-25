package com.simiacryptus.cognotik.docops.plan

    import com.simiacryptus.cognotik.docops.model.ContributionKind
    import com.simiacryptus.cognotik.docops.model.DocSpec
    import com.simiacryptus.cognotik.docops.model.TargetContribution
    import java.io.File

    /** Primary source selection + the union of every context file that feeds a target. */
    class RelatedFileCollector(
      private val additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
    ) {

      fun primarySource(contributions: List<TargetContribution>): File? =
        contributions.sortedBy { it.kind.sourcePriority }.firstNotNullOfOrNull { contribution ->
          when (contribution.kind) {
            ContributionKind.TRANSFORM -> contribution.sourceFiles.firstOrNull()
            ContributionKind.SPECIFIES -> contribution.spec.docFile
            ContributionKind.DOCUMENT,
            ContributionKind.GENERATE -> contribution.sourceFiles.firstOrNull() ?: contribution.spec.docFile
            ContributionKind.FOLDER -> contribution.spec.docFile
          }
        }

      fun relatedFiles(
        target: File,
        contributions: List<TargetContribution>,
        ctx: ResolveContext,
      ): List<File> {
        val resolvedRelatedBySpec = HashMap<File, List<File>>()
        val out = LinkedHashSet<File>()
        for (contribution in contributions.sortedBy { it.kind.sourcePriority }) {
          out.addAll(contribution.sourceFiles)
          val spec = contribution.spec
          out.addAll(resolvedRelatedBySpec.getOrPut(spec.docFile) {
            spec.related.flatMap { ctx.resolveRelated(spec.baseDir, it) }
          })
          out.addAll(additionalContext(spec, target).map { File(it) })
        }
        return out.toList()
      }
    }