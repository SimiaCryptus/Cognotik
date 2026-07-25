package com.simiacryptus.cognotik.docops.plan

import com.simiacryptus.cognotik.docops.model.ContributionKind
import com.simiacryptus.cognotik.docops.model.DocSpec
import com.simiacryptus.cognotik.docops.model.TargetContribution
import com.simiacryptus.cognotik.docops.model.TargetPath
import com.simiacryptus.cognotik.docops.resolve.TransformExpander
import org.slf4j.LoggerFactory

fun interface TargetResolver {
  fun contributions(specs: List<DocSpec>, ctx: ResolveContext): List<TargetContribution>
}

private val log = LoggerFactory.getLogger(TargetResolver::class.java)

object SpecifiesTargetResolver : TargetResolver {
  override fun contributions(specs: List<DocSpec>, ctx: ResolveContext) =
    specs.filter { it.specifies.isNotEmpty() }.flatMap { spec ->
      spec.specifies.flatMap { pattern ->
        val files = ctx.expand(spec.baseDir, pattern)
        log.info("Doc ${spec.docFile.name} specifies '$pattern' -> ${files.size} file(s)")
        files.map { TargetContribution(TargetPath.of(it), spec, ContributionKind.SPECIFIES) }
      }
    }
}

object TransformTargetResolver : TargetResolver {
  override fun contributions(specs: List<DocSpec>, ctx: ResolveContext) =
    specs.filter { it.transforms.isNotEmpty() }.flatMap { spec ->
      spec.transforms.flatMap { transform ->
        TransformExpander.expand(ctx.root, transform, spec, ctx.lister).map { match ->
          TargetContribution(
            target = TargetPath.of(match.destinationFile),
            spec = spec,
            kind = ContributionKind.TRANSFORM,
            sourceFiles = listOf(match.sourceFile),
          )
        }
      }
    }

  /**
   * Contributions for targets that do not exist yet but that a transform would consume,
   * i.e. chained pipelines. Driven by [DocPlanner]'s fixpoint loop.
   */
  fun hypothetical(
    specs: List<DocSpec>,
    candidates: Collection<TargetPath>,
    known: Set<TargetPath>,
  ): List<TargetContribution> = specs.filter { it.transforms.isNotEmpty() }.flatMap { spec ->
    spec.transforms.flatMap { transform ->
      candidates.mapNotNull { candidate ->
        val dest = TransformExpander.destinationForHypothetical(spec, transform, candidate.file)
          ?: return@mapNotNull null
        val destPath = TargetPath.of(dest)
        if (destPath in known) return@mapNotNull null
        log.debug("Transitive target discovered: $destPath (via ${transform.sourcePattern} -> ${transform.destinationPattern})")
        TargetContribution(
          target = destPath,
          spec = spec,
          kind = ContributionKind.TRANSFORM,
          sourceFiles = listOf(candidate.file),
          hypothetical = true,
        )
      }
    }
  }
}

object DocumentTargetResolver : TargetResolver {
  override fun contributions(specs: List<DocSpec>, ctx: ResolveContext) =
    specs.filter { it.documents.isNotEmpty() }.map { spec ->
      val supporting = spec.documents.flatMap { pattern ->
        val matched = ctx.expandGlob(spec.baseDir, pattern)
        log.info("Doc ${spec.docFile.name} documents '$pattern' -> ${matched.size} supporting file(s)")
        matched
      }.distinct()
      TargetContribution(
        target = TargetPath.of(spec.docFile),
        spec = spec,
        kind = ContributionKind.DOCUMENT,
        sourceFiles = supporting,
      )
    }
}

object GenerateTargetResolver : TargetResolver {
  override fun contributions(specs: List<DocSpec>, ctx: ResolveContext) =
    specs.filter { it.generates.isNotEmpty() }.flatMap { spec ->
      spec.generates.map { gen ->
        val inputs = gen.inputs.flatMap { ctx.expandGlob(spec.baseDir, it) }.distinct()
        log.info("Doc ${spec.docFile.name} generates '${gen.output}' from ${inputs.size} input(s)")
        TargetContribution(
          target = TargetPath.of(spec.baseDir.resolve(gen.output)),
          spec = spec,
          kind = ContributionKind.GENERATE,
          sourceFiles = inputs,
        )
      }
    }
}

object FolderTargetResolver : TargetResolver {
  override fun contributions(specs: List<DocSpec>, ctx: ResolveContext) =
    specs.filter { it.hasOnlyFolderTarget }.map { spec ->
      val folder = spec.baseDir.resolve(spec.targetFolder!!)
      log.info("Doc ${spec.docFile.name} targets folder '${spec.targetFolder}' -> ${folder.absolutePath}")
      TargetContribution(TargetPath.of(folder), spec, ContributionKind.FOLDER)
    }
}

val defaultTargetResolvers: List<TargetResolver> = listOf(
  SpecifiesTargetResolver,
  TransformTargetResolver,
  DocumentTargetResolver,
  GenerateTargetResolver,
  FolderTargetResolver,
)