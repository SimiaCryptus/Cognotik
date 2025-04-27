package com.simiacryptus.cognotik.apps.graph

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.cognotik.apps.graph.SoftwareNodeType.*
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.util.DynamicEnum
import com.simiacryptus.util.DynamicEnumDeserializer
import com.simiacryptus.util.DynamicEnumSerializer
import com.simiacryptus.util.copy

@JsonDeserialize(using = SoftwareNodeTypesDeserializer::class)
@JsonSerialize(using = SoftwareNodeTypesSerializer::class)
class SoftwareNodeType<T : NodeBase<T>> private constructor(
    @JsonDeserialize(using = SoftwareGraphDeserializer::class)
    name: String = "",
    val nodeClass: Class<T> = NodeBase::class.java as Class<T>,
    val description: String? = null,
) : DynamicEnum<SoftwareNodeType<*>>(name) {
    companion object {
        @JvmStatic
        fun values() = listOf(
            ProjectImport,
            TestCodeFile,
            ScmProject,
            CodeFile,
            CodePackage,
            CodeProject,
            SpecificationDocument
        )

        val ProjectImport = SoftwareNodeType(
            "ProjectImport",
            ProjectImportNode::class.java,
            "Project Import"
        )
        val TestCodeFile = SoftwareNodeType(
            "TestCodeFile",
            TestCodeFileNode::class.java,
            "Test Code File"
        )
        val ScmProject = SoftwareNodeType(
            "ScmProject",
            ScmProjectNode::class.java,
            "Source Control Management Project"
        )
        val CodeFile = SoftwareNodeType(
            "CodeFile",
            CodeFileNode::class.java,
            "Code File"
        )
        val CodePackage = SoftwareNodeType(
            "CodePackage",
            CodePackageNode::class.java,
            "Code Package"
        )
        val CodeProject = SoftwareNodeType(
            "CodeProject",
            CodeProjectNode::class.java,
            "Code Project"
        )
        val SpecificationDocument = SoftwareNodeType(
            "SpecificationDocument",
            SpecificationDocumentNode::class.java,
            "Specification Document"
        )
    }

    @JvmInline
    value class NodeId<out T : NodeBase<out T>> private constructor(val value: String) {
        override fun toString() = value
        fun negate(): NodeId<T> = NodeId<T>("!${value}")
        val isNegated get() = value.startsWith("!")
        val absoluteValue get() = if (isNegated) NodeId<T>(value.substring(1)) else this

        companion object {
            fun <T : NodeBase<T>> create(value: String): NodeId<T> = NodeId(value)
            fun <T : NodeBase<T>> createNew(): NodeId<T> = create(java.util.UUID.randomUUID().toString())
        }
    }

    interface NodeBase<T : NodeBase<T>> {
        var id: NodeId<T>
        val type: String
    }

    data class ScmProjectNode(
        override var id: NodeId<ScmProjectNode> = NodeId.createNew(),
        override val type: String = "ScmProject",
        @Description("IDs of code projects in this SCM project")
        var projectIds: MutableSet<NodeId<CodeProjectNode>>? = mutableSetOf<NodeId<CodeProjectNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        @Description("Name of the SCM project")
        var name: String? = null,
        @Description("Project status (e.g. Planning, In Development, Maintenance)")
        var status: String? = "Planning",
        @Description("Project priority (e.g. High, Medium, Low)")
        var priority: String? = "Medium"
    ) : NodeBase<ScmProjectNode>

    data class ProjectImportNode(
        override var id: NodeId<ProjectImportNode> = NodeId.createNew(),
        override val type: String = "ProjectImport",
        @Description("Source location of the imported project")
        var sourceLocation: String? = null,
        @Description("Import configuration settings")
        var importConfig: Map<String, String>? = mutableMapOf<String, String>().toSortedMap(),
        @Description("List of imported resources")
        var importedResources: MutableSet<String>? = mutableSetOf<String>().toSortedSet(),
    ) : NodeBase<ProjectImportNode>

    data class CodeFileNode(
        override var id: NodeId<CodeFileNode> = NodeId.createNew(),
        override val type: String = "CodeFile",
        @Description("Parent package containing this file")
        var packageId: NodeId<CodePackageNode>? = null,
        @Description("List of files this file depends on")
        var dependencyIds: MutableSet<NodeId<CodeFileNode>>? = mutableSetOf<NodeId<CodeFileNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        @Description("List of specification documents for this file")
        val specificationIds: MutableSet<NodeId<SpecificationDocumentNode>> = mutableSetOf<NodeId<SpecificationDocumentNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        val importIds: MutableSet<NodeId<ProjectImportNode>> = mutableSetOf<NodeId<ProjectImportNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        @Description("Programming language")
        var language: String? = null,
        @Description("File path relative to project root")
        var path: String? = null,
        @Description("Code complexity metrics")
        var metrics: Map<String, Double>? = null,
        @Description("Identified design patterns")
        var designPatterns: MutableSet<String>? = null,
        @Description("Code quality issues")
        var qualityIssues: MutableList<String>? = null,
        @Description("Documentation coverage percentage")
        var docCoverage: Double? = null
    ) : NodeBase<CodeFileNode>

    data class CodePackageNode(
        override var id: NodeId<CodePackageNode> = NodeId.createNew(),
        override val type: String = "CodePackage",
        var projectId: NodeId<CodeProjectNode>? = null,
        val fileIds: MutableSet<NodeId<CodeFileNode>> = mutableSetOf<NodeId<CodeFileNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        val specificationIds: MutableSet<NodeId<SpecificationDocumentNode>> = mutableSetOf<NodeId<SpecificationDocumentNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        val importIds: MutableSet<NodeId<ProjectImportNode>> = mutableSetOf<NodeId<ProjectImportNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        @Description("Package name")
        var name: String? = null,
        @Description("Package description")
        var description: String? = null,
        @Description("Package-level architecture patterns")
        var architecturePatterns: MutableSet<String>? = null,
        @Description("Package dependencies and their coupling metrics")
        var dependencyMetrics: Map<String, Double>? = null,
        @Description("Package cohesion score")
        var cohesionScore: Double? = null
    ) : NodeBase<CodePackageNode>

    data class CodeProjectNode(
        override var id: NodeId<CodeProjectNode> = NodeId.createNew(),
        override val type: String = "CodeProject",
        var projectId: NodeId<ScmProjectNode>? = null,
        val packageIds: MutableSet<NodeId<CodePackageNode>> = mutableSetOf<NodeId<CodePackageNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        @Description("Project name")
        var name: String? = null,
        @Description("Minimum required dependencies (e.g. Java 11, Python 3.8)")
        var requirements: Map<String, String>? = mutableMapOf(),
        @Description("Development status (e.g. Alpha, Beta, Production)")
        var developmentStatus: String? = "Alpha",
        @Description("Project architecture style")
        var architectureStyle: String? = null,
        @Description("Technical debt estimate")
        var technicalDebt: Map<String, Double>? = null,
        @Description("Code duplication percentage")
        var duplicationPercentage: Double? = null,
        @Description("Overall maintainability score")
        var maintainabilityScore: Double? = null
    ) : NodeBase<CodeProjectNode>

    data class SpecificationDocumentNode(
        override var id: NodeId<SpecificationDocumentNode> = NodeId.createNew(),
        override val type: String = "SpecificationDocument",
        var projectId: NodeId<ScmProjectNode>? = null,
        @Description("Document title")
        var title: String? = null,
        @Description("Document type (e.g. API, Architecture)")
        var documentType: String? = null,
        @Description("Document status (e.g. Draft, Review, Approved)")
        var status: String? = "Draft",
        @Description("Document classification (e.g. Public, Internal, Confidential)")
        var classification: String? = "Internal"
    ) : NodeBase<SpecificationDocumentNode>

    data class TestCodeFileNode(
        override var id: NodeId<TestCodeFileNode> = NodeId.createNew(),
        override val type: String = "TestCodeFile",
        @Description("Parent package containing this test file")
        var packageId: NodeId<CodePackageNode>? = null,
        @Description("Code files being tested")
        val testedFileIds: MutableSet<NodeId<CodeFileNode>> = mutableSetOf<NodeId<CodeFileNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        @Description("Test dependencies")
        val dependencyIds: MutableSet<NodeId<CodeFileNode>> = mutableSetOf<NodeId<CodeFileNode>>().toSortedSet { o1: NodeId<*>, o2: NodeId<*> ->
            o1.value.compareTo(
                o2.value
            )
        },
        @Description("Test framework being used (e.g. JUnit, TestNG)")
        var testFramework: String? = null,
        @Description("Test category (e.g. Unit, Integration, E2E)")
        var testCategory: String? = "Unit",
        @Description("File path relative to project test root")
        var path: String? = null,
        var language: String? = null
    ) : NodeBase<TestCodeFileNode>

    @JsonDeserialize(using = SoftwareGraphDeserializer::class)
    class SoftwareGraph(
        val nodes: MutableSet<NodeBase<*>> = mutableSetOf(),
    ) {
        /*Used to apply a patch made by the minus operator*/
        operator fun plus(other: SoftwareGraph): SoftwareGraph {
            val newGraph = SoftwareGraph()
            newGraph.nodes.addAll(this.nodes)
            other.nodes.forEach { otherNode: NodeBase<*> ->
                if (otherNode.id.isNegated) {

                    newGraph.nodes.removeAll { it.id == otherNode.id.absoluteValue }
                } else if (newGraph.nodes.none { it.id == otherNode.id }) {
                    newGraph.nodes.add(otherNode)
                } else {

                    val existingNode: NodeBase<*> = newGraph.nodes.first { it.id == otherNode.id }
                    newGraph.nodes.remove(existingNode)
                    newGraph.nodes.add(otherNode + existingNode)
                }
            }
            return newGraph
        }

        /*Used to create a patch that converts this graph to the other graph*/
        operator fun minus(other: SoftwareGraph): SoftwareGraph {
            val newGraph = SoftwareGraph()
            this.nodes.forEach { thisNode ->
                val otherNode = other.nodes.find {
                    it.id == thisNode.id || it.id.absoluteValue == thisNode.id
                }
                if (otherNode != null) {
                    if (otherNode.id.isNegated) {

                        newGraph.nodes.add(thisNode.copy {
                            id = thisNode.id.negate() as NodeId<Nothing>
                        })
                    } else {

                        newGraph.nodes.add(thisNode - otherNode)
                    }
                } else {

                    newGraph.nodes.add(thisNode)
                }
            }
            return newGraph
        }
    }

}

