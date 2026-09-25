package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.SessionMetadataInterface
import com.simiacryptus.cognotik.platform.client.DeleteCountResponse
import com.simiacryptus.cognotik.platform.client.DeleteSessionRequest
import com.simiacryptus.cognotik.platform.client.ErrorResponse
import com.simiacryptus.cognotik.platform.client.ExistsResponse
import com.simiacryptus.cognotik.platform.client.MessageIdsResponse
import com.simiacryptus.cognotik.platform.client.SessionIdsResponse
import com.simiacryptus.cognotik.platform.client.SessionListEntryListResponse
import com.simiacryptus.cognotik.platform.client.SessionMetadataListResponse
import com.simiacryptus.cognotik.platform.client.SessionMetadataMapRequest
import com.simiacryptus.cognotik.platform.client.SessionMetadataMapResponse
import com.simiacryptus.cognotik.platform.client.SessionNameResponse
import com.simiacryptus.cognotik.platform.client.SessionOwnerResponse
import com.simiacryptus.cognotik.platform.client.SessionPathResponse
import com.simiacryptus.cognotik.platform.client.SessionTimestampResponse
import com.simiacryptus.cognotik.platform.client.SessionWorkerResponse
import com.simiacryptus.cognotik.platform.client.SetMessageIdsRequest
import com.simiacryptus.cognotik.platform.client.SetSessionNameRequest
import com.simiacryptus.cognotik.platform.client.SetSessionOwnerRequest
import com.simiacryptus.cognotik.platform.client.SetSessionPathRequest
import com.simiacryptus.cognotik.platform.client.SetSessionTimestampRequest
import com.simiacryptus.cognotik.platform.client.SetSessionWorkerRequest
import com.simiacryptus.cognotik.platform.client.StatusResponse
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * HTTP surface for [SessionMetadataInterface], consumed by
 * [com.simiacryptus.cognotik.platform.client.SessionMetadataClient].
 *
 * User-scoped listing operations (`listSessionMetadata`, `listSessionEntries`,
 * `sessionsForUser`, `deleteAllForUser`) act on the *authenticated caller*; there is
 * no support for listing another user's sessions through this API. Per-session
 * operations are keyed purely by session id (matching [SessionMetadataInterface]'s
 * own semantics, where `user` is mostly informational/for auditing).
 *
 * NOTE: the constructor default assumes a `metadataStorage` property on
 * [ApplicationServicesImpl.fileApplicationServices]; adjust if the actual property
 * name differs in this codebase.
 */
