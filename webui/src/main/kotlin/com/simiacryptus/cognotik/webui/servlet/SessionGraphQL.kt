package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.PageResult
import com.simiacryptus.cognotik.platform.model.Page
import com.simiacryptus.cognotik.platform.model.Patch
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionListEntry
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.SessionMetadataPatch
import com.simiacryptus.cognotik.platform.model.SessionQuery
import com.simiacryptus.cognotik.platform.model.SessionSort
import com.simiacryptus.cognotik.platform.model.SessionSummary
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.service.SessionMetadataInterface
import com.simiacryptus.cognotik.platform.service.StorageInterface
import com.simiacryptus.cognotik.platform.service.UsageInterface
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * GraphQL schema fragment for sessions and session metadata.
 *
 * Merged into the [UsageGraphQL] endpoint so that session metadata and usage
 * can be fetched in a single request (`Session.usage` resolves to `SessionUsage`).
 *
 * Mutations are only accepted over POST.
 */
object SessionGraphQL {

  val sdl: String = """
    enum SessionSort { ${SessionSort.values().joinToString(" ") { it.name }} }

    input SessionFilter {
      # Exact application path match
      path: String
      # Case-insensitive substring match on the display name (or id when unnamed)
      nameContains: String
      ownerId: String
      workerId: String
      # ISO-8601 instant or yyyy-MM-dd (UTC); from inclusive, to exclusive
      from: String
      to: String
      # Restrict to an explicit set of session ids
      sessionIds: [String!]
    }

    # Omit a field to leave it unchanged; pass null to clear it
    input SessionPatchInput {
      name: String
      path: String
      # ISO-8601 instant or yyyy-MM-dd (UTC)
      sessionTime: String
      messageIds: [String!]
    }

    type Message {
      id: String!
      content: String
    }

    type FileEntry {
      path: String!
      size: Float
      lastModified: String
    }

    type Session {
      sessionId: String!
      name: String
      sessionTime: String
      ownerId: String
      workerId: String
      path: String
      isGlobal: Boolean!
      messageIds: [String!]!
      messageCount: Int!
      messages(limit: Int, offset: Int): [Message!]
      message(messageId: String!): Message
      files(prefix: String): [FileEntry!]
      parentSession: Session
      childSessions: [Session!]
      usage: SessionUsage!
    }

    type SessionPage {
      items: [Session!]!
      nextCursor: String
      hasMore: Boolean!
      totalCount: Int!
    }

    extend type Query {
      # A single session, or null if it has no recorded metadata
      session(sessionId: String!): Session
      # Filtered, sorted, cursor-paged session listing for the authenticated user
      sessions(filter: SessionFilter, sort: SessionSort = TIME_DESC, limit: Int = 50, cursor: String): SessionPage!
      # Distinct application paths the user has sessions under
      sessionPaths: [String!]!
    }

    type Mutation {
      updateSession(sessionId: String!, patch: SessionPatchInput!): Session!
      deleteSession(sessionId: String!): Boolean!
      setParentSession(sessionId: String!, parentSessionId: String!): Session!
    }
  """.trimIndent()

  private const val MAX_PAGE_SIZE = 1000

  // ---- Source objects ----

  class SessionSource(val user: User, val session: Session, private val entry: SessionSummary? = null) {
    val summary: SessionSummary? by lazy {
      entry ?: metadata().getSessionEntries(user, listOf(session.sessionId))[session.sessionId]
    }
    val messageIds: List<String> by lazy {
      (entry as? SessionMetadata)?.messageIds ?: metadata().getMessageIds(user, session)
    }
    val messageCount: Int by lazy {
      (entry as? SessionMetadata)?.messageIds?.size ?: metadata().getMessageCount(user, session)
    }
  }

  class SessionPageSource(val user: User, val query: SessionQuery, val result: PageResult<SessionListEntry>)

  // ---- Services ----

  private inline fun <reified T : Any> serviceOrNull(): T? = (ServiceRouter as Any) as? T
  private inline fun <reified T : Any> service(): T =
    serviceOrNull<T>() ?: throw IllegalStateException("${T::class.simpleName} is not available")

  private fun metadata(): SessionMetadataInterface = service()
  private fun storage(): StorageInterface = service()
  private fun usage(): UsageInterface = service()

