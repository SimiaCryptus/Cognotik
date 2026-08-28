package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.hsql.GiftedCreditsDB
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Gift
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
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

/**
 * Represents a visual theme for the Gifted Credits UI.
 */
data class GiftTheme(
    val id: String,
    val displayName: String,
    val emoji: String,
    val fontFamily: String,
    val headingFont: String,
    val gradientStart: String,
    val gradientMid: String,
    val gradientEnd: String,
    val gradientExtra: String,
    val primaryColor: String,
    val primaryDark: String,
    val accentColor: String,
    val accentLight: String,
    val textColor: String,
    val cardBackground: String,
    val cardAccent: String,
    val tableEvenRow: String,
    val tableHoverRow: String,
    val confettiContent: String,
    val bannerContent: String,
    val dividerContent: String,
    val rainContent: String,
    val title: String,
    val subtitle: String
)

object GiftThemes {
    val PINK_PARTY = GiftTheme(
        id = "pink-party",
        displayName = "Pink Party",
        emoji = "💖",
        fontFamily = "'Quicksand', 'Arial', sans-serif",
        headingFont = "'Pacifico', cursive",
        gradientStart = "#ff4d8d",
        gradientMid = "#ff85a2",
        gradientEnd = "#ffb6c1",
        gradientExtra = "#ff4d8d",
        primaryColor = "#e91e63",
        primaryDark = "#c2185b",
        accentColor = "#ff85a2",
        accentLight = "#ffd1dc",
        textColor = "#2c2c2c",
        cardBackground = "rgba(255, 255, 255, 0.97)",
        cardAccent = "#fff0f5",
        tableEvenRow = "#fff0f5",
        tableHoverRow = "#ffe0ec",
        confettiContent = "💖 💗 💕 💞 💘 💝 💖 💗 💕 💞 💘 💝",
        bannerContent = "💖 🎂 💖 🎈 💖",
        dividerContent = "💖 🎈 💖 🎈 💖 🎈 💖",
        rainContent = "💖 💗 💕 💞 💘 💝 💖 💗 💕 💞 💘 💝",
        title = "🎂 💝 Gifted Credits 💝 🎈",
        subtitle = "✨ Your account has been credited with love! ✨"
    )
    val OCEAN = GiftTheme(
        id = "ocean",
        displayName = "Ocean Breeze",
        emoji = "🌊",
        fontFamily = "'Quicksand', 'Arial', sans-serif",
        headingFont = "'Pacifico', cursive",
        gradientStart = "#006994",
        gradientMid = "#0099cc",
        gradientEnd = "#66d9ef",
        gradientExtra = "#006994",
        primaryColor = "#0277bd",
        primaryDark = "#01579b",
        accentColor = "#4fc3f7",
        accentLight = "#b3e5fc",
        textColor = "#1a3a4a",
        cardBackground = "rgba(255, 255, 255, 0.97)",
        cardAccent = "#e1f5fe",
        tableEvenRow = "#e1f5fe",
        tableHoverRow = "#b3e5fc",
        confettiContent = "🌊 🐚 🐬 🐠 🌊 🐚 🐬 🐠 🌊 🐚 🐬 🐠",
        bannerContent = "🌊 🐚 🌊 🐬 🌊",
        dividerContent = "🌊 🐚 🌊 🐬 🌊 🐠 🌊",
        rainContent = "🌊 🐚 🐬 🐠 💧 🐳 🌊 🐚 🐬 🐠 💧 🐳",
        title = "🌊 🐚 Gifted Credits 🐬 🐠",
        subtitle = "🌊 Your account has been credited! 🐚"
    )
    val FOREST = GiftTheme(
        id = "forest",
        displayName = "Forest Glade",
        emoji = "🌲",
        fontFamily = "'Quicksand', 'Arial', sans-serif",
        headingFont = "'Pacifico', cursive",
        gradientStart = "#1b5e20",
        gradientMid = "#388e3c",
        gradientEnd = "#81c784",
        gradientExtra = "#1b5e20",
        primaryColor = "#2e7d32",
        primaryDark = "#1b5e20",
        accentColor = "#66bb6a",
        accentLight = "#c8e6c9",
        textColor = "#1a2e1a",
        cardBackground = "rgba(255, 255, 255, 0.97)",
        cardAccent = "#e8f5e9",
        tableEvenRow = "#e8f5e9",
        tableHoverRow = "#c8e6c9",
        confettiContent = "🌲 🍃 🌿 🌳 🍀 🌱 🌲 🍃 🌿 🌳 🍀 🌱",
        bannerContent = "🌲 🍃 🌳 🌿 🌲",
        dividerContent = "🌲 🍃 🌳 🌿 🍀 🌱 🌲",
        rainContent = "🌲 🍃 🌿 🌳 🍀 🌱 🦋 🐿️ 🌲 🍃 🌿 🌳",
        title = "🌲 🍃 Gifted Credits 🌿 🌳",
        subtitle = "🍃 Your account has been credited naturally! 🌿"
    )
    val SUNSET = GiftTheme(
        id = "sunset",
        displayName = "Sunset Glow",
        emoji = "🌅",
        fontFamily = "'Quicksand', 'Arial', sans-serif",
        headingFont = "'Pacifico', cursive",
        gradientStart = "#ff6e40",
        gradientMid = "#ff9100",
        gradientEnd = "#ffd54f",
        gradientExtra = "#ff6e40",
        primaryColor = "#e65100",
        primaryDark = "#bf360c",
        accentColor = "#ffab40",
        accentLight = "#ffe0b2",
        textColor = "#3e2723",
        cardBackground = "rgba(255, 255, 255, 0.97)",
        cardAccent = "#fff3e0",
        tableEvenRow = "#fff3e0",
        tableHoverRow = "#ffe0b2",
        confettiContent = "🌅 ☀️ 🔥 🌇 🌄 🌞 🌅 ☀️ 🔥 🌇 🌄 🌞",
        bannerContent = "🌅 ☀️ 🌇 🌄 🌅",
        dividerContent = "🌅 ☀️ 🔥 🌇 🌄 🌞 🌅",
        rainContent = "🌅 ☀️ 🔥 🌇 🌄 🌞 🦩 🌅 ☀️ 🔥 🌇 🌄",
        title = "🌅 ☀️ Gifted Credits 🌇 🌄",
        subtitle = "☀️ Your account glows with new credits! 🌅"
    )
    val MIDNIGHT = GiftTheme(
        id = "midnight",
        displayName = "Midnight Sky",
        emoji = "🌙",
        fontFamily = "'Quicksand', 'Arial', sans-serif",
        headingFont = "'Pacifico', cursive",
        gradientStart = "#0d1b2a",
        gradientMid = "#1b263b",
        gradientEnd = "#415a77",
        gradientExtra = "#0d1b2a",
        primaryColor = "#7e57c2",
        primaryDark = "#5e35b1",
        accentColor = "#9575cd",
        accentLight = "#d1c4e9",
        textColor = "#e8e8f0",
        cardBackground = "rgba(30, 30, 50, 0.97)",
        cardAccent = "#2a2a4a",
        tableEvenRow = "#252540",
        tableHoverRow = "#3a3a5a",
        confettiContent = "🌙 ⭐ ✨ 🌟 💫 🌌 🌙 ⭐ ✨ 🌟 💫 🌌",
        bannerContent = "🌙 ⭐ ✨ 🌟 💫",
        dividerContent = "🌙 ⭐ ✨ 🌟 💫 🌌 🌙",
        rainContent = "🌙 ⭐ ✨ 🌟 💫 🌌 🛸 🪐 🌙 ⭐ ✨ 🌟",
        title = "🌙 ⭐ Gifted Credits ✨ 🌟",
        subtitle = "✨ Cosmic credits added to your account! 🌟"
    )
    val CANDY = GiftTheme(
        id = "candy",
        displayName = "Candy Land",
        emoji = "🍭",
        fontFamily = "'Quicksand', 'Arial', sans-serif",
        headingFont = "'Pacifico', cursive",
        gradientStart = "#ff1493",
        gradientMid = "#9c27b0",
        gradientEnd = "#00bcd4",
        gradientExtra = "#ff1493",
        primaryColor = "#d81b60",
        primaryDark = "#880e4f",
        accentColor = "#ba68c8",
        accentLight = "#e1bee7",
        textColor = "#2c2c2c",
        cardBackground = "rgba(255, 255, 255, 0.97)",
        cardAccent = "#f3e5f5",
        tableEvenRow = "#f3e5f5",
        tableHoverRow = "#e1bee7",
        confettiContent = "🍭 🍬 🍫 🧁 🍰 🎂 🍭 🍬 🍫 🧁 🍰 🎂",
        bannerContent = "🍭 🍬 🍫 🧁 🍰",
        dividerContent = "🍭 🍬 🍫 🧁 🍰 🎂 🍭",
        rainContent = "🍭 🍬 🍫 🧁 🍰 🎂 🍩 🍪 🍭 🍬 🍫 🧁",
        title = "🍭 🍬 Gifted Credits 🧁 🍰",
        subtitle = "🍭 Your account got a sweet credit boost! 🍬"
    )
    val ALL = listOf(PINK_PARTY, OCEAN, FOREST, SUNSET, MIDNIGHT, CANDY)
    val DEFAULT = PINK_PARTY
    fun byId(id: String?): GiftTheme = ALL.firstOrNull { it.id == id } ?: DEFAULT
}

