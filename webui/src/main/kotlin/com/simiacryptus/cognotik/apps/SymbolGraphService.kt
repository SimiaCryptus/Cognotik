package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.describe.Description
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
@Description("Service for managing a graph-based representation of code symbols, files, and their relationships. It uses an in-memory TinkerGraph to store vertices representing Files, Symbols, Languages, Libraries, and Packages, and edges representing relationships like DEFINED_IN, REFERENCES, WRITTEN_IN, IN_LIBRARY, and IN_PACKAGE.")

class SymbolGraphService {

    private val graph = TinkerGraph.open()

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
        annotations: String? = null
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
        val filePath = fileId
        val fileV = getOrCreateVertex(fileId, "File")
        fileV.property(VertexProperty.Cardinality.single, "name", filePath.substringAfterLast('/'))
        if (!v.vertices(Direction.OUT, "DEFINED_IN").hasNext()) v.addEdge("DEFINED_IN", fileV)
        val extension = filePath.substringAfterLast('.', "").lowercase()
        val language = when (extension) {
            "kt" -> "Kotlin"
            "java" -> "Java"
            "js" -> "JavaScript"
            "py" -> "Python"
            "class" -> "Bytecode"
            else -> extension
        }
        if (language.isNotEmpty()) {
            val langV = getOrCreateVertex(language, "Language")
            langV.property(VertexProperty.Cardinality.single, "name", language)
            if (!v.vertices(Direction.OUT, "WRITTEN_IN").hasNext()) v.addEdge("WRITTEN_IN", langV)
        }
        var libraryName: String? = null
        var packageName: String? = null
        if (filePath.contains(".jar!/")) {
            val jarParts = filePath.split("!/")
            libraryName = jarParts[0].substringAfterLast('/')
            packageName = jarParts[1].substringBeforeLast('/').replace('/', '.')
        } else if (filePath.contains("/src/")) {
            val srcParts = filePath.split("/src/")
            libraryName = srcParts[0].substringAfterLast('/')
            val pathAfterSrc = srcParts[1]
            val cleanPath = pathAfterSrc
                .replaceFirst("main/kotlin/", "")
                .replaceFirst("main/java/", "")
                .replaceFirst("test/kotlin/", "")
                .replaceFirst("test/java/", "")
            if (cleanPath.contains('/')) packageName = cleanPath.substringBeforeLast('/').replace('/', '.')
        }
        if (libraryName != null) {
            val libV = getOrCreateVertex(libraryName, "Library")
            libV.property(VertexProperty.Cardinality.single, "name", libraryName)
            if (!v.vertices(Direction.OUT, "IN_LIBRARY").hasNext()) v.addEdge("IN_LIBRARY", libV)
        }
        if (packageName != null) {
            val pkgV = getOrCreateVertex(packageName, "Package")
            pkgV.property(VertexProperty.Cardinality.single, "name", packageName)
            if (!v.vertices(Direction.OUT, "IN_PACKAGE").hasNext()) v.addEdge("IN_PACKAGE", pkgV)
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
        val ids = mutableSetOf<String>()
        graph.traversal().V().hasLabel("File").forEachRemaining { ids.add(it.id() as String) }
        return ids
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
        val toRemove = mutableListOf<Vertex>()
        graph.traversal().V().has("Symbol", "file", fileId).forEachRemaining { v ->
            if (v.id() !in keptSymbolIds) {
                toRemove.add(v)
            }
        }
        toRemove.forEach { it.remove() }
    }

    @Synchronized
    @Description("Adds a 'REFERENCES' edge from a source symbol to a target symbol. If the target symbol vertex does not exist, it is created with basic information (name, file).")
    fun addReference(sourceId: String, targetId: String, targetName: String, targetFile: String) {
        val sourceIter = graph.vertices(sourceId)
        if (sourceIter.hasNext()) {
            val sourceV = sourceIter.next()
            val targetV = getOrCreateVertex(targetId, "Symbol")
            if (!targetV.properties<Any>("name").hasNext()) {
                targetV.property(VertexProperty.Cardinality.single, "name", targetName)
                targetV.property(VertexProperty.Cardinality.single, "file", targetFile)
            }
            val exists = sourceV.edges(Direction.OUT, "REFERENCES").asSequence().any { it.inVertex().id() == targetId }
            if (!exists) {
                sourceV.addEdge("REFERENCES", targetV)
            }
        }
    }

