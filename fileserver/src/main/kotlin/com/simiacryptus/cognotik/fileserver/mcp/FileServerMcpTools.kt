package com.simiacryptus.cognotik.fileserver.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.fileserver.handler.FileAccessControl
import com.simiacryptus.cognotik.fileserver.handler.FsApiConfig
import com.simiacryptus.cognotik.fileserver.handler.FsErrorCode
import com.simiacryptus.cognotik.fileserver.handler.FsException
import com.simiacryptus.cognotik.fileserver.handler.FsToolsHandler
import com.simiacryptus.cognotik.fileserver.util.FsJson
import com.simiacryptus.cognotik.fileserver.util.FsPath
import com.simiacryptus.cognotik.fileserver.util.FsTarget
import com.simiacryptus.cognotik.platform.model.mcp.MCPCallContext
import com.simiacryptus.cognotik.platform.model.mcp.MCPContent
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolResult
import com.simiacryptus.cognotik.util.jsonCast
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Exposes FileServer filesystem operations and [FsToolsHandler] static-analysis /
 * compression utilities as platform-model [MCPTool] instances.
 *
 * Security and capability invariants:
 *  * All paths resolve through [FsPath.resolve], keeping every access inside [root].
 *  * Hidden files and directories ([FileAccessControl.isHidden]) are invisible and
 *    answer `ENOENT` across every tool.
 *  * Mutating tools (`write_file`, `edit_file`, `index_symbols`, and sidecar-writing
 *    invocations of `describe` / `caveman`) are strictly gated by [FsApiConfig.readOnly],
 *    [isWriteAllowed], and [FileAccessControl.isReadOnly].
 *  * Tool availability is further gated by [FsApiConfig.mcpEnabled],
 *    [FsApiConfig.symbolsEnabled], [FsApiConfig.describeEnabled],
 *    [FsApiConfig.cavemanEnabled], and [FsApiConfig.maxToolInputBytes].
 */
