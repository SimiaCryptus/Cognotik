package com.simiacryptus.cognotik.text.caveman

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Interactive command-line utility for exploring the Caveman compression
 * pipeline by hand. This is not part of the automated test suite; it is a
 * developer tool colocated with [CavemanPipelineTest] for convenience when
 * debugging tokenization, stopword removal, stemming, POS filtering,
 * salience extraction, or grammar reconstruction behavior.
 *
 * Run it directly (e.g. from an IDE "run" gutter icon on [main]) and type
 * plain sentences to see them pushed through [Caveman.run] using the
 * current configuration. Lines starting with ':' are commands.
 *
 * Commands:
 *   :help                              Show this help text.
 *   :config                            Print the current configuration.
 *   :reset                             Reset configuration to CavemanConfig().
 *   :preset keywords|aggressive|default [topN]
 *                                       Load a named preset configuration.
 *   :mode run|compress                 Choose Caveman.run (default) or
 *                                       Caveman.compress only.
 *   :stem on|off                       Toggle stemming.
 *   :stemmer porter|light              Choose the stemmer implementation.
 *   :stopwords on|off                  Toggle stopword removal.
 *   :salience on|off [topN] [freq|textrank]
 *                                       Configure salience extraction.
 *   :domain add term1,term2,...        Add domain terms to the registry.
 *   :domain distributed                Load the DISTRIBUTED_SYSTEMS registry.
 *   :domain clear                      Clear domain terms.
 *   :pos CATEGORY[,CATEGORY...]        Restrict output to the given POS
 *                                       categories (e.g. NOUN,VERB).
 *   :grammar default|terse             Select the grammar reconstruction
 *                                       engine.
 *   :trace on|off                      Toggle trace/explain output.
 *   :quit / :exit                      Leave the REPL.
 *
 * Any line that does not start with ':' is treated as input text.
 */
