package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.h2.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.model.Gift
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.servlet.GiftTemplates.escape
import com.simiacryptus.cognotik.webui.servlet.GiftTemplates.render
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GiftedCreditsServlet : HttpServlet() {

    public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        val remoteAddr = request.remoteAddr
        val requestUri = request.requestURI
        log.debug("Handling GET request from {} for {}", remoteAddr, requestUri)
        try {
            response.status = HttpServletResponse.SC_OK
            val user = try {
                ServiceRouter.authenticate(request)
            } catch (e: Exception) {
                log.warn("Authentication error during GET from {}: {}", remoteAddr, e.message, e)
                null
            }
            log.debug("GET authenticated user: {}", user)

            val theme = GiftThemes.byId(request.getParameter("theme"))

            if (request.getParameter("action") == "claim") {
                handleGetClaim(request, response, user, theme)
                return
            }

            if (user == null) {
                log.info("Unauthenticated visit to gifts page from {} - showing welcome/login page", remoteAddr)
                showLoginWelcomePage(request, response, theme)
                return
            }

            val gifts = try {
                manager.listGifts()
            } catch (e: Exception) {
                log.error("Failed to list gifts for request from {}", remoteAddr, e)
                response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to retrieve gifts: ${e.message}"
                )
                return
            }
            log.debug("Retrieved {} gifts", gifts.size)
            val filterGiftId = request.getParameter("filterGiftId")?.takeIf { it.isNotBlank() }
            val filterUserId = request.getParameter("filterUserId")?.takeIf { it.isNotBlank() }
            val claims = try {
                manager.listClaims(filterGiftId, filterUserId)
            } catch (e: Exception) {
                log.error("Failed to list claims for request from {}", remoteAddr, e)
                emptyList()
            }
            log.debug("Retrieved {} claims (filterGiftId={}, filterUserId={})", claims.size, filterGiftId, filterUserId)

            if ((request.getHeader("Accept") ?: "").contains("application/json")) {
                response.contentType = "application/json"
                try {
                    if (request.getParameter("view") == "claims") {
                        response.writer.write(claims.toJson())
                    } else {
                        response.writer.write(gifts.toJson())
                    }
                } catch (e: Exception) {
                    log.error("Failed to serialize gifts to JSON", e)
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to serialize response")
                }
                return
            }

            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

            val currentBalance = try {
                ServiceRouter.getUserBalance(user)
            } catch (e: Exception) {
                log.warn("Failed to retrieve balance for user={}: {}", user.id, e.message)
                0.0
            }

            val themeButtons = GiftThemes.ALL.joinToString("\n") { t ->
                render(
                    "theme-button.html", mapOf(
                        "href" to escape(
                            requestUri + query(
                                "theme" to t.id,
                                "filterGiftId" to filterGiftId,
                                "filterUserId" to filterUserId
                            )
                        ),
                        "activeClass" to if (t.id == theme.id) " active" else "",
                        "emoji" to t.emoji,
                        "name" to escape(t.displayName)
                    )
                )
            }

            val themeOptions = GiftThemes.ALL.joinToString("\n") { t ->
                render(
                    "theme-option.html", mapOf(
                        "id" to t.id,
                        "selected" to if (t.id == theme.id) " selected" else "",
                        "emoji" to t.emoji,
                        "name" to escape(t.displayName)
                    )
                )
            }

            val giftRows = if (gifts.isEmpty()) messageRow(10, "No gifts found.") else gifts.joinToString("\n") { gift ->
                try {
                    val giftTheme = resolveGiftTheme(gift)
                    render(
                        "gift-row.html", mapOf(
                            // Gift claim links always use the gift's assigned theme
                            "claimUrl" to escape(
                                requestUri + query("action" to "claim", "giftId" to gift.id, "theme" to giftTheme.id)
                            ),
                            "viewClaimsUrl" to escape(
                                requestUri + query("filterGiftId" to gift.id, "theme" to theme.id) + "#claims"
                            ),
                            "giftId" to escape(gift.id),
                            "amountGranted" to "%.2f".format(gift.amountGranted),
                            "durationDays" to "%.2f".format(gift.grantDuration.toMillis() / 86400000.0),
                            "expires" to dateFormatter.format(Instant.now().plus(gift.grantDuration)),
                            "totalBudget" to "%.2f".format(gift.totalBudget),
                            "spentBudget" to "%.2f".format(gift.spentBudget),
                            "claimants" to escape(gift.claimants),
                            "createdBy" to escape(gift.createdBy ?: "-"),
                            "giftThemeEmoji" to giftTheme.emoji,
                            "giftThemeName" to escape(giftTheme.displayName)
                        )
                    )
                } catch (e: Exception) {
                    log.warn("Failed to render gift row for giftId={}", gift.id, e)
                    messageRow(10, "Error rendering gift ${gift.id}")
                }
            }

            val claimRows = if (claims.isEmpty()) messageRow(3, "No claims found.") else claims.joinToString("\n") { claim ->
                try {
                    render(
                        "claim-row.html", mapOf(
                            "giftFilterUrl" to escape(
                                requestUri + query("filterGiftId" to claim.giftId, "theme" to theme.id) + "#claims"
                            ),
                            "userFilterUrl" to escape(
                                requestUri + query("filterUserId" to claim.userId, "theme" to theme.id) + "#claims"
                            ),
                            "giftId" to escape(claim.giftId),
                            "userId" to escape(claim.userId),
                            "claimedAt" to (claim.claimedAt?.let { dateFormatter.format(it) } ?: "-")
                        )
                    )
                } catch (e: Exception) {
                    log.warn("Failed to render claim row for giftId={} userId={}", claim.giftId, claim.userId, e)
                    messageRow(3, "Error rendering claim")
                }
            }

            renderPage(
                response, "main.html", theme, mapOf(
                    "requestUri" to escape(requestUri),
                    "themeButtons" to themeButtons,
                    "themeOptions" to themeOptions,
                    "balance" to "%.2f".format(currentBalance),
                    "giftRows" to giftRows,
                    "claimRows" to claimRows,
                    "filterGiftId" to escape(filterGiftId),
                    "filterUserId" to escape(filterUserId),
                    "claimsFilterSuffix" to if (filterGiftId != null || filterUserId != null) " (filtered)" else ""
                )
            )
        } catch (e: Exception) {
            log.error("Unhandled exception in doGet from {}", remoteAddr, e)
            try {
                if (!response.isCommitted) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error")
                }
            } catch (ioe: Exception) {
                log.error("Failed to send error response", ioe)
            }
        }
    }

    private fun handleGetClaim(
        request: HttpServletRequest,
        response: HttpServletResponse,
        user: User?,
        theme: GiftTheme
    ) {
        val remoteAddr = request.remoteAddr
        val requestUri = request.requestURI
        val giftId = request.getParameter("giftId")
        val confirmed = request.getParameter("confirm")?.equals("true", ignoreCase = true) == true
        log.info("Claim action via GET from user={} giftId={} confirmed={}", user, giftId, confirmed)
        if (user == null) {
            log.info(
                "Unauthenticated claim attempt from {} for giftId={} - showing login redirect page",
                remoteAddr, giftId
            )
            val gift = if (!giftId.isNullOrBlank()) {
                try {
                    manager.getGift(giftId)
                } catch (e: Exception) {
                    log.warn("Failed to retrieve gift {} for unauthenticated user: {}", giftId, e.message)
                    null
                }
            } else null
            showLoginRedirectForGift(request, response, giftId, gift, gift?.let { resolveGiftTheme(it) } ?: theme)
            return
        }
        if (giftId.isNullOrBlank()) {
            log.warn("Claim attempt with missing giftId by user={}", user)
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "giftId parameter is required")
            return
        }
        try {
            val gift = manager.getGift(giftId)
            if (gift == null) {
                log.warn("Claim attempt for non-existent giftId={} by user={}", giftId, user)
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Gift not found: $giftId")
                return
            }
            val giftTheme = resolveGiftTheme(gift)
            if (!confirmed) {
                log.debug("Showing claim confirmation page for user={} giftId={}", user, giftId)
                showClaimConfirmation(user, giftId, gift, response, requestUri, giftTheme)
                return
            }
            if (!manager.claimGift(user, giftId)) {
                log.info("Claim failed (exhausted/already claimed) for user={} giftId={}", user, giftId)
                response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Failed to claim gift. It may be exhausted or already claimed."
                )
                return
            }
            log.info("Gift claimed successfully by user={} giftId={}", user, giftId)
            claimSuccess(user, giftId, manager.getGift(giftId)!!, response, giftTheme)
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid claim request user={} giftId={}: {}", user, giftId, e.message, e)
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.message ?: "Invalid claim request")
        } catch (e: Exception) {
            log.error("Error claiming gift user={} giftId={}", user, giftId, e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message ?: "Internal server error")
        }
    }

    public override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        val remoteAddr = request.remoteAddr
        val requestUri = request.requestURI
        val action = request.getParameter("action")
        val themeParam = request.getParameter("theme")
        val theme = GiftThemes.byId(themeParam)
        log.debug("Handling POST request from {} for {} action={}", remoteAddr, requestUri, action)

        val user = try {
            ServiceRouter.authenticate(request)
        } catch (e: Exception) {
            log.warn("Authentication error during POST from {}: {}", remoteAddr, e.message, e)
            if (!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed")
            }
            return
        }
        if (user == null) {
            log.warn("Unauthenticated POST request from {} action={}", remoteAddr, action)
            if (!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
            }
            return
        }

        try {
            when (action) {
                "create" -> {
                    val amountGrantedStr = request.getParameter("amountGranted")
                    val grantDurationStr = request.getParameter("grantDuration")
                    val totalBudgetStr = request.getParameter("totalBudget")
                    val giftThemeId = request.getParameter("giftTheme")?.takeIf { it.isNotBlank() }
                    val acceptLicense = request.getParameter("acceptLicense")
                    if (acceptLicense.isNullOrBlank() || !(acceptLicense.equals("on", ignoreCase = true) ||
                                acceptLicense.equals("true", ignoreCase = true) ||
                                acceptLicense == "1")
                    ) {
                        log.warn("Gift creation rejected: license not accepted by user={}", user)
                        response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "You must accept the Cognotik Software License Agreement to create a gift. See /LICENSE.html"
                        )
                        return
                    }
                    if (amountGrantedStr.isNullOrBlank() || grantDurationStr.isNullOrBlank() || totalBudgetStr.isNullOrBlank()) {
                        log.warn(
                            "Create gift missing parameters by user={} (amount={}, duration={}, budget={})",
                            user, amountGrantedStr, grantDurationStr, totalBudgetStr
                        )
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameters")
                        return
                    }
                    val amountGranted = amountGrantedStr.toDoubleOrNull() ?: run {
                        log.warn("Invalid amountGranted '{}' by user={}", amountGrantedStr, user)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid amountGranted: $amountGrantedStr")
                        return
                    }
                    val grantDurationDays = grantDurationStr.toDoubleOrNull() ?: run {
                        log.warn("Invalid grantDuration '{}' by user={}", grantDurationStr, user)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid grantDuration: $grantDurationStr")
                        return
                    }
                    val totalBudget = totalBudgetStr.toDoubleOrNull() ?: run {
                        log.warn("Invalid totalBudget '{}' by user={}", totalBudgetStr, user)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid totalBudget: $totalBudgetStr")
                        return
                    }
                    if (amountGranted <= 0 || grantDurationDays <= 0 || totalBudget <= 0) {
                        log.warn(
                            "Create gift with non-positive values by user={} amount={} duration={} budget={}",
                            user, amountGranted, grantDurationDays, totalBudget
                        )
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Values must be positive")
                        return
                    }
                    val grantDuration = Duration.ofMillis((grantDurationDays * 86400000).toLong())
                    log.info(
                        "Creating gift by user={} amount={} duration={}d budget={} theme={}",
                        user, amountGranted, grantDurationDays, totalBudget, giftThemeId
                    )
                    try {
                        manager.createGift(user, amountGranted, grantDuration, totalBudget, giftThemeId)
                    } catch (e: IllegalArgumentException) {
                        log.warn("Gift creation rejected for user={}: {}", user, e.message)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.message ?: "Invalid gift parameters")
                        return
                    }
                    log.info("Gift created successfully by user={}", user)
                }

                "claim" -> {
                    val giftId = request.getParameter("giftId")
                    if (giftId.isNullOrBlank()) {
                        log.warn("Claim attempt with missing giftId by user={}", user)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "giftId parameter is required")
                        return
                    }
                    log.info("Claim action via POST by user={} giftId={}", user, giftId)
                    val gift = manager.getGift(giftId)
                    if (gift == null) {
                        log.warn("Claim attempt for non-existent giftId={} by user={}", giftId, user)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Gift not found: $giftId")
                        return
                    }
                    val giftTheme = resolveGiftTheme(gift).let {
                        if (it.id == GiftThemes.DEFAULT.id && themeParam != null) theme else it
                    }
                    if (!manager.claimGift(user, giftId)) {
                        log.info("Claim failed (exhausted/already claimed) for user={} giftId={}", user, giftId)
                        response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "Failed to claim gift. It may be exhausted or already claimed."
                        )
                    } else {
                        claimSuccess(user, giftId, gift, response, giftTheme)
                    }
                    return
                }

                null -> {
                    log.warn("POST request without action parameter from user={}", user)
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'action' parameter")
                    return
                }

                else -> {
                    log.warn("Unknown action '{}' from user={}", action, user)
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action: $action")
                    return
                }
            }
            response.sendRedirect(requestUri + query("theme" to theme.id))
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid argument in POST action={} user={}: {}", action, user, e.message, e)
            if (!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.message ?: "Invalid request")
            }
        } catch (e: Exception) {
            log.error("Error processing POST action={} user={}", action, user, e)
            if (!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.message ?: "Internal server error")
            }
        }
    }

    // ---------------------------------------------------------------- pages

    private fun showLoginRedirectForGift(
        request: HttpServletRequest,
        response: HttpServletResponse,
        giftId: String?,
        gift: Gift?,
        theme: GiftTheme
    ) {
        val giftDetails = when {
            gift != null -> render(
                "login-gift-details.html", mapOf(
                    "giftId" to escape(giftId ?: "(unknown)"),
                    "amount" to "%.2f".format(gift.amountGranted),
                    "themeName" to escape(theme.displayName)
                )
            )
            !giftId.isNullOrBlank() -> render("login-gift-missing.html", mapOf("giftId" to escape(giftId)))
            else -> ""
        }
        renderPage(
            response, "login-redirect.html", theme, mapOf(
                "found" to if (gift != null) "found" else "unknown",
                "giftDetails" to giftDetails,
                "loginUrl" to escape(loginUrl(request))
            )
        )
    }

    private fun showLoginWelcomePage(
        request: HttpServletRequest,
        response: HttpServletResponse,
        theme: GiftTheme
    ) {
        renderPage(response, "welcome.html", theme, mapOf("loginUrl" to escape(loginUrl(request))))
    }

    private fun showClaimConfirmation(
        userinfo: User,
        giftId: String,
        gift: Gift,
        response: HttpServletResponse,
        requestUri: String?,
        theme: GiftTheme = GiftThemes.DEFAULT
    ) {
        val base = requestUri ?: ""
        renderPage(
            response, "confirm.html", theme, mapOf(
                "giftId" to escape(giftId),
                "amount" to "%.2f".format(gift.amountGranted),
                "claimant" to escape(userinfo.email ?: userinfo.id),
                "confirmUrl" to escape(
                    base + query("action" to "claim", "giftId" to giftId, "confirm" to "true", "theme" to theme.id)
                ),
                "cancelUrl" to escape(base + query("theme" to theme.id))
            )
        )
    }

    private fun claimSuccess(
        userinfo: User,
        giftId: String,
        gift: Gift,
        response: HttpServletResponse,
        theme: GiftTheme = GiftThemes.DEFAULT
    ) {
        log.info(
            "Gift claimed successfully by user={} giftId={} amountGranted={} theme={}",
            userinfo, giftId, gift.amountGranted, theme.id
        )
        ServiceRouter.creditUser(
            user = userinfo,
            amount = gift.amountGranted,
            comment = "Claimed gift $giftId",
        )
        renderPage(
            response, "success.html", theme, mapOf(
                "giftId" to escape(giftId),
                "amount" to "%.2f".format(gift.amountGranted),
                "redirectUri" to escape("/" + query("theme" to theme.id))
            )
        )
    }

    // -------------------------------------------------------------- helpers

    /**
     * Renders a page template with the common variables every page uses.
     */
    private fun renderPage(
        response: HttpServletResponse,
        template: String,
        theme: GiftTheme,
        vars: Map<String, Any?>
    ) {
        response.contentType = "text/html;charset=UTF-8"
        response.status = HttpServletResponse.SC_OK
        val common = mapOf(
            "head" to headHtml,
            "appearanceSelector" to GiftTemplates.resource("appearance-selector.html"),
            "themeId" to theme.id,
            "themeName" to escape(theme.displayName),
            "emoji" to theme.emoji,
            "banner" to theme.bannerContent,
            "divider" to theme.dividerContent,
            "themeTitle" to theme.title,
            "subtitle" to theme.subtitle
        )
        response.writer.write(render(template, common + vars))
    }

    private fun messageRow(colspan: Int, message: String) =
        render("message-row.html", mapOf("colspan" to colspan, "message" to escape(message)))

    private fun resolveGiftTheme(gift: Gift): GiftTheme = GiftThemes.byId(gift.theme)

    private fun loginUrl(request: HttpServletRequest): String =
        "/login/" + query("target" to buildReturnTarget(request))

    private fun buildReturnTarget(request: HttpServletRequest): String {
        val uri = request.requestURI ?: "/"
        val qs = request.queryString
        return if (qs.isNullOrBlank()) uri else "$uri?$qs"
    }

    /** Builds a `?k=v&...` query string, URL-encoding values and skipping nulls. */
    private fun query(vararg params: Pair<String, String?>): String =
        params.filter { it.second != null }
            .joinToString("&", prefix = "?") { (k, v) -> "$k=${urlEncode(v!!)}" }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        val log = LoggerFactory.getLogger(GiftedCreditsServlet::class.java)!!
        val manager = GiftedCreditsDB()
        private val headHtml: String by lazy {
            render("head.html", mapOf("css" to GiftTemplates.resource("gifts.css")))
        }
    }
}