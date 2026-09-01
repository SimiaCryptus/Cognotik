package com.simiacryptus.cognotik.plan.tools.images

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.docs.RenderableDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO

class ImageDecompositionTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ImageDecompositionConfig?
) : AbstractFileTask<ImageDecompositionTask.ImageDecompositionConfig>(
  orchestrationConfig,
  planTask
) {

  class ImageDecompositionConfig(
    @Description("The image file to analyze (relative path)")
    files: List<String> = emptyList(),
    @Description("The goal of the analysis (e.g., 'Find Waldo', 'Read all text', 'Describe every person')")
    var segmentation_query: String? = "Describe the contents of this image in detail",
    @Description("The specific query for detailed analysis of identified regions")
    var analysis_query: String? = "Describe the contents of this image in detail",
    @Description("DPI for rendering document pages (default: 150)")
    var dpi: Float = 150f,
    @Description("Maximum recursion depth (1-5). Higher is slower but more detailed.")
    var max_depth: Int = 2,
    @Description("Minimum width/height (in pixels) of a region to trigger recursive analysis.")
    var min_region_size: Int = 100,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = ImageDecomposition.name,
    task_description = segmentation_query,
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (listOf<String>(main_file).isEmpty()) return "Input image file must be specified"
      if (main_file.isNullOrBlank()) return "Output JSON file must be specified"
      if (max_depth < 1) return "Max depth must be at least 1"
      return ValidatedObject.Companion.validateFields(this)
    }
  }

  // Data classes for the LLM interaction
  class IdentifiedRegion(
    @Description("A descriptive label or text content found in this region")
    val label: String = "",
    @Description("Detailed description of what is happening or visible in this specific region")
    val description: String = "",
    @Description("True if this region requires further zooming/recursion to answer the query")
    val requires_zoom: Boolean = false
  ) : ImageBounds()

  data class RegionAnalysisResult(
    @Description("List of distinct regions identified in the image")
    val regions: List<IdentifiedRegion> = emptyList(),
    @Description("Summary of the current view")
    val summary: String = ""
  )

  data class LeafAnalysisResult(
    @Description("Detailed analysis of the image segment")
    val analysis: String = ""
  )

  // Internal data structure for the final tree
  data class AnalysisNode(
    val id: String,
    val label: String,
    val description: String,
    val bounds: ImageBounds, // Global coordinates
    val depth: Int,
    val children: MutableList<AnalysisNode> = mutableListOf(),
    var analysis: String? = null
  )

  open class ImageBounds(
    @Description("X coordinate of the top-left corner (0-1000 scale relative to parent)")
    var x: Int = 0,
    @Description("Y coordinate of the top-left corner (0-1000 scale relative to parent)")
    var y: Int = 0,
    @Description("Width of the region (0-1000 scale relative to parent)")
    var width: Int = 0,
    @Description("Height of the region (0-1000 scale relative to parent)")
    var height: Int = 0,
  ) {
    fun isValid(
      currentImage: BufferedImage,
      minSize: Int
    ): Boolean {
      val localX = (x * currentImage.width / 1000.0).toInt()
      val localY = (y * currentImage.height / 1000.0).toInt()
      val localW = (width * currentImage.width / 1000.0).toInt()
      val localH = (height * currentImage.height / 1000.0).toInt()
      val safeX = localX.coerceIn(0, currentImage.width - 1)
      val safeY = localY.coerceIn(0, currentImage.height - 1)
      val safeW = localW.coerceAtMost(currentImage.width - safeX)
      val safeH = localH.coerceAtMost(currentImage.height - safeY)
      return localW > minSize && localH > minSize && safeW > 0 && safeH > 0
    }

    fun subImage(currentImage: BufferedImage): BufferedImage {
      val localX = (x * currentImage.width / 1000.0).toInt()
      val localY = (y * currentImage.height / 1000.0).toInt()
      val localW = (width * currentImage.width / 1000.0).toInt()
      val localH = (height * currentImage.height / 1000.0).toInt()
      val safeX = localX.coerceIn(0, currentImage.width - 1)
      val safeY = localY.coerceIn(0, currentImage.height - 1)
      val safeW = localW.coerceAtMost(currentImage.width - safeX)
      val safeH = localH.coerceAtMost(currentImage.height - safeY)
      return currentImage.getSubimage(safeX, safeY, safeW, safeH)
    }
  }

  override fun promptSegment() = """
    IterativeImageDecomposition - Recursively analyzes images for fine details.
    * Use for: OCR on complex documents, finding small objects, or detailed scene analysis.
    * Inputs: Image file path, segmentation query, and analysis query.
    * Outputs: A hierarchical JSON report and annotated debug images.
    * Mechanism: Recursively crops and re-prompts the vision model on regions of interest.
      """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val executionConfig = executionConfig ?: return resultFn("Execution config is missing")
    val imagePath = listOf(executionConfig.main_file).firstOrNull() ?: return resultFn("No image file specified")
    val segmentation_query = executionConfig.segmentation_query ?: "Analyze image"
    val analysis_query = executionConfig.analysis_query ?: "Describe the contents of this image in detail"
    val maxDepth = executionConfig.max_depth
    val minSize = executionConfig.min_region_size
    val outputFile = executionConfig.main_file ?: "analysis.json"

    val transcript = task.newUserFileStream(transcriptFile())
    val tabs = TabbedDisplay(task)
    val logTab = tabs.newTask("Live Log")

    task.pool.submit {
      log.info("Starting ImageDecompositionTask for $imagePath")
      try {
        task.add("## Starting Iterative Analysis\nProcessing image: `$imagePath`".renderMarkdown())
        transcript?.write("## Image Decomposition Analysis: $imagePath\n".toByteArray())
        logTab.add("Loading image...".renderMarkdown())

        val inputFile = root.resolve(imagePath).toFile()
        if (!inputFile.exists()) throw RuntimeException("File not found: $inputFile")


        val pages: Int = inputFile.getDocumentReader().use { reader ->
          if (reader is RenderableDocumentReader) {
            reader.getPageCount()
          } else {
            1
          }
        }

        val allNodes = ConcurrentLinkedQueue<AnalysisNode>()
        val rootNodes = mutableListOf<AnalysisNode>()
        val model =
          (typeConfig?.model?.let { it.instance(orchestrationConfig.user) } ?: defaultSmart).getChildClient(task)

        (0 until pages).forEach { page ->
          logTab.add("Processing page ${page + 1} of $pages".renderMarkdown())
          transcript?.write(
            "\n## Page ${page + 1} of $pages\n".toByteArray()
          )
          val originalImage = loadImage(inputFile, executionConfig, page)

          // Root node of our analysis tree
          val rootID = if (pages > 1) "page_$page" else "root"
          val rootNode = AnalysisNode(
            id = rootID,
            label = "Full Image",
            description = "Root level analysis",
            bounds = ImageBounds(0, 0, originalImage.width, originalImage.height),
            depth = 0
          )

          // Shared list for visualization later
          allNodes.add(rootNode)
          rootNodes.add(rootNode)

          fun analyzeLeaf(image: BufferedImage, node: AnalysisNode) {
            try {
              logTab.add("Analyzing leaf node: ${node.label}".renderMarkdown())
              val result = ParsedImageAgent(
                resultClass = LeafAnalysisResult::class.java,
                model = model,
                prompt = "Analyze this image segment for the query: \"$analysis_query\"",
              ).answer(listOf(ImageAndText(text = "", image = image))).obj
              node.analysis = result.analysis
              transcript?.write(
                """

### Leaf Analysis - ${node.label}
${result.analysis}
""".toByteArray()
              )
            } catch (e: Exception) {
              log.error("Error analyzing leaf node ${node.label}", e)
              transcript?.write(
                "Error analyzing leaf node ${node.label}: ${e.message}\n".toByteArray()
              )
            }
          }


          // Recursive function
          fun processRegion(
            currentImage: BufferedImage,
            currentNode: AnalysisNode,
            currentDepth: Int,
            globalOffsetX: Int,
            globalOffsetY: Int
          ) {
            if (currentDepth >= maxDepth) {
              transcript?.write(
                "Reached max depth at node ${currentNode.label}, performing leaf analysis.\n".toByteArray()
              )
              analyzeLeaf(currentImage, currentNode)
              return
            }

            try {
              logTab.add("Analyzing depth $currentDepth: ${currentNode.label} (${currentNode.bounds.width}x${currentNode.bounds.height})".renderMarkdown())
              transcript?.write(
                """
### Depth $currentDepth - ${currentNode.label}
Analyzing region at depth $currentDepth with bounds (${currentNode.bounds.x}, ${currentNode.bounds.y}, ${currentNode.bounds.width}, ${currentNode.bounds.height})
""".toByteArray()
              )

              val result = ParsedImageAgent(
                resultClass = RegionAnalysisResult::class.java,
                model = model,
                prompt = """
Analyze this image segment for the query: "$segmentation_query".
Identify distinct regions that are relevant or contain dense information.
Output coordinates on a 0-1000 scale relative to this specific image segment.
If a region looks like it contains smaller details (text, faces, objects) that are hard to see, mark 'requires_zoom' as true.
                    """
              ).answer(listOf(ImageAndText(text = "", image = currentImage))).obj

              // Log raw result to transcript
              transcript?.write(
                """

                ### Depth $currentDepth - ${currentNode.label}
                <details>
                <summary>Raw Region Analysis JSON</summary>

                ```json
                ${result.toJson()}
                ```
                </details>
                        """.toByteArray()
              )

              if (result.regions.isNotEmpty()) {
                result.regions.forEachIndexed { index, region: IdentifiedRegion ->
                  val localX = (region.x * currentImage.width / 1000.0).toInt()
                  val localY = (region.y * currentImage.height / 1000.0).toInt()
                  val localW = (region.width * currentImage.width / 1000.0).toInt()
                  val localH = (region.height * currentImage.height / 1000.0).toInt()
                  val globalX = globalOffsetX + localX
                  val globalY = globalOffsetY + localY

                  val childNode = AnalysisNode(
                    id = "${currentNode.id}_$index",
                    label = region.label,
                    description = region.description,
                    bounds = ImageBounds(globalX, globalY, localW, localH),
                    depth = currentDepth + 1
                  )
                  currentNode.children.add(childNode)
                  allNodes.add(childNode)


                  val canRecurse = (currentDepth + 1) <= maxDepth
                  val validForRecursion = region.isValid(currentImage, minSize)
                  val validForAnalysis = region.isValid(currentImage, 0)

                  if (region.requires_zoom && canRecurse && validForRecursion) {
                    val subImage = region.subImage(currentImage)
                    task.resolveUserFile("${currentNode.id}_$index.png")?.also { imgFile ->
                      ImageIO.write(subImage, "png", imgFile)
                      val imgLink = task.linkTo(imgFile.name)
                      logTab.add(
                        """Saved sub-image for ${childNode.label} at depth ${currentDepth + 1}: 
                                                                  |<a href="$imgLink" target="_blank">${imgFile.name}</a>""".trimMargin()
                          .renderMarkdown()
                      )
                      transcript?.write(
                        """
                    |
                    |#### Sub-image for ${childNode.label} at depth ${currentDepth + 1} 
                    |<a href="$imgLink" target="_blank"><img src="$imgLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
                    |""".trimMargin().toByteArray()
                      )
                    }
                    processRegion(subImage, childNode, currentDepth + 1, globalX, globalY)
                  } else if (validForAnalysis) {
                    if (region.requires_zoom && !validForRecursion) {
                      logTab.add("Skipped recursion for ${childNode.label} (too small) at depth ${currentDepth + 1}".renderMarkdown())
                      transcript?.write(
                        "Skipped recursion for ${childNode.label} (too small) at depth ${currentDepth + 1}\n".toByteArray()
                      )
                    }
                    analyzeLeaf(region.subImage(currentImage), childNode)
                  }
                }
              } else {
                logTab.add("No regions identified at depth $currentDepth for ${currentNode.label}".renderMarkdown())
                transcript?.write("No regions identified at depth $currentDepth for ${currentNode.label}\n".toByteArray())
                analyzeLeaf(currentImage, currentNode)
              }
            } catch (e: Exception) {
              log.error("Error processing region at depth $currentDepth for ${currentNode.label}", e)
              transcript?.write(
                "Error processing region at depth $currentDepth for ${currentNode.label}: ${e.message}\n".toByteArray()
              )
            }
          }
          processRegion(originalImage, rootNode, 0, 0, 0)

          task.add("### Generating Visual Report...".renderMarkdown())

          // 1. Draw Debug Image
          val debugImage = BufferedImage(originalImage.width, originalImage.height, BufferedImage.TYPE_INT_ARGB)
          val g = debugImage.createGraphics()
          g.drawImage(originalImage, 0, 0, null)

          // Color map for depth
          val depthColors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.ORANGE, Color.MAGENTA)

          allNodes.forEach { node ->
            if (node.depth > 0) { // Don't draw root
              g.color = depthColors[node.depth % depthColors.size]
              g.stroke = BasicStroke((maxDepth - node.depth + 1).toFloat()) // Thicker lines for higher levels
              g.drawRect(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height)

              // Draw label background
              if (node.bounds.width > 20) {
                g.color = Color(0, 0, 0, 150)
                g.fillRect(node.bounds.x, node.bounds.y, node.bounds.width.coerceAtMost(100), 15)
                g.color = Color.WHITE
                g.font = g.font.deriveFont(10f)
                g.drawString(node.label, node.bounds.x + 2, node.bounds.y + 12)
              }
            }
          }
          g.dispose()

          val debugFileName = "${File(imagePath).nameWithoutExtension}_${rootID}_analysis.png"
          ImageIO.write(debugImage, "png", root.resolve(debugFileName).toFile())
          val debugLink = task.linkTo(debugFileName)

          // 3. Update UI Tabs
          tabs["Visual Analysis"] = """
                    <a href="$debugLink" target="_blank"><img src="$debugLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
                """.trimIndent()

          // 4. Flattened Summary for LLM
          val flatSummary = allNodes.joinToString("\n") { node ->
            val analysisText = node.analysis?.let { " Analysis: $it" } ?: ""
            if (node.depth == 0) "Root Image: ${node.description}$analysisText" else
              "- [Depth ${node.depth}] ${node.label} at (${node.bounds.x},${node.bounds.y}): ${node.description}$analysisText"
          }

          tabs["Summary"] = MarkdownUtil.renderMarkdown(flatSummary)
        }


        val jsonOutput = rootNodes.toJson()
        root.resolve(outputFile).toFile().writeText(jsonOutput)
        tabs["Structured Data"] = """
            <p>Full analysis saved to <a href="${task.linkTo(outputFile)}">$outputFile</a></p>
            <details>
            <summary>Raw JSON Tree</summary>
            <pre style="max-height: 400px; overflow: auto;">$jsonOutput</pre>
            </details>
        """.trimIndent()

        task.add("### Finalizing Analysis...".renderMarkdown())
        val finalResult = ChatAgent(
          model = model,
          prompt = """Review the following hierarchical analysis of the image regions and answer the query: "$analysis_query" """.trimIndent()
        ).answer(listOf(rootNodes.toJson()))

        tabs["Final Report"] = MarkdownUtil.renderMarkdown(finalResult)
        task.safeComplete("### Analysis Complete\nFound **${allNodes.size - 1}** regions.".renderMarkdown(), log)
        root.resolve("final_analysis_${WaterfallMode.Companion.now()}.md").toFile().writeText(finalResult)

        log.info("ImageDecompositionTask completed successfully for $imagePath")
        resultFn(
          """
            ## Image Analysis Complete
            * **Source:** `$imagePath`
            * **Regions Identified:** ${allNodes.size - 1}
            * **Structured Data:** `$outputFile`
            
            ### Summary
            $finalResult
        """.trimIndent()
        )

      } catch (e: Exception) {
        log.error("Error in IterativeImageDecomposition", e)
        task.error(e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        resultFn("Error analyzing image: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }


  private fun loadImage(
    inputFile: File,
    executionConfig: ImageDecompositionConfig,
    page: Int
  ): BufferedImage {
    var originalImage: BufferedImage? = null
    try {
      inputFile.getDocumentReader().use { reader ->
        if (reader is RenderableDocumentReader) {
          originalImage = reader.renderImage(page, executionConfig.dpi)
        }
      }
    } catch (e: Throwable) {
      log.warn("Error reading as document: ${e.message}")
    }
    if (originalImage == null) {
      originalImage = ImageIO.read(inputFile)
    }
    if (originalImage == null) throw RuntimeException("Failed to load image from $inputFile")
    return originalImage
  }

  override fun acceptButtonFooter(ui: ISessionTask, fn: () -> Unit): String {
    return ui.hrefLink("Accept Analysis") { fn() }
  }

  companion object {
    private val log: Logger = getLogger(ImageDecompositionTask::class.java)

    @JvmStatic
    val ImageDecomposition = TaskType(
      name = "ImageDecomposition",
      category = "File",
      taskClass = ImageDecompositionTask::class.java,
      executionConfigClass = ImageDecompositionConfig::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Recursively analyze an image to find details, text, or specific objects.",
      tooltipHtml = """
      Performs a deep-dive analysis of an image by:
      <ul>
          <li>Identifying regions of interest based on a query</li>
          <li>Recursively cropping and re-analyzing those regions</li>
          <li>Stitching results into a hierarchical dataset</li>
      </ul>
      Useful for OCR on complex forms, crowd analysis, or finding small details.
                  """,
    )
  }
}