class SoftwareGraphDeserializer : JsonDeserializer<SoftwareGraph>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SoftwareGraph {
        val mapper = p.codec as ObjectMapper
        val node: JsonNode = mapper.readTree(p)
        val nodes: MutableSet<NodeBase<*>> = when {
            node.has("nodes") -> {

                node["nodes"].map { nodeJson ->
                    val typeNode = nodeJson["type"]
                    if (typeNode == null) {
                        throw IllegalArgumentException("Node is missing required 'type' field")
                    }
                    when (typeNode.asText()) {
                        "ScmProject" -> mapper.treeToValue(nodeJson, ScmProjectNode::class.java)
                        "CodeProject" -> mapper.treeToValue(nodeJson, CodeProjectNode::class.java)
                        "CodePackage" -> mapper.treeToValue(nodeJson, CodePackageNode::class.java)
                        "CodeFile" -> mapper.treeToValue(nodeJson, CodeFileNode::class.java)
                        "TestCodeFile" -> mapper.treeToValue(nodeJson, TestCodeFileNode::class.java)
                        "ProjectImport" -> mapper.treeToValue(nodeJson, ProjectImportNode::class.java)
                        "SpecificationDocument" -> mapper.treeToValue(nodeJson, SpecificationDocumentNode::class.java)
                        else -> throw IllegalArgumentException("Unknown node type: ${typeNode.asText()}")
                    }
                }.toMutableSet()
            }

            node.isObject -> {

                val typeNode = node["type"]
                if (typeNode == null) {
                    throw IllegalArgumentException("Node is missing required 'type' field")
                }
                when (typeNode.asText()) {
                    "ScmProject" -> mutableSetOf(mapper.treeToValue(node, ScmProjectNode::class.java))
                    "CodeProject" -> mutableSetOf(mapper.treeToValue(node, CodeProjectNode::class.java))
                    "CodePackage" -> mutableSetOf(mapper.treeToValue(node, CodePackageNode::class.java))
                    "CodeFile" -> mutableSetOf(mapper.treeToValue(node, CodeFileNode::class.java))
                    "TestCodeFile" -> mutableSetOf(mapper.treeToValue(node, TestCodeFileNode::class.java))
                    "ProjectImport" -> mutableSetOf(mapper.treeToValue(node, ProjectImportNode::class.java))
                    "SpecificationDocument" -> mutableSetOf(
                        mapper.treeToValue(
                            node,
                            SpecificationDocumentNode::class.java
                        )
                    )

                    else -> throw IllegalArgumentException("Unknown node type: ${typeNode.asText()}")
                }
            }

            else -> {

                throw IllegalArgumentException("Invalid SoftwareGraph format")
            }
        }
        return SoftwareGraph(nodes)
    }
}

