package com.simiacryptus.cognotik.apps

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

class SymbolGraphService {

    private val graph = TinkerGraph.open()

    @Synchronized
    fun clear() {
        graph.traversal().V().drop().iterate()
    }

    @Synchronized
    fun addFile(id: String, name: String, lastModified: Long) {
        val v = getOrCreateVertex(id, "File")
        v.property(VertexProperty.Cardinality.single, "name", name)
        v.property(VertexProperty.Cardinality.single, "lastModified", lastModified)
    }

    @Synchronized
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
    fun listFileIds(): Set<String> {
        val ids = mutableSetOf<String>()
        graph.traversal().V().hasLabel("File").forEachRemaining { ids.add(it.id() as String) }
        return ids
    }

    @Synchronized
    fun removeFile(id: String) {
        graph.traversal().V().has("Symbol", "file", id).drop().iterate()
        graph.traversal().V(id).drop().iterate()
    }

    @Synchronized
    fun clearOutgoingReferences(fileId: String) {
        graph.traversal().V().has("Symbol", "file", fileId).outE("REFERENCES").drop().iterate()
    }

    @Synchronized
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
    fun getSymbol(id: String): Symbol? {
        val iter = graph.vertices(id)
        return if (iter.hasNext()) Symbol(iter.next()) else null
    }

    @Synchronized
    fun search(query: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("Symbol")
            .has("name", TextP.containing(query))
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    fun getSymbolsByFile(fileId: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().has("Symbol", "file", fileId).forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    fun getFiles(): List<Symbol> {
        val files = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("File").forEachRemaining { files.add(Symbol(it)) }
        return files
    }

    @Synchronized
    fun getSymbolsByLanguage(language: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Language", "name", language)
            .`in`("WRITTEN_IN")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    fun getSymbolsByLibrary(library: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Library", "name", library)
            .`in`("IN_LIBRARY")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    fun getSymbolsByPackage(pkg: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Package", "name", pkg)
            .`in`("IN_PACKAGE")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    fun listLanguages(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Language").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    fun listLibraries(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Library").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    fun listPackages(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Package").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    fun save(path: String) {
        FileOutputStream(path).use { os ->
            GraphSONWriter.build().create().writeGraph(os, graph)
        }
    }

    @Synchronized
    fun load(path: File) {
        FileInputStream(path).use { `is` ->
            GraphSONReader.build().create().readGraph(`is`, graph)
        }
    }

    @Synchronized
    fun listSymbols(): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("Symbol").forEachRemaining { v ->
            symbols.add(Symbol(v))
        }
        return symbols
    }

    data class Symbol(private val vertex: Vertex) {
        val id: String = vertex.id() as String
        val name: String? get() = getProperty("name")
        val fileId: String? get() = getProperty("file")
        val startOffset: Int? get() = getProperty("startOffset")
        val endOffset: Int? get() = getProperty("endOffset")
        val line: Int? get() = getProperty("line")
        val visibility: String? get() = getProperty("visibility")
        val modifiers: String? get() = getProperty("modifiers")
        val annotations: String? get() = getProperty("annotations")
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
        fun references(): List<Symbol> {
            val list = mutableListOf<Symbol>()
            vertex.vertices(Direction.OUT, "REFERENCES").forEachRemaining { list.add(Symbol(it)) }
            return list
        }
        fun referencedBy(): List<Symbol> {
            val list = mutableListOf<Symbol>()
            vertex.vertices(Direction.IN, "REFERENCES").forEachRemaining { list.add(Symbol(it)) }
            return list
        }
        fun file(): Symbol? {
            val iter = vertex.vertices(Direction.OUT, "DEFINED_IN")
            return if (iter.hasNext()) Symbol(iter.next()) else null
        }

        fun language(): String? =
            vertex.vertices(Direction.OUT, "WRITTEN_IN").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)

        fun packageName(): String? =
            vertex.vertices(Direction.OUT, "IN_PACKAGE").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)

        fun libraryName(): String? =
            vertex.vertices(Direction.OUT, "IN_LIBRARY").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)
    }

    private fun getOrCreateVertex(id: String, label: String): Vertex {
        val iter = graph.vertices(id)
        return if (iter.hasNext()) iter.next() else graph.addVertex(T.label, label, T.id, id)
    }
}