class GiftedCreditsServlet : HttpServlet() {
    public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        val remoteAddr = request.remoteAddr
        val requestUri = request.requestURI
        log.debug("Handling GET request from {} for {}", remoteAddr, requestUri)
        try {
            response.status = HttpServletResponse.SC_OK
            val user = try {
              UserProviderImpl().authenticate(request, response)
            } catch (e: Exception) {
                log.warn("Authentication error during GET from {}: {}", remoteAddr, e.message, e)
                null
            }
            log.debug("GET authenticated user: {}", user)

            val action = request.getParameter("action")
            val themeParam = request.getParameter("theme")
            val theme = GiftThemes.byId(themeParam)

            if (action == "claim") {
                val giftId = request.getParameter("giftId")
                val confirmed = request.getParameter("confirm")?.equals("true", ignoreCase = true) == true
                log.info("Claim action via GET from user={} giftId={} confirmed={}", user, giftId, confirmed)
                if (user == null) {
                    log.info(
                        "Unauthenticated claim attempt from {} for giftId={} - showing login redirect page",
                        remoteAddr,
                        giftId
                    )
                    val gift = if (!giftId.isNullOrBlank()) {
                        try {
                            manager.getGift(giftId)
                        } catch (e: Exception) {
                            log.warn("Failed to retrieve gift {} for unauthenticated user: {}", giftId, e.message)
                            null
                        }
                    } else null
                    val giftTheme = gift?.let { resolveGiftTheme(it) } ?: theme
                    showLoginRedirectForGift(request, response, giftId, gift, giftTheme)
                    return
                }
                if (giftId.isNullOrBlank()) {
                    log.warn("Claim attempt with missing giftId by user={}", user)
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "giftId parameter is required")
                    return
                }
                try {
                    val gift = manager.getGift(giftId)
                    val giftTheme = gift?.let { resolveGiftTheme(it) } ?: theme
                    if (gift == null) {
                        log.warn("Claim attempt for non-existent giftId={} by user={}", giftId, user)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Gift not found: $giftId")
                        return
                    }
                    if (!confirmed) {
                        // Show confirmation page before actually claiming
                        log.debug("Showing claim confirmation page for user={} giftId={}", user, giftId)
                        showClaimConfirmation(user, giftId, gift, response, requestUri, giftTheme)
                        return
                    }
                    val success = manager.claimGift(user, giftId)
                    if (!success) {
                        log.info("Claim failed (exhausted/already claimed) for user={} giftId={}", user, giftId)
                        response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "Failed to claim gift. It may be exhausted or already claimed."
                        )
                        return
                    }
                    log.info("Gift claimed successfully by user={} giftId={}", user, giftId)
                    claimSuccess(user, giftId, manager.getGift(giftId)!!, response, requestUri, giftTheme)
                    return
                } catch (e: IllegalArgumentException) {
                    log.warn("Invalid claim request user={} giftId={}: {}", user, giftId, e.message, e)
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.message ?: "Invalid claim request")
                    return
                } catch (e: Exception) {
                    log.error("Error claiming gift user={} giftId={}", user, giftId, e)
                    response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        e.message ?: "Internal server error"
                    )
                    return
                }
            }

            if (user == null) {
                log.info("Unauthenticated visit to gifts page from {} - showing welcome/login page", remoteAddr)
                showLoginWelcomePage(request, response, theme)
                return
            }

            // Admins see all gifts; regular users see only gifts they created
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
            val requestedFilterGiftId = request.getParameter("filterGiftId")?.takeIf { it.isNotBlank() }
            val requestedFilterUserId = request.getParameter("filterUserId")?.takeIf { it.isNotBlank() }

            val filterGiftId = requestedFilterGiftId
            val filterUserId = requestedFilterUserId
            val claims = try {
                manager.listClaims(filterGiftId, filterUserId)
            } catch (e: Exception) {
                log.error("Failed to list claims for request from {}", remoteAddr, e)
                emptyList()
            }
            log.debug(
                "Retrieved {} claims (filterGiftId={}, filterUserId={})", claims.size, filterGiftId, filterUserId
            )

            val acceptHeader = request.getHeader("Accept") ?: ""
            if (acceptHeader.contains("application/json")) {
                response.contentType = "application/json"
                try {
                    val view = request.getParameter("view")
                    if (view == "claims") {
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

            response.contentType = "text/html"
            // Build navigation bar
            val navBarHtml = buildNavBar("gifts", theme)


            // Build theme selector
            val themeSelector = buildString {
                append("""<div class="theme-selector"><span class="theme-label">🎨 Theme:</span>""")
                GiftThemes.ALL.forEach { t ->
                    val params = mutableListOf<String>()
                    params.add("theme=${t.id}")
                    filterGiftId?.let { params.add("filterGiftId=$it") }
                    requestedFilterUserId?.let { params.add("filterUserId=$it") }
                    val href = "$requestUri?${params.joinToString("&")}"
                    val activeClass = if (t.id == theme.id) " active" else ""
                    append("""<a class="theme-btn$activeClass" href="$href" title="${t.displayName}">${t.emoji} ${t.displayName}</a>""")
                }
                append("</div>")
            }

            // Gift creation is now available to all authenticated users.
            // Show current balance so users know what they can afford.
            val currentBalance = try {
                ApplicationServices.fileApplicationServices().usageDB.getUserBalance(user)
            } catch (e: Exception) {
                log.warn("Failed to retrieve balance for user={}: {}", user.id, e.message)
                0.0
            }
            val createGiftSection = run {
                val themeOptions = GiftThemes.ALL.joinToString("\n") { t ->
                    val selected = if (t.id == theme.id) " selected" else ""
                    """<option value="${t.id}"$selected>${t.emoji} ${t.displayName}</option>"""
                }
                """
                <div class="form-container">
                    <h2>🎂 Create Gift</h2>
                     <p style="margin-top:-5px;"><strong>💳 Your Current Balance:</strong> ${"%.2f".format(currentBalance)} credits</p>
                     <p style="font-size:0.9em;color:#666;">The total budget will be deducted from your account when the gift is created.</p>
                     <form method="post" action="$requestUri">
                        <input type="hidden" name="action" value="create"/>
                        <input type="hidden" name="theme" value="${theme.id}"/>
                        <label>💰 Amount Granted: <input type="number" step="0.01" name="amountGranted" required/></label>
                         <label>⏰ Grant Duration (days): <input type="number" step="0.01" name="grantDuration" required/></label>
                        <label>💝 Total Budget: <input type="number" step="0.01" name="totalBudget" required/></label>
                        <label>🎨 Gift Theme:
                            <select name="giftTheme">
                                $themeOptions
                            </select>
                        </label>
                        <div class="license-notice">
                            <label style="display:flex;align-items:flex-start;gap:8px;font-weight:500;font-size:0.95em;">
                                <input type="checkbox" name="acceptLicense" required style="width:auto;margin-top:4px;"/>
                                <span>I have read and agree to the <a href="/LICENSE.html" target="_blank" rel="noopener" class="license-link">Cognotik Software License Agreement</a>, including the terms regarding API Credits, gifts, and non-refundability (Section 8.2).</span>
                            </label>
                        </div>
                        <button type="submit">✨ Create Gift ✨</button>
                    </form>
                </div>
                    """.trimIndent()
            }
            val giftsTableHeading = "Available Gifts (All)"
            val giftsTable = if (gifts.isNotEmpty() || true) {
                """
                 <h2>${theme.emoji} $giftsTableHeading ${theme.emoji}</h2>
                <table>
                    <tr class="table-header">
                        <th>🎫 ID</th>
                        <th>💰 Amount Granted</th>
                         <th>⏰ Duration (days)</th>
                         <th>📅 Expires</th>
                        <th>💝 Total Budget</th>
                        <th>💸 Spent Budget</th>
                        <th>👥 Claimants</th>
                         <th>👤 Created By</th>
                         <th>🎨 Theme</th>
                         <th>⚡ Actions</th>
                    </tr>
                    ${
                    gifts.joinToString("\n") { gift ->
                        try {
                            val durationDays = gift.grantDuration.toMillis() / 86400000.0
                            val expirationInstant = Instant.now().plus(gift.grantDuration)
                            val expirationFormatted = dateFormatter.format(expirationInstant)
                            val giftTheme = resolveGiftTheme(gift)
                            // Gift claim links always use the gift's assigned theme
                            val claimUrl = "$requestUri?action=claim&giftId=${gift.id}&theme=${giftTheme.id}"
                            val viewClaimsUrl = "$requestUri?filterGiftId=${gift.id}&theme=${theme.id}#claims"
                            """
                        <tr>
                             <td><a class="claim-link" href="$claimUrl" title="Click to claim this gift (Theme: ${giftTheme.displayName})">${gift.id}</a></td>
                            <td>${"%.2f".format(gift.amountGranted)}</td>
                             <td>${"%.2f".format(durationDays)}</td>
                             <td>$expirationFormatted</td>
                            <td>${"%.2f".format(gift.totalBudget)}</td>
                            <td>${"%.2f".format(gift.spentBudget)}</td>
                            <td>${gift.claimants}</td>
                             <td>${gift.createdBy ?: "-"}</td>
                            <td>${giftTheme.emoji} ${giftTheme.displayName}</td>
                             <td><a class="claim-link" href="$viewClaimsUrl" title="View claims for this gift">🔍 View Claims</a></td>
                        </tr>
                        """.trimIndent()
                        } catch (e: Exception) {
                            log.warn("Failed to render gift row for giftId={}", gift.id, e)
                            "<tr><td colspan=\"10\">Error rendering gift ${gift.id}</td></tr>"
                        }
                    }
                }
                </table>
                    """.trimIndent()
            } else ""
            val claimsFilterForm = """
             <div class="form-container">
                 <form method="get" action="$requestUri#claims">
                     <input type="hidden" name="theme" value="${theme.id}"/>
                     <label>🎫 Filter by Gift ID: <input type="text" name="filterGiftId" value="${filterGiftId ?: ""}"/></label>
                     <label>👤 Filter by User ID: <input type="text" name="filterUserId" value="${requestedFilterUserId ?: ""}"/></label>
                     <button type="submit">🔍 Filter</button>
                     <a class="claim-link" href="$requestUri?theme=${theme.id}#claims" style="margin-left:10px;">✖ Clear</a>
                 </form>
             </div>
                """.trimIndent()
            val claimsHeading =
                "💌 All Claims${if (filterGiftId != null || requestedFilterUserId != null) " (filtered)" else ""} 💌"

            response.writer.write(
                """
                <html>
                <head>
                    <title>${theme.emoji} Gifted Credits ${theme.emoji}</title>
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    <script src="/modules/theme.js"></script>
                    <script>
                        try { ThemeManager.init(); } catch (e) { console.warn('ThemeManager init failed', e); }
                    </script>
                    <style>
                        ${buildThemeStyles(theme)}
                        .theme-selector {
                            background: rgba(255, 255, 255, 0.85);
                            padding: 15px 20px;
                            border-radius: 12px;
                            margin-bottom: 20px;
                            text-align: center;
                            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
                        }
                        .theme-label {
                            font-weight: 700;
                            margin-right: 10px;
                            color: ${theme.primaryDark};
                        }
                        .theme-btn {
                            display: inline-block;
                            padding: 8px 16px;
                            margin: 4px;
                            background: ${theme.cardAccent};
                            color: ${theme.primaryDark};
                            border-radius: 20px;
                            text-decoration: none;
                            font-weight: 600;
                            font-size: 14px;
                            border: 2px solid transparent;
                            transition: all 0.3s ease;
                        }
                        .theme-btn:hover {
                            background: ${theme.accentLight};
                            transform: translateY(-2px);
                            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
                        }
                        .theme-btn.active {
                            background: linear-gradient(135deg, ${theme.primaryColor} 0%, ${theme.primaryDark} 100%);
                            color: white;
                            border-color: ${theme.accentLight};
                        }
                        .nav-bar {
                            display: flex;
                            gap: 10px;
                            padding: 12px 16px;
                            background: rgba(255, 255, 255, 0.9);
                            border-radius: 12px;
                            margin-bottom: 20px;
                            flex-wrap: wrap;
                            justify-content: center;
                            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
                        }
                        .nav-bar a {
                            color: ${theme.primaryDark};
                            text-decoration: none;
                            padding: 8px 18px;
                            border-radius: 20px;
                            font-weight: 600;
                            font-size: 15px;
                            background: ${theme.cardAccent};
                            transition: all 0.3s ease;
                            border: 2px solid transparent;
                        }
                        .nav-bar a:hover {
                            background: ${theme.accentLight};
                            transform: translateY(-2px);
                            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
                        }
                        .nav-bar a.active {
                            background: linear-gradient(135deg, ${theme.primaryColor} 0%, ${theme.primaryDark} 100%);
                            color: white;
                            border-color: ${theme.accentLight};
                        }
                        .site-theme-selector {
                            background: rgba(255, 255, 255, 0.85);
                            padding: 10px 15px;
                            border-radius: 12px;
                            margin-bottom: 20px;
                            text-align: center;
                            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
                        }
                        .site-theme-selector label {
                            font-weight: 700;
                            margin-right: 10px;
                            color: ${theme.primaryDark};
                        }
                        .site-theme-selector select {
                            padding: 6px 12px;
                            border-radius: 8px;
                            border: 2px solid ${theme.accentColor};
                            background: ${theme.cardAccent};
                            color: ${theme.primaryDark};
                            font-family: ${theme.fontFamily};
                            font-weight: 600;
                            cursor: pointer;
                        }
                        .license-notice {
                            background: ${theme.cardAccent};
                            border: 2px dashed ${theme.accentColor};
                            border-radius: 10px;
                            padding: 12px 16px;
                            margin: 15px 0;
                        }
                        .license-link {
                            color: ${theme.primaryColor};
                            font-weight: 700;
                            text-decoration: underline;
                        }
                        .license-link:hover {
                            color: ${theme.primaryDark};
                        }
                        .license-footer {
                            margin-top: 30px;
                            padding: 20px;
                            background: ${theme.cardAccent};
                            border-radius: 12px;
                            text-align: center;
                            border-top: 3px solid ${theme.accentColor};
                        }
                        .license-footer p {
                            margin: 0;
                            color: ${theme.primaryDark};
                            font-size: 0.95em;
                            line-height: 1.5;
                        }
                    </style>
                </head>
                <body>
                <div class="main-container">
                <h1>${theme.title}</h1>
                <div class="festive-divider">${theme.dividerContent}</div>
                $navBarHtml
    

                $themeSelector
                <div class="site-theme-selector">
                    <label for="site-theme-selector">🌓 Site Mode:</label>
                    <select id="site-theme-selector">
                        <option value="auto">🌗 Auto (System)</option>
                        <option value="light">☀️ Light</option>
                        <option value="dark">🌙 Dark</option>
                    </select>
                </div>
                <script>
                    try {
                        ThemeManager.bindSelector(document.getElementById('site-theme-selector'));
                    } catch (e) { console.warn('ThemeManager bindSelector failed', e); }
                </script>
    
                 $createGiftSection
    
<div class="form-container">
                     <h2>${theme.emoji} Claim Your Gift ${theme.emoji}</h2>
                      <form method="post" action="$requestUri">
                         <input type="hidden" name="action" value="claim"/>
                         <input type="hidden" name="theme" value="${theme.id}"/>
                         <label>🎫 Gift ID: <input type="text" name="giftId" required/></label>
                         <button type="submit">${theme.emoji} Claim Now! ${theme.emoji}</button>
                     </form>
                 </div>
                 $giftsTable
                $giftsTable
                 <h2 id="claims">$claimsHeading</h2>
                 $claimsFilterForm
                 <table>
                     <tr class="table-header">
                         <th>🎫 Gift ID</th>
                         <th>👤 User ID</th>
                         <th>📅 Claimed At</th>
                     </tr>
                     ${
                    if (claims.isEmpty()) {
                        "<tr><td colspan=\"3\">No claims found.</td></tr>"
                    } else claims.joinToString("\n") { claim ->
                        try {
                            val claimedAtFormatted = claim.claimedAt?.let { dateFormatter.format(it) } ?: "-"
                            run {
                                val giftFilterUrl = "$requestUri?filterGiftId=${claim.giftId}&theme=${theme.id}#claims"
                                val userFilterUrl = "$requestUri?filterUserId=${claim.userId}&theme=${theme.id}#claims"
                                """
                                                 <tr>
                                                     <td><a class="claim-link" href="$giftFilterUrl" title="Filter by this gift">${claim.giftId}</a></td>
                                                     <td><a class="claim-link" href="$userFilterUrl" title="Filter by this user">${claim.userId}</a></td>
                                                     <td>$claimedAtFormatted</td>
                                                 </tr>
                                                 """.trimIndent()
                            }
                        } catch (e: Exception) {
                            log.warn(
                                "Failed to render claim row for giftId={} userId={}", claim.giftId, claim.userId, e
                            )
                            "<tr><td colspan=\"3\">Error rendering claim</td></tr>"
                        }
                    }
                }
                 </table>
                 <div class="festive-divider">${theme.dividerContent}</div>
                 <footer class="license-footer">
                     <p>
                         📜 Use of Cognotik, gifted credits, and the cloud-hosted service is governed by the
                         <a href="/LICENSE.html" target="_blank" rel="noopener" class="license-link">Cognotik Software License Agreement</a>.
                         API Credits and gifts are non-refundable and have no cash value (see Section 8.2).
                     </p>
                 </footer>
                 </div>
                </body>
                </html>
                """.trimIndent())
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

    /**
     * Attempt to determine a gift's assigned theme. Since the Gift data model may not
     * include a theme field, we try to read it reflectively; otherwise fall back to default.
     */
    private fun resolveGiftTheme(gift: Gift): GiftTheme {
        return GiftThemes.byId(gift.theme)
    }

    /**
     * Builds a themed navigation bar linking Usage, Credits, and Gifts pages.
     */
    private fun buildNavBar(active: String, theme: GiftTheme): String {
        fun cls(name: String) = if (name == active) " class=\"active\"" else ""
        return """
            <nav class="nav-bar">
                <a href="/usage"${cls("usage")}>📊 Usage</a>
                <a href="/credits"${cls("credits")}>💳 Buy Credits</a>
                <a href="/gifts/?theme=${theme.id}"${cls("gifts")}>🎁 Gifts</a>
            </nav>
        """.trimIndent()
    }


    /**
     * Builds the full path (URI + query string) that should be returned to after login.
     */
    private fun buildReturnTarget(request: HttpServletRequest): String {
        val uri = request.requestURI ?: "/"
        val qs = request.queryString
        return if (qs.isNullOrBlank()) uri else "$uri?$qs"
    }

    /**
     * URL-encode a string for inclusion as a query parameter value.
     */
    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    /**
     * Show a friendly "you've received a gift" page to an unauthenticated user
     * directing them to log in to claim the gift.
     */
    private fun showLoginRedirectForGift(
      request: HttpServletRequest,
      response: HttpServletResponse,
      giftId: String?,
      gift: Gift?,
      theme: GiftTheme
    ) {
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK
        val returnTarget = buildReturnTarget(request)
        val loginUrl = "/login/?target=${urlEncode(returnTarget)}"
        val giftIdDisplay = giftId ?: "(unknown)"
        val amountDisplay = gift?.let { "%.2f".format(it.amountGranted) } ?: "?"
        val giftDetailsBlock = if (gift != null) {
            """
             <div class="details">
                 <p><strong>🎫 Gift ID:</strong> $giftIdDisplay</p>
                 <p><strong>💰 Amount to be Credited:</strong> $amountDisplay credits</p>
                 <p><span class="theme-badge">🎨 ${theme.displayName}</span></p>
             </div>
             """.trimIndent()
        } else if (!giftId.isNullOrBlank()) {
            """
             <div class="details">
                 <p><strong>🎫 Gift ID:</strong> $giftIdDisplay</p>
                 <p style="color:#b00; font-weight:600;">⚠️ This gift could not be found right now. You may still log in and try again.</p>
             </div>
             """.trimIndent()
        } else ""
        val heading = if (gift != null) "🎁 You've Received a Gift!" else "🎁 Claim Your Gift"
        val message = if (gift != null) {
            "Someone has sent you credits on Cognotik! Sign in to add them to your account."
        } else {
            "Sign in to your Cognotik account to claim your gift."
        }
        response.writer.write(
            """
             <html>
             <head>
                 <title>${theme.emoji} You've Received a Gift! ${theme.emoji}</title>
                 <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                 <script src="/modules/theme.js"></script>
                 <script>
                     try { ThemeManager.init(); } catch (e) { console.warn('ThemeManager init failed', e); }
                 </script>
                 <style>
                     @import url('https://fonts.googleapis.com/css2?family=Pacifico&family=Quicksand:wght@400;500;600;700&display=swap');
                     * { box-sizing: border-box; }
                     body {
                         font-family: ${theme.fontFamily};
                         margin: 0;
                         padding: 20px;
                         background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.gradientMid} 50%, ${theme.gradientEnd} 100%);
                         background-size: 400% 400%;
                         animation: partyGradient 15s ease infinite;
                         min-height: 100vh;
                         display: flex;
                         align-items: center;
                         justify-content: center;
                         color: ${theme.textColor};
                     }
                     @keyframes partyGradient {
                         0% { background-position: 0% 50%; }
                         50% { background-position: 100% 50%; }
                         100% { background-position: 0% 50%; }
                     }
                     .gift-container {
                         max-width: 640px;
                         margin: 0 auto;
                         padding: 50px 40px;
                         border-radius: 20px;
                         background: ${theme.cardBackground};
                         box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3),
                                     0 0 0 4px ${theme.accentLight},
                                     0 0 0 8px ${theme.primaryColor},
                                     0 0 0 12px ${theme.accentLight};
                         text-align: center;
                         animation: popIn 0.6s ease-out;
                     }
                     @keyframes popIn {
                         0% { transform: scale(0.5); opacity: 0; }
                         70% { transform: scale(1.05); }
                         100% { transform: scale(1); opacity: 1; }
                     }
                     .gift-icon {
                         font-size: 96px;
                         margin-bottom: 20px;
                         display: inline-block;
                         animation: wiggle 2s ease-in-out infinite;
                     }
                     @keyframes wiggle {
                         0%, 100% { transform: rotate(0deg); }
                         25% { transform: rotate(-10deg); }
                         75% { transform: rotate(10deg); }
                     }
                     h1 {
                         font-family: ${theme.headingFont};
                         color: ${theme.primaryColor};
                         font-size: 2.8em;
                         margin: 10px 0;
                         text-shadow: 2px 2px 0 ${theme.accentLight}, 4px 4px 10px rgba(0, 0, 0, 0.2);
                     }
                     p {
                         color: ${theme.primaryDark};
                         font-size: 1.2em;
                         font-weight: 500;
                     }
                     .details {
                         background: linear-gradient(135deg, ${theme.cardAccent} 0%, ${theme.cardBackground} 100%);
                         padding: 20px;
                         border-radius: 12px;
                         margin: 25px 0;
                         text-align: left;
                         border: 2px dashed ${theme.accentColor};
                     }
                     .details p { margin: 8px 0; font-size: 1.1em; }
                     .details strong { color: ${theme.primaryColor}; font-weight: 700; }
                     .theme-badge {
                         display: inline-block;
                         padding: 4px 12px;
                         background: ${theme.accentLight};
                         color: ${theme.primaryDark};
                         border-radius: 12px;
                         font-size: 0.85em;
                         font-weight: 600;
                     }
                     a.button {
                         display: inline-block;
                         padding: 16px 40px;
                         color: white;
                         text-decoration: none;
                         border-radius: 30px;
                         font-weight: 700;
                         letter-spacing: 0.5px;
                         font-size: 1.1em;
                         box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
                         transition: all 0.3s ease;
                         background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.primaryDark} 100%);
                         margin-top: 10px;
                     }
                     a.button:hover {
                         transform: translateY(-2px);
                         box-shadow: 0 6px 25px rgba(0, 0, 0, 0.5);
                     }
                     .festive-banner {
                         font-size: 24px;
                         letter-spacing: 8px;
                         margin: 15px 0;
                     }
                     .note {
                         color: #777;
                         font-size: 0.95em;
                         margin-top: 20px;
                         font-style: italic;
                     }
                 </style>
             </head>
             <body>
                 <div class="gift-container">
                     <div class="festive-banner">${theme.bannerContent}</div>
                     <div class="gift-icon">🎁</div>
                     <h1>$heading</h1>
                     <p>$message</p>
                     $giftDetailsBlock
                     <a class="button" href="$loginUrl">🔐 Sign In to Claim</a>
                     <p class="note">After signing in, you'll be returned here to complete your claim.</p>
                     <p style="color:#888;font-size:0.85em;margin-top:10px;">
                         📜 By signing in and claiming, you agree to the
                         <a href="/LICENSE.html" target="_blank" rel="noopener" style="color:${theme.primaryColor};font-weight:600;">Cognotik License Agreement</a>.
                     </p>
                     <div class="festive-banner">${theme.bannerContent}</div>
                     <div style="margin-top:20px;">
                         <label for="site-theme-selector" style="font-weight:700;color:${theme.primaryDark};margin-right:8px;">🌓 Site Mode:</label>
                         <select id="site-theme-selector" style="padding:6px 12px;border-radius:8px;border:2px solid ${theme.accentColor};background:${theme.cardAccent};color:${theme.primaryDark};font-family:${theme.fontFamily};font-weight:600;cursor:pointer;">
                             <option value="auto">🌗 Auto (System)</option>
                             <option value="light">☀️ Light</option>
                             <option value="dark">🌙 Dark</option>
                         </select>
                     </div>
                     <script>
                         try {
                             ThemeManager.bindSelector(document.getElementById('site-theme-selector'));
                         } catch (e) { console.warn('ThemeManager bindSelector failed', e); }
                     </script>
                 </div>
             </body>
             </html>
             """.trimIndent()
        )
    }

    /**
     * Show a friendly welcome page for unauthenticated users visiting the gifts page
     * without trying to claim a specific gift.
     */
    private fun showLoginWelcomePage(
        request: HttpServletRequest,
        response: HttpServletResponse,
        theme: GiftTheme
    ) {
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK
        val returnTarget = buildReturnTarget(request)
        val loginUrl = "/login/?target=${urlEncode(returnTarget)}"
        response.writer.write(
            """
             <html>
             <head>
                 <title>${theme.emoji} Cognotik Gifted Credits ${theme.emoji}</title>
                 <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                 <script src="/modules/theme.js"></script>
                 <script>
                     try { ThemeManager.init(); } catch (e) { console.warn('ThemeManager init failed', e); }
                 </script>
                 <style>
                     @import url('https://fonts.googleapis.com/css2?family=Pacifico&family=Quicksand:wght@400;500;600;700&display=swap');
                     * { box-sizing: border-box; }
                     body {
                         font-family: ${theme.fontFamily};
                         margin: 0;
                         padding: 20px;
                         background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.gradientMid} 50%, ${theme.gradientEnd} 100%);
                         background-size: 400% 400%;
                         animation: partyGradient 15s ease infinite;
                         min-height: 100vh;
                         display: flex;
                         align-items: center;
                         justify-content: center;
                         color: ${theme.textColor};
                     }
                     @keyframes partyGradient {
                         0% { background-position: 0% 50%; }
                         50% { background-position: 100% 50%; }
                         100% { background-position: 0% 50%; }
                     }
                     .welcome-container {
                         max-width: 640px;
                         margin: 0 auto;
                         padding: 50px 40px;
                         border-radius: 20px;
                         background: ${theme.cardBackground};
                         box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3),
                                     0 0 0 4px ${theme.accentLight},
                                     0 0 0 8px ${theme.primaryColor},
                                     0 0 0 12px ${theme.accentLight};
                         text-align: center;
                         animation: popIn 0.6s ease-out;
                     }
                     @keyframes popIn {
                         0% { transform: scale(0.5); opacity: 0; }
                         70% { transform: scale(1.05); }
                         100% { transform: scale(1); opacity: 1; }
                     }
                     .welcome-icon {
                         font-size: 80px;
                         margin-bottom: 20px;
                         display: inline-block;
                         animation: bounce 2s ease-in-out infinite;
                     }
                     @keyframes bounce {
                         0%, 100% { transform: translateY(0); }
                         50% { transform: translateY(-10px); }
                     }
                     h1 {
                         font-family: ${theme.headingFont};
                         color: ${theme.primaryColor};
                         font-size: 2.8em;
                         margin: 10px 0;
                         text-shadow: 2px 2px 0 ${theme.accentLight}, 4px 4px 10px rgba(0, 0, 0, 0.2);
                     }
                     p {
                         color: ${theme.primaryDark};
                         font-size: 1.2em;
                         font-weight: 500;
                     }
                     a.button {
                         display: inline-block;
                         padding: 16px 40px;
                         color: white;
                         text-decoration: none;
                         border-radius: 30px;
                         font-weight: 700;
                         letter-spacing: 0.5px;
                         font-size: 1.1em;
                         box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
                         transition: all 0.3s ease;
                         background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.primaryDark} 100%);
                         margin-top: 10px;
                     }
                     a.button:hover {
                         transform: translateY(-2px);
                         box-shadow: 0 6px 25px rgba(0, 0, 0, 0.5);
                     }
                     .festive-banner {
                         font-size: 24px;
                         letter-spacing: 8px;
                         margin: 15px 0;
                     }
                     .hint {
                         color: #777;
                         font-size: 0.95em;
                         margin-top: 20px;
                         font-style: italic;
                     }
                 </style>
             </head>
             <body>
                 <div class="welcome-container">
                     <div class="festive-banner">${theme.bannerContent}</div>
                     <div class="welcome-icon">${theme.emoji}</div>
                     <h1>Welcome to Cognotik Gifts</h1>
                     <p>Sign in to view, create, and claim gifted credits.</p>
                     <p>If someone shared a gift link with you, signing in will let you claim it.</p>
                     <a class="button" href="$loginUrl">🔐 Sign In</a>
                     <p class="hint">You'll be returned to this page after signing in. After signing in, you'll also have access to <strong>📊 Usage</strong> tracking and <strong>💳 Credit purchases</strong>.</p>
                     <p style="color:#888;font-size:0.85em;margin-top:10px;">
                         📜 Use of Cognotik is governed by the
                         <a href="/LICENSE.html" target="_blank" rel="noopener" style="color:${theme.primaryColor};font-weight:600;">Cognotik Software License Agreement</a>.
                     </p>
                     <div class="festive-banner">${theme.bannerContent}</div>
                     <div style="margin-top:20px;">
                         <label for="site-theme-selector" style="font-weight:700;color:${theme.primaryDark};margin-right:8px;">🌓 Site Mode:</label>
                         <select id="site-theme-selector" style="padding:6px 12px;border-radius:8px;border:2px solid ${theme.accentColor};background:${theme.cardAccent};color:${theme.primaryDark};font-family:${theme.fontFamily};font-weight:600;cursor:pointer;">
                             <option value="auto">🌗 Auto (System)</option>
                             <option value="light">☀️ Light</option>
                             <option value="dark">🌙 Dark</option>
                         </select>
                     </div>
                     <script>
                         try {
                             ThemeManager.bindSelector(document.getElementById('site-theme-selector'));
                         } catch (e) { console.warn('ThemeManager bindSelector failed', e); }
                     </script>
                 </div>
             </body>
             </html>
             """.trimIndent()
        )
    }


    public override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        val remoteAddr = request.remoteAddr
        val requestUri = request.requestURI
        val action = request.getParameter("action")
        val themeParam = request.getParameter("theme")
        val theme = GiftThemes.byId(themeParam)
        log.debug("Handling POST request from {} for {} action={}", remoteAddr, requestUri, action)

        val user = try {
          UserProviderImpl().authenticate(request, response)
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
                    // Gift creation is open to all authenticated users.
                    // Credit checks are performed inside manager.createGift().
                    val amountGrantedStr = request.getParameter("amountGranted")
                    val grantDurationStr = request.getParameter("grantDuration")
                    val totalBudgetStr = request.getParameter("totalBudget")
                    val giftThemeId = request.getParameter("giftTheme")?.takeIf { it.isNotBlank() }
                    val acceptLicense = request.getParameter("acceptLicense")
                    if (acceptLicense.isNullOrBlank() || !(acceptLicense.equals("on", ignoreCase = true) ||
                                acceptLicense.equals("true", ignoreCase = true) ||
                                acceptLicense == "1")) {
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
                            user,
                            amountGrantedStr,
                            grantDurationStr,
                            totalBudgetStr
                        )
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameters")
                        return
                    }
                    val amountGranted = try {
                        amountGrantedStr.toDouble()
                    } catch (e: NumberFormatException) {
                        log.warn("Invalid amountGranted '{}' by user={}", amountGrantedStr, user, e)
                        response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid amountGranted: $amountGrantedStr"
                        )
                        return
                    }
                    val grantDurationDays = try {
                        grantDurationStr.toDouble()
                    } catch (e: NumberFormatException) {
                        log.warn("Invalid grantDuration '{}' by user={}", grantDurationStr, user, e)
                        response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid grantDuration: $grantDurationStr"
                        )
                        return
                    }
                    val totalBudget = try {
                        totalBudgetStr.toDouble()
                    } catch (e: NumberFormatException) {
                        log.warn("Invalid totalBudget '{}' by user={}", totalBudgetStr, user, e)
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid totalBudget: $totalBudgetStr")
                        return
                    }
                    if (amountGranted <= 0 || grantDurationDays <= 0 || totalBudget <= 0) {
                        log.warn(
                            "Create gift with non-positive values by user={} amount={} duration={} budget={}",
                            user,
                            amountGranted,
                            grantDurationDays,
                            totalBudget
                        )
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Values must be positive")
                        return
                    }
                    val grantDuration = Duration.ofMillis((grantDurationDays * 86400000).toLong())
                    log.info(
                        "Creating gift by user={} amount={} duration={}d budget={} theme={}",
                        user,
                        amountGranted,
                        grantDurationDays,
                        totalBudget,
                        giftThemeId
                    )
                    val createdGift = try {
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
                    val success = manager.claimGift(user, giftId)
                    if (!success) {
                        log.info("Claim failed (exhausted/already claimed) for user={} giftId={}", user, giftId)
                        response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "Failed to claim gift. It may be exhausted or already claimed."
                        )
                        return
                    } else {
                        claimSuccess(user, giftId, gift, response, requestUri, giftTheme)
                        return
                    }
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
            response.sendRedirect("$requestUri?theme=${theme.id}")
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

    private fun showClaimConfirmation(
      userinfo: User,
      giftId: String,
      gift: Gift,
      response: HttpServletResponse,
      requestUri: String?,
      theme: GiftTheme = GiftThemes.DEFAULT
    ) {
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK
        val confirmUrl = "$requestUri?action=claim&giftId=$giftId&confirm=true&theme=${theme.id}"
        val cancelUrl = "$requestUri?theme=${theme.id}"
        response.writer.write(
            """
            <html>
            <head>
                <title>${theme.emoji} Confirm Gift Claim ${theme.emoji}</title>
                <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                <script src="/modules/theme.js"></script>
                <script>
                    try { ThemeManager.init(); } catch (e) { console.warn('ThemeManager init failed', e); }
                </script>
                <style>
                    @import url('https://fonts.googleapis.com/css2?family=Pacifico&family=Quicksand:wght@400;500;600;700&display=swap');
                    * { box-sizing: border-box; }
                    body {
                        font-family: ${theme.fontFamily};
                        margin: 0;
                        padding: 20px;
                        background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.gradientMid} 50%, ${theme.gradientEnd} 100%);
                        background-size: 400% 400%;
                        animation: partyGradient 15s ease infinite;
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: ${theme.textColor};
                    }
                    @keyframes partyGradient {
                        0% { background-position: 0% 50%; }
                        50% { background-position: 100% 50%; }
                        100% { background-position: 0% 50%; }
                    }
                    .confirm-container {
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 50px 40px;
                        border-radius: 20px;
                        background: ${theme.cardBackground};
                        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3),
                                    0 0 0 4px ${theme.accentLight},
                                    0 0 0 8px ${theme.primaryColor},
                                    0 0 0 12px ${theme.accentLight};
                        text-align: center;
                        animation: popIn 0.6s ease-out;
                    }
                    @keyframes popIn {
                        0% { transform: scale(0.5); opacity: 0; }
                        70% { transform: scale(1.05); }
                        100% { transform: scale(1); opacity: 1; }
                    }
                    .confirm-icon {
                        font-size: 80px;
                        margin-bottom: 20px;
                        display: inline-block;
                        animation: bounce 2s ease-in-out infinite;
                    }
                    @keyframes bounce {
                        0%, 100% { transform: translateY(0); }
                        50% { transform: translateY(-10px); }
                    }
                    h1 {
                        font-family: ${theme.headingFont};
                        color: ${theme.primaryColor};
                        font-size: 2.8em;
                        margin: 10px 0;
                        text-shadow: 2px 2px 0 ${theme.accentLight}, 4px 4px 10px rgba(0, 0, 0, 0.2);
                    }
                    p {
                        color: ${theme.primaryDark};
                        font-size: 1.2em;
                        font-weight: 500;
                    }
                    .details {
                        background: linear-gradient(135deg, ${theme.cardAccent} 0%, ${theme.cardBackground} 100%);
                        padding: 20px;
                        border-radius: 12px;
                        margin: 25px 0;
                        text-align: left;
                        border: 2px dashed ${theme.accentColor};
                    }
                    .details p {
                        margin: 8px 0;
                        font-size: 1.1em;
                    }
                    .details strong {
                        color: ${theme.primaryColor};
                        font-weight: 700;
                    }
                    .button-row {
                        display: flex;
                        gap: 15px;
                        justify-content: center;
                        flex-wrap: wrap;
                        margin-top: 20px;
                    }
                    a.button {
                        display: inline-block;
                        padding: 14px 32px;
                        color: white;
                        text-decoration: none;
                        border-radius: 30px;
                        font-weight: 700;
                        letter-spacing: 0.5px;
                        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
                        transition: all 0.3s ease;
                    }
                    a.button.primary {
                        background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.primaryDark} 100%);
                    }
                    a.button.secondary {
                        background: linear-gradient(135deg, #888 0%, #555 100%);
                    }
                    a.button:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 6px 25px rgba(0, 0, 0, 0.5);
                    }
                    .festive-banner {
                        font-size: 24px;
                        letter-spacing: 8px;
                        margin: 15px 0;
                    }
                    .theme-badge {
                        display: inline-block;
                        padding: 4px 12px;
                        background: ${theme.accentLight};
                        color: ${theme.primaryDark};
                        border-radius: 12px;
                        font-size: 0.85em;
                        font-weight: 600;
                        margin-top: 10px;
                    }
                </style>
            </head>
            <body>
                <div class="confirm-container">
                    <div class="festive-banner">${theme.bannerContent}</div>
                    <div class="confirm-icon">${theme.emoji}</div>
                    <h1>Confirm Gift Claim</h1>
                    <p>You're about to claim the following gift:</p>
                    <div class="details">
                        <p><strong>🎫 Gift ID:</strong> $giftId</p>
                        <p><strong>💰 Amount to be Credited:</strong> ${"%.2f".format(gift.amountGranted)}</p>
                        <p><strong>👤 Claiming as:</strong> ${userinfo.email ?: userinfo.id}</p>
                        <p><span class="theme-badge">🎨 ${theme.displayName}</span></p>
                    </div>
                    <p>Are you sure you want to claim this gift? This action cannot be undone.</p>
                    <p style="font-size:0.9em;color:#666;font-style:italic;">
                        By claiming, you agree to the
                        <a href="/LICENSE.html" target="_blank" rel="noopener" style="color:${theme.primaryColor};font-weight:600;">Cognotik Software License Agreement</a>.
                        Credits are non-refundable and have no cash value.
                    </p>
                    <div class="button-row">
                        <a class="button primary" href="$confirmUrl">${theme.emoji} Yes, Claim It!</a>
                        <a class="button secondary" href="$cancelUrl">✖ Cancel</a>
                    </div>
                    <div class="festive-banner">${theme.bannerContent}</div>
                    <div style="margin-top:20px;">
                        <label for="site-theme-selector" style="font-weight:700;color:${theme.primaryDark};margin-right:8px;">🌓 Site Mode:</label>
                        <select id="site-theme-selector" style="padding:6px 12px;border-radius:8px;border:2px solid ${theme.accentColor};background:${theme.cardAccent};color:${theme.primaryDark};font-family:${theme.fontFamily};font-weight:600;cursor:pointer;">
                            <option value="auto">🌗 Auto (System)</option>
                            <option value="light">☀️ Light</option>
                            <option value="dark">🌙 Dark</option>
                        </select>
                    </div>
                    <script>
                        try {
                            ThemeManager.bindSelector(document.getElementById('site-theme-selector'));
                        } catch (e) { console.warn('ThemeManager bindSelector failed', e); }
                    </script>
                </div>
            </body>
            </html>
            """.trimIndent()
        )
    }


    private fun claimSuccess(
      userinfo: User,
      giftId: String,
      gift: Gift,
      response: HttpServletResponse,
      requestUri: String?,
      theme: GiftTheme = GiftThemes.DEFAULT
    ) {
        val redirectUri = "/?theme=${theme.id}"
        log.info(
            "Gift claimed successfully by user={} giftId={} amountGranted={} theme={}",
            userinfo, giftId, gift.amountGranted, theme.id
        )
        ApplicationServices.fileApplicationServices().usageDB.creditUser(
            user = userinfo,
            amount = gift.amountGranted,
            comment = "Claimed gift $giftId",
        )
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK
        response.writer.write(
            """
                                     <html>
                                     <head>
                                         <title>${theme.emoji} Gift Claimed Successfully! ${theme.emoji}</title>
                                         <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                                         <meta http-equiv="refresh" content="5;url=$redirectUri"/>
                                         <script src="/modules/theme.js"></script>
                                         <script>
                                             try { ThemeManager.init(); } catch (e) { console.warn('ThemeManager init failed', e); }
                                         </script>
                                         <style>
                                             @import url('https://fonts.googleapis.com/css2?family=Pacifico&family=Quicksand:wght@400;500;600;700&display=swap');
                                             * { box-sizing: border-box; }
                                             body {
                                                 font-family: ${theme.fontFamily};
                                                 margin: 0;
                                                 padding: 20px;
                                                 background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.gradientMid} 50%, ${theme.gradientEnd} 100%);
                                                 background-size: 400% 400%;
                                                 animation: partyGradient 10s ease infinite;
                                                 min-height: 100vh;
                                                 display: flex;
                                                 align-items: center;
                                                 justify-content: center;
                                                 overflow: hidden;
                                                 position: relative;
                                                 color: ${theme.textColor};
                                             }
                                             @keyframes partyGradient {
                                                 0% { background-position: 0% 50%; }
                                                 50% { background-position: 100% 50%; }
                                                 100% { background-position: 0% 50%; }
                                             }
                                             body::before {
                                                 content: '${theme.emoji}';
                                                 position: absolute;
                                                 font-size: 40px;
                                                 top: 10%;
                                                 left: 10%;
                                                 animation: confetti1 3s ease-in-out infinite;
                                             }
                                             body::after {
                                                 content: '${theme.emoji}';
                                                 position: absolute;
                                                 font-size: 40px;
                                                 top: 15%;
                                                 right: 10%;
                                                 animation: confetti2 3s ease-in-out infinite;
                                             }
                                             @keyframes confetti1 {
                                                 0%, 100% { transform: translateY(0) rotate(0deg); }
                                                 50% { transform: translateY(-30px) rotate(180deg); }
                                             }
                                             @keyframes confetti2 {
                                                 0%, 100% { transform: translateY(0) rotate(0deg); }
                                                 50% { transform: translateY(-30px) rotate(-180deg); }
                                             }
                                             .success-container {
                                                 max-width: 600px;
                                                 margin: 0 auto;
                                                 padding: 50px 40px;
                                                 border-radius: 20px;
                                                 background: ${theme.cardBackground};
                                                 box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3),
                                                             0 0 0 4px ${theme.accentLight},
                                                             0 0 0 8px ${theme.primaryColor},
                                                             0 0 0 12px ${theme.accentLight};
                                                 text-align: center;
                                                 position: relative;
                                                 z-index: 1;
                                                 animation: popIn 0.6s ease-out;
                                             }
                                             @keyframes popIn {
                                                 0% { transform: scale(0.5); opacity: 0; }
                                                 70% { transform: scale(1.05); }
                                                 100% { transform: scale(1); opacity: 1; }
                                             }
                                             .success-icon {
                                                 font-size: 80px;
                                                 margin-bottom: 20px;
                                                 animation: spin 2s ease-in-out infinite;
                                                 display: inline-block;
                                             }
                                             @keyframes spin {
                                                 0%, 100% { transform: rotate(0deg) scale(1); }
                                                 50% { transform: rotate(360deg) scale(1.2); }
                                             }
                                             h1 {
                                                 font-family: ${theme.headingFont};
                                                 color: ${theme.primaryColor};
                                                 font-size: 2.8em;
                                                 margin: 10px 0;
                                                 text-shadow: 2px 2px 0 ${theme.accentLight}, 4px 4px 10px rgba(0, 0, 0, 0.2);
                                             }
                                             p {
                                                 color: ${theme.primaryDark};
                                                 font-size: 1.2em;
                                                 font-weight: 500;
                                             }
                                             .details {
                                                 background: linear-gradient(135deg, ${theme.cardAccent} 0%, ${theme.cardBackground} 100%);
                                                 padding: 20px;
                                                 border-radius: 12px;
                                                 margin: 25px 0;
                                                 text-align: left;
                                                 border: 2px dashed ${theme.accentColor};
                                             }
                                             .details p {
                                                 margin: 8px 0;
                                                 font-size: 1.1em;
                                             }
                                             .details strong {
                                                 color: ${theme.primaryColor};
                                                 font-weight: 700;
                                             }
                                             a.button {
                                                 display: inline-block;
                                                 padding: 14px 32px;
                                                 background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.primaryDark} 100%);
                                                 color: white;
                                                 text-decoration: none;
                                                 border-radius: 30px;
                                                 margin-top: 15px;
                                                 font-weight: 700;
                                                 letter-spacing: 0.5px;
                                                 box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
                                                 transition: all 0.3s ease;
                                             }
                                             a.button:hover {
                                                 transform: translateY(-2px);
                                                 box-shadow: 0 6px 25px rgba(0, 0, 0, 0.5);
                                             }
                                             a.button.secondary {
                                                 background: linear-gradient(135deg, ${theme.accentColor} 0%, ${theme.primaryColor} 100%);
                                             }
                                             .button-row {
                                                 display: flex;
                                                 gap: 12px;
                                                 justify-content: center;
                                                 flex-wrap: wrap;
                                                 margin-top: 10px;
                                             }
                                             .redirect-note {
                                                 color: #888;
                                                 font-size: 0.9em;
                                                 margin-top: 20px;
                                                 font-style: italic;
                                             }
                                             .festive-banner {
                                                 font-size: 24px;
                                                 letter-spacing: 8px;
                                                 margin: 15px 0;
                                             }
                                             .theme-badge {
                                                 display: inline-block;
                                                 padding: 4px 12px;
                                                 background: ${theme.accentLight};
                                                 color: ${theme.primaryDark};
                                                 border-radius: 12px;
                                                 font-size: 0.85em;
                                                 font-weight: 600;
                                                 margin-top: 10px;
                                             }
                                         </style>
                                     </head>
                                     <body>
                                         <div class="success-container">
                                             <div class="festive-banner">${theme.bannerContent}</div>
                                             <div class="success-icon">${theme.emoji}</div>
                                             <h1>🎉 Gift Claimed! ${theme.emoji}</h1>
                                             <p>${theme.subtitle}</p>
                                             <div class="details">
                                                 <p><strong>🎫 Gift ID:</strong> $giftId</p>
                                                 <p><strong>💰 Amount Credited:</strong> ${"%.2f".format(gift.amountGranted)}</p>
                                                 <p><span class="theme-badge">🎨 ${theme.displayName}</span></p>
                                             </div>
                                             <div class="button-row">
                                                 <a class="button" href="$redirectUri">🏠 Return to Gifts Page</a>
                                                 <a class="button secondary" href="/usage">📊 View Usage</a>
                                                 <a class="button secondary" href="/credits">💳 Buy More Credits</a>
                                             </div>
                                             <p class="redirect-note">🕐 You will be redirected automatically in 5 seconds...</p>
                                             <p style="color:#888;font-size:0.85em;margin-top:15px;">
                                                 📜 Use of credits is governed by the
                                                 <a href="/LICENSE.html" target="_blank" rel="noopener" style="color:${theme.primaryColor};font-weight:600;">Cognotik License Agreement</a>.
                                             </p>
                                             <div class="festive-banner">${theme.bannerContent}</div>
                                             <div style="margin-top:20px;">
                                                 <label for="site-theme-selector" style="font-weight:700;color:${theme.primaryDark};margin-right:8px;">🌓 Site Mode:</label>
                                                 <select id="site-theme-selector" style="padding:6px 12px;border-radius:8px;border:2px solid ${theme.accentColor};background:${theme.cardAccent};color:${theme.primaryDark};font-family:${theme.fontFamily};font-weight:600;cursor:pointer;">
                                                     <option value="auto">🌗 Auto (System)</option>
                                                     <option value="light">☀️ Light</option>
                                                     <option value="dark">🌙 Dark</option>
                                                 </select>
                                             </div>
                                             <script>
                                                 try {
                                                     ThemeManager.bindSelector(document.getElementById('site-theme-selector'));
                                                 } catch (e) { console.warn('ThemeManager bindSelector failed', e); }
                                             </script>
                                         </div>
                                     </body>
                                     </html>
                                     """.trimIndent()
        )
    }

    /**
     * Builds the dynamic CSS based on the selected theme.
     */
    private fun buildThemeStyles(theme: GiftTheme): String {
        return """
                        @import url('https://fonts.googleapis.com/css2?family=Pacifico&family=Quicksand:wght@400;500;600;700&display=swap');
                        * { box-sizing: border-box; }
                        body {
                            font-family: ${theme.fontFamily};
                            margin: 0;
                            padding: 20px;
                            background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.gradientMid} 35%, ${theme.gradientEnd} 70%, ${theme.gradientExtra} 100%);
                            background-size: 400% 400%;
                            animation: partyGradient 15s ease infinite;
                            min-height: 100vh;
                            color: ${theme.textColor};
                            position: relative;
                            overflow-x: hidden;
                        }
                        @keyframes partyGradient {
                            0% { background-position: 0% 50%; }
                            50% { background-position: 100% 50%; }
                            100% { background-position: 0% 50%; }
                        }
                        body::before {
                            content: '${theme.rainContent} ${theme.rainContent}';
                            position: fixed;
                            top: 0;
                            left: 0;
                            right: 0;
                            color: rgba(255, 255, 255, 0.35);
                            font-size: 24px;
                            word-spacing: 30px;
                            line-height: 80px;
                            pointer-events: none;
                            z-index: 0;
                            animation: heartfall 20s linear infinite;
                        }
                        @keyframes heartfall {
                            0% { transform: translateY(-100px); }
                            100% { transform: translateY(100vh); }
                        }
                        .main-container {
                            max-width: 1200px;
                            margin: 0 auto;
                            background: ${theme.cardBackground};
                            border-radius: 20px;
                            padding: 40px;
                            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3),
                                        0 0 0 4px ${theme.accentLight},
                                        0 0 0 8px ${theme.primaryColor},
                                        0 0 0 12px ${theme.accentLight};
                            position: relative;
                            z-index: 1;
                        }
                        h1 {
                            font-family: ${theme.headingFont};
                            color: ${theme.primaryColor};
                            text-align: center;
                            font-size: 3.5em;
                            margin: 0 0 30px 0;
                            text-shadow: 3px 3px 0 ${theme.accentLight}, 6px 6px 10px rgba(0, 0, 0, 0.2);
                            letter-spacing: 2px;
                            animation: bounce 2s ease-in-out infinite;
                        }
                        @keyframes bounce {
                            0%, 100% { transform: translateY(0); }
                            50% { transform: translateY(-5px); }
                        }
                        h2 {
                            font-family: ${theme.headingFont};
                            color: ${theme.primaryDark};
                            font-size: 2em;
                            border-bottom: 3px dashed ${theme.accentColor};
                            padding-bottom: 10px;
                            margin-top: 30px;
                        }
                        table {
                            width: 100%;
                            border-collapse: separate;
                            border-spacing: 0;
                            margin-bottom: 30px;
                            border-radius: 12px;
                            overflow: hidden;
                            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
                        }
                        th, td {
                            border: none;
                            padding: 14px 12px;
                            text-align: left;
                        }
                        td {
                            border-bottom: 1px solid ${theme.accentLight};
                            color: ${theme.textColor};
                        }
                        tr:nth-child(even) {
                            background-color: ${theme.tableEvenRow};
                        }
                        tr:nth-child(odd) {
                            background-color: ${theme.cardBackground};
                        }
                        tr:hover td {
                            background-color: ${theme.tableHoverRow};
                            transition: background-color 0.3s ease;
                        }
                        .table-header {
                            background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.primaryDark} 100%) !important;
                            color: white;
                        }
                        .table-header th {
                            font-weight: 600;
                            letter-spacing: 0.5px;
                            text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.3);
                            color: white;
                        }
                        .form-container {
                            margin-bottom: 30px;
                            padding: 25px;
                            border: 3px solid ${theme.accentColor};
                            border-radius: 15px;
                            background: linear-gradient(135deg, ${theme.cardAccent} 0%, ${theme.cardBackground} 100%);
                            box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
                            position: relative;
                        }
                        .form-container::before {
                            content: '${theme.emoji}';
                            position: absolute;
                            top: -15px;
                            left: 20px;
                            font-size: 28px;
                            background: ${theme.cardBackground};
                            padding: 0 10px;
                        }
                        .form-container label {
                            display: block;
                            margin-bottom: 12px;
                            font-weight: 600;
                            color: ${theme.primaryDark};
                        }
                        .form-container input, .form-container select {
                            margin-bottom: 10px;
                            padding: 10px 12px;
                            width: 250px;
                            border: 2px solid #ddd;
                            border-radius: 8px;
                            font-family: ${theme.fontFamily};
                            font-size: 14px;
                            transition: border-color 0.3s ease, box-shadow 0.3s ease;
                        }
                        .form-container input:focus, .form-container select:focus {
                            outline: none;
                            border-color: ${theme.primaryColor};
                            box-shadow: 0 0 0 3px ${theme.accentLight};
                        }
                        .form-container button {
                            padding: 12px 28px;
                            cursor: pointer;
                            background: linear-gradient(135deg, ${theme.gradientStart} 0%, ${theme.primaryDark} 100%);
                            color: white;
                            border: none;
                            border-radius: 25px;
                            font-family: ${theme.fontFamily};
                            font-weight: 700;
                            font-size: 15px;
                            letter-spacing: 0.5px;
                            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
                            transition: all 0.3s ease;
                        }
                        .form-container button:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 6px 20px rgba(0, 0, 0, 0.5);
                        }
                        a.claim-link {
                            color: ${theme.primaryColor};
                            text-decoration: none;
                            font-weight: 700;
                            transition: all 0.3s ease;
                        }
                        a.claim-link:hover {
                            color: ${theme.primaryDark};
                            text-decoration: underline;
                            text-shadow: 0 0 8px ${theme.accentLight};
                        }
                        .festive-divider {
                            text-align: center;
                            font-size: 24px;
                            margin: 20px 0;
                            letter-spacing: 10px;
                        }
            """.trimIndent()
    }

    companion object {
        val log = LoggerFactory.getLogger(GiftedCreditsServlet::class.java)!!
        val manager = GiftedCreditsDB()
    }
}