class SoftwareNodeTypesDeserializer : DynamicEnumDeserializer<SoftwareNodeType<*>>(SoftwareNodeType::class.java)

class SoftwareNodeTypesSerializer : DynamicEnumSerializer<SoftwareNodeType<*>>(SoftwareNodeType::class.java)

operator fun NodeBase<*>.minus(other: NodeBase<*>) = when {
    this.javaClass != other.javaClass -> throw IllegalArgumentException("Cannot merge nodes of different types")
    this is CodeFileNode && other is CodeFileNode -> {
        this.copy(
            dependencyIds = this.dependencyIds?.minus(other.dependencyIds ?: emptySet())?.toMutableSet(),
            specificationIds = this.specificationIds.minus(other.specificationIds).toMutableSet(),
            importIds = this.importIds.minus(other.importIds).toMutableSet()
        )
    }

    this is TestCodeFileNode && other is TestCodeFileNode -> {
        this.copy(
            testedFileIds = this.testedFileIds.minus(other.testedFileIds).toMutableSet(),
            dependencyIds = this.dependencyIds.minus(other.dependencyIds).toMutableSet()
        )
    }

    this is CodePackageNode && other is CodePackageNode -> {
        this.copy(
            fileIds = this.fileIds.minus(other.fileIds).toMutableSet(),
            specificationIds = this.specificationIds.minus(other.specificationIds).toMutableSet(),
            importIds = this.importIds.minus(other.importIds).toMutableSet()
        )
    }

    this is CodeProjectNode && other is CodeProjectNode -> {
        this.copy(
            packageIds = this.packageIds.minus(other.packageIds).toMutableSet()
        )
    }

    this is ScmProjectNode && other is ScmProjectNode -> {
        this.copy(
            projectIds = this.projectIds?.minus(other.projectIds ?: emptySet())?.toMutableSet()
        )
    }

    this is ProjectImportNode && other is ProjectImportNode -> {
        this.copy(
            importedResources = this.importedResources?.minus(other.importedResources ?: emptySet())?.toMutableSet(),
            importConfig = this.importConfig?.filterKeys { false == other.importConfig?.containsKey(it) }
        )
    }

    this is SpecificationDocumentNode && other is SpecificationDocumentNode -> {
        this.copy(
            projectId = if (this.projectId == other.projectId) null else this.projectId,
            title = if (this.title == other.title) "" else this.title,
            documentType = if (this.documentType == other.documentType) "" else this.documentType
        )
    }

    else -> throw IllegalArgumentException("Unsupported node type for merging")
}

