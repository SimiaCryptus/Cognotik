package com.simiacryptus.cognotik.platform.client

import com.simiacryptus.cognotik.platform.model.SessionListEntry
import com.simiacryptus.cognotik.platform.model.SessionMetadata

/**
 * Wire types shared between `com.simiacryptus.cognotik.webui.servlet.MetadataStorageApiServlet`
 * (server side, in the `webui` module) and [SessionMetadataClient] (client
 * side, here).
 *
 * [StatusResponse] and [ErrorResponse] are shared with the usage API and defined in
 * `UsageApiDtos.kt` (same package).
 */

data class SessionNameResponse(val name: String)
data class SetSessionNameRequest(val sessionId: String, val name: String)

data class MessageIdsResponse(val ids: List<String>)
data class SetMessageIdsRequest(val sessionId: String, val ids: List<String>)

data class SessionTimestampResponse(val timestamp: String?)
data class SetSessionTimestampRequest(val sessionId: String, val timestamp: String)

data class SessionOwnerResponse(val ownerId: String?)
data class SetSessionOwnerRequest(val sessionId: String, val ownerId: String?)

data class SessionWorkerResponse(val workerId: String?)
data class SetSessionWorkerRequest(val sessionId: String, val workerId: String?)

data class SessionPathResponse(val path: String?)
data class SetSessionPathRequest(val sessionId: String, val path: String?)

data class ExistsResponse(val exists: Boolean)
data class DeleteSessionRequest(val sessionId: String)
data class DeleteCountResponse(val deleted: Int)

data class SessionIdsResponse(val sessionIds: List<String>)
data class SessionMetadataListResponse(val items: List<SessionMetadata>)
data class SessionListEntryListResponse(val items: List<SessionListEntry>)
data class SessionMetadataMapRequest(val sessionIds: List<String>)
data class SessionMetadataMapResponse(val items: Map<String, SessionMetadata>)