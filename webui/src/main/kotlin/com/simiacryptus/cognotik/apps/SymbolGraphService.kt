package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.platform.Description
import org.apache.tinkerpop.gremlin.process.traversal.TextP
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONReader
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Description("Service for managing a graph-based representation of code symbols, files, and their relationships. It uses an in-memory TinkerGraph to store vertices representing Files, Symbols, Languages, Libraries, and Packages, and edges representing relationships like DEFINED_IN, REFERENCES, WRITTEN_IN, IN_LIBRARY, and IN_PACKAGE.")
class SymbolGraphService {

  private val graph = TinkerGraph.open()

  companion object {
    private val LANGUAGE_MAP = mapOf(
      "kt" to "Kotlin",
      "java" to "Java",
      "js" to "JavaScript",
      "ts" to "TypeScript",
      "py" to "Python",
      "rb" to "Ruby",
      "go" to "Go",
      "rs" to "Rust",
      "cpp" to "C++",
      "c" to "C",
      "cs" to "C#",
      "class" to "Bytecode"
    )
  }


  @Synchronized
  @Description("Clears the entire symbol graph, removing all vertices and edges. This resets the service to an empty state.")
  fun clear() {
    graph.traversal().V().drop().iterate()
  }

  @Synchronized
  @Description("Adds or updates a file vertex in the graph. If a vertex with the given ID exists, it is reused. Sets the file name and last modified timestamp.")
  fun addFile(id: String, name: String, lastModified: Long) {
    val v = getOrCreateVertex(id, "File")
    v.property(VertexProperty.Cardinality.single, "name", name)
    v.property(VertexProperty.Cardinality.single, "lastModified", lastModified)
  }

  @Synchronized
  @Description("Adds or updates a symbol vertex in the graph. Creates relationships to the containing file, language, library, and package based on the file path and extension. Stores symbol properties like name, location (offsets/line), visibility, modifiers, and annotations.")
  fun addSymbol(
    id: String,
    name: String,
    fileId: String,
    startOffset: Int? = null,
    endOffset: Int? = null,
    line: Int? = null,
    visibility: String? = null,
    modifiers: String? = null,
    annotations: String? = null,
    lastModified: Long? = null,
    nodeType: String? = null,
    parentSymbolId: String? = null
  ) {
    val v = getOrCreateVertex(id, "Symbol")
    v.property(VertexProperty.Cardinality.single, "name", name)
    v.property(VertexProperty.Cardinality.single, "file", fileId)
    startOffset?.let { v.property(VertexProperty.Cardinality.single, "startOffset", it) }
    endOffset?.let { v.property(VertexProperty.Cardinality.single, "endOffset", it) }
    line?.let { v.property(VertexProperty.Cardinality.single, "line", it) }
    visibility?.let { v.property(VertexProperty.Cardinality.single, "visibility", it) }
    modifiers?.let { v.property(VertexProperty.Cardinality.single, "modifiers", it) }
    annotations?.let { v.property(VertexProperty.Cardinality.single, "annotations", it) }
    lastModified?.let { v.property(VertexProperty.Cardinality.single, "lastModified", it) }
    nodeType?.let { v.property(VertexProperty.Cardinality.single, "nodeType", it) }

    val fileV = getOrCreateVertex(fileId, "File")
    fileV.property(VertexProperty.Cardinality.single, "name", fileId.substringAfterLast('/'))
    addEdgeIfNotExists(v, "DEFINED_IN", fileV)
    // Add CONTAINS relationship from parent symbol
    if (parentSymbolId != null) {
      val parentIter = graph.vertices(parentSymbolId)
      if (parentIter.hasNext()) {
        val parentV = parentIter.next()
        addEdgeIfNotExists(parentV, "CONTAINS", v)
      }
    }

    val extension = fileId.substringAfterLast('.', "").lowercase()
    val language = LANGUAGE_MAP[extension] ?: extension.ifEmpty { null }

    if (language?.isNotEmpty() == true) {
      val langV = getOrCreateVertex(language, "Language")
      langV.property(VertexProperty.Cardinality.single, "name", language)
      addEdgeIfNotExists(v, "WRITTEN_IN", langV)
    }

    val (libraryName, packageName) = extractLibraryAndPackage(fileId)

    if (libraryName != null) {
      val libV = getOrCreateVertex(libraryName, "Library")
      libV.property(VertexProperty.Cardinality.single, "name", libraryName)
      addEdgeIfNotExists(v, "IN_LIBRARY", libV)
    }
    if (packageName != null) {
      val pkgV = getOrCreateVertex(packageName, "Package")
      pkgV.property(VertexProperty.Cardinality.single, "name", packageName)
      addEdgeIfNotExists(v, "IN_PACKAGE", pkgV)
    }
  }