operator fun NodeBase<*>.plus(other: NodeBase<*>) = when {
    this.javaClass != other.javaClass -> throw IllegalArgumentException("Cannot merge nodes of different types")
    this is CodeFileNode && other is CodeFileNode -> {
        this.copy(
            packageId = other.packageId ?: this.packageId,
            dependencyIds = this.dependencyIds?.union(other.dependencyIds ?: emptySet())
                ?.toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) },
            specificationIds = this.specificationIds?.union(other.specificationIds ?: emptySet())
                ?.toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) }
                ?: mutableSetOf<NodeId<SpecificationDocumentNode>>(),
            importIds = this.importIds.union(other.importIds)
                .toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) },
            language = other.language?.ifEmpty { this.language },
            path = other.path?.ifEmpty { this.path }
        )
    }

    this is TestCodeFileNode && other is TestCodeFileNode -> {
        this.copy(
            packageId = other.packageId ?: this.packageId,
            testedFileIds = this.testedFileIds.union(other.testedFileIds)
                ?.toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) }
                ?: mutableSetOf<NodeId<CodeFileNode>>(),
            dependencyIds = this.dependencyIds.union(other.dependencyIds)
                ?.toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) }
                ?: mutableSetOf<NodeId<CodeFileNode>>(),
            testFramework = other.testFramework?.ifEmpty { this.testFramework },
            testCategory = other.testCategory?.ifEmpty { this.testCategory },
            path = other.path?.ifEmpty { this.path },
            language = other.language?.ifEmpty { this.language }
        )
    }

    this is CodePackageNode && other is CodePackageNode -> {
        this.copy(
            projectId = other.projectId ?: this.projectId,
            fileIds = this.fileIds.union(other.fileIds)
                .toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) },
            specificationIds = this.specificationIds.union(other.specificationIds)
                .toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) },
            importIds = this.importIds.union(other.importIds)
                .toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) },
            name = other.name?.ifEmpty { this.name },
            description = other.description?.ifEmpty { this.description }
        )
    }

    this is CodeProjectNode && other is CodeProjectNode -> {
        this.copy(
            projectId = other.projectId ?: this.projectId,
            name = other.name?.ifEmpty { this.name },
            packageIds = this.packageIds.union(other.packageIds)
                .toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) }
        )
    }

    this is ScmProjectNode && other is ScmProjectNode -> {
        this.copy(
            projectIds = this.projectIds?.union(other.projectIds ?: emptySet())
                ?.toSortedSet { o1: NodeId<*>, o2: NodeId<*> -> o1.value.compareTo(o2.value) }
                ?: mutableSetOf<NodeId<CodeProjectNode>>(),
            name = other.name?.ifEmpty { this.name },
        )
    }

    this is ProjectImportNode && other is ProjectImportNode -> {
        this.copy(
            importedResources = this.importedResources?.union(other.importedResources ?: emptySet())?.toSortedSet()
                ?: mutableSetOf(),
            importConfig = (this.importConfig ?: emptyMap<String, String>()) + (other.importConfig
                ?: emptyMap<String, String>())
        )
    }

    this is SpecificationDocumentNode && other is SpecificationDocumentNode -> {
        this.copy(
            projectId = other.projectId ?: this.projectId,
            title = other.title?.ifEmpty { this.title },
            documentType = other.documentType?.ifEmpty { this.documentType }
        )
    }

    else -> throw IllegalArgumentException("Unsupported node type for merging")
}