class MetadataStorageApiServlet(
  private val metadata: SessionMetadataInterface = ApplicationServicesImpl.fileApplicationServices().metadataDB
) : HttpServlet() {

  private fun currentUser(request: HttpServletRequest): User =
    UserProviderImpl().authenticate(request) ?: throw IllegalStateException("Authentication failed")

  private fun action(request: HttpServletRequest): String =
    (request.pathInfo ?: request.servletPath)
      ?.trim('/')?.substringBefore('/')?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("No action specified")

  private fun requireParam(request: HttpServletRequest, name: String): String =
    request.getParameter(name) ?: throw IllegalArgumentException("Missing required parameter: $name")

  private fun writeJson(response: HttpServletResponse, value: Any) {
    response.contentType = "application/json"
    response.status = HttpServletResponse.SC_OK
    response.writer.write(JsonUtil.toJson(value))
  }

  private fun writeError(response: HttpServletResponse, status: Int, message: String?) {
    response.status = status
    response.contentType = "application/json"
    response.writer.write(JsonUtil.toJson(ErrorResponse(message ?: "error")))
  }

  private fun <T> readBody(request: HttpServletRequest, clazz: Class<T>): T {
    val body = request.reader.use { it.readText() }
    return JsonUtil.fromJson(body, clazz)
  }

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      when (action(request)) {
        "sessionName" -> {
          val user = currentUser(request)
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, SessionNameResponse(metadata.getSessionName(user, session)))
        }

        "messageIds" -> {
          val user = currentUser(request)
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, MessageIdsResponse(metadata.getMessageIds(user, session)))
        }

        "sessionTimestamp" -> {
          val user = currentUser(request)
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, SessionTimestampResponse(metadata.getSessionTimestamp(user, session)?.toString()))
        }

        "sessionOwner" -> {
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, SessionOwnerResponse(metadata.getSessionOwner(session)))
        }

        "sessionWorker" -> {
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, SessionWorkerResponse(metadata.getSessionWorker(session)))
        }

        "sessionPath" -> {
          val user = currentUser(request)
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, SessionPathResponse(metadata.getSessionPath(user, session)))
        }

        "exists" -> {
          val user = currentUser(request)
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, ExistsResponse(metadata.exists(user, session)))
        }

        "sessionMetadata" -> {
          val user = currentUser(request)
          val session = Session(requireParam(request, "sessionId"))
          writeJson(response, metadata.getSessionMetadata(user, session))
        }

        "listSessionMetadata" -> {
          val user = currentUser(request)
          writeJson(response, SessionMetadataListResponse(metadata.listSessionMetadata(user)))
        }

        "listSessionMetadataByPath" -> {
          val path = requireParam(request, "path")
          writeJson(response, SessionMetadataListResponse(metadata.listSessionMetadata(path)))
        }

        "listSessionEntries" -> {
          val user = currentUser(request)
          writeJson(response, SessionListEntryListResponse(metadata.listSessionEntries(user)))
        }

        "listSessionEntriesByPath" -> {
          val path = requireParam(request, "path")
          writeJson(response, SessionListEntryListResponse(metadata.listSessionEntries(path)))
        }

        "sessionsForUser" -> {
          val user = currentUser(request)
          writeJson(response, SessionIdsResponse(metadata.listSessionsForUser(user)))
        }

        "sessionsByPath" -> {
          val path = requireParam(request, "path")
          writeJson(response, SessionIdsResponse(metadata.listSessionsByPath(path)))
        }

        else -> writeError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown action")
      }
    } catch (e: IllegalArgumentException) {
      writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, e.message)
    } catch (e: Exception) {
      log.error("Error handling metadata storage API GET request (path=${request.pathInfo})", e)
      writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message)
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      when (action(request)) {
        "setSessionName" -> {
          val user = currentUser(request)
          val req = readBody(request, SetSessionNameRequest::class.java)
          metadata.setSessionName(user, Session(req.sessionId), req.name)
          writeJson(response, StatusResponse())
        }

        "setMessageIds" -> {
          val user = currentUser(request)
          val req = readBody(request, SetMessageIdsRequest::class.java)
          metadata.setMessageIds(user, Session(req.sessionId), req.ids)
          writeJson(response, StatusResponse())
        }

        "setSessionTimestamp" -> {
          val user = currentUser(request)
          val req = readBody(request, SetSessionTimestampRequest::class.java)
          metadata.setSessionTimestamp(user, Session(req.sessionId), Instant.parse(req.timestamp))
          writeJson(response, StatusResponse())
        }

        "setSessionOwner" -> {
          val req = readBody(request, SetSessionOwnerRequest::class.java)
          metadata.setSessionOwner(Session(req.sessionId), req.ownerId)
          writeJson(response, StatusResponse())
        }

        "setSessionWorker" -> {
          val req = readBody(request, SetSessionWorkerRequest::class.java)
          metadata.setSessionWorker(Session(req.sessionId), req.workerId)
          writeJson(response, StatusResponse())
        }

        "setSessionPath" -> {
          val user = currentUser(request)
          val req = readBody(request, SetSessionPathRequest::class.java)
          metadata.setSessionPath(user, Session(req.sessionId), req.path)
          writeJson(response, StatusResponse())
        }

        "deleteSession" -> {
          val user = currentUser(request)
          val req = readBody(request, DeleteSessionRequest::class.java)
          metadata.deleteSession(user, Session(req.sessionId))
          writeJson(response, StatusResponse())
        }

        "deleteAllForUser" -> {
          val user = currentUser(request)
          writeJson(response, DeleteCountResponse(metadata.deleteAllForUser(user)))
        }

        "sessionMetadataMap" -> {
          val user = currentUser(request)
          val req = readBody(request, SessionMetadataMapRequest::class.java)
          writeJson(response, SessionMetadataMapResponse(metadata.getSessionMetadataMap(user, req.sessionIds)))
        }

        else -> writeError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown action")
      }
    } catch (e: IllegalArgumentException) {
      writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.message)
    } catch (e: IllegalStateException) {
      writeError(response, HttpServletResponse.SC_UNAUTHORIZED, e.message)
    } catch (e: Exception) {
      log.error("Error handling metadata storage API POST request (path=${request.pathInfo})", e)
      writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(MetadataStorageApiServlet::class.java)
  }
}