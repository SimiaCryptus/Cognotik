package com.simiacryptus.cognotik.docops

    import com.simiacryptus.cognotik.docops.exec.*
    import com.simiacryptus.cognotik.docops.model.*
    import com.simiacryptus.cognotik.docops.plan.RelatedFileCollector
    import com.simiacryptus.cognotik.docops.plan.ResolveContext
    import com.simiacryptus.cognotik.docops.plan.TaskBuilder
    import com.simiacryptus.cognotik.docops.plan.policy.*
    import com.simiacryptus.cognotik.docops.resolve.*
    import com.simiacryptus.cognotik.text.patch.PatchProcessor
    import com.simiacryptus.cognotik.text.patch.PatchProcessors
    import java.io.File
    import java.time.Clock
    import java.time.Duration
    import java.time.Instant
    import java.time.ZoneId
    import java.util.concurrent.CompletableFuture

    /**
     * A [DocTaskKind] implemented as a plain class rather than an enum, so tests never fight the
     * `Enum.name` / `DocTaskKind.name` overlap.
     */
    class TestKind(
      override val name: String,
      override val isFileTask: Boolean = false,
      override val isSubPlanTask: Boolean = false,
      override val isTemplateTask: Boolean = false,
      private val defaults: Map<String, Any>? = null,
    ) : DocTaskKind {
      override fun defaultConfig(): Map<String, Any>? = defaults
      override fun toString(): String = name
      override fun equals(other: Any?): Boolean = other is TestKind && other.name == name
      override fun hashCode(): Int = name.hashCode()

      companion object {
        val CodeEdit = TestKind("CodeEdit")
        val FileEdit = TestKind("FileEdit", isFileTask = true)
        val ErbTemplate = TestKind("ErbTemplate", isFileTask = true, isTemplateTask = true)
        val SubPlan = TestKind("SubPlan", isSubPlanTask = true)
        val WithDefaults = TestKind("WithDefaults", defaults = mapOf("task_type" to "WithDefaults", "n" to 7))
      }
    }

    class TestKinds(
      override val default: TestKind = TestKind.CodeEdit,
      private val all: List<TestKind> = listOf(
        TestKind.CodeEdit, TestKind.FileEdit, TestKind.ErbTemplate, TestKind.SubPlan, TestKind.WithDefaults
      ),
    ) : DocTaskKindResolver<TestKind> {
      override fun byName(name: String): TestKind? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /* ---------------------------------------------------------------- filesystem helpers */

    fun File.child(path: String, content: String = ""): File {
      val f = File(this, path)
      f.parentFile?.mkdirs()
      f.writeText(content)
      return f
    }

    fun File.childDir(path: String): File = File(this, path).apply { mkdirs() }

    fun docSpec(
      docFile: File,
      specifies: List<String> = emptyList(),
      documents: List<String> = emptyList(),
      transforms: List<TransformSpec> = emptyList(),
      generates: List<GenerateSpec> = emptyList(),
      related: List<String> = emptyList(),
      content: String = "",
      frontmatter: Map<String, Any> = emptyMap(),
      taskType: String? = null,
      taskConfigJson: String? = null,
      updateMode: String? = null,
      targetFolder: String? = null,
      prompt: String? = null,
    ) = DocSpec(
      docFile = docFile,
      specifies = specifies,
      documents = documents,
      transforms = transforms,
      generates = generates,
      related = related,
      content = content,
      frontmatter = frontmatter,
      taskConfigJson = taskConfigJson,
      taskType = taskType,
      updateMode = updateMode,
      targetFolder = targetFolder,
      prompt = prompt,
    )

    fun contribution(
      target: File,
      spec: DocSpec,
      kind: ContributionKind,
      sourceFiles: List<File> = emptyList(),
    ) = TargetContribution(TargetPath.of(target), spec, kind, sourceFiles)


    class FakeHttpFetcher(private val responder: (HttpFetchRequest) -> HttpFetchResponse) : HttpFetcher {
      val requests = mutableListOf<HttpFetchRequest>()
      var calls = 0
        private set

      override fun fetch(request: HttpFetchRequest): HttpFetchResponse {
        calls++
        requests += request
        return responder(request)
      }
    }

    class MutableClock(
      var now: Instant = Instant.parse("2024-05-01T12:00:00Z"),
      private val zone: ZoneId = ZoneId.of("UTC"),
    ) : Clock() {
      override fun getZone(): ZoneId = zone
      override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
      override fun instant(): Instant = now
      fun advance(duration: Duration) {
        now = now.plus(duration)
      }
    }

    /* ---------------------------------------------------------------- execution seams */



