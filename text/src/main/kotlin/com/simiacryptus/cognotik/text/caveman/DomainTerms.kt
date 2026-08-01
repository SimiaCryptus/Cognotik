package com.simiacryptus.cognotik.text.caveman

/**
 * Layerable registry of domain terms that are exempt from stemming and stopword removal
 * (spec 3.4, 4.4 "Custom Domain Dictionaries"). Supports multi-word phrases, which the
 * pipeline merges into a single token before stopword filtering.
 */
class DomainTermRegistry private constructor(private val canonicalByKey: Map<String, String>) {

  val terms: Set<String> get() = canonicalByKey.keys

  /** Longest phrase length (in words) present in this registry; 0 when empty. */
  val maxPhraseLength: Int = canonicalByKey.keys.fold(0) { acc, key ->
    val n = key.split(' ').size
    if (n > acc) n else acc
  }

  fun contains(token: String): Boolean = canonicalByKey.containsKey(normalizeTerm(token))

  /** Canonical (case-preserved) surface form for a matched term, or null. */
  fun canonicalFor(token: String): String? = canonicalByKey[normalizeTerm(token)]

  /**
   * Greedy longest match of a registry phrase starting at [from].
   * @return number of tokens matched (0 when nothing matched).
   */
  fun matchPhrase(keys: List<String>, from: Int): Int {
    if (from >= keys.size || canonicalByKey.isEmpty()) return 0
    var len = minOf(maxPhraseLength, keys.size - from)
    while (len > 1) {
      val phrase = keys.subList(from, from + len).joinToString(" ")
      if (canonicalByKey.containsKey(normalizeTerm(phrase))) return len
      len--
    }
    return if (canonicalByKey.containsKey(normalizeTerm(keys[from]))) 1 else 0
  }

  fun plus(other: DomainTermRegistry): DomainTermRegistry =
    DomainTermRegistry(LinkedHashMap(canonicalByKey).apply { putAll(other.canonicalByKey) })

  fun plus(more: Iterable<String>): DomainTermRegistry = plus(of(more))

  companion object {

    internal fun normalizeTerm(value: String): String =
      value.trim().lowercase().replace(Regex("\\s+"), " ")

    fun empty(): DomainTermRegistry = DomainTermRegistry(emptyMap())

    fun of(vararg terms: String): DomainTermRegistry = of(terms.toList())

    fun of(terms: Iterable<String>): DomainTermRegistry {
      val map = LinkedHashMap<String, String>()
      terms.forEach { term ->
        val key = normalizeTerm(term)
        if (key.isNotEmpty()) map[key] = term.trim()
      }
      return DomainTermRegistry(map)
    }

    /** One term per line; `#` starts a comment. */
    fun fromLines(lines: Iterable<String>): DomainTermRegistry =
      of(lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() })

    fun fromResource(
      path: String,
      loader: ClassLoader = DomainTermRegistry::class.java.classLoader,
    ): DomainTermRegistry {
      val stream = loader.getResourceAsStream(path)
        ?: throw IllegalArgumentException("Domain dictionary resource not found: $path")
      return stream.bufferedReader(Charsets.UTF_8).use { fromLines(it.readLines()) }
    }

    val DISTRIBUTED_SYSTEMS: DomainTermRegistry by lazy {
      of(
        "CRDT", "CAP", "HNSW", "Raft", "Paxos", "Zab", "Kubernetes", "etcd", "ZooKeeper",
        "Kafka", "gRPC", "gossip", "quorum", "sharding", "leader election", "two-phase commit",
        "vector clock", "Lamport clock", "eventual consistency", "linearizability",
        "serializability", "idempotency", "write-ahead log", "consensus", "replication log"
      )
    }

    val ROBOTICS: DomainTermRegistry by lazy {
      of(
        "SLAM", "LIDAR", "IMU", "ROS", "PID", "odometry", "kinematics", "actuator",
        "end effector", "inverse kinematics", "occupancy grid", "waypoint", "servo",
        "teleoperation", "point cloud"
      )
    }

    val MACHINE_LEARNING: DomainTermRegistry by lazy {
      of(
        "LLM", "RAG", "BM25", "TF-IDF", "embedding", "tokenizer", "perplexity",
        "attention", "transformer", "fine-tuning", "quantization", "gradient descent"
      )
    }
  }
}