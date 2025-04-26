package com.simiacryptus.cognotik.apps.graph

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.util.DynamicEnum
import com.simiacryptus.util.DynamicEnumDeserializer
import com.simiacryptus.util.DynamicEnumSerializer

@JsonDeserialize(using = CloudNodeTypesDeserializer::class)
@JsonSerialize(using = CloudNodeTypesSerializer::class)
@Suppress("UNUSED_PARAMETER")
class CloudNodeType<T : CloudNodeType.NodeBase> private constructor(
    name: String,
    nodeClass: Class<T>,
    description: String? = null,
    tooltipHtml: String? = null,
) : DynamicEnum<CloudNodeType<*>>(name) {
    @JsonDeserialize(using = CloudGraphDeserializer::class)
    class CloudGraph(
        val nodes: MutableSet<NodeBase> = mutableSetOf(),
    ) {
        /*Used to apply a patch made by the minus operator*/
        operator fun plus(other: CloudGraph): CloudGraph {
            val newGraph = CloudGraph()
            newGraph.nodes.addAll(this.nodes)
            other.nodes.forEach { otherNode ->
                if (newGraph.nodes.none { it.id == otherNode.id }) {
                    newGraph.nodes.add(otherNode)
                } else {
                    // Merge nodes with the same ID
                    val existingNode = newGraph.nodes.first { it.id == otherNode.id }
                    newGraph.nodes.remove(existingNode)
                    newGraph.nodes.add(otherNode + existingNode)
                }
            }
            return newGraph
        }

        /*Used to create a patch that converts this graph to the other graph*/
        operator fun minus(other: CloudGraph): CloudGraph {
            val newGraph = CloudGraph()
            this.nodes.forEach { thisNode ->
                val otherNode = other.nodes.find { it.id == thisNode.id }
                if (otherNode != null) {
                    // If node exists in both graphs, create diff
                    newGraph.nodes.add(thisNode - otherNode)
                } else {
                    // If node only exists in this graph, include it in diff
                    newGraph.nodes.add(thisNode)
                }
            }
            return newGraph
        }
    }

    class CloudGraphDeserializer : JsonDeserializer<CloudGraph>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CloudGraph {
            val mapper = p.codec as ObjectMapper
            val node: JsonNode = mapper.readTree(p)
            val nodes = when {
                node.has("nodes") -> {
                    mapper.treeToValue(node["nodes"], Array<NodeBase>::class.java).toMutableSet()
                }

                node.isObject -> {
                    mapper.treeToValue(node, Array<NodeBase>::class.java).toMutableSet()
                }

                else -> {
                    throw IllegalArgumentException("Invalid CloudGraph format")
                }
            }
            return CloudGraph(nodes)
        }
    }

    companion object {
        @JvmStatic
        fun values() = listOf(
            StorageResource,
            DnsResource,
            LoadBalancerResource,
            QueueResource,
            InstanceResource,
            InstanceGroupResource
        )

        val StorageResource = CloudNodeType(
            "StorageResource",
            StorageResourceNode::class.java,
            "Storage Resource",
            """
            Represents a cloud storage resource.
            <ul>
        <li>Manages cloud storage resources including object storage, block storage, and file systems</li>
        <li>Supports versioning and lifecycle management</li>
                <li>Configures storage policies, encryption and access controls</li>
                <li>Handles backup scheduling and retention policies</li>
                <li>Supports multiple storage tiers and replication</li>
                <li>Monitors storage metrics and quotas</li>
        <li>Manages data transfer and cross-region replication</li>
        <li>Configures access points and endpoint policies</li>
            </ul>
            """
        )
        val DnsResource = CloudNodeType(
            "DnsResource",
            DnsResourceNode::class.java,
            "DNS Resource",
            """
            Represents DNS configurations and records.
            <ul>
        <li>Manages DNS zones, records and aliases</li>
        <li>Configures routing policies and traffic flow</li>
        <li>Handles domain registration and transfers</li>
        <li>Supports health checks and failover</li>
        <li>Manages DNS security extensions (DNSSEC)</li>
        <li>Configures geolocation and latency-based routing</li>
            </ul>
            """
        )
        val LoadBalancerResource = CloudNodeType(
            "LoadBalancerResource",
            LoadBalancerResourceNode::class.java,
            "Load Balancer Resource",
            """
            Represents load balancing services.
            <ul>
                <li>Configures traffic distribution algorithms and rules</li>
                <li>Manages health checks and failure detection</li>
                <li>Handles SSL/TLS termination and certificate management</li>
                <li>Supports multiple load balancing schemes (round-robin, least connections)</li>
                <li>Provides access logging and monitoring</li>
        <li>Configures sticky sessions and session persistence</li>
        <li>Manages cross-zone load balancing</li>
        <li>Supports WebSocket and HTTP/2 protocols</li>
            </ul>
            """
        )
        val QueueResource = CloudNodeType(
            "QueueResource",
            QueueResourceNode::class.java,
            "Queue Resource",
            """
            Represents message queues and event systems.
            <ul>
                <li>Message queue configurations</li>
                <li>Event routing rules</li>
                <li>Queue policies</li>
            </ul>
            """
        )
        val InstanceResource = CloudNodeType(
            "InstanceResource",
            InstanceResourceNode::class.java,
            "Instance Resource",
            """
            Represents individual compute instances.
            <ul>
                <li>VM configurations</li>
                <li>Instance specifications</li>
                <li>Runtime settings</li>
            </ul>
            """
        )
        val InstanceGroupResource = CloudNodeType(
            "InstanceGroupResource",
            InstanceGroupResourceNode::class.java,
            "Instance Group Resource",
            """
            Represents groups of managed instances.
            <ul>
                <li>Auto-scaling configurations</li>
                <li>Group policies</li>
                <li>Deployment settings</li>
            </ul>
            """
        )
    }

    interface NodeBase {
        val id: String
    }

    data class StorageResourceNode(
        override val id: String,
        @Description("Storage type (e.g. Object, Block, File)")
        var storageType: String = "",
        @Description("Storage capacity in GB")
        var capacityGB: Int = 0,
        @Description("Storage tier (e.g. Standard, Premium)")
        var storageTier: String = "Standard",
        @Description("Encryption settings")
        var encrypted: Boolean = true,
        @Description("Backup retention period in days")
        var backupRetentionDays: Int = 7
    ) : NodeBase

    data class DnsResourceNode(
        override val id: String,
        @Description("Domain name")
        var domainName: String = "",
        @Description("Record type (e.g. A, CNAME, MX)")
        var recordType: String = "",
        @Description("DNS record value")
        var recordValue: String = "",
        @Description("Time-to-live in seconds")
        var ttl: Int = 300
    ) : NodeBase

    data class LoadBalancerResourceNode(
        override val id: String,
        @Description("Load balancer type (e.g. Application, Network)")
        var lbType: String = "",
        @Description("List of target instances/groups")
        val targets: MutableList<String> = mutableListOf(),
        @Description("Health check configuration")
        var healthCheckPath: String = "/health",
        @Description("SSL certificate ARN")
        var sslCertArn: String = ""
    ) : NodeBase

    data class QueueResourceNode(
        override val id: String,
        @Description("Queue type (e.g. Standard, FIFO)")
        var queueType: String = "Standard",
        @Description("Message retention period in days")
        var messageRetentionDays: Int = 4,
        @Description("Maximum message size in KB")
        var maxMessageSizeKB: Int = 256,
        @Description("Delivery delay in seconds")
        var deliveryDelay: Int = 0
    ) : NodeBase

    data class InstanceResourceNode(
        override val id: String,
        @Description("Instance type/size")
        var instanceType: String = "",
        @Description("Operating system")
        var operatingSystem: String = "",
        @Description("Attached storage volumes")
        val volumes: MutableList<String> = mutableListOf(),
        @Description("Network security groups")
        val securityGroups: MutableList<String> = mutableListOf()
    ) : NodeBase

    data class InstanceGroupResourceNode(
        override val id: String,
        @Description("Minimum instance count")
        var minSize: Int = 1,
        @Description("Maximum instance count")
        var maxSize: Int = 4,
        @Description("Desired instance count")
        var desiredCapacity: Int = 2,
        @Description("Auto-scaling policies")
        val scalingPolicies: MutableList<String> = mutableListOf()
    ) : NodeBase
}

