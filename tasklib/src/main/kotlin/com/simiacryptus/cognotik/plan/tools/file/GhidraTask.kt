package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class GhidraTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: GhidraTaskExecutionConfigData?
) : AbstractTask<GhidraTask.GhidraTaskExecutionConfigData, GhidraTask.GhidraTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class GhidraTypeConfig(
    @Description("Path to Ghidra installation directory (overrides GHIDRA_INSTALL_DIR environment variable)")
    var ghidra_install_dir: String? = null,
    @Description("Temperature for AI analysis of extracted binary artifacts")
    var analysis_temperature: Double = 0.2,
    @Description("Prompt template for analyzing Ghidra output")
    var analysis_prompt_template: String = """
You are a software reverse engineering and binary analysis expert.
Analyze the following artifacts extracted from a compiled binary using Ghidra.

## Binary File:
{binary_path}

## Analysis Goal:
{analysis_goal}

## Extracted Binary Metadata & Decompilation:
{extracted_data}

{context}

## Instructions:
1. **Architecture & Overview**: Summarize the binary format, architecture, compiler conventions, and primary purpose based on imports, exports, and strings.
2. **Key Functions & Control Flow**: Explain the behavior and responsibility of key functions and entry points.
3. **Data & String Insights**: Highlight notable configuration strings, file paths, or protocol indicators.
4. **Goal-Specific Findings**: Directly address the stated analysis goal with clear evidence from the decompiled code and symbol tables.
    """.trimIndent()
  ) : TaskTypeConfig()

  class GhidraTaskExecutionConfigData(
    @Description("Path to the binary file to analyze, relative to workspace root")
    var binary_path: String? = null,
    @Description("Specific question or goal for the binary analysis")
    var analysis_goal: String? = "Understand the program structure, key functions, and overall behavior",
    @Description("Optional list of specific function names or addresses to decompile (if empty, top functions are decompiled)")
    var target_functions: List<String>? = null,
    @Description("Maximum number of functions to decompile when target_functions is not specified")
    var max_decompiled_functions: Int = 25,
    @Description("Whether to extract defined strings from the binary")
    var extract_strings: Boolean = true,
    @Description("Minimum string length to extract")
    var min_string_length: Int = 5,
    @Description("Whether to extract imported and exported symbols")
    var extract_imports_exports: Boolean = true,
    @Description("Optional path to a custom Ghidra script (Python or Java) to run as a postScript")
    var custom_script_path: String? = null,
    @Description("Arguments to pass to the custom Ghidra script")
    var custom_script_args: List<String>? = null,
    @Description("Optional processor/language ID (e.g., 'x86:LE:64:default')")
    var processor: String? = null,
    @Description("Timeout for Ghidra headless execution in seconds")
    var timeout_seconds: Int = 600,
    @Description("Optional path to Ghidra installation directory")
    var ghidra_install_dir: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = Ghidra.name,
    task_description = task_description ?: "Analyze binary '${binary_path ?: "?"}' with Ghidra",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      binary_path = binary_path?.trim()
      if (binary_path.isNullOrBlank()) {
        return "binary_path must not be null or blank"
      }
      analysis_goal = analysis_goal?.trim()?.ifBlank { "Analyze binary structure and functions" }
      max_decompiled_functions = max_decompiled_functions.coerceIn(0, 500)
      min_string_length = min_string_length.coerceIn(1, 200)
      timeout_seconds = timeout_seconds.coerceIn(10, 86400)
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
Ghidra - Analyze compiled binaries using Ghidra Headless Analyzer
  ** Specify binary_path (relative to workspace) and analysis_goal
  ** Extracts function tables, decompiled C code, imports, exports, and strings
  ** Optionally target specific functions via target_functions or run a custom Ghidra script via custom_script_path
  ** Requires Ghidra installed locally (configured via ghidra_install_dir or GHIDRA_INSTALL_DIR environment variable)
    """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val cfg = executionConfig
    if (cfg == null) {
      val msg = "CONFIGURATION ERROR: Execution configuration is null"
      task.safeComplete(msg, log)
      resultFn(msg)
      return
    }
    cfg.validate()?.let { err ->
      val msg = "CONFIGURATION ERROR: $err"
      log.error(msg)
      task.safeComplete(msg, log)
      resultFn(msg)
      return
    }

    val binaryFile = root.resolve(cfg.binary_path!!).toFile().canonicalFile
    if (!binaryFile.exists() || !binaryFile.isFile) {
      val msg = "ERROR: Binary file does not exist: ${binaryFile.path}"
      log.error(msg)
      task.safeComplete(msg, log)
      resultFn(msg)
      return
    }

    val effectiveTypeConfig = typeConfig ?: GhidraTypeConfig()
     val analyzeHeadlessCmd = resolveAnalyzeHeadless(
       cfg.ghidra_install_dir ?: effectiveTypeConfig.ghidra_install_dir ?: System.getenv("GHIDRA_INSTALL_DIR") ?: System.getProperty("GHIDRA_INSTALL_DIR")
     )
    if (analyzeHeadlessCmd == null) {
      val msg = "CONFIGURATION ERROR: Could not locate Ghidra 'analyzeHeadless'. " +
           "Set GHIDRA_INSTALL_DIR environment variable, configure ghidra_install_dir, or install to /opt/ghidra."
      log.error(msg)
      task.safeComplete(msg, log)
      resultFn(msg)
      return
    }

    val startTime = System.currentTimeMillis()
    val api = defaultSmart

    task.pool.submit {
      var transcript: OutputStream? = null
      val tmpDir = Files.createTempDirectory("cognotik-ghidra-").toFile()
      try {
        transcript = task.newUserFileStream(transcriptFile())
        val tabs = TabbedDisplay(task)
        val overviewTab = tabs.newTask("Overview")

        val headerMd = """
          |## Ghidra Binary Analysis
          |
          |**Binary:** `${cfg.binary_path}` (${binaryFile.length()} bytes)
          |
          |**Goal:** ${cfg.analysis_goal}
          |
          |**Headless Runner:** `$analyzeHeadlessCmd`
        """.trimMargin()

        overviewTab.add(headerMd.renderMarkdown())
        overviewTab.add("🔄 Running Ghidra headless analysis...".renderMarkdown())
        overviewTab.update()

        transcript?.write("# Ghidra Binary Analysis\n\n".toByteArray())
        transcript?.write(
          "**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n".toByteArray()
        )
        transcript?.write("$headerMd\n\n".toByteArray())

        // Prepare built-in extraction script and output paths
        val projectDir = File(tmpDir, "project").apply { mkdirs() }
        val scriptDir = File(tmpDir, "scripts").apply { mkdirs() }
        val outputReportFile = File(tmpDir, "ghidra_extracted.md")
        val extractionScript = File(scriptDir, "CognotikExtractScript.java")
        extractionScript.writeText(buildJavaExtractionScript(), StandardCharsets.UTF_8)

        val cmd = mutableListOf(
          analyzeHeadlessCmd,
          projectDir.absolutePath,
          "TempProject",
          "-import",
          binaryFile.absolutePath,
          "-deleteProject"
        )

        if (!cfg.processor.isNullOrBlank()) {
          cmd.addAll(listOf("-processor", cfg.processor!!))
        }

        cmd.addAll(
          listOf(
            "-scriptPath",
            scriptDir.absolutePath,
            "-postScript",
            extractionScript.name,
            outputReportFile.absolutePath,
            cfg.max_decompiled_functions.toString(),
            cfg.extract_strings.toString(),
            cfg.min_string_length.toString(),
            cfg.extract_imports_exports.toString(),
            (cfg.target_functions ?: emptyList()).joinToString(",")
          )
        )

        if (!cfg.custom_script_path.isNullOrBlank()) {
          val customScriptFile = root.resolve(cfg.custom_script_path!!).toFile().canonicalFile
          if (customScriptFile.exists()) {
            cmd.addAll(listOf("-scriptPath", customScriptFile.parentFile.absolutePath))
            cmd.add("-postScript")
            cmd.add(customScriptFile.name)
            cfg.custom_script_args?.forEach { cmd.add(it) }
          } else {
            log.warn("Custom script not found: ${customScriptFile.path}")
          }
        }

        val (exitCode, consoleOutput) = runProcess(cmd, tmpDir, cfg.timeout_seconds.toLong())
        log.info("Ghidra headless finished with exitCode=$exitCode in ${System.currentTimeMillis() - startTime}ms")

        val logTab = tabs.newTask("Ghidra Log")
        logTab.add("```\n${consoleOutput.takeLast(15000)}\n```".renderMarkdown())
        logTab.complete()
        transcript?.write("## Ghidra Headless Log\n<details><summary>Console Output</summary>\n\n```\n${consoleOutput.takeLast(20000)}\n```\n</details>\n\n".toByteArray())

        val extractedText = if (outputReportFile.exists()) {
          outputReportFile.readText(StandardCharsets.UTF_8)
        } else {
          ""
        }

        if (extractedText.isBlank() && exitCode != 0) {
          val errMsg = "ERROR: Ghidra headless execution failed (exit code $exitCode). Check Ghidra Log tab for details."
          overviewTab.add("❌ $errMsg".renderMarkdown())
          overviewTab.complete()
          task.safeComplete(errMsg, log)
          resultFn(errMsg)
          return@submit
        }

        // Save raw extracted report as artifact
        val artifactName = "ghidra_${binaryFile.nameWithoutExtension}_extracted.md"
        val (artifactLink, artifactFile) = task.createFile(artifactName)
        artifactFile?.let {
          it.parentFile?.mkdirs()
          it.writeText(extractedText, StandardCharsets.UTF_8)
        }

        val extractedTab = tabs.newTask("Extracted Artifacts")
        extractedTab.add(extractedText.truncateForDisplay(30000).renderMarkdown())
        extractedTab.complete()
        transcript?.write("## Extracted Artifacts\n<details><summary>Extracted Markdown</summary>\n\n$extractedText\n</details>\n\n".toByteArray())

        // Step 2: AI Analysis
        overviewTab.add("🔄 Running AI analysis on extracted binary data...".renderMarkdown())
        overviewTab.update()

        val analysisTab = tabs.newTask("AI Analysis")
        val priorContext = try {
          getPriorCode(agent.executionState)
        } catch (_: Exception) {
          ""
        }

        val prompt = effectiveTypeConfig.analysis_prompt_template
          .replace("{binary_path}", cfg.binary_path ?: binaryFile.name)
          .replace("{analysis_goal}", cfg.analysis_goal ?: "")
          .replace("{extracted_data}", extractedText.take(60000))
          .replace(
            "{context}",
            if (priorContext.isNotBlank()) "## Context from Prior Tasks:\n$priorContext" else ""
          )

        val chatAgent = ChatAgent(
          prompt = prompt,
          model = api,
          temperature = effectiveTypeConfig.analysis_temperature
        )

        val aiAnalysis = chatAgent.answer(
          (messages + listOf("Analyze the extracted Ghidra artifacts according to the goal.")).filter { it.isNotBlank() }
        )

        analysisTab.add(aiAnalysis.renderMarkdown())
        analysisTab.complete()
        transcript?.write("## AI Analysis\n\n$aiAnalysis\n\n".toByteArray())

        val durationSec = (System.currentTimeMillis() - startTime) / 1000
        overviewTab.add(
          buildString {
            appendLine("✅ **Analysis complete in ${durationSec}s**")
            artifactLink?.let { appendLine("- [Download Extracted Ghidra Report]($it)") }
          }.renderMarkdown()
        )
        overviewTab.complete()

        val finalResult = buildString {
          appendLine("# Ghidra Analysis: `${cfg.binary_path}`")
          appendLine()
          appendLine("**Goal:** ${cfg.analysis_goal}")
          artifactLink?.let { appendLine("- [Extracted Ghidra Artifacts]($it)") }
          appendLine()
          appendLine("## Analysis Summary")
          appendLine(aiAnalysis.truncateForDisplay(15000))
        }

        task.complete()
        resultFn(finalResult)
      } catch (e: Exception) {
        log.error("GhidraTask failed", e)
        try {
          task.error(e)
        } catch (_: Exception) {
        }
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        resultFn("ERROR: Ghidra analysis failed - ${e.message}")
      } finally {
        try {
          transcript?.close()
        } catch (_: Exception) {
        }
        try {
          tmpDir.deleteRecursively()
        } catch (_: Exception) {
        }
      }
    }
  }

  private fun resolveAnalyzeHeadless(configuredDir: String?): String? {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val scriptName = if (isWindows) "analyzeHeadless.bat" else "analyzeHeadless"

    val candidates = mutableListOf<File>()
     fun addCandidateDir(dir: File) {
       if (dir.isFile && (dir.name == scriptName || dir.name.startsWith("analyzeHeadless"))) {
         candidates.add(dir)
       } else {
         candidates.add(File(dir, "support/$scriptName"))
         candidates.add(File(dir, scriptName))
       }
     }

    if (!configuredDir.isNullOrBlank()) {
       addCandidateDir(File(configuredDir.trim()))
    }
    System.getenv("GHIDRA_INSTALL_DIR")?.takeIf { it.isNotBlank() }?.let { envDir ->
       addCandidateDir(File(envDir.trim()))
     }
     System.getProperty("GHIDRA_INSTALL_DIR")?.takeIf { it.isNotBlank() }?.let { propDir ->
       addCandidateDir(File(propDir.trim()))
    }

    candidates.firstOrNull { it.exists() && it.isFile }?.let { return it.absolutePath }

     // Fallback 1: check system PATH
    val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
    for (p in pathDirs) {
      if (p.isBlank()) continue
      val f = File(p, scriptName)
      if (f.exists() && f.isFile) return f.absolutePath
    }
     // Fallback 2: Autodiscovery in sensible default installation locations
     val userHome = System.getProperty("user.home") ?: ""
     val searchRoots = listOfNotNull(
       File("/opt/ghidra"),
       File("/usr/local/ghidra"),
       File("/usr/share/ghidra"),
       if (userHome.isNotBlank()) File(userHome, "ghidra") else null,
       if (userHome.isNotBlank()) File(userHome, ".local/share/ghidra") else null,
       File("/opt"),
       File("/usr/local"),
       File("/Applications"),
       if (userHome.isNotBlank()) File(userHome) else null,
       if (isWindows) File("C:\\ghidra") else null,
       if (isWindows) File("C:\\Program Files\\ghidra") else null,
       if (isWindows) File("C:\\Program Files") else null,
       if (isWindows) File("C:\\Tools") else null
     )
     val discoveredDirs = mutableListOf<File>()
     for (rootDir in searchRoots) {
       if (!rootDir.exists()) continue
       if (rootDir.name.lowercase().startsWith("ghidra")) {
         discoveredDirs.add(rootDir)
       }
       rootDir.listFiles { file ->
         file.isDirectory && file.name.lowercase().startsWith("ghidra")
       }?.sortedByDescending { it.name }?.let {
         discoveredDirs.addAll(it)
       }
     }
     for (dir in discoveredDirs) {
       val supportScript = File(dir, "support/$scriptName")
       if (supportScript.exists() && supportScript.isFile) return supportScript.absolutePath
       val directScript = File(dir, scriptName)
       if (directScript.exists() && directScript.isFile) return directScript.absolutePath
     }

    return null
  }

  private fun runProcess(cmd: List<String>, workDir: File, timeoutSeconds: Long): Pair<Int, String> {
    log.info("Executing Ghidra command: ${cmd.joinToString(" ")}")
    val outFile = Files.createTempFile("ghidra-headless-out", ".log").toFile()
    try {
      val pb = ProcessBuilder(cmd)
        .directory(workDir)
        .redirectErrorStream(true)
        .redirectOutput(outFile)
       val headlessExecutable = File(cmd.first()).canonicalFile
       val supportDir = headlessExecutable.parentFile
       val installDir = if (supportDir?.name == "support") supportDir.parentFile else supportDir
       val env = pb.environment()
       if (installDir != null && installDir.exists()) {
         env.putIfAbsent("GHIDRA_INSTALL_DIR", installDir.absolutePath)
       }
       if (supportDir != null && supportDir.exists()) {
         val currentPath = env["PATH"] ?: env["Path"] ?: ""
         if (!currentPath.contains(supportDir.absolutePath)) {
           env["PATH"] = if (currentPath.isBlank()) supportDir.absolutePath else "$currentPath${File.pathSeparator}${supportDir.absolutePath}"
         }
       }

      val process = pb.start()
      process.outputStream.close()
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        val partial = outFile.readText(StandardCharsets.UTF_8)
        return -1 to "TIMEOUT after ${timeoutSeconds}s\n$partial"
      }
      return process.exitValue() to outFile.readText(StandardCharsets.UTF_8)
    } finally {
      outFile.delete()
    }
  }

  /**
   * Generates a self-contained Ghidra Java Script (`GhidraScript`) that extracts
   * program metadata, imports, exports, functions, decompiled C code, and defined strings.
   */
  private fun buildJavaExtractionScript(): String = """
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.data.DataUtilities;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class CognotikExtractScript extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length < 1) {
            println("Missing output file argument");
            return;
        }
        File outFile = new File(args[0]);
        int maxFunctions = args.length > 1 ? Integer.parseInt(args[1]) : 25;
        boolean extractStrings = args.length > 2 ? Boolean.parseBoolean(args[2]) : true;
        int minStringLen = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        boolean extractImpExp = args.length > 4 ? Boolean.parseBoolean(args[4]) : true;
        Set<String> targetSet = new HashSet<>();
        if (args.length > 5 && !args[5].trim().isEmpty()) {
            for (String s : args[5].split(",")) {
                if (!s.trim().isEmpty()) targetSet.add(s.trim());
            }
        }

        try (PrintWriter out = new PrintWriter(outFile, "UTF-8")) {
            out.println("# Program Information");
            out.println("- **Name:** `" + currentProgram.getName() + "`");
            out.println("- **Format:** `" + currentProgram.getExecutableFormat() + "`");
            out.println("- **Language:** `" + currentProgram.getLanguageID() + "`");
            out.println("- **Compiler:** `" + currentProgram.getCompilerSpec().getCompilerSpecID() + "`");
            out.println("- **Image Base:** `" + currentProgram.getImageBase() + "`");
            out.println();

            if (extractImpExp) {
                out.println("## Imported Symbols");
                SymbolTable symTab = currentProgram.getSymbolTable();
                SymbolIterator extSyms = symTab.getExternalSymbols();
                int impCount = 0;
                while (extSyms.hasNext() && impCount < 200) {
                    Symbol s = extSyms.next();
                    out.println("- `" + s.getName(true) + "`");
                    impCount++;
                }
                out.println();

                out.println("## Entry Points & Exports");
                SymbolIterator allSyms = symTab.getAllSymbols(false);
                int expCount = 0;
                while (allSyms.hasNext() && expCount < 200) {
                    Symbol s = allSyms.next();
                    if (s.isExternalEntryPoint()) {
                        out.println("- `" + s.getName(true) + "` @ `" + s.getAddress() + "`");
                        expCount++;
                    }
                }
                out.println();
            }

            FunctionManager fm = currentProgram.getFunctionManager();
            List<Function> allFunctions = new ArrayList<>();
            FunctionIterator fit = fm.getFunctions(true);
            while (fit.hasNext()) {
                Function f = fit.next();
                if (!f.isExternal() && !f.isThunk()) {
                    allFunctions.add(f);
                }
            }

            out.println("## Functions (" + allFunctions.size() + " internal non-thunk)");
            int listed = 0;
            for (Function f : allFunctions) {
                if (listed++ >= 200) break;
                out.println("- `" + f.getName() + "` @ `" + f.getEntryPoint() + "` (size: " + f.getBody().getNumAddresses() + ")");
            }
            out.println();

            // Sort by body size descending if no specific targets requested
            List<Function> toDecompile = new ArrayList<>();
            if (!targetSet.isEmpty()) {
                for (Function f : allFunctions) {
                    if (targetSet.contains(f.getName()) || targetSet.contains(f.getEntryPoint().toString())) {
                        toDecompile.add(f);
                    }
                }
            } else {
                allFunctions.sort((a, b) -> Long.compare(b.getBody().getNumAddresses(), a.getBody().getNumAddresses()));
                for (int i = 0; i < Math.min(maxFunctions, allFunctions.size()); i++) {
                    toDecompile.add(allFunctions.get(i));
                }
            }

            if (!toDecompile.isEmpty()) {
                out.println("## Decompiled Functions");
                DecompInterface decomp = new DecompInterface();
                decomp.openProgram(currentProgram);
                try {
                    for (Function f : toDecompile) {
                        if (monitor.isCancelled()) break;
                        DecompileResults res = decomp.decompileFunction(f, 30, monitor);
                        out.println("### `" + f.getName() + "` (`" + f.getEntryPoint() + "`)");
                        if (res != null && res.decompileCompleted() && res.getDecompiledFunction() != null) {
                            out.println("```c");
                            out.println(res.getDecompiledFunction().getC().trim());
                            out.println("```");
                        } else {
                            out.println("_Decompilation unavailable_");
                        }
                        out.println();
                    }
                } finally {
                    decomp.dispose();
                }
            }

            if (extractStrings) {
                out.println("## Defined Strings");
                DataIterator dit = currentProgram.getListing().getDefinedData(true);
                int strCount = 0;
                while (dit.hasNext() && strCount < 300) {
                    Data d = dit.next();
                    if (d.hasStringValue()) {
                        Object val = d.getValue();
                        if (val != null) {
                            String s = val.toString();
                            if (s.length() >= minStringLen) {
                                String clean = s.replace("\n", "\\n").replace("\r", "\\r");
                                if (clean.length() > 200) clean = clean.substring(0, 200) + "...";
                                out.println("- `" + d.getAddress() + "`: `" + clean.replace("`", "'") + "`");
                                strCount++;
                            }
                        }
                    }
                }
                out.println();
            }
        }
    }
}
  """.trimIndent()

  companion object {
    private val log: Logger = LoggerFactory.getLogger(GhidraTask::class.java)

    @JvmStatic
    val Ghidra = TaskType(
      name = "Ghidra",
      category = "Code",
      taskClass = GhidraTask::class.java,
      executionConfigClass = GhidraTaskExecutionConfigData::class.java,
      taskSettingsClass = GhidraTypeConfig::class.java,
      description = "Analyze compiled binaries using Ghidra Headless Analyzer",
      tooltipHtml = """
                        Performs automated binary analysis and decompilation using NSA's Ghidra Headless Analyzer.
                        <ul>
                          <li>Extracts binary format, architecture, and compiler metadata</li>
                          <li>Lists imported and exported symbols and entry points</li>
                          <li>Decompiles target or key functions into readable C code</li>
                          <li>Extracts defined strings and data references</li>
                          <li>Supports custom post-analysis Ghidra scripts (Java/Python)</li>
                          <li>Provides AI-assisted architectural and behavioral summaries</li>
                        </ul>
                      """,
    )
  }
}