  // ---- Helpers ----

  private fun DataFetchingEnvironment.user(): User =
    graphQlContext.get<User?>(UsageGraphQL.USER_KEY) ?: throw IllegalStateException("Authentication required")

  private fun DataFetchingEnvironment.requireMutationsAllowed() {
    if (graphQlContext.getOrDefault(UsageGraphQL.MUTATIONS_ALLOWED_KEY, false) != true) {
      throw IllegalStateException("Mutations must be sent with POST")
    }
  }

  private fun DataFetchingEnvironment.src(): SessionSource = getSource<SessionSource>()!!

  private fun parseSession(id: String?): Session {
    require(!id.isNullOrBlank()) { "sessionId is required" }
    return Session.parseSessionID(id)
  }

  /** Denies access to sessions owned by another user; global sessions are world-readable. */
  private fun requireAccess(user: User, session: Session, write: Boolean) {
    if (!write && session.isGlobal()) return
    val owner = metadata().getSessionOwner(user, session)
    if (owner != null && owner != user.id) {
      throw SecurityException("Access denied to session ${session.sessionId}")
    }
  }

  private fun parseInstant(s: String?): Instant? = when {
    s.isNullOrBlank() -> null
    s.length == 10 -> LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant()
    else -> Instant.parse(s)
  }

  private fun parseQuery(filter: Map<String, Any?>?, sort: String?): SessionQuery = SessionQuery(
    path = filter?.get("path") as? String,
    nameContains = filter?.get("nameContains") as? String,
    ownerId = filter?.get("ownerId") as? String,
    workerId = filter?.get("workerId") as? String,
    from = parseInstant(filter?.get("from") as? String),
    to = parseInstant(filter?.get("to") as? String),
    sessionIds = (filter?.get("sessionIds") as? List<*>)?.map { it.toString() }?.toSet(),
    sort = sort?.let { SessionSort.valueOf(it) } ?: SessionSort.TIME_DESC,
  )

  /** Key presence distinguishes "omitted" (unchanged) from explicit null (clear). */
  private fun parsePatch(p: Map<String, Any?>): SessionMetadataPatch = SessionMetadataPatch(
    name = if ("name" in p) Patch.Set(p["name"] as String?) else Patch.Unchanged,
    path = if ("path" in p) Patch.Set(p["path"] as String?) else Patch.Unchanged,
    sessionTime = if ("sessionTime" in p) Patch.Set(parseInstant(p["sessionTime"] as String?)) else Patch.Unchanged,
    messageIds = if ("messageIds" in p) {
      Patch.Set((p["messageIds"] as? List<*>)?.map { it.toString() } ?: emptyList())
    } else Patch.Unchanged,
  )

  // ---- Wiring ----