class FileServerMcpTools(
  private val root: File,
  private val config: FsApiConfig = FsApiConfig(),
  private val isWriteAllowed: (MCPCallContext) -> Boolean = { !config.readOnly },
) {
  constructor(
    root: File,
    config: FsApiConfig,
    writeAllowed: Boolean,
  ) : this(root, config, { writeAllowed && !config.readOnly })

  private val log = LoggerFactory.getLogger(FileServerMcpTools::class.java)

  /** Returns the [MCPTool] instances enabled under the current [FsApiConfig]. */
  fun tools(): List<MCPTool> {
    if (!config.mcpEnabled) return emptyList()
    val list = ArrayList<MCPTool>()
    list += readFileTool()
    list += listDirectoryTool()
    list += searchFilesTool()
    if (!config.readOnly) {
      list += writeFileTool()
      list += editFileTool()
    }
    if (config.symbolsEnabled) {
      list += symbolsTool()
      if (!config.readOnly) {
        list += indexSymbolsTool()
      }
    }
    if (config.describeEnabled) {
      list += describeTool()
    }
    if (config.cavemanEnabled) {
      list += cavemanTool()
    }
    return list
  }

  /** Returns the [MCPToolDefinition] metadata for all enabled tools. */
  fun toolDefinitions(): List<MCPToolDefinition> = tools().map { it.definition }

  /** Looks up an enabled tool by name. */
  fun findTool(name: String): MCPTool? = tools().firstOrNull { it.definition.name == name }

  /** Dispatches a named tool invocation, returning an MCP error result if unavailable. */
  fun callTool(
    name: String,
    arguments: Map<String, Any?>,
    context: MCPCallContext,
  ): MCPToolResult {
    if (!config.mcpEnabled) {
      return errorResult("ENOSYS: MCP tool access is disabled for this mount")
    }
    val tool = findTool(name)
      ?: return errorResult("ENOSYS: unknown or disabled tool '$name'")
    return tool.call(arguments.jsonCast(), context)
  }

  // --------------------------------------------------------- filesystem tools

  private fun readFileTool(): MCPTool = mcpTool(
    name = "read_file",
    description = "Read UTF-8 text content from a file within the fileserver root, with optional 1-based line range.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual path of the file to read (e.g. /src/Main.kt)"),
        "startLine" to prop("integer", "Optional 1-based inclusive start line"),
        "endLine" to prop("integer", "Optional 1-based inclusive end line"),
        "maxBytes" to prop("integer", "Optional maximum byte limit (capped by server maxFileSize)"),
      ),
      required = listOf("path"),
    ),
  ) { args, _ ->
    val path = requireString(args, "path", "read_file")
    val target = visible(path, "read_file")
    if (!target.file.exists()) {
      throw FsException(FsErrorCode.ENOENT, "read_file", target.virtual)
    }
    if (target.file.isDirectory) {
      throw FsException(FsErrorCode.EISDIR, "read_file", target.virtual)
    }
    val requestedMaxBytes = optionalLong(args, "maxBytes")
    val limit = if (requestedMaxBytes != null && requestedMaxBytes > 0) {
      minOf(requestedMaxBytes, config.maxFileSize)
    } else {
      config.maxFileSize
    }
    if (target.file.length() > config.maxFileSize) {
      throw FsException(
        FsErrorCode.EFBIG,
        "read_file",
        target.virtual,
        "file size (${target.file.length()}) exceeds maxFileSize (${config.maxFileSize})",
      )
    }

    val startLine = optionalInt(args, "startLine")
    val endLine = optionalInt(args, "endLine")
    if (startLine != null && startLine < 1) {
      throw FsException(FsErrorCode.EINVAL, "read_file", target.virtual, "startLine must be >= 1")
    }
    if (endLine != null && endLine < 1) {
      throw FsException(FsErrorCode.EINVAL, "read_file", target.virtual, "endLine must be >= 1")
    }
    if (startLine != null && endLine != null && endLine < startLine) {
      throw FsException(FsErrorCode.EINVAL, "read_file", target.virtual, "endLine must be >= startLine")
    }

    val content = if (startLine == null && endLine == null) {
      if (target.file.length() > limit) {
        throw FsException(FsErrorCode.EFBIG, "read_file", target.virtual, "file exceeds maxBytes ($limit)")
      }
      target.file.readText(Charsets.UTF_8)
    } else {
      val from = startLine ?: 1
      val to = endLine ?: Int.MAX_VALUE
      val lines = ArrayList<String>()
      var lineNum = 0
      target.file.bufferedReader(Charsets.UTF_8).use { reader ->
        while (true) {
          val line = reader.readLine() ?: break
          lineNum++
          if (lineNum < from) continue
          if (lineNum > to) break
          lines += line
        }
      }
      val joined = lines.joinToString("\n")
      if (joined.toByteArray(Charsets.UTF_8).size > limit) {
        throw FsException(FsErrorCode.EFBIG, "read_file", target.virtual, "selected range exceeds maxBytes ($limit)")
      }
      joined
    }
    textResult(content)
  }

  private fun listDirectoryTool(): MCPTool = mcpTool(
    name = "list_directory",
    description = "List visible files and subdirectories at a virtual path within the fileserver root.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual directory path (default '/')"),
        "recursive" to prop("boolean", "Descend into subdirectories (default false)"),
        "maxDepth" to prop("integer", "Maximum recursion depth (capped by server maxDepth)"),
        "maxEntries" to prop("integer", "Maximum entries to return (capped by server maxDirEntries)"),
      ),
    ),
  ) { args, _ ->
    val path = optionalString(args, "path") ?: "/"
    val target = visible(path, "list_directory")
    if (!target.file.exists()) {
      throw FsException(FsErrorCode.ENOENT, "list_directory", target.virtual)
    }
    if (!target.file.isDirectory) {
      throw FsException(FsErrorCode.EINVAL, "list_directory", target.virtual, "not a directory")
    }
    val recursive = optionalBoolean(args, "recursive", false)
    val maxDepth = (optionalInt(args, "maxDepth") ?: if (recursive) config.maxDepth else 1)
      .coerceIn(1, config.maxDepth.coerceAtLeast(1))
    val maxEntries = (optionalInt(args, "maxEntries") ?: config.maxDirEntries)
      .coerceIn(1, config.maxDirEntries.coerceAtLeast(1))

    val entries = ArrayList<Map<String, Any?>>()
    var truncated = false

    fun walk(dir: File, depth: Int) {
      if (entries.size >= maxEntries) {
        truncated = true
        return
      }
      val children = dir.listFiles()?.sortedBy { it.name } ?: return
      for (child in children) {
        if (entries.size >= maxEntries) {
          truncated = true
          return
        }
        if (FileAccessControl.isHidden(root, child)) continue
        val isDir = child.isDirectory
        entries += linkedMapOf(
          "name" to child.name,
          "path" to virtual(child),
          "type" to if (isDir) "directory" else "file",
          "size" to if (isDir) null else child.length(),
          "readOnly" to FileAccessControl.isReadOnly(root, child),
        )
        if (isDir && recursive && depth < maxDepth) {
          walk(child, depth + 1)
        }
      }
    }

    walk(target.file, 1)
    jsonResult(
      linkedMapOf(
        "path" to target.virtual,
        "count" to entries.size,
        "truncated" to truncated,
        "entries" to entries,
      ),
    )
  }

  private fun searchFilesTool(): MCPTool = mcpTool(
    name = "search_files",
    description = "Search visible files under a virtual path by filename pattern, extension, and/or text/regex content.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Starting virtual directory or file path (default '/')"),
        "query" to prop("string", "Optional text or regex to search for inside file contents"),
        "namePattern" to prop("string", "Optional regex matched against file names"),
        "ext" to prop("string", "Optional comma-separated file extension allow-list (e.g. 'kt,java,md')"),
        "regex" to prop("boolean", "Interpret query as a regular expression (default false)"),
        "caseSensitive" to prop("boolean", "Case-sensitive content matching (default false)"),
        "maxDepth" to prop("integer", "Maximum directory recursion depth"),
        "maxResults" to prop("integer", "Maximum matches to return (default 100)"),
      ),
    ),
  ) { args, _ ->
    val path = optionalString(args, "path") ?: "/"
    val target = visible(path, "search_files")
    if (!target.file.exists()) {
      throw FsException(FsErrorCode.ENOENT, "search_files", target.virtual)
    }
    val query = optionalString(args, "query")
    val isRegex = optionalBoolean(args, "regex", false)
    val caseSensitive = optionalBoolean(args, "caseSensitive", false)
    val nameRegex = optionalString(args, "namePattern")?.let { raw ->
      try {
        Regex(raw)
      } catch (e: Exception) {
        throw FsException(FsErrorCode.EINVAL, "search_files", target.virtual, "invalid namePattern: ${e.message}")
      }
    }
    val queryRegex = if (query != null && isRegex) {
      try {
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        Regex(query, options)
      } catch (e: Exception) {
        throw FsException(FsErrorCode.EINVAL, "search_files", target.virtual, "invalid query regex: ${e.message}")
      }
    } else {
      null
    }
    val extensions = optionalString(args, "ext")
      ?.split(',')
      ?.map { it.trim().removePrefix(".").lowercase() }
      ?.filter { it.isNotEmpty() }
      ?.toSet()
    val maxDepth = (optionalInt(args, "maxDepth") ?: config.maxDepth)
      .coerceIn(1, config.maxDepth.coerceAtLeast(1))
    val maxResults = (optionalInt(args, "maxResults") ?: 100)
      .coerceIn(1, config.maxDirEntries.coerceAtLeast(1))

    val matches = ArrayList<Map<String, Any?>>()
    var truncated = false
    val contentLimit = minOf(config.maxFileSize, config.maxToolInputBytes)

    fun checkFile(file: File) {
      if (matches.size >= maxResults) {
        truncated = true
        return
      }
      if (FileAccessControl.isHidden(root, file)) return
      if (extensions != null && file.extension.lowercase() !in extensions) return
      if (nameRegex != null && !nameRegex.containsMatchIn(file.name)) return

      val fileVirtual = virtual(file)
      if (query == null) {
        matches += linkedMapOf(
          "path" to fileVirtual,
          "size" to file.length(),
        )
        return
      }
      if (file.length() > contentLimit || isProbablyBinary(file)) return
      var lineNum = 0
      file.bufferedReader(Charsets.UTF_8).use { reader ->
        while (true) {
          val line = reader.readLine() ?: break
          lineNum++
          val hit = if (queryRegex != null) {
            queryRegex.containsMatchIn(line)
          } else {
            line.contains(query, ignoreCase = !caseSensitive)
          }
          if (hit) {
            matches += linkedMapOf(
              "path" to fileVirtual,
              "line" to lineNum,
              "text" to if (line.length > 500) line.substring(0, 500) + "..." else line,
            )
            if (matches.size >= maxResults) {
              truncated = true
              return
            }
          }
        }
      }
    }

    fun walk(dir: File, depth: Int) {
      if (truncated || matches.size >= maxResults) {
        truncated = true
        return
      }
      val children = dir.listFiles()?.sortedBy { it.name } ?: return
      for (child in children) {
        if (matches.size >= maxResults) {
          truncated = true
          return
        }
        if (FileAccessControl.isHidden(root, child)) continue
        if (child.isDirectory) {
          if (depth < maxDepth) walk(child, depth + 1)
        } else {
          checkFile(child)
        }
      }
    }

    if (target.file.isDirectory) {
      walk(target.file, 1)
    } else {
      checkFile(target.file)
    }

    jsonResult(
      linkedMapOf(
        "path" to target.virtual,
        "query" to query,
        "count" to matches.size,
        "truncated" to truncated,
        "matches" to matches,
      ),
    )
  }

  private fun writeFileTool(): MCPTool = mcpTool(
    name = "write_file",
    description = "Create or overwrite a UTF-8 text file within the fileserver sandbox.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual path of the file to write"),
        "content" to prop("string", "UTF-8 text content to write"),
        "overwrite" to prop("boolean", "Overwrite existing file (default true)"),
        "createDirs" to prop("boolean", "Create missing parent directories (default true)"),
      ),
      required = listOf("path", "content"),
    ),
  ) { args, context ->
    requireWritable("write_file", context)
    val path = requireString(args, "path", "write_file")
    val content = args["content"] as? String
      ?: throw FsException(FsErrorCode.EINVAL, "write_file", path, "missing string 'content'")
    val overwrite = optionalBoolean(args, "overwrite", true)
    val createDirs = optionalBoolean(args, "createDirs", true)

    val target = visible(path, "write_file")
    if (FileAccessControl.isReadOnly(root, target.file)) {
      throw FsException(FsErrorCode.EACCES, "write_file", target.virtual, "target is read-only")
    }
    val existed = target.file.exists()
    if (existed && target.file.isDirectory) {
      throw FsException(FsErrorCode.EISDIR, "write_file", target.virtual)
    }
    if (existed && !overwrite) {
      throw FsException(FsErrorCode.EEXIST, "write_file", target.virtual, "file exists (set overwrite=true)")
    }
    val bytes = content.toByteArray(Charsets.UTF_8)
    if (bytes.size > config.maxFileSize) {
      throw FsException(
        FsErrorCode.EFBIG,
        "write_file",
        target.virtual,
        "content size (${bytes.size}) exceeds maxFileSize (${config.maxFileSize})",
      )
    }

    val parent = target.file.parentFile
    if (parent != null && !parent.exists()) {
      if (!createDirs) {
        throw FsException(FsErrorCode.ENOENT, "write_file", target.virtual, "parent directory does not exist")
      }
      val ancestor = nearestExistingAncestor(parent)
      if (ancestor != null) {
        if (FileAccessControl.isHidden(root, ancestor)) {
          throw FsException(FsErrorCode.ENOENT, "write_file", target.virtual)
        }
        if (FileAccessControl.isReadOnly(root, ancestor)) {
          throw FsException(FsErrorCode.EACCES, "write_file", target.virtual, "parent directory is read-only")
        }
      }
    }

    atomicWrite(target.file, bytes, "write_file", target.virtual)
    jsonResult(
      linkedMapOf(
        "path" to target.virtual,
        "created" to !existed,
        "bytesWritten" to bytes.size,
      ),
    )
  }

  private fun editFileTool(): MCPTool = mcpTool(
    name = "edit_file",
    description = "Apply one or more exact search-and-replace edits to an existing UTF-8 text file in the sandbox.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual path of the file to edit"),
        "oldText" to prop("string", "Exact text to replace (for a single edit)"),
        "newText" to prop("string", "Replacement text (for a single edit)"),
        "replaceAll" to prop("boolean", "Replace all occurrences instead of requiring a unique match (default false)"),
        "edits" to linkedMapOf(
          "type" to "array",
          "description" to "Optional list of {oldText, newText, replaceAll?} edits applied in order",
          "items" to objectSchema(
            properties = linkedMapOf(
              "oldText" to prop("string", "Exact text to replace"),
              "newText" to prop("string", "Replacement text"),
              "replaceAll" to prop("boolean", "Replace all occurrences (default false)"),
            ),
            required = listOf("oldText", "newText"),
          ),
        ),
      ),
      required = listOf("path"),
    ),
  ) { args, context ->
    requireWritable("edit_file", context)
    val path = requireString(args, "path", "edit_file")
    val target = visible(path, "edit_file")
    if (!target.file.exists()) {
      throw FsException(FsErrorCode.ENOENT, "edit_file", target.virtual)
    }
    if (target.file.isDirectory) {
      throw FsException(FsErrorCode.EISDIR, "edit_file", target.virtual)
    }
    if (FileAccessControl.isReadOnly(root, target.file)) {
      throw FsException(FsErrorCode.EACCES, "edit_file", target.virtual, "target is read-only")
    }
    if (target.file.length() > config.maxFileSize) {
      throw FsException(
        FsErrorCode.EFBIG,
        "edit_file",
        target.virtual,
        "file size (${target.file.length()}) exceeds maxFileSize (${config.maxFileSize})",
      )
    }

    val editList = parseEdits(args, target.virtual)
    var text = target.file.readText(Charsets.UTF_8)
    var totalReplacements = 0

    for ((index, edit) in editList.withIndex()) {
      val count = countOccurrences(text, edit.oldText)
      if (count == 0) {
        throw FsException(
          FsErrorCode.EINVAL,
          "edit_file",
          target.virtual,
          "edit[$index]: oldText not found in file",
        )
      }
      if (count > 1 && !edit.replaceAll) {
        throw FsException(
          FsErrorCode.EINVAL,
          "edit_file",
          target.virtual,
          "edit[$index]: oldText matched $count times; provide more context or set replaceAll=true",
        )
      }
      text = if (edit.replaceAll) {
        totalReplacements += count
        text.replace(edit.oldText, edit.newText)
      } else {
        totalReplacements += 1
        replaceFirstExact(text, edit.oldText, edit.newText)
      }
    }

    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size > config.maxFileSize) {
      throw FsException(
        FsErrorCode.EFBIG,
        "edit_file",
        target.virtual,
        "resulting file size (${bytes.size}) exceeds maxFileSize (${config.maxFileSize})",
      )
    }

    atomicWrite(target.file, bytes, "edit_file", target.virtual)
    jsonResult(
      linkedMapOf(
        "path" to target.virtual,
        "editsApplied" to editList.size,
        "totalReplacements" to totalReplacements,
        "bytesWritten" to bytes.size,
      ),
    )
  }

  // --------------------------------------------------------- FsToolsHandler tools

  private fun symbolsTool(): MCPTool = mcpTool(
    name = "symbols",
    description = "Read the SymbolIndexer sidecar (.data/<name>.json) or folder manifest (.data/package.json) for a path.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual file or folder path (default '/')"),
        "packageManifests" to prop("boolean", "Use per-package manifests (default true)"),
        "errors" to prop("boolean", "Include validation errors (default true)"),
        "references" to prop("boolean", "Include symbol references (default true)"),
        "referenceDetails" to prop("boolean", "Include detailed reference locations (default false)"),
        "resolve" to prop("boolean", "Resolve cross-file references (default true)"),
        "manifestDetails" to prop("boolean", "Include file details in manifest (default false)"),
      ),
    ),
  ) { args, _ ->
    if (!config.symbolsEnabled) {
      throw FsException(FsErrorCode.ENOSYS, "symbols", null, "symbols capability disabled for this mount")
    }
    jsonResult(FsToolsHandler.symbols(root, config, args))
  }

  private fun indexSymbolsTool(): MCPTool = mcpTool(
    name = "index_symbols",
    description = "Build or refresh the SymbolIndexer sidecar index (.data) for a file or subtree.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual file or folder path to index (default '/')"),
        "clean" to prop("boolean", "Clean existing index before rebuilding (default false)"),
        "incremental" to prop("boolean", "Perform incremental indexing (default true)"),
        "parallel" to prop("boolean", "Index files in parallel (default true)"),
        "packageManifests" to prop("boolean", "Write per-package manifests (default true)"),
        "errors" to prop("boolean", "Include validation errors (default true)"),
        "references" to prop("boolean", "Include symbol references (default true)"),
        "referenceDetails" to prop("boolean", "Include detailed reference locations (default false)"),
        "resolve" to prop("boolean", "Resolve cross-file references (default true)"),
        "manifestDetails" to prop("boolean", "Include file details in manifest (default false)"),
      ),
    ),
  ) { args, context ->
    if (!config.symbolsEnabled) {
      throw FsException(FsErrorCode.ENOSYS, "symbols", null, "symbols capability disabled for this mount")
    }
    requireWritable("symbols", context)
    val result = FsToolsHandler.indexSymbols(
      root = root,
      config = config,
      options = args,
      writeAllowed = isWriteAllowed(context),
      readOnly = config.readOnly,
    )
    jsonResult(result)
  }

  private fun describeTool(): MCPTool = mcpTool(
    name = "describe",
    description = "Run JsonSchemaDescriber over inline JSON ('text') or a JSON file/subtree ('path'), writing <name>.describe.md sidecars unless dryRun=true or inline without output.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual file or folder path to describe"),
        "text" to prop("string", "Inline JSON input (bounded by maxToolInputBytes)"),
        "output" to prop("string", "Optional output path when using inline 'text'"),
        "mode" to prop("string", "Description mode: auto | data | schema (default 'auto')"),
        "samples" to prop("boolean", "Include sample values (default false)"),
        "topN" to prop("integer", "Top-N frequent values/types to report (default 5)"),
        "maxDepth" to prop("integer", "Maximum schema/data traversal depth (default 16)"),
        "maxKeys" to prop("integer", "Maximum detailed keys per object (default 24)"),
        "maxValue" to prop("integer", "Maximum characters per inline sample value (default 48)"),
        "recursion" to prop("boolean", "Detect recursive structures (default true)"),
        "summary" to prop("boolean", "Include summary section (default true)"),
        "suffix" to prop("string", "Sidecar output suffix (default '.describe.md')"),
        "recursive" to prop("boolean", "Descend into subdirectories (default true)"),
        "ext" to prop("string", "Comma-separated extension allow-list"),
        "pattern" to prop("string", "Filename regex filter"),
        "maxFiles" to prop("integer", "Maximum files to crawl (default 500)"),
        "overwrite" to prop("boolean", "Overwrite existing sidecars (default true)"),
        "dryRun" to prop("boolean", "Plan/preview without writing files (default false)"),
        "preview" to prop("boolean", "Include generated content in manifest results (default false)"),
      ),
    ),
  ) { args, context ->
    if (!config.describeEnabled) {
      throw FsException(FsErrorCode.ENOSYS, "describe", null, "describe capability disabled for this mount")
    }
    enforceMaxToolInputBytes(args, "describe")
    val result = FsToolsHandler.describe(
      root = root,
      config = config,
      options = args,
      writeAllowed = isWriteAllowed(context),
      readOnly = config.readOnly,
    )
    jsonResult(result)
  }

  private fun cavemanTool(): MCPTool = mcpTool(
    name = "caveman",
    description = "Compress inline text ('text') or text file(s)/subtree ('path') using Caveman, writing <name>.caveman.txt sidecars unless dryRun=true or inline without output.",
    inputSchema = objectSchema(
      properties = linkedMapOf(
        "path" to prop("string", "Virtual file or folder path to compress"),
        "text" to prop("string", "Inline text input (bounded by maxToolInputBytes)"),
        "output" to prop("string", "Optional output path when using inline 'text'"),
        "mode" to prop("string", "Pipeline mode: run | compress (default 'run')"),
        "preset" to prop("string", "Configuration preset: default | keywords | aggressive"),
        "stem" to prop("boolean", "Enable stemming"),
        "stopwords" to prop("boolean", "Enable stopword removal"),
        "stemmer" to prop("string", "Stemmer implementation: porter | light"),
        "salience" to prop("boolean", "Enable salience filtering"),
        "extractor" to prop("string", "Salience extractor: freq | frequency | textrank"),
        "topN" to prop("integer", "Top-N salient terms to retain"),
        "threshold" to prop("number", "Minimum salience score threshold"),
        "pos" to prop("string", "Comma-separated POS categories to keep"),
        "grammar" to prop("string", "Grammar template: default | caveman | terse"),
        "domain" to prop("string", "'distributed' or comma-separated domain terms to preserve"),
        "trace" to prop("boolean", "Include stage decisions and explanation in result (default false)"),
        "suffix" to prop("string", "Sidecar output suffix (default '.caveman.txt')"),
        "recursive" to prop("boolean", "Descend into subdirectories (default true)"),
        "ext" to prop("string", "Comma-separated extension allow-list"),
        "pattern" to prop("string", "Filename regex filter"),
        "maxFiles" to prop("integer", "Maximum files to crawl (default 500)"),
        "overwrite" to prop("boolean", "Overwrite existing sidecars (default true)"),
        "dryRun" to prop("boolean", "Plan/preview without writing files (default false)"),
        "preview" to prop("boolean", "Include generated content in manifest results (default false)"),
      ),
    ),
  ) { args, context ->
    if (!config.cavemanEnabled) {
      throw FsException(FsErrorCode.ENOSYS, "caveman", null, "caveman capability disabled for this mount")
    }
    enforceMaxToolInputBytes(args, "caveman")
    val result = FsToolsHandler.caveman(
      root = root,
      config = config,
      options = args,
      writeAllowed = isWriteAllowed(context),
      readOnly = config.readOnly,
    )
    jsonResult(result)
  }

  // --------------------------------------------------------- helpers

  private data class EditOp(
    val oldText: String,
    val newText: String,
    val replaceAll: Boolean,
  )

  private fun mcpTool(
    name: String,
    description: String,
    inputSchema: Map<String, Any?>,
    handler: (Map<String, Any?>, MCPCallContext) -> MCPToolResult,
  ): MCPTool {
    val def = MCPToolDefinition(
      name = name,
      description = description,
      inputSchema = inputSchema.jsonCast(),
    )
    return object : MCPTool {
      override val definition: MCPToolDefinition = def
      override fun call(
        arguments: JsonNode,
        context: MCPCallContext
      ): MCPToolResult {
        if (!config.mcpEnabled) {
          return errorResult("ENOSYS: MCP tool access is disabled for this mount")
        }
        return try {
          handler(arguments.jsonCast(), context)
        } catch (e: FsException) {
          log.debug("MCP tool '{}' failed ({}): {}", name, e.code, e.message)
          errorResult("${e.code.name}: ${e.message ?: e.code.name}")
        } catch (e: Exception) {
          log.warn("MCP tool '{}' raised unexpected exception", name, e)
          errorResult(e.message ?: e.javaClass.simpleName)
        }
      }
    }
  }

  private fun requireWritable(syscall: String, context: MCPCallContext) {
    if (config.readOnly) {
      throw FsException(FsErrorCode.EROFS, syscall, null, "fileserver is in read-only mode")
    }
    if (!isWriteAllowed(context)) {
      throw FsException(FsErrorCode.EACCES, syscall, null, "write access denied")
    }
  }

  private fun visible(path: String, syscall: String): FsTarget {
    val resolved = FsPath.resolve(root, path, syscall)
    if (FileAccessControl.isHidden(root, resolved.file)) {
      throw FsException(FsErrorCode.ENOENT, syscall, resolved.virtual)
    }
    return resolved
  }

  private fun virtual(file: File): String = try {
    FsPath.virtualPath(root, file)
  } catch (e: Exception) {
    file.name
  }

  private fun enforceMaxToolInputBytes(args: Map<String, Any?>, syscall: String) {
    val inline = args["text"] as? String ?: return
    val limit = minOf(config.maxToolInputBytes, config.maxFileSize)
    val byteLength = inline.toByteArray(Charsets.UTF_8).size
    if (byteLength > limit) {
      throw FsException(
        FsErrorCode.EFBIG,
        syscall,
        null,
        "inline text ($byteLength bytes) exceeds maxToolInputBytes ($limit)",
      )
    }
  }

  private fun parseEdits(args: Map<String, Any?>, virtualPath: String): List<EditOp> {
    val rawEdits = args["edits"] as? List<*>
    if (rawEdits != null) {
      if (rawEdits.isEmpty()) {
        throw FsException(FsErrorCode.EINVAL, "edit_file", virtualPath, "'edits' array must not be empty")
      }
      return rawEdits.mapIndexed { index, item ->
        val map = item as? Map<*, *>
          ?: throw FsException(FsErrorCode.EINVAL, "edit_file", virtualPath, "edits[$index] must be an object")
        val oldText = (map["oldText"] as? String)?.takeIf { it.isNotEmpty() }
          ?: throw FsException(FsErrorCode.EINVAL, "edit_file", virtualPath, "edits[$index].oldText must be non-empty")
        val newText = map["newText"] as? String
          ?: throw FsException(FsErrorCode.EINVAL, "edit_file", virtualPath, "edits[$index].newText must be a string")
        val replaceAll = optionalBoolean(map, "replaceAll", false)
        EditOp(oldText, newText, replaceAll)
      }
    }
    val oldText = (args["oldText"] as? String)?.takeIf { it.isNotEmpty() }
      ?: throw FsException(FsErrorCode.EINVAL, "edit_file", virtualPath, "provide non-empty 'oldText' or 'edits'")
    val newText = args["newText"] as? String
      ?: throw FsException(FsErrorCode.EINVAL, "edit_file", virtualPath, "missing string 'newText'")
    val replaceAll = optionalBoolean(args, "replaceAll", false)
    return listOf(EditOp(oldText, newText, replaceAll))
  }

  private fun countOccurrences(haystack: String, needle: String): Int {
    var count = 0
    var index = 0
    while (true) {
      val found = haystack.indexOf(needle, index)
      if (found < 0) return count
      count++
      index = found + needle.length
    }
  }

  private fun replaceFirstExact(haystack: String, needle: String, replacement: String): String {
    val index = haystack.indexOf(needle)
    if (index < 0) return haystack
    return buildString(haystack.length - needle.length + replacement.length) {
      append(haystack, 0, index)
      append(replacement)
      append(haystack, index + needle.length, haystack.length)
    }
  }

  private fun nearestExistingAncestor(dir: File): File? {
    val rootCanonical = root.canonicalFile
    var cur: File? = dir
    while (cur != null) {
      if (cur.exists()) return cur
      if (cur.canonicalFile == rootCanonical) return cur
      cur = cur.parentFile
    }
    return null
  }

  private fun atomicWrite(file: File, bytes: ByteArray, syscall: String, virtualPath: String) {
    try {
      file.parentFile?.mkdirs()
      val tmp = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
      tmp.writeBytes(bytes)
      try {
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
      } catch (e: Exception) {
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
      }
    } catch (e: Exception) {
      throw FsException(FsErrorCode.EIO, syscall, virtualPath, e.message ?: e.javaClass.simpleName)
    }
  }

  private fun isProbablyBinary(file: File): Boolean = try {
    FileInputStream(file).use { input ->
      val buf = ByteArray(4096)
      val read = input.read(buf)
      for (i in 0 until read) {
        if (buf[i] == 0.toByte()) return@use true
      }
      false
    }
  } catch (_: Exception) {
    true
  }

  private fun requireString(args: Map<String, Any?>, name: String, syscall: String): String =
    optionalString(args, name)
      ?: throw FsException(FsErrorCode.EINVAL, syscall, null, "missing required string '$name'")

  private fun optionalString(args: Map<String, Any?>, name: String): String? =
    args[name]?.toString()?.takeIf { it.isNotBlank() }

  private fun optionalBoolean(args: Map<*, *>, name: String, default: Boolean): Boolean {
    val value = args[name] ?: return default
    if (value is Boolean) return value
    val text = value.toString().trim()
    return text.equals("true", true) || text == "1" || text.equals("yes", true)
  }

  private fun optionalInt(args: Map<String, Any?>, name: String): Int? =
    (args[name] as? Number)?.toInt() ?: args[name]?.toString()?.trim()?.toIntOrNull()

  private fun optionalLong(args: Map<String, Any?>, name: String): Long? =
    (args[name] as? Number)?.toLong() ?: args[name]?.toString()?.trim()?.toLongOrNull()

  private fun textResult(text: String): MCPToolResult =
    MCPToolResult(content = listOf(MCPContent.Text(text = text)), isError = false)

  private fun jsonResult(payload: Any?): MCPToolResult =
    textResult(FsJson.stringify(payload))

  private fun errorResult(message: String): MCPToolResult =
    MCPToolResult(content = listOf(
      MCPContent.Text(text = message)
    ), isError = true)

  private fun objectSchema(
    properties: Map<String, Any?>,
    required: List<String> = emptyList(),
  ): Map<String, Any?> = linkedMapOf<String, Any?>(
    "type" to "object",
    "properties" to properties,
  ).apply {
    if (required.isNotEmpty()) put("required", required)
  }

  private fun prop(type: String, description: String): Map<String, Any?> = linkedMapOf(
    "type" to type,
    "description" to description,
  )
}