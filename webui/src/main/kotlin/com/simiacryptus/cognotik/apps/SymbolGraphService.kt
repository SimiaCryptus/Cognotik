package com.simiacryptus.cognotik.apps

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONReader
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import java.io.FileInputStream
import java.io.FileOutputStream

class SymbolGraphService {
    private val graph = TinkerGraph.open()

    fun addFile(id: String, name: String) {
        val v = getOrCreateVertex(id, "File")
        v.property(VertexProperty.Cardinality.single, "name", name)
    }

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
    }

    fun addReference(sourceId: String, targetId: String, targetName: String, targetFile: String) {
        val sourceIter = graph.vertices(sourceId)
        if (sourceIter.hasNext()) {
            val sourceV = sourceIter.next()
            val targetV = getOrCreateVertex(targetId, "Symbol")
            if (!targetV.properties<Any>("name").hasNext()) {
                targetV.property(VertexProperty.Cardinality.single, "name", targetName)
                targetV.property(VertexProperty.Cardinality.single, "file", targetFile)
            }
            sourceV.addEdge("REFERENCES", targetV)
        }
    }

    fun save(path: String) {
        FileOutputStream(path).use { os ->
            GraphSONWriter.build().create().writeGraph(os, graph)
        }
    }

    fun load(path: String) {
        FileInputStream(path).use { `is` ->
            GraphSONReader.build().create().readGraph(`is`, graph)
        }
    }

    fun listSymbols(): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("Symbol").forEachRemaining { v ->
            symbols.add(Symbol(v))
        }
        return symbols
    }

    data class Symbol(private val vertex: Vertex) {
        val id: String = vertex.id() as String
        val properties: Map<String, Any>
            get() {
                val map = mutableMapOf<String, Any>()
                map["id"] = id
                vertex.properties<Any>().forEachRemaining { p -> map[p.key()] = p.value() }
                return map
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
    }

    private fun getOrCreateVertex(id: String, label: String): Vertex {
        val iter = graph.vertices(id)
        return if (iter.hasNext()) iter.next() else graph.addVertex(T.label, label, T.id, id)
    }
}