  fun wire(builder: RuntimeWiring.Builder): RuntimeWiring.Builder = builder
    .type("Query") { b ->
      b.dataFetcher("session", DataFetcher { env ->
        val user = env.user()
        val session = parseSession(env.getArgument<String>("sessionId"))
        requireAccess(user, session, write = false)
        metadata().getSessionEntries(user, listOf(session.sessionId))[session.sessionId]
          ?.let { SessionSource(user, session, it) }
      })
        .dataFetcher("sessions", DataFetcher { env ->
          val user = env.user()
          val query = parseQuery(env.getArgument<Map<String, Any?>?>("filter"), env.getArgument<String?>("sort"))
          val limit = (env.getArgument<Int?>("limit") ?: 50).coerceIn(1, MAX_PAGE_SIZE)
          val page = Page(limit = limit, cursor = env.getArgument<String?>("cursor"))
          SessionPageSource(user, query, metadata().querySessions(user, query, page))
        })
        .dataFetcher("sessionPaths", DataFetcher { env -> metadata().listSessionPaths(env.user()) })
    }
    .type("Mutation") { b ->
      b.dataFetcher("updateSession", DataFetcher { env ->
        env.requireMutationsAllowed()
        val user = env.user()
        val session = parseSession(env.getArgument<String>("sessionId"))
        requireAccess(user, session, write = true)
        val patch = env.getArgument<Map<String, Any?>>("patch") ?: emptyMap()
        metadata().updateSessionMetadata(user, session, parsePatch(patch))
        SessionSource(user, session)
      })
        .dataFetcher("deleteSession", DataFetcher { env ->
          env.requireMutationsAllowed()
          val user = env.user()
          val session = parseSession(env.getArgument<String>("sessionId"))
          requireAccess(user, session, write = true)
          val storage = serviceOrNull<StorageInterface>()
          if (storage != null) {
            storage.deleteSessionIfExists(user, session)
          } else {
            metadata().deleteSession(user, session)
            true
          }
        })
        .dataFetcher("setParentSession", DataFetcher { env ->
          env.requireMutationsAllowed()
          val user = env.user()
          val child = parseSession(env.getArgument<String>("sessionId"))
          val parent = parseSession(env.getArgument<String>("parentSessionId"))
          require(child != parent) { "A session cannot be its own parent" }
          requireAccess(user, child, write = true)
          requireAccess(user, parent, write = true)
          usage().setParentSession(user, child, parent)
          SessionSource(user, child)
        })
    }
    .type("SessionPage") { b ->
      b.dataFetcher("items", DataFetcher { env ->
        val src = env.getSource<SessionPageSource>()!!
        src.result.items.map { SessionSource(src.user, it.id, it) }
      })
        .dataFetcher("nextCursor", DataFetcher { env -> env.getSource<SessionPageSource>()!!.result.nextCursor })
        .dataFetcher("hasMore", DataFetcher { env -> env.getSource<SessionPageSource>()!!.result.hasMore })
        .dataFetcher("totalCount", DataFetcher { env ->
          val src = env.getSource<SessionPageSource>()!!
          metadata().countSessions(src.user, src.query)
        })
    }
    .type("Session") { b ->
      b.dataFetcher("sessionId", DataFetcher { env -> env.src().session.sessionId })
        .dataFetcher("name", DataFetcher { env -> env.src().summary?.name })
        .dataFetcher("sessionTime", DataFetcher { env -> env.src().summary?.sessionInstant?.toString() })
        .dataFetcher("ownerId", DataFetcher { env -> env.src().summary?.ownerId })
        .dataFetcher("workerId", DataFetcher { env -> env.src().summary?.workerId })
        .dataFetcher("path", DataFetcher { env -> env.src().summary?.path })
        .dataFetcher("isGlobal", DataFetcher { env -> env.src().session.isGlobal() })
        .dataFetcher("messageIds", DataFetcher { env -> env.src().messageIds })
        .dataFetcher("messageCount", DataFetcher { env -> env.src().messageCount })
        .dataFetcher("messages", DataFetcher { env ->
          val src = env.src()
          val offset = (env.getArgument<Int?>("offset") ?: 0).coerceAtLeast(0)
          val limit = env.getArgument<Int?>("limit")?.coerceAtLeast(0)
          val ids = src.messageIds.drop(offset).let { if (limit != null) it.take(limit) else it }
          val contents = storage().getMessages(src.user, src.session, ids)
          ids.map { id -> mapOf("id" to id, "content" to contents[id]) }
        })
        .dataFetcher("message", DataFetcher { env ->
          val src = env.src()
          val id = env.getArgument<String>("messageId") ?: throw IllegalArgumentException("messageId is required")
          storage().getMessage(src.user, src.session, id)?.let { mapOf("id" to id, "content" to it) }
        })
        .dataFetcher("files", DataFetcher { env ->
          val src = env.src()
          storage().listEntries(src.user, src.session, env.getArgument<String?>("prefix") ?: "").map {
            mapOf(
              "path" to it.path,
              "size" to it.size,
              "lastModified" to it.lastModified?.toString()
            )
          }
        })
        .dataFetcher("parentSession", DataFetcher { env ->
          val src = env.src()
          usage().getParentSession(src.user, src.session)?.let { SessionSource(src.user, it) }
        })
        .dataFetcher("childSessions", DataFetcher { env ->
          val src = env.src()
          usage().listChildSessions(src.user, src.session).map { SessionSource(src.user, it) }
        })
        .dataFetcher("usage", DataFetcher { env ->
          val src = env.src()
          UsageGraphQL.SessionUsageSource(src.user, src.session)
        })
    }
}