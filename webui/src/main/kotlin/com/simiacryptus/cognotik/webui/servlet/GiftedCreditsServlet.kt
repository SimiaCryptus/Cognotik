package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.model.Claim
import com.simiacryptus.cognotik.platform.model.Credits
import com.simiacryptus.cognotik.platform.model.Gift
import com.simiacryptus.cognotik.platform.model.GiftStatus
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.service.GiftedCreditsInterface
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Gifted credits: a JSON API under `/gifts/api/` plus a single-page HTML UI
 * (`/gifts/index.html`, classpath resource) that talks to it.
 *
 * Any GET that is not an API call or a known static asset is answered with the UI,
 * so legacy links such as `/gifts/?action=claim&giftId=...&theme=...` keep working
 * (the UI interprets the query string client-side).
 *
 * API:
 *  - `GET  /api/session`, `GET /api/themes`
 *  - `GET  /api/gifts[?mine=1]`, `GET /api/gifts/{id}`
 *  - `GET  /api/claims[?giftId=&userId=]`
 *  - `POST /api/gifts` – create (`description`, `message` optional)
 *  - `POST /api/gifts/{id}/claim`
 *  - `POST /api/gifts/{id}/cancel` – creator only; refunds unclaimed escrow
 */
class GiftedCreditsServlet : HttpServlet() {

  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val path = subPath(request)
    log.debug("GET {} from {}", path, request.remoteAddr)
    try {
      when {
        isApi(path) -> apiGet(request, response, segments(path))
        STATIC_ASSETS.containsKey(path) -> serveStatic(request, response, path)
        path == "/" && prefersJson(request) -> legacyJson(request, response)
        else -> serveStatic(request, response, "/index.html")
      }
    } catch (e: Exception) {
      fail(response, "GET $path", e)
    }
  }

  public override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    val path = subPath(request)
    log.debug("POST {} from {}", path, request.remoteAddr)
    try {
      if (!isApi(path)) {
        return sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Use the JSON API under /api/")
      }
      apiPost(request, response, segments(path))
    } catch (e: Exception) {
      fail(response, "POST $path", e)
    }
  }

  // ------------------------------------------------------------------ API

  private fun apiGet(request: HttpServletRequest, response: HttpServletResponse, seg: List<String>) {
    val user = authenticateOrNull(request)
    when {
      seg == listOf("session") -> sendJson(response, HttpServletResponse.SC_OK, sessionDto(user))

      seg == listOf("themes") -> sendJson(response, HttpServletResponse.SC_OK, themesDto())

      seg == listOf("gifts") -> {
        if (user == null) return unauthorized(response)
        val mineOnly = truthy(request.getParameter("mine"))
        val gifts = manager.listGifts().map(::withExtras).filter { !mineOnly || it.createdBy == user }
        sendJson(response, HttpServletResponse.SC_OK, gifts.map { giftDto(it, user) })
      }

      seg.size == 2 && seg[0] == "gifts" -> {
        val gift = loadGift(seg[1])
          ?: return sendError(response, HttpServletResponse.SC_NOT_FOUND, "Gift not found: ${seg[1]}")
        sendJson(response, HttpServletResponse.SC_OK, if (user == null) publicGiftDto(gift) else giftDto(gift, user))
      }

      seg == listOf("claims") -> {
        if (user == null) return unauthorized(response)
        val giftId = request.getParameter("giftId")?.takeIf { it.isNotBlank() }
        val userId = request.getParameter("userId")?.takeIf { it.isNotBlank() }
        sendJson(response, HttpServletResponse.SC_OK, manager.listClaims(giftId, userId).map(::claimDto))
      }

      else -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown API endpoint")
    }
  }

  private fun apiPost(request: HttpServletRequest, response: HttpServletResponse, seg: List<String>) {
    val user = authenticateOrNull(request) ?: return unauthorized(response)
    if (request.contentType?.lowercase()?.startsWith("application/json") != true) {
      return sendError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json")
    }
    when {
      seg == listOf("gifts") -> createGift(user, readBody(request), response)
      seg.size == 3 && seg[0] == "gifts" && seg[2] == "claim" -> claimGift(user, seg[1], response)
      seg.size == 3 && seg[0] == "gifts" && seg[2] == "cancel" -> cancelGift(user, seg[1], response)
      else -> sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown API endpoint")
    }
  }

  private fun createGift(user: User, body: Map<String, Any?>, response: HttpServletResponse) {
    if (!truthy(body["acceptLicense"])) {
      log.warn("Gift creation rejected: license not accepted by user={}", user)
      return sendError(
        response, HttpServletResponse.SC_BAD_REQUEST,
        "You must accept the Cognotik Software License Agreement to create a gift. See /LICENSE.html"
      )
    }
    val amountGranted = number(body["amountGranted"])
    val grantDurationDays = number(body["grantDurationDays"] ?: body["grantDuration"])
    val totalBudget = number(body["totalBudget"])
    if (amountGranted == null || grantDurationDays == null || totalBudget == null) {
      return sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid parameters")
    }
    if (amountGranted <= 0 || grantDurationDays <= 0 || totalBudget <= 0) {
      return sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Values must be positive")
    }
    if (totalBudget < amountGranted) {
      return sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Total budget must be at least the amount granted")
    }
    val description = text(body["description"], MAX_DESCRIPTION, "Description")
    val message = text(body["message"], MAX_MESSAGE, "Message")
    val hasExtras = description != null || message != null
    val themeId = (body["theme"] as? String)?.takeIf { it.isNotBlank() }?.let { GiftThemes.byId(it).id }
    log.info(
      "Creating gift by user={} amount={} duration={}d budget={} theme={} hasDescription={} hasMessage={}",
      user, amountGranted, grantDurationDays, totalBudget, themeId, description != null, message != null
    )
    val knownIds = if (hasExtras) manager.listGifts().map { it.id }.toSet() else emptySet()
    val created: Any? = try {
      manager.createGift(
        user, amountGranted, Duration.ofMillis((grantDurationDays * 86400000).toLong()), totalBudget, themeId
      )
    } catch (e: IllegalArgumentException) {
      log.warn("Gift creation rejected for user={}: {}", user, e.message)
      return sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.message ?: "Invalid gift parameters")
    } catch (e: IllegalStateException) {
      log.warn("Gift creation rejected for user={}: {}", user, e.message)
      return sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.message ?: "Unable to create gift")
    }
    val giftId = (created as? Gift)?.id
      ?: manager.listGifts().filter { it.id !in knownIds && it.createdBy == user }.singleOrNull()?.id
    if (hasExtras) {
      if (giftId != null) {
        extras.update(giftId) { it.copy(description = description, message = message) }
      } else {
        log.warn("Gift created by user={} but its id could not be determined; description/message not saved", user)
      }
    }
    sendJson(
      response, HttpServletResponse.SC_CREATED,
      mapOf("created" to true, "giftId" to giftId, "balance" to balanceOf(user))
    )
  }

  private fun claimGift(user: User, giftId: String, response: HttpServletResponse) {
    if (manager.getGift(giftId) == null) {
      return sendError(response, HttpServletResponse.SC_NOT_FOUND, "Gift not found: $giftId")
    }
    withGiftLock(giftId) {
      val gift = loadGift(giftId)
        ?: return@withGiftLock sendError(response, HttpServletResponse.SC_NOT_FOUND, "Gift not found: $giftId")
      inactiveReason(gift)?.let { (status, reason) ->
        log.info("Claim rejected ({}) for user={} giftId={}", status, user, giftId)
        return@withGiftLock sendError(response, status, reason)
      }
      if (!manager.claimGift(user, giftId)) {
        log.info("Claim failed (exhausted/already claimed) for user={} giftId={}", user, giftId)
        return@withGiftLock sendError(
          response, HttpServletResponse.SC_CONFLICT,
          "Failed to claim gift. It may be exhausted or already claimed."
        )
      }
      ServiceRouter.creditUser(user = user, amount = gift.amountGranted, comment = "Claimed gift $giftId")
      log.info("Gift claimed by user={} giftId={} amount={}", user, giftId, gift.amountGranted)
      sendJson(
        response, HttpServletResponse.SC_OK, mapOf(
          "giftId" to giftId,
          "amountGranted" to gift.amountGranted,
          "theme" to GiftThemes.byId(gift.theme).id,
          "message" to gift.message,
          "balance" to balanceOf(user)
        )
      )
    }
  }

  private fun cancelGift(user: User, giftId: String, response: HttpServletResponse) {
    if (manager.getGift(giftId) == null) {
      return sendError(response, HttpServletResponse.SC_NOT_FOUND, "Gift not found: $giftId")
    }
    withGiftLock(giftId) {
      val gift = loadGift(giftId)
        ?: return@withGiftLock sendError(response, HttpServletResponse.SC_NOT_FOUND, "Gift not found: $giftId")
      val creator = gift.createdBy
      if (creator == null || creator != user) {
        log.warn("Cancel rejected: user={} is not the creator of giftId={}", user, giftId)
        return@withGiftLock sendError(response, HttpServletResponse.SC_FORBIDDEN, "Only the creator of a gift can cancel it")
      }
      if (gift.isCancelled) {
        return@withGiftLock sendError(response, HttpServletResponse.SC_CONFLICT, "This gift has already been cancelled")
      }
      if (gift.revoked) {
        return@withGiftLock sendError(response, HttpServletResponse.SC_CONFLICT, "This gift has been revoked and cannot be cancelled")
      }

      val refund = gift.escrowCredits
      val backend = (manager as Any) as? GiftedCreditsInterface
      val delegated = try {
        backend?.cancelGift(gift.giftId, user)
      } catch (e: UnsupportedOperationException) {
        null
      }

      val refunded: Credits = if (delegated != null) {
        // Backend stopped claims and refunded atomically; just record it for display.
        extras.markCancelled(giftId, delegated.cancelledAt, user.id, delegated.refunded.toDouble())
        delegated.refunded
      } else {
        // Record the cancellation first so no claim can slip in, then refund the escrow.
        extras.markCancelled(giftId, Instant.now(), user.id, 0.0)
        try {
          if (refund.isPositive) {
            ServiceRouter.creditUser(
              user = user, amount = refund.toDouble(), comment = "Refund of cancelled gift $giftId"
            )
          }
        } catch (e: Exception) {
          log.error("Refund failed for cancelled giftId={}; reverting cancellation", giftId, e)
          extras.clearCancellation(giftId)
          throw e
        }
        extras.setRefunded(giftId, refund.toDouble())
        refund
      }

      log.info("Gift cancelled by user={} giftId={} refunded={}", user, giftId, refunded)
      sendJson(
        response, HttpServletResponse.SC_OK, mapOf(
          "giftId" to giftId,
          "cancelled" to true,
          "refunded" to refunded.toDouble(),
          "balance" to balanceOf(user),
          "gift" to loadGift(giftId)?.let { giftDto(it, user) }
        )
      )
    }
  }

  /** Backward compatibility: `GET /gifts/` with `Accept: application/json`. */
  private fun legacyJson(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticateOrNull(request) ?: return unauthorized(response)
    if (request.getParameter("view") == "claims") {
      val giftId = request.getParameter("filterGiftId")?.takeIf { it.isNotBlank() }
      val userId = request.getParameter("filterUserId")?.takeIf { it.isNotBlank() }
      sendJson(response, HttpServletResponse.SC_OK, manager.listClaims(giftId, userId).map(::claimDto))
    } else {
      sendJson(response, HttpServletResponse.SC_OK, manager.listGifts().map(::withExtras).map { giftDto(it, user) })
    }
  }

  // --------------------------------------------------------------- gifts

  /** Overlays servlet-side extras (description, message, cancellation) onto a DB gift. */
  private fun withExtras(gift: Gift): Gift {
    val x = extras[gift.id] ?: return gift
    return gift.copy(
      description = gift.description ?: x.description,
      message = gift.message ?: x.message,
      cancelledAt = gift.cancelledAt ?: x.cancelledAt,
      refundedBudget = if (gift.refundedBudget > 0) gift.refundedBudget else (x.refunded ?: 0.0)
    )
  }

  private fun loadGift(id: String): Gift? = manager.getGift(id)?.let(::withExtras)

  /** HTTP status + message explaining why a gift cannot be claimed, or null if it can. */
  private fun inactiveReason(gift: Gift): Pair<Int, String>? = when (gift.status()) {
    GiftStatus.CANCELLED -> HttpServletResponse.SC_GONE to "This gift has been cancelled by its creator."
    GiftStatus.REVOKED -> HttpServletResponse.SC_GONE to "This gift has been revoked."
    GiftStatus.EXPIRED -> HttpServletResponse.SC_GONE to "This gift has expired."
    GiftStatus.EXHAUSTED -> HttpServletResponse.SC_CONFLICT to "This gift has been fully claimed."
    GiftStatus.ACTIVE -> null
  }

  private inline fun <T> withGiftLock(giftId: String, block: () -> T): T =
    synchronized(giftLocks.computeIfAbsent(giftId) { Any() }) { block() }

  // ----------------------------------------------------------------- DTOs

  private fun sessionDto(user: User?): Map<String, Any?> = if (user == null) {
    mapOf("authenticated" to false)
  } else {
    mapOf(
      "authenticated" to true,
      "userId" to user.id,
      "email" to user.email,
      "balance" to balanceOf(user)
    )
  }

  private fun themesDto(): Map<String, Any?> = mapOf(
    "default" to GiftThemes.DEFAULT.id,
    "themes" to GiftThemes.ALL.map {
      mapOf(
        "id" to it.id,
        "displayName" to it.displayName,
        "emoji" to it.emoji,
        "banner" to it.bannerContent,
        "divider" to it.dividerContent,
        "title" to it.title,
        "subtitle" to it.subtitle
      )
    }
  )

  private fun giftDto(gift: Gift, viewer: User?): Map<String, Any?> {
    val now = Instant.now()
    val mine = viewer != null && gift.createdBy == viewer
    return linkedMapOf(
      "id" to gift.id,
      "amountGranted" to gift.amountGranted,
      "grantDurationDays" to gift.grantDuration.toMillis() / 86400000.0,
      "totalBudget" to gift.totalBudget,
      "spentBudget" to gift.spentBudget,
      "claimants" to gift.claimants.toString(),
      "claimCount" to gift.claimants,
      "createdBy" to gift.createdBy?.name,
      "theme" to GiftThemes.byId(gift.theme).id,
      "status" to gift.status(now).wireName,
      "deliveredCredits" to gift.deliveredCredits.toDouble(),
      "escrowCredits" to gift.escrowCredits.toDouble(),
      "refundedCredits" to gift.refundedBudgetCredits.toDouble(),
      "remainingClaims" to gift.remainingClaims(now),
      "expiresAt" to gift.expiresAt?.toString(),
      "cancelledAt" to gift.cancelledAt?.toString(),
      "message" to gift.message,
      "description" to if (mine) gift.description else null,
      "mine" to mine,
      "canCancel" to (mine && gift.isCancelable())
    )
  }

  /** What an unauthenticated visitor holding a gift link may see. */
  private fun publicGiftDto(gift: Gift): Map<String, Any?> = linkedMapOf(
    "id" to gift.id,
    "amountGranted" to gift.amountGranted,
    "theme" to GiftThemes.byId(gift.theme).id,
    "status" to gift.status().wireName,
    "message" to gift.message
  )

  private fun claimDto(claim: Claim): Map<String, Any?> = linkedMapOf(
    "giftId" to claim.giftId,
    "userId" to claim.userId,
    "claimedAt" to claim.claimedAt?.toString()
  )


  // -------------------------------------------------------------- helpers

  private fun serveStatic(request: HttpServletRequest, response: HttpServletResponse, path: String) {
    val contentType = STATIC_ASSETS[path] ?: return sendError(response, HttpServletResponse.SC_NOT_FOUND, "Not found")
    var body = resource(path.removePrefix("/"))
    if (path == "/index.html") body = body.replace("{{base}}", htmlEscape(request.contextPath ?: ""))
    response.status = HttpServletResponse.SC_OK
    response.contentType = contentType
    response.setHeader("Cache-Control", "no-cache")
    response.writer.write(body)
  }

  private fun subPath(request: HttpServletRequest): String =
    (request.requestURI ?: "/").removePrefix(request.contextPath ?: "").ifEmpty { "/" }

  private fun isApi(path: String) = path == "/api" || path.startsWith("/api/")

  private fun segments(path: String): List<String> =
    path.removePrefix("/api").split('/').filter { it.isNotEmpty() }
      .map { URLDecoder.decode(it, StandardCharsets.UTF_8) }

  private fun prefersJson(request: HttpServletRequest): Boolean {
    val accept = request.getHeader("Accept") ?: return false
    return accept.contains("application/json") && !accept.contains("text/html")
  }

  private fun authenticateOrNull(request: HttpServletRequest): User? = try {
    ServiceRouter.authenticate(request)
  } catch (e: Exception) {
    log.warn("Authentication error from {}: {}", request.remoteAddr, e.message)
    null
  }

  private fun balanceOf(user: User): Double = try {
    ServiceRouter.getUserBalance(user)
  } catch (e: Exception) {
    log.warn("Failed to retrieve balance for user={}: {}", user.id, e.message)
    0.0
  }

  @Suppress("UNCHECKED_CAST")
  private fun readBody(request: HttpServletRequest): Map<String, Any?> {
    val text = request.reader.readText()
    if (text.isBlank()) return emptyMap()
    return mapper.readValue(text, Map::class.java) as Map<String, Any?>
  }

  private fun number(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.trim().toDoubleOrNull()
    else -> null
  }?.takeIf { it.isFinite() }

  /** Optional free text: trimmed, null when blank; throws IllegalArgumentException (400) when too long. */
  private fun text(v: Any?, max: Int, field: String): String? {
    val s = (v as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    require(s.length <= max) { "$field must be at most $max characters" }
    return s
  }

  private fun truthy(v: Any?): Boolean = when (v) {
    is Boolean -> v
    is Number -> v.toInt() == 1
    is String -> v.equals("true", true) || v.equals("on", true) || v == "1"
    else -> false
  }

  private fun sendJson(response: HttpServletResponse, status: Int, body: Any?) {
    response.status = status
    response.contentType = "application/json;charset=UTF-8"
    response.setHeader("Cache-Control", "no-store")
    response.writer.write(mapper.writeValueAsString(body))
  }

  private fun sendError(response: HttpServletResponse, status: Int, message: String) {
    if (response.isCommitted) return
    sendJson(response, status, mapOf("error" to message))
  }

  private fun unauthorized(response: HttpServletResponse) =
    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")

  private fun fail(response: HttpServletResponse, what: String, e: Exception) {
    if (e is IllegalArgumentException) {
      log.warn("Bad request in {}: {}", what, e.message)
      sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.message ?: "Invalid request")
    } else {
      log.error("Unhandled exception in {}", what, e)
      sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message ?: "Internal server error")
    }
  }

  private fun htmlEscape(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&#39;")

  companion object {
    val log = LoggerFactory.getLogger(GiftedCreditsServlet::class.java)!!
    val manager = GiftedCreditsDB()
    val extras: GiftExtrasStore by lazy { GiftExtrasStore.default() }
    private val mapper = ObjectMapper()
    private val giftLocks = ConcurrentHashMap<String, Any>()

    const val MAX_DESCRIPTION = 200
    const val MAX_MESSAGE = 1000

    private const val RESOURCE_BASE = "/gifts/"
    private val STATIC_ASSETS = mapOf(
      "/index.html" to "text/html;charset=UTF-8",
      "/gifts.css" to "text/css;charset=UTF-8"
    )
    private val cache = ConcurrentHashMap<String, String>()

    private fun resource(name: String): String = cache.computeIfAbsent(name) { n ->
      GiftedCreditsServlet::class.java.getResourceAsStream(RESOURCE_BASE + n)?.use {
        String(it.readAllBytes(), StandardCharsets.UTF_8)
      } ?: throw IllegalStateException("Missing resource $RESOURCE_BASE$n")
    }
  }
}