  private fun extractLibraryAndPackage(filePath: String): Pair<String?, String?> {
    return when {
      filePath.contains(".jar!/") -> {
        val jarParts = filePath.split("!/", limit = 2)
        val libraryName = jarParts[0].substringAfterLast('/')
        val packageName = jarParts.getOrNull(1)?.substringBeforeLast('/')?.replace('/', '.')
        libraryName to packageName
      }

      filePath.contains("/src/") -> {
        val srcParts = filePath.split("/src/", limit = 2)
        val libraryName = srcParts[0].substringAfterLast('/')
        val pathAfterSrc = srcParts.getOrNull(1) ?: ""
        val cleanPath = pathAfterSrc
          .replaceFirst(Regex("^(main|test)/(kotlin|java|scala|groovy)/"), "")
        val packageName = if (cleanPath.contains('/')) {
          cleanPath.substringBeforeLast('/').replace('/', '.')
        } else null
        libraryName to packageName
      }

      else -> null to null
    }
  }

  private fun addEdgeIfNotExists(from: Vertex, label: String, to: Vertex) {
    if (!from.vertices(Direction.OUT, label).asSequence().any { it.id() == to.id() }) {
      from.addEdge(label, to)
    }
  }


  @Synchronized
  @Description("Retrieves the last modified timestamp for a given file ID from the graph. Returns null if the file is not found or has no timestamp.")
  fun getLastModified(fileId: String): Long? {
    val iter = graph.vertices(fileId)
    if (iter.hasNext()) {
      val v = iter.next()
      val prop = v.property<Long>("lastModified")
      if (prop.isPresent) return prop.value()
    }
    return null
  }

  @Synchronized
  @Description("Returns a set of all file IDs currently present in the graph.")
  fun listFileIds(): Set<String> {
    return graph.traversal().V().hasLabel("File")
      .id()
      .toList()
      .map { it as String }
      .toSet()
  }

  @Synchronized
  @Description("Removes a file and all symbols defined in it from the graph. Also removes the file vertex itself.")
  fun removeFile(id: String) {
    graph.traversal().V().has("Symbol", "file", id).drop().iterate()
    graph.traversal().V(id).drop().iterate()
  }

  @Synchronized
  @Description("Removes all outgoing 'REFERENCES' edges from symbols defined in the specified file. This is typically done before re-analyzing a file to clear stale references.")
  fun clearOutgoingReferences(fileId: String) {
    graph.traversal().V().has("Symbol", "file", fileId).outE("REFERENCES").drop().iterate()
  }

  @Synchronized
  @Description("Removes symbol vertices associated with a file that are not present in the provided set of kept symbol IDs. This cleans up symbols that no longer exist in the file after an update.")
  fun pruneRemovedSymbols(fileId: String, keptSymbolIds: Set<String>) {
    graph.traversal().V()
      .has("Symbol", "file", fileId)
      .filter { !keptSymbolIds.contains(it.get().id()) }
      .drop()
      .iterate()
  }

  @Synchronized
  @Description("Adds a 'REFERENCES' edge from a source symbol to a target symbol. If the target symbol vertex does not exist, it is created with basic information (name, file).")
  fun addReference(
    sourceId: String,
    targetId: String,
    targetName: String,
    targetFile: String,
    referenceFile: String? = null,
    referenceLine: Int? = null,
    referenceStartOffset: Int? = null,
    referenceEndOffset: Int? = null
  ) {
    val sourceIter = graph.vertices(sourceId)
    if (!sourceIter.hasNext()) return

    val sourceV = sourceIter.next()
    val targetV = getOrCreateVertex(targetId, "Symbol")
    if (!targetV.properties<Any>("name").hasNext()) {
      targetV.property(VertexProperty.Cardinality.single, "name", targetName)
      targetV.property(VertexProperty.Cardinality.single, "file", targetFile)
    }
    // Create edge with reference location properties
    val existingEdge = sourceV.edges(Direction.OUT, "REFERENCES").asSequence()
      .find {
        it.inVertex().id() == targetV.id() &&
            it.property<Int>("line").orElse(null) == referenceLine &&
            it.property<Int>("startOffset").orElse(null) == referenceStartOffset
      }
    if (existingEdge == null) {
      val edge = sourceV.addEdge("REFERENCES", targetV)
      referenceFile?.let { edge.property("file", it) }
      referenceLine?.let { edge.property("line", it) }
      referenceStartOffset?.let { edge.property("startOffset", it) }
      referenceEndOffset?.let { edge.property("endOffset", it) }
    }
  }