class CloudNodeTypesDeserializer : DynamicEnumDeserializer<CloudNodeType<*>>(CloudNodeType::class.java)

class CloudNodeTypesSerializer : DynamicEnumSerializer<CloudNodeType<*>>(CloudNodeType::class.java)

operator fun CloudNodeType.NodeBase.minus(other: CloudNodeType.NodeBase) = when {
    this.javaClass != other.javaClass -> throw IllegalArgumentException("Cannot merge nodes of different types")
    this is CloudNodeType.StorageResourceNode && other is CloudNodeType.StorageResourceNode -> {
        this.copy(
            storageType = if (this.storageType == other.storageType) "" else this.storageType,
            capacityGB = if (this.capacityGB == other.capacityGB) 0 else this.capacityGB,
            storageTier = if (this.storageTier == other.storageTier) "Standard" else this.storageTier,
            encrypted = if (this.encrypted == other.encrypted) true else this.encrypted,
            backupRetentionDays = if (this.backupRetentionDays == other.backupRetentionDays) 7 else this.backupRetentionDays
        )
    }

    this is CloudNodeType.LoadBalancerResourceNode && other is CloudNodeType.LoadBalancerResourceNode -> {
        this.copy(
            lbType = if (this.lbType == other.lbType) "" else this.lbType,
            targets = this.targets.minus(other.targets).toMutableList(),
            healthCheckPath = if (this.healthCheckPath == other.healthCheckPath) "/health" else this.healthCheckPath,
            sslCertArn = if (this.sslCertArn == other.sslCertArn) "" else this.sslCertArn
        )
    }
    // Add similar cases for other node types
    else -> throw IllegalArgumentException("Unsupported node type for diffing")
}

operator fun CloudNodeType.NodeBase.plus(other: CloudNodeType.NodeBase) = when {
    this.javaClass != other.javaClass -> throw IllegalArgumentException("Cannot merge nodes of different types")
    this is CloudNodeType.StorageResourceNode && other is CloudNodeType.StorageResourceNode -> {
        this.copy(
            storageType = other.storageType.ifEmpty { this.storageType },
            capacityGB = if (other.capacityGB > 0) other.capacityGB else this.capacityGB,
            storageTier = other.storageTier.ifEmpty { this.storageTier },
            encrypted = other.encrypted,
            backupRetentionDays = if (other.backupRetentionDays != 7) other.backupRetentionDays else this.backupRetentionDays
        )
    }

    this is CloudNodeType.LoadBalancerResourceNode && other is CloudNodeType.LoadBalancerResourceNode -> {
        this.copy(
            lbType = other.lbType.ifEmpty { this.lbType },
            targets = (this.targets + other.targets).distinct().toMutableList(),
            healthCheckPath = other.healthCheckPath.ifEmpty { this.healthCheckPath },
            sslCertArn = other.sslCertArn.ifEmpty { this.sslCertArn }
        )
    }
    // Add similar cases for other node types
    else -> throw IllegalArgumentException("Unsupported node type for merging")
}