    @Synchronized
    @Description("Retrieves a Symbol object wrapper for the vertex with the specified ID. Returns null if no such vertex exists.")
    fun getSymbol(id: String): Symbol? {
        val iter = graph.vertices(id)
        return if (iter.hasNext()) Symbol(iter.next()) else null
    }

    @Synchronized
    @Description("Searches for symbols whose names contain the given query string. Returns a list of matching Symbol objects.")
    fun search(query: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("Symbol")
            .has("name", TextP.containing(query))
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all symbols defined in the specified file.")
    fun getSymbolsByFile(fileId: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().has("Symbol", "file", fileId).forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all file vertices in the graph as Symbol objects.")
    fun getFiles(): List<Symbol> {
        val files = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("File").forEachRemaining { files.add(Symbol(it)) }
        return files
    }

    @Synchronized
    @Description("Retrieves all symbols written in the specified language.")
    fun getSymbolsByLanguage(language: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Language", "name", language)
            .`in`("WRITTEN_IN")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all symbols belonging to the specified library.")
    fun getSymbolsByLibrary(library: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Library", "name", library)
            .`in`("IN_LIBRARY")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all symbols belonging to the specified package.")
    fun getSymbolsByPackage(pkg: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Package", "name", pkg)
            .`in`("IN_PACKAGE")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Lists the names of all languages present in the graph.")
    fun listLanguages(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Language").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    @Description("Lists the names of all libraries present in the graph.")
    fun listLibraries(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Library").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    @Description("Lists the names of all packages present in the graph.")
    fun listPackages(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Package").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
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
        FileInputStream(path).use { `is` ->
            GraphSONReader.build().create().readGraph(`is`, graph)
        }
    }

    @Synchronized
    @Description("Lists all symbol vertices in the graph.")
    fun listSymbols(): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("Symbol").forEachRemaining { v ->
            symbols.add(Symbol(v))
        }
        return symbols
    }
    @Description("Represents a node in the symbol graph, which can be a Symbol, File, Language, Library, or Package. Provides access to properties and related nodes.")

    data class Symbol(private val vertex: Vertex) {
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
        fun references(): List<Symbol> {
            val list = mutableListOf<Symbol>()
            vertex.vertices(Direction.OUT, "REFERENCES").forEachRemaining { list.add(Symbol(it)) }
            return list
        }
        @Description("Returns a list of symbols that reference this symbol.")
        fun referencedBy(): List<Symbol> {
            val list = mutableListOf<Symbol>()
            vertex.vertices(Direction.IN, "REFERENCES").forEachRemaining { list.add(Symbol(it)) }
            return list
        }
        @Description("Returns the file symbol where this symbol is defined.")
        fun file(): Symbol? {
            val iter = vertex.vertices(Direction.OUT, "DEFINED_IN")
            return if (iter.hasNext()) Symbol(iter.next()) else null
        }
        @Description("Returns the name of the language this symbol is written in.")

        fun language(): String? =
            vertex.vertices(Direction.OUT, "WRITTEN_IN").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)
        @Description("Returns the name of the package this symbol belongs to.")

        fun packageName(): String? =
            vertex.vertices(Direction.OUT, "IN_PACKAGE").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)
        @Description("Returns the name of the library this symbol belongs to.")

        fun libraryName(): String? =
            vertex.vertices(Direction.OUT, "IN_LIBRARY").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)
    }

    private fun getOrCreateVertex(id: String, label: String): Vertex {
        val iter = graph.vertices(id)
        return if (iter.hasNext()) iter.next() else graph.addVertex(T.label, label, T.id, id)
    }
}