class CavemanRepl(
  private val input: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
  private val output: PrintStream = System.out,
) {

  var config: CavemanConfig = CavemanConfig()
    private set

  private var traceEnabled: Boolean = false
  private var mode: String = "run"

  /** Runs the REPL loop until the user quits or input is exhausted. */
  fun run() {
    output.println("Caveman REPL — type :help for commands, :quit to exit.")
    printPrompt()
    var line = input.readLine()
    while (line != null) {
      val shouldContinue = handleLine(line)
      if (!shouldContinue) return
      printPrompt()
      line = input.readLine()
    }
  }

  /**
   * Processes a single line of input. Returns `false` if the REPL should terminate, `true` otherwise. Exposed separately from [run] so the command handling logic can be exercised programmatically without a real stdin/stdout.
   */
  fun handleLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.startsWith(":")) {
      return handleCommand(trimmed.substring(1).trim())
    }
    processText(trimmed)
    return true
  }

  private fun printPrompt() {
    output.print("caveman> ")
    output.flush()
  }

  private fun handleCommand(command: String): Boolean {
    val parts = command.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val name = parts.firstOrNull()?.lowercase() ?: ""
    val args = parts.drop(1)
    when (name) {
      "help", "h", "?" -> printHelp()
      "quit", "exit", "q" -> {
        output.println("bye.")
        return false
      }

      "config" -> output.println(config)
      "reset" -> {
        config = CavemanConfig()
        output.println("Configuration reset to defaults.")
      }

      "preset" -> handlePreset(args)
      "mode" -> handleMode(args)
      "stem" -> handleStem(args)
      "stemmer" -> handleStemmerChoice(args)
      "stopwords" -> handleStopwords(args)
      "salience" -> handleSalience(args)
      "domain" -> handleDomain(args)
      "pos" -> handlePos(args)
      "grammar" -> handleGrammar(args)
      "trace" -> handleTrace(args)
      "" -> output.println("Empty command. Type :help for a list of commands.")
      else -> output.println("Unknown command ':$name'. Type :help for a list of commands.")
    }
    return true
  }

  private fun printHelp() {
    output.println(
      """
        |Commands:
        |  :help                              Show this help text.
        |  :config                            Print the current configuration.
        |  :reset                             Reset configuration to CavemanConfig().
        |  :preset keywords|aggressive|default [topN]
        |                                      Load a named preset configuration.
        |  :mode run|compress                 Choose Caveman.run (default) or
        |                                      Caveman.compress only.
        |  :stem on|off                       Toggle stemming.
        |  :stemmer porter|light              Choose the stemmer implementation.
        |  :stopwords on|off                  Toggle stopword removal.
        |  :salience on|off [topN] [freq|textrank]
        |                                      Configure salience extraction.
        |  :domain add term1,term2,...        Add domain terms to the registry.
        |  :domain distributed                Load the DISTRIBUTED_SYSTEMS registry.
        |  :domain clear                      Clear domain terms.
        |  :pos CATEGORY[,CATEGORY...]        Restrict output to the given POS
        |                                      categories (e.g. NOUN,VERB).
        |  :grammar default|terse             Select the grammar reconstruction
        |                                      engine.
        |  :trace on|off                      Toggle trace/explain output.
        |  :quit / :exit                      Leave the REPL.
        |Any other line is treated as input text and run through the pipeline.
        """.trimMargin()
    )
  }

  private fun handlePreset(args: List<String>) {
    val name = args.firstOrNull()?.lowercase()
    config = when (name) {
      "keywords" -> CavemanConfig.keywordsOnly()
      "aggressive" -> CavemanConfig.aggressive(topN = args.getOrNull(1)?.toIntOrNull() ?: 5)
      "default", null -> CavemanConfig()
      else -> {
        output.println("Unknown preset '$name'. Use one of: keywords, aggressive, default.")
        return
      }
    }
    output.println("Loaded preset '${name ?: "default"}'.")
  }

  private fun handleMode(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "compress" -> {
        mode = "compress"
        output.println("Mode set to 'compress' (Caveman.compress only).")
      }

      "run" -> {
        mode = "run"
        output.println("Mode set to 'run' (full pipeline with intent/template).")
      }

      else -> output.println("Usage: :mode run|compress")
    }
  }

  private fun handleStem(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "on" -> config = config.copy(stemmingEnabled = true)
      "off" -> config = config.copy(stemmingEnabled = false)
      else -> {
        output.println("Usage: :stem on|off")
        return
      }
    }
    output.println("Stemming ${if (config.stemmingEnabled) "enabled" else "disabled"}.")
  }

  private fun handleStemmerChoice(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "porter" -> {
        config = config.copy(stemmer = PorterStemmer())
        output.println("Stemmer set to PorterStemmer.")
      }

      "light" -> {
        config = config.copy(stemmer = LightEnglishStemmer())
        output.println("Stemmer set to LightEnglishStemmer.")
      }

      else -> output.println("Usage: :stemmer porter|light")
    }
  }

  private fun handleStopwords(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "on" -> config = config.copy(stopwordRemovalEnabled = true)
      "off" -> config = config.copy(stopwordRemovalEnabled = false)
      else -> {
        output.println("Usage: :stopwords on|off")
        return
      }
    }
    output.println("Stopword removal ${if (config.stopwordRemovalEnabled) "enabled" else "disabled"}.")
  }

  private fun handleSalience(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "off" -> {
        config = config.copy(salienceEnabled = false)
        output.println("Salience extraction disabled.")
      }

      "on" -> {
        val topN = args.getOrNull(1)?.toIntOrNull() ?: config.salienceTopN
        val extractor = when (args.getOrNull(2)?.lowercase()) {
          "textrank" -> TextRankSalienceExtractor()
          "freq", "frequency", null -> FrequencySalienceExtractor()
          else -> {
            output.println("Unknown extractor '${args[2]}'. Use 'freq' or 'textrank'.")
            return
          }
        }
        config = config.withSalience(topN = topN, extractor = extractor)
        output.println("Salience extraction enabled (topN=$topN, extractor=${extractor::class.simpleName}).")
      }

      else -> output.println("Usage: :salience on|off [topN] [freq|textrank]")
    }
  }

  private fun handleDomain(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "clear" -> {
        config = config.withDomains(DomainTermRegistry.of())
        output.println("Domain term registry cleared.")
      }

      "distributed" -> {
        config = config.withDomains(DomainTermRegistry.DISTRIBUTED_SYSTEMS)
        output.println("Loaded DISTRIBUTED_SYSTEMS domain terms.")
      }

      "add" -> {
        val terms = args.drop(1).joinToString(" ").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) {
          output.println("Usage: :domain add term1,term2,...")
          return
        }
        config = config.withDomains(DomainTermRegistry.of(*terms.toTypedArray()))
        output.println("Added domain terms: ${terms.joinToString(", ")}")
      }

      else -> output.println("Usage: :domain add term1,term2,... | :domain distributed | :domain clear")
    }
  }

  private fun handlePos(args: List<String>) {
    if (args.isEmpty()) {
      output.println("Usage: :pos CATEGORY[,CATEGORY...]  (available: ${PosCategory.values().joinToString(", ")})")
      return
    }
    val names = args.joinToString(" ").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val categories = try {
      names.map { PosCategory.valueOf(it.uppercase()) }
    } catch (ex: IllegalArgumentException) {
      output.println("Unknown POS category. Available: ${PosCategory.values().joinToString(", ")}")
      return
    }
    config = config.withPosFilter(*categories.toTypedArray())
    output.println("POS filter set to: ${categories.joinToString(", ")}")
  }

  private fun handleGrammar(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "default" -> {
        config = config.copy(grammar = CavemanConfig().grammar)
        output.println("Grammar engine reset to default.")
      }

      "terse" -> {
        config = config.copy(grammar = GrammarTemplateEngine.terseImperative())
        output.println("Grammar engine set to terse imperative.")
      }

      else -> output.println("Usage: :grammar default|terse")
    }
  }

  private fun handleTrace(args: List<String>) {
    when (args.firstOrNull()?.lowercase()) {
      "on" -> traceEnabled = true
      "off" -> traceEnabled = false
      else -> {
        output.println("Usage: :trace on|off")
        return
      }
    }
    output.println("Trace output ${if (traceEnabled) "enabled" else "disabled"}.")
  }

  private fun processText(text: String) {
    try {
      if (mode == "compress") {
        val compressed = Caveman.compress(text, config)
        output.println("output : $compressed")
      } else {
        val result = Caveman.run(text, config)
        output.println("output  : ${result.output}")
        output.println("intent  : ${result.intent ?: "<none>"}")
        output.println("template: ${result.template ?: "<none>"}")
        if (traceEnabled) {
          output.println("--- trace ---")
          result.trace.forEach { stage ->
            output.println("[${stage.stage}]")
            stage.decisions.forEach { decision ->
              output.println("  ${decision.token} -> ${decision.action}")
            }
          }
          output.println(result.explain())
          output.println("--- end trace ---")
        }
      }
    } catch (ex: Exception) {
      output.println("error: ${ex.message}")
    }
  }
}

/** Entry point for running the REPL from the command line / IDE. */
fun main() {
  CavemanRepl().run()
}