  @Synchronized
  @Description("Retrieves a Symbol object wrapper for the vertex with the specified ID. Returns null if no such vertex exists.")
  fun getSymbol(id: String): Symbol? {
    val iter = graph.vertices(id)
    return if (iter.hasNext()) Symbol(iter.next(), graph) else null
  }

  @Synchronized
  @Description("Searches for symbols whose names contain the given query string. Returns a list of matching Symbol objects.")
  fun search(query: String, limit: Int = 100): List<Symbol> {
    return graph.traversal().V().hasLabel("Symbol")
      .has("name", TextP.containing(query))
      .limit(limit.toLong())
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Retrieves all symbols defined in the specified file.")
  fun getSymbolsByFile(fileId: String): List<Symbol> {
    return graph.traversal().V()
      .has("Symbol", "file", fileId)
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Retrieves all file vertices in the graph as Symbol objects.")
  fun getFiles(): List<Symbol> {
    return graph.traversal().V()
      .hasLabel("File")
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Retrieves all symbols written in the specified language.")
  fun getSymbolsByLanguage(language: String): List<Symbol> {
    return graph.traversal().V()
      .has("Language", "name", language)
      .`in`("WRITTEN_IN")
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Retrieves all symbols belonging to the specified library.")
  fun getSymbolsByLibrary(library: String): List<Symbol> {
    return graph.traversal().V()
      .has("Library", "name", library)
      .`in`("IN_LIBRARY")
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Retrieves all symbols belonging to the specified package.")
  fun getSymbolsByPackage(pkg: String): List<Symbol> {
    return graph.traversal().V()
      .has("Package", "name", pkg)
      .`in`("IN_PACKAGE")
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Lists the names of all languages present in the graph.")
  fun listLanguages(): List<String> {
    return graph.traversal().V()
      .hasLabel("Language")
      .values<String>("name")
      .toList()
      .sorted()
  }

  @Synchronized
  @Description("Lists the names of all libraries present in the graph.")
  fun listLibraries(): List<String> {
    return graph.traversal().V()
      .hasLabel("Library")
      .values<String>("name")
      .toList()
      .sorted()
  }

  @Synchronized
  @Description("Lists the names of all packages present in the graph.")
  fun listPackages(): List<String> {
    return graph.traversal().V()
      .hasLabel("Package")
      .values<String>("name")
      .toList()
      .sorted()
  }

  @Synchronized
  @Description("Lists all distinct node types present in the graph.")
  fun listNodeTypes(): List<String> {
    return graph.traversal().V()
      .hasLabel("Symbol")
      .values<String>("nodeType")
      .toList()
      .distinct()
      .sorted()
  }

  @Synchronized
  @Description("Retrieves all symbols of a specific node type.")
  fun getSymbolsByNodeType(nodeType: String): List<Symbol> {
    return graph.traversal().V()
      .has("Symbol", "nodeType", nodeType)
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Returns files that have at least one symbol defined in them.")
  fun getFilesWithSymbols(): List<Symbol> {
    return graph.traversal().V()
      .hasLabel("File")
      .filter { it.get().vertices(Direction.IN, "DEFINED_IN").hasNext() }
      .toList()
      .map { Symbol(it, graph) }
  }

  @Synchronized
  @Description("Returns a hierarchical structure of folders containing files with symbols.")
  fun getFolderHierarchy(): Map<String, Any> {
    val files = getFilesWithSymbols().mapNotNull { it.id }
    return buildFolderTree(files)
  }

  private fun buildFolderTree(filePaths: List<String>): Map<String, Any> {
    val root = mutableMapOf<String, Any>()
    for (path in filePaths) {
      val parts = path.split("/").filter { it.isNotEmpty() }
      var current = root
      for ((index, part) in parts.withIndex()) {
        if (index == parts.size - 1) {
          // This is a file
          @Suppress("UNCHECKED_CAST")
          val files = current.getOrPut("_files") { mutableListOf<String>() } as MutableList<String>
          files.add(path)
        } else {
          // This is a folder
          @Suppress("UNCHECKED_CAST")
          current = current.getOrPut(part) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        }
      }
    }
    return root
  }

  @Synchronized
  @Description("Returns a hierarchical structure of packages.")
  fun getPackageHierarchy(): Map<String, Any> {
    val packages = listPackages()
    return buildPackageTree(packages)
  }

  private fun buildPackageTree(packages: List<String>): Map<String, Any> {
    val root = mutableMapOf<String, Any>()
    for (pkg in packages) {
      val parts = pkg.split(".")
      var current = root
      for ((index, part) in parts.withIndex()) {
        if (index == parts.size - 1) {
          // Mark as a leaf package
          @Suppress("UNCHECKED_CAST")
          val leaves = current.getOrPut("_packages") { mutableListOf<String>() } as MutableList<String>
          leaves.add(pkg)
        }
        @Suppress("UNCHECKED_CAST")
        current = current.getOrPut(part) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
      }
    }
    return root
  }


  @Synchronized
  @Description("Saves the current graph to a file in GraphSON format.")
  fun save(path: String) {
    FileOutputStream(path).use { os ->
      GraphSONWriter.build().create().writeGraph(os, graph)
    }
  }

  @Synchronized
  @Description("Loads the graph from a file in GraphSON format, merging it into the current graph.")
  fun load(path: File) {
    if (!path.exists()) {
      throw IllegalArgumentException("Graph file does not exist: ${path.absolutePath}")
    }
    FileInputStream(path).use { `is` ->
      GraphSONReader.build().create().readGraph(`is`, graph)
    }
  }

  @Synchronized
  @Description("Lists all symbol vertices in the graph.")
  fun listSymbols(): List<Symbol> {
    return graph.traversal().V()
      .hasLabel("Symbol")
      .toList()
      .map { Symbol(it, graph) }
  }

  @Description("Represents a node in the symbol graph, which can be a Symbol, File, Language, Library, or Package. Provides access to properties and related nodes.")
  data class Symbol(private val vertex: Vertex, private val graph: TinkerGraph? = null) {
    @get:Description("The unique identifier of the node.")
    val id: String = vertex.id() as String

    @get:Description("The name of the symbol or entity.")
    val name: String? get() = getProperty("name")

    @get:Description("The ID of the file containing this symbol.")
    val fileId: String? get() = getProperty("file")

    @get:Description("The start character offset of the symbol definition.")
    val startOffset: Int? get() = getProperty("startOffset")

    @get:Description("The end character offset of the symbol definition.")
    val endOffset: Int? get() = getProperty("endOffset")

    @get:Description("The line number where the symbol is defined.")
    val line: Int? get() = getProperty("line")

    @get:Description("The visibility modifier of the symbol (e.g., public, private).")
    val visibility: String? get() = getProperty("visibility")

    @get:Description("Other modifiers associated with the symbol (e.g., static, final).")
    val modifiers: String? get() = getProperty("modifiers")

    @get:Description("Annotations present on the symbol.")
    val annotations: String? get() = getProperty("annotations")

    @get:Description("The timestamp when the symbol was last modified.")
    val lastModified: Long? get() = getProperty("lastModified")

    @get:Description("The grammatical node type of the symbol (e.g., class, function, field).")
    val nodeType: String? get() = getProperty("nodeType")

    @get:Description("A map of all properties stored on the vertex.")
    val properties: Map<String, Any>
      get() {
        val map = mutableMapOf<String, Any>()
        map["id"] = id
        vertex.properties<Any>().forEachRemaining { p -> map[p.key()] = p.value() }
        return map
      }

    private inline fun <reified T> getProperty(key: String): T? {
      val p = vertex.property<T>(key)
      return if (p.isPresent) p.value() else null
    }

    @Description("Returns a list of symbols referenced by this symbol.")
    fun references(): List<Reference> {
      return vertex.edges(Direction.OUT, "REFERENCES")
        .asSequence()
        .map { edge ->
          Reference(
            targetSymbol = Symbol(edge.inVertex(), graph),
            file = edge.property<String>("file").orElse(null),
            line = edge.property<Int>("line").orElse(null),
            startOffset = edge.property<Int>("startOffset").orElse(null),
            endOffset = edge.property<Int>("endOffset").orElse(null)
          )
        }
        .toList()
    }

    @Description("Returns a list of symbols that reference this symbol.")
    fun referencedBy(): List<Reference> {
      return vertex.edges(Direction.IN, "REFERENCES")
        .asSequence()
        .map { edge ->
          Reference(
            targetSymbol = Symbol(edge.outVertex(), graph),
            file = edge.property<String>("file").orElse(null),
            line = edge.property<Int>("line").orElse(null),
            startOffset = edge.property<Int>("startOffset").orElse(null),
            endOffset = edge.property<Int>("endOffset").orElse(null)
          )
        }
        .toList()
    }

    @Description("Returns the file symbol where this symbol is defined.")
    fun file(): Symbol? {
      val iter = vertex.vertices(Direction.OUT, "DEFINED_IN")
      return if (iter.hasNext()) Symbol(iter.next(), graph) else null
    }

    @Description("Returns the name of the language this symbol is written in.")
    fun language(): String? =
      vertex.vertices(Direction.OUT, "WRITTEN_IN")
        .asSequence()
        .firstOrNull()
        ?.property<String>("name")
        ?.orElse(null)

    @Description("Returns the name of the package this symbol belongs to.")
    fun packageName(): String? =
      vertex.vertices(Direction.OUT, "IN_PACKAGE")
        .asSequence()
        .firstOrNull()
        ?.property<String>("name")
        ?.orElse(null)

    @Description("Returns the name of the library this symbol belongs to.")
    fun libraryName(): String? =
      vertex.vertices(Direction.OUT, "IN_LIBRARY")
        .asSequence()
        .firstOrNull()
        ?.property<String>("name")
        ?.orElse(null)

    @Description("Returns a list of symbols contained within this symbol (e.g., methods in a class).")
    fun contains(): List<Symbol> {
      return vertex.vertices(Direction.OUT, "CONTAINS")
        .asSequence()
        .map { Symbol(it, graph) }
        .toList()
    }

    @Description("Returns the parent symbol that contains this symbol.")
    fun containedBy(): Symbol? {
      val iter = vertex.vertices(Direction.IN, "CONTAINS")
      return if (iter.hasNext()) Symbol(iter.next(), graph) else null
    }
  }

  @Synchronized
  @Description("Returns all symbols transitively referenced by the given symbol, up to a maximum limit. Includes the original symbol and all symbols it references, and all symbols those reference, etc.")
  fun getTransitiveReferences(symbolId: String, maxResults: Int = 1000): TransitiveResult {
    val visited = mutableSetOf<String>()
    val result = mutableListOf<Symbol>()
    val queue = ArrayDeque<Pair<String, Int>>() // symbol id, depth
    queue.add(symbolId to 0)
    visited.add(symbolId)
    var maxDepthReached = 0
    var truncated = false
    while (queue.isNotEmpty() && result.size < maxResults) {
      val (currentId, depth) = queue.removeFirst()
      maxDepthReached = maxOf(maxDepthReached, depth)
      val symbol = getSymbol(currentId)
      if (symbol != null) {
        result.add(symbol)
        for (ref in symbol.references()) {
          val refId = ref.targetSymbol.id
          if (refId !in visited) {
            visited.add(refId)
            queue.add(refId to depth + 1)
          }
        }
      }
    }
    if (queue.isNotEmpty()) {
      truncated = true
    }
    return TransitiveResult(
      symbols = result,
      totalFound = result.size,
      maxDepth = maxDepthReached,
      truncated = truncated,
      remainingInQueue = queue.size
    )
  }

  @Synchronized
  @Description("Returns all symbols that transitively reference the given symbol, up to a maximum limit. Includes the original symbol and all symbols that reference it, and all symbols that reference those, etc.")
  fun getTransitiveReferencedBy(symbolId: String, maxResults: Int = 1000): TransitiveResult {
    val visited = mutableSetOf<String>()
    val result = mutableListOf<Symbol>()
    val queue = ArrayDeque<Pair<String, Int>>() // symbol id, depth
    queue.add(symbolId to 0)
    visited.add(symbolId)
    var maxDepthReached = 0
    var truncated = false
    while (queue.isNotEmpty() && result.size < maxResults) {
      val (currentId, depth) = queue.removeFirst()
      maxDepthReached = maxOf(maxDepthReached, depth)
      val symbol = getSymbol(currentId)
      if (symbol != null) {
        result.add(symbol)
        for (ref in symbol.referencedBy()) {
          val refId = ref.targetSymbol.id
          if (refId !in visited) {
            visited.add(refId)
            queue.add(refId to depth + 1)
          }
        }
      }
    }
    if (queue.isNotEmpty()) {
      truncated = true
    }
    return TransitiveResult(
      symbols = result,
      totalFound = result.size,
      maxDepth = maxDepthReached,
      truncated = truncated,
      remainingInQueue = queue.size
    )
  }

  @Description("Result of a transitive reference query.")
  data class TransitiveResult(
    val symbols: List<Symbol>,
    val totalFound: Int,
    val maxDepth: Int,
    val truncated: Boolean,
    val remainingInQueue: Int
  )

  private fun getOrCreateVertex(id: String, label: String): Vertex {
    val iter = graph.vertices(id)
    return if (iter.hasNext()) iter.next() else graph.addVertex(T.label, label, T.id, id)
  }

  @Description("Represents a reference edge with location information.")
  data class Reference(
    val targetSymbol: Symbol,
    val file: String?,
    val line: Int?,
    val startOffset: Int?,
    val endOffset: Int?
  ) {
    val properties: Map<String, Any>
      get() {
        val map = mutableMapOf<String, Any>()
        map["target"] = targetSymbol.properties
        file?.let { map["file"] = it }
        line?.let { map["line"] = it }
        startOffset?.let { map["startOffset"] = it }
        endOffset?.let { map["endOffset"] = it }
        return map
      }
  }

  // ==================== Report Generation ====================
  @Synchronized
  @Description("Generates markdown reports with Mermaid diagrams documenting package and file dependencies.")
  fun generateReports(outputDir: File) {
    outputDir.mkdirs()
    // Generate index/overview report
    generateOverviewReport(File(outputDir, "00_overview.md"))
    // Generate package dependency report
    generatePackageDependencyReport(File(outputDir, "01_package_dependencies.md"))
    // Generate file dependency reports by package
    val packages = listPackages()
    packages.forEachIndexed { index, pkg ->
      val safeFileName = pkg.replace(".", "_").take(100)
      generateFileDependencyReport(pkg, File(outputDir, "pkg_${String.format("%03d", index)}_$safeFileName.md"))
    }
    // Generate language statistics report
    generateLanguageReport(File(outputDir, "02_languages.md"))
    // Generate library dependencies report
    generateLibraryReport(File(outputDir, "03_libraries.md"))
  }

  private fun generateOverviewReport(file: File) {
    val sb = StringBuilder()
    sb.appendLine("# Symbol Graph Overview")
    sb.appendLine()
    sb.appendLine(
      "Generated: ${
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
          Instant.now().atZone(ZoneId.systemDefault())
        )
      }"
    )
    sb.appendLine()
    sb.appendLine("## Statistics")
    sb.appendLine()
    sb.appendLine("| Metric | Count |")
    sb.appendLine("|--------|-------|")
    sb.appendLine("| Files | ${getFiles().size} |")
    sb.appendLine("| Symbols | ${listSymbols().size} |")
    sb.appendLine("| Packages | ${listPackages().size} |")
    sb.appendLine("| Libraries | ${listLibraries().size} |")
    sb.appendLine("| Languages | ${listLanguages().size} |")
    sb.appendLine()
    sb.appendLine("## Node Types")
    sb.appendLine()
    sb.appendLine("| Type | Count |")
    sb.appendLine("|------|-------|")
    listNodeTypes().forEach { nodeType ->
      val count = getSymbolsByNodeType(nodeType).size
      sb.appendLine("| $nodeType | $count |")
    }
    sb.appendLine()
    sb.appendLine("## Report Index")
    sb.appendLine()
    sb.appendLine("- [Package Dependencies](01_package_dependencies.md)")
    sb.appendLine("- [Languages](02_languages.md)")
    sb.appendLine("- [Libraries](03_libraries.md)")
    sb.appendLine()
    sb.appendLine("### Package Reports")
    sb.appendLine()
    listPackages().forEachIndexed { index, pkg ->
      val safeFileName = pkg.replace(".", "_").take(100)
      sb.appendLine("- [$pkg](pkg_${String.format("%03d", index)}_$safeFileName.md)")
    }
    file.writeText(sb.toString())
  }

  private fun generatePackageDependencyReport(file: File) {
    val sb = StringBuilder()
    sb.appendLine("# Package Dependencies")
    sb.appendLine()
    sb.appendLine("[← Back to Overview](00_overview.md)")
    sb.appendLine()
    // Build package dependency map
    val packageDeps = mutableMapOf<String, MutableSet<String>>()
    val packages = listPackages().toSet()
    for (pkg in packages) {
      packageDeps[pkg] = mutableSetOf()
      val symbols = getSymbolsByPackage(pkg)
      for (symbol in symbols) {
        for (ref in symbol.references()) {
          val targetPkg = ref.targetSymbol.packageName()
          if (targetPkg != null && targetPkg != pkg && targetPkg in packages) {
            packageDeps[pkg]!!.add(targetPkg)
          }
        }
      }
    }
    // Generate Mermaid diagram
    sb.appendLine("## Dependency Graph")
    sb.appendLine()
    sb.appendLine("```mermaid")
    sb.appendLine("graph LR")
    // Create short IDs for packages to keep diagram readable
    val packageIds = packages.mapIndexed { index, pkg -> pkg to "P$index" }.toMap()
    // Add nodes with labels
    packages.forEach { pkg ->
      val shortName = pkg.substringAfterLast(".")
      sb.appendLine("    ${packageIds[pkg]}[\"$shortName\"]")
    }
    // Add edges
    packageDeps.forEach { (from, tos) ->
      tos.forEach { to ->
        sb.appendLine("    ${packageIds[from]} --> ${packageIds[to]}")
      }
    }
    sb.appendLine("```")
    sb.appendLine()
    // Add legend
    sb.appendLine("### Package Legend")
    sb.appendLine()
    sb.appendLine("| ID | Package |")
    sb.appendLine("|----|---------|")
    packageIds.forEach { (pkg, id) ->
      sb.appendLine("| $id | $pkg |")
    }
    sb.appendLine()
    // Dependency table
    sb.appendLine("## Dependency Table")
    sb.appendLine()
    sb.appendLine("| Package | Dependencies | Dependents |")
    sb.appendLine("|---------|--------------|------------|")
    val dependents = mutableMapOf<String, MutableSet<String>>()
    packageDeps.forEach { (from, tos) ->
      tos.forEach { to ->
        dependents.getOrPut(to) { mutableSetOf() }.add(from)
      }
    }
    packages.sorted().forEach { pkg ->
      val deps = packageDeps[pkg]?.size ?: 0
      val depBy = dependents[pkg]?.size ?: 0
      sb.appendLine("| $pkg | $deps | $depBy |")
    }
    file.writeText(sb.toString())
  }

  private fun generateFileDependencyReport(packageName: String, file: File) {
    val sb = StringBuilder()
    sb.appendLine("# Package: $packageName")
    sb.appendLine()
    sb.appendLine("[← Back to Overview](00_overview.md) | [Package Dependencies](01_package_dependencies.md)")
    sb.appendLine()
    val symbols = getSymbolsByPackage(packageName)
    val filesInPackage = symbols.mapNotNull { it.fileId }.toSet()
    sb.appendLine("## Statistics")
    sb.appendLine()
    sb.appendLine("| Metric | Count |")
    sb.appendLine("|--------|-------|")
    sb.appendLine("| Files | ${filesInPackage.size} |")
    sb.appendLine("| Symbols | ${symbols.size} |")
    sb.appendLine()
    // Build file dependency map
    val fileDeps = mutableMapOf<String, MutableSet<String>>()
    val externalDeps = mutableMapOf<String, MutableSet<String>>()
    for (fileId in filesInPackage) {
      fileDeps[fileId] = mutableSetOf()
      externalDeps[fileId] = mutableSetOf()
      val fileSymbols = getSymbolsByFile(fileId)
      for (symbol in fileSymbols) {
        for (ref in symbol.references()) {
          val targetFile = ref.targetSymbol.fileId
          if (targetFile != null && targetFile != fileId) {
            if (targetFile in filesInPackage) {
              fileDeps[fileId]!!.add(targetFile)
            } else {
              externalDeps[fileId]!!.add(targetFile)
            }
          }
        }
      }
    }
    // Generate Mermaid diagram for internal dependencies
    if (filesInPackage.isNotEmpty()) {
      sb.appendLine("## Internal File Dependencies")
      sb.appendLine()
      sb.appendLine("```mermaid")
      sb.appendLine("graph TD")
      val fileIds = filesInPackage.mapIndexed { index, f -> f to "F$index" }.toMap()
      filesInPackage.forEach { f ->
        val shortName = f.substringAfterLast("/").take(30)
        sb.appendLine("    ${fileIds[f]}[\"$shortName\"]")
      }
      fileDeps.forEach { (from, tos) ->
        tos.forEach { to ->
          sb.appendLine("    ${fileIds[from]} --> ${fileIds[to]}")
        }
      }
      sb.appendLine("```")
      sb.appendLine()
      // File legend
      sb.appendLine("### File Legend")
      sb.appendLine()
      sb.appendLine("| ID | File |")
      sb.appendLine("|----|------|")
      fileIds.forEach { (f, id) ->
        sb.appendLine("| $id | ${f.substringAfterLast("/")} |")
      }
      sb.appendLine()
    }
    // Files table
    sb.appendLine("## Files")
    sb.appendLine()
    sb.appendLine("| File | Symbols | Internal Deps | External Deps |")
    sb.appendLine("|------|---------|---------------|---------------|")
    filesInPackage.sorted().forEach { f ->
      val symbolCount = getSymbolsByFile(f).size
      val intDeps = fileDeps[f]?.size ?: 0
      val extDeps = externalDeps[f]?.size ?: 0
      val shortName = f.substringAfterLast("/")
      sb.appendLine("| $shortName | $symbolCount | $intDeps | $extDeps |")
    }
    sb.appendLine()
    // Symbols table
    sb.appendLine("## Symbols")
    sb.appendLine()
    sb.appendLine("| Symbol | Type | File | Line | References | Referenced By |")
    sb.appendLine("|--------|------|------|------|------------|---------------|")
    symbols.sortedBy { it.name }.forEach { symbol ->
      val refs = symbol.references().size
      val refBy = symbol.referencedBy().size
      val fileName = symbol.fileId?.substringAfterLast("/") ?: "-"
      sb.appendLine("| ${symbol.name ?: "-"} | ${symbol.nodeType ?: "-"} | $fileName | ${symbol.line ?: "-"} | $refs | $refBy |")
    }
    file.writeText(sb.toString())
  }

  private fun generateLanguageReport(file: File) {
    val sb = StringBuilder()
    sb.appendLine("# Languages")
    sb.appendLine()
    sb.appendLine("[← Back to Overview](00_overview.md)")
    sb.appendLine()
    sb.appendLine("## Language Distribution")
    sb.appendLine()
    sb.appendLine("```mermaid")
    sb.appendLine("pie title Symbol Distribution by Language")
    listLanguages().forEach { lang ->
      val count = getSymbolsByLanguage(lang).size
      if (count > 0) {
        sb.appendLine("    \"$lang\" : $count")
      }
    }
    sb.appendLine("```")
    sb.appendLine()
    sb.appendLine("## Details")
    sb.appendLine()
    sb.appendLine("| Language | Symbols | Files |")
    sb.appendLine("|----------|---------|-------|")
    listLanguages().forEach { lang ->
      val symbols = getSymbolsByLanguage(lang)
      val files = symbols.mapNotNull { it.fileId }.toSet().size
      sb.appendLine("| $lang | ${symbols.size} | $files |")
    }
    file.writeText(sb.toString())
  }

  private fun generateLibraryReport(file: File) {
    val sb = StringBuilder()
    sb.appendLine("# Libraries")
    sb.appendLine()
    sb.appendLine("[← Back to Overview](00_overview.md)")
    sb.appendLine()
    // Build library dependency map
    val libraries = listLibraries()
    val libraryDeps = mutableMapOf<String, MutableSet<String>>()
    for (lib in libraries) {
      libraryDeps[lib] = mutableSetOf()
      val symbols = getSymbolsByLibrary(lib)
      for (symbol in symbols) {
        for (ref in symbol.references()) {
          val targetLib = ref.targetSymbol.libraryName()
          if (targetLib != null && targetLib != lib && targetLib in libraries) {
            libraryDeps[lib]!!.add(targetLib)
          }
        }
      }
    }
    // Generate Mermaid diagram (limit to libraries with dependencies to keep it readable)
    val librariesWithDeps = libraries.filter {
      (libraryDeps[it]?.isNotEmpty() == true) ||
          libraryDeps.values.any { deps -> it in deps }
    }.take(50) // Limit for readability
    if (librariesWithDeps.isNotEmpty()) {
      sb.appendLine("## Library Dependency Graph")
      sb.appendLine()
      sb.appendLine("```mermaid")
      sb.appendLine("graph LR")
      val libIds = librariesWithDeps.mapIndexed { index, lib -> lib to "L$index" }.toMap()
      librariesWithDeps.forEach { lib ->
        val shortName = lib.substringBeforeLast(".").take(20)
        sb.appendLine("    ${libIds[lib]}[\"$shortName\"]")
      }
      libraryDeps.filter { it.key in librariesWithDeps }.forEach { (from, tos) ->
        tos.filter { it in librariesWithDeps }.forEach { to ->
          sb.appendLine("    ${libIds[from]} --> ${libIds[to]}")
        }
      }
      sb.appendLine("```")
      sb.appendLine()
      sb.appendLine("### Library Legend")
      sb.appendLine()
      sb.appendLine("| ID | Library |")
      sb.appendLine("|----|---------|")
      libIds.forEach { (lib, id) ->
        sb.appendLine("| $id | $lib |")
      }
      sb.appendLine()
    }
    sb.appendLine("## All Libraries")
    sb.appendLine()
    sb.appendLine("| Library | Symbols | Packages | Dependencies |")
    sb.appendLine("|---------|---------|----------|--------------|")
    libraries.sorted().forEach { lib ->
      val symbols = getSymbolsByLibrary(lib)
      val packages = symbols.mapNotNull { it.packageName() }.toSet().size
      val deps = libraryDeps[lib]?.size ?: 0
      sb.appendLine("| $lib | ${symbols.size} | $packages | $deps |")
    }
    file.writeText(sb.toString())
  }
}