package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.User
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class LoginServlet : HttpServlet() {

    /** Touch all built-in methods so their `init` blocks run. */
    fun ensureRegistered() {
        // Referencing the singleton triggers its `init` block which registers it.
        try {
            UsernamePasswordLoginMethod.toString()
            log.debug("Built-in login methods registered successfully")
        } catch (e: Exception) {
            log.error("Failed to register built-in login methods", e)
        }
    }

    init {
        try {
            ensureRegistered()
            log.debug("LoginServlet initialized")
        } catch (e: Exception) {
            log.error("Error during LoginServlet initialization", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LoginServlet::class.java)

        var SESSION_ENVELOPE_KEY_SEED: String = UUID.randomUUID().toString() + System.currentTimeMillis().toString()

        var IS_LOCAL_AUTH_ENABLED: Boolean = System.getenv("LOCAL_AUTH_ENABLED")?.toBoolean() ?: true
        private const val LOGIN_TEMPLATE_RESOURCE = "login.html"
        private const val REGISTER_TEMPLATE_RESOURCE = "register.html"
        private const val DEBOUNCE_INTERVAL_MS = 30_000L // 30 seconds between registration attempts per IP/username
        private val registrationAttempts = ConcurrentHashMap<String, Long>()
        private val dialogActive = AtomicBoolean(false)
        private val alwaysDeny = AtomicBoolean(false)
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private val secureRandom = SecureRandom()
        private val gson = Gson()

        /**
         * The encryption key used for token envelopes. Defaults to a random key generated at startup.
         * For multi-instance deployments, set the environment variable `SESSION_ENVELOPE_KEY` to a
         * shared Base64-encoded 32-byte key so tokens can be verified across instances.
         */
        private val envelopeKey: SecretKey by lazy {
            try {
                val keyBytes: ByteArray = hashData(SESSION_ENVELOPE_KEY_SEED).take(32).toByteArray()
                SecretKeySpec(keyBytes, "AES")
            } catch (e: Exception) {
                log.error("Failed to initialize session envelope key", e)
                throw e
            }
        }

        fun hashData(data: String): ByteArray {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.digest(data.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                log.error("Failed to hash data with SHA-256", e)
                throw e
            }
        }

        fun hashPassword(password: String): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
                Base64.getEncoder().encodeToString(hashBytes)
            } catch (e: Exception) {
                log.error("Failed to hash password", e)
                throw e
            }
        }

        /**
         * Computes a salted hash-of-hash: SHA-256(salt + SHA-256(passwordHash)).
         * This provides an additional layer so the raw passwordHash is not embedded in the token.
         */
        fun saltedHashOfHash(passwordHash: String, salt: String): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val inner = digest.digest(passwordHash.toByteArray(Charsets.UTF_8))
                digest.reset()
                digest.update(salt.toByteArray(Charsets.UTF_8))
                digest.update(inner)
                Base64.getEncoder().encodeToString(digest.digest())
            } catch (e: Exception) {
                log.error("Failed to compute salted hash-of-hash", e)
                throw e
            }
        }

        /**
         * Creates an encrypted session token envelope containing:
         * - username: the authenticated user
         * - hohash: salted hash-of-hash of the password
         * - salt: random salt used for the hash-of-hash
         * - created: creation timestamp (epoch millis)
         * - nonce: random number for uniqueness
         *
         * The envelope is AES-GCM encrypted and returned as a URL-safe Base64 string.
         */
        fun createSessionToken(username: String, passwordHash: String): String {
            return try {
                require(username.isNotBlank()) { "username must not be blank" }
                require(passwordHash.isNotBlank()) { "passwordHash must not be blank" }
                val salt = UUID.randomUUID().toString()
                val hohash = saltedHashOfHash(passwordHash, salt)
                val nonce = secureRandom.nextLong()
                val payload = JsonObject().apply {
                    addProperty("username", username)
                    addProperty("hohash", hohash)
                    addProperty("salt", salt)
                    addProperty("created", System.currentTimeMillis())
                    addProperty("nonce", nonce)
                }
                val plaintext = gson.toJson(payload).toByteArray(Charsets.UTF_8)
                val iv = ByteArray(GCM_IV_LENGTH)
                secureRandom.nextBytes(iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, envelopeKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val ciphertext = cipher.doFinal(plaintext)
                // Concatenate IV + ciphertext and encode as URL-safe Base64
                val combined = ByteArray(iv.size + ciphertext.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
                val token = Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
                log.debug("Created session token for user: {} (token length: {})", username, token.length)
                token
            } catch (e: IllegalArgumentException) {
                log.error("Invalid arguments for createSessionToken (username='{}')", username, e)
                throw e
            } catch (e: Exception) {
                log.error("Failed to create session token for user: {}", username, e)
                throw e
            }
        }

        /**
         * Decrypts and parses a session token envelope.
         * Returns the parsed [SessionEnvelope] or null if decryption/parsing fails.
         */
        fun decryptSessionToken(token: String): SessionEnvelope? {
            return try {
                if (token.isBlank()) {
                    log.warn("decryptSessionToken called with blank token")
                    return null
                }
                val combined = try {
                    Base64.getUrlDecoder().decode(token)
                } catch (e: IllegalArgumentException) {
                    log.warn("Session token is not valid URL-safe Base64: {}", e.message)
                    return null
                }
                if (combined.size < GCM_IV_LENGTH + 1) {
                    log.warn("Session token too short: {} bytes (minimum {})", combined.size, GCM_IV_LENGTH + 1)
                    return null
                }
                val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
                val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, envelopeKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val plaintext = cipher.doFinal(ciphertext)
                val json = gson.fromJson(String(plaintext, Charsets.UTF_8), JsonObject::class.java)
                if (json == null) {
                    log.warn("Decrypted session token did not parse to JSON object")
                    return null
                }
                SessionEnvelope(
                    username = json.get("username").asString,
                    hohash = json.get("hohash").asString,
                    salt = json.get("salt").asString,
                    created = json.get("created").asLong,
                    nonce = json.get("nonce").asLong
                )
            } catch (e: javax.crypto.AEADBadTagException) {
                log.warn("Session token failed authentication (bad tag): {}", e.message)
                null
            } catch (e: NullPointerException) {
                log.warn("Session token JSON missing required fields: {}", e.message)
                null
            } catch (e: Exception) {
                log.warn("Failed to decrypt session token: {} ({})", e.message, e.javaClass.simpleName)
                null
            }
        }

        /**
         * Verifies a session token against a known password hash without requiring the persistence store.
         * This re-derives the salted hash-of-hash from the provided passwordHash and the salt embedded
         * in the token, then compares it to the value in the envelope.
         *
         * @param token the encrypted session token
         * @param passwordHash the SHA-256 hash of the user's password (as stored in settings)
         * @param maxAgeMs optional maximum token age in milliseconds (default: 7 days)
         * @return a [SessionVerificationResult] describing success (with envelope) or the reason for failure
         */
        fun verifySessionToken(
            token: String, passwordHash: String, maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000
        ): SessionVerificationResult {
            return try {
                if (token.isBlank()) {
                    log.debug("verifySessionToken called with blank token")
                    return SessionVerificationResult.Failure(SessionVerificationError.BLANK_TOKEN, "Token is blank")
                }
                if (passwordHash.isBlank()) {
                    log.debug("verifySessionToken called with blank passwordHash")
                    return SessionVerificationResult.Failure(
                        SessionVerificationError.BLANK_PASSWORD_HASH,
                        "Password hash is blank"
                    )
                }
                val envelope = decryptSessionToken(token)
                if (envelope == null) {
                    log.debug("Session token could not be decrypted")
                    return SessionVerificationResult.Failure(
                        SessionVerificationError.DECRYPTION_FAILED,
                        "Session token could not be decrypted or parsed"
                    )
                }
                // Check expiration
                val age = System.currentTimeMillis() - envelope.created
                if (age < 0) {
                    log.warn("Session token has future creation time for user: {} (age={}ms)", envelope.username, age)
                    return SessionVerificationResult.Failure(
                        SessionVerificationError.FUTURE_CREATION_TIME,
                        "Session token has a future creation time (age=${age}ms)"
                    )
                }
                if (age > maxAgeMs) {
                    log.debug(
                        "Session token expired for user: {} (age={}ms, max={}ms)",
                        envelope.username,
                        age,
                        maxAgeMs
                    )
                    return SessionVerificationResult.Failure(
                        SessionVerificationError.EXPIRED,
                        "Session token expired (age=${age}ms, max=${maxAgeMs}ms)"
                    )
                }
                // Re-derive the salted hash-of-hash and compare
                val expectedHohash = saltedHashOfHash(passwordHash, envelope.salt)
                if (expectedHohash != envelope.hohash) {
                    log.warn("Session token hohash mismatch for user: {}", envelope.username)
                    return SessionVerificationResult.Failure(
                        SessionVerificationError.HOHASH_MISMATCH,
                        "Session token hash-of-hash does not match expected value for user '${envelope.username}'"
                    )
                }
                log.debug("Session token verified successfully for user: {}", envelope.username)
                SessionVerificationResult.Success(envelope)
            } catch (e: Exception) {
                log.error("Unexpected error verifying session token", e)
                return SessionVerificationResult.Failure(
                    SessionVerificationError.UNEXPECTED_ERROR,
                    "Unexpected error verifying session token: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }

        /**
         * Data class representing the decrypted contents of a session token envelope.
         */
        data class SessionEnvelope(
            val username: String, val hohash: String, val salt: String, val created: Long, val nonce: Long
        )
        /**
         * Enumeration of possible reasons a session token verification can fail.
         */
        enum class SessionVerificationError {
            BLANK_TOKEN,
            BLANK_PASSWORD_HASH,
            DECRYPTION_FAILED,
            FUTURE_CREATION_TIME,
            EXPIRED,
            HOHASH_MISMATCH,
            UNEXPECTED_ERROR
        }
        /**
         * Sealed result type for session token verification. On success, contains the
         * decoded [SessionEnvelope]. On failure, contains an [SessionVerificationError]
         * code and a human-readable reason.
         */
        sealed class SessionVerificationResult {
            data class Success(val envelope: SessionEnvelope) : SessionVerificationResult()
            data class Failure(val error: SessionVerificationError, val reason: String) : SessionVerificationResult()
            /** Convenience accessor: returns the envelope if successful, otherwise null. */
        }

        /**
         * Returns true if the registration attempt should be throttled (debounced).
         */
        fun isThrottled(key: String): Boolean {
            return try {
                val now = System.currentTimeMillis()
                val lastAttempt = registrationAttempts[key]
                if (lastAttempt != null && (now - lastAttempt) < DEBOUNCE_INTERVAL_MS) {
                    log.debug(
                        "Throttling registration for key '{}' (elapsed: {}ms < {}ms)",
                        key,
                        now - lastAttempt,
                        DEBOUNCE_INTERVAL_MS
                    )
                    return true
                }
                registrationAttempts[key] = now
                // Clean up old entries periodically
                if (registrationAttempts.size > 1000) {
                    val cutoff = now - DEBOUNCE_INTERVAL_MS
                    val before = registrationAttempts.size
                    registrationAttempts.entries.removeIf { it.value < cutoff }
                    log.debug("Cleaned up registration attempts cache: {} -> {}", before, registrationAttempts.size)
                }
                false
            } catch (e: Exception) {
                log.error("Error checking throttle for key '{}'", key, e)
                // Fail closed: treat errors as throttled to prevent abuse
                true
            }
        }

        /**
         * Shows a Swing confirmation dialog on the local machine asking the operator
         * to approve a new user registration. Returns true if approved.
         */
        fun confirmRegistrationViaDialog(username: String, remoteAddr: String): Boolean {
            // If the operator has previously chosen "Always Deny", short-circuit all future requests
            if (alwaysDeny.get()) {
                log.warn("Registration auto-denied (always-deny mode active) for user: {}", username)
                return false
            }
            // Prevent multiple dialogs from stacking up
            if (!dialogActive.compareAndSet(false, true)) {
                log.warn("Registration confirmation dialog already active, rejecting request for user: {}", username)
                return false
            }
            try {
                val latch = CountDownLatch(1)
                val approved = AtomicBoolean(false)
                SwingUtilities.invokeLater {
                    var parentFrame: JFrame? = null
                    try {
                        // Create an always-on-top invisible parent frame to ensure the dialog surfaces
                        // reliably on Windows/macOS, where null-parent dialogs often appear behind other windows.
                        parentFrame = JFrame().apply {
                            isUndecorated = true
                            isAlwaysOnTop = true
                            setLocationRelativeTo(null)
                            // Keep it off-screen and effectively invisible, but realized so it can own the dialog
                            setSize(1, 1)
                            setLocation(-10000, -10000)
                            isVisible = true
                            toFront()
                            requestFocus()
                        }
                        val options = arrayOf<Any>("Allow", "Deny", "Always Deny")
                        val message = "A new user registration has been requested:\n\n" +
                                "Username: $username\n" +
                                "Remote IP: $remoteAddr\n\n" +
                                "Do you want to allow this registration?\n" +
                                "(Choose 'Always Deny' to stop asking and automatically reject all future registrations.)"
                        val result = JOptionPane.showOptionDialog(
                            parentFrame,
                            message,
                            "Registration Confirmation",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[1] // Default to "Deny"
                        )
                        when (result) {
                            0 -> approved.set(true)                      // Allow
                            1 -> approved.set(false)                     // Deny
                            2 -> {                                       // Always Deny
                                alwaysDeny.set(true)
                                approved.set(false)
                                log.warn("Operator selected 'Always Deny' - all future registration requests will be automatically rejected")
                            }

                            else -> approved.set(false)                  // Closed / escaped
                        }
                    } catch (e: Exception) {
                        log.error("Error showing registration confirmation dialog", e)
                        approved.set(false)
                    } finally {
                        try {
                            parentFrame?.dispose()
                        } catch (e: Exception) {
                            log.debug("Error disposing parent frame", e)
                        }
                        latch.countDown()
                    }
                }
                // Wait up to 60 seconds for the operator to respond
                if (!latch.await(60, TimeUnit.SECONDS)) {
                    log.warn("Registration confirmation dialog timed out for user: {}", username)
                    return false
                }
                return approved.get()
            } finally {
                dialogActive.set(false)
            }
        }

        /**
         * Loads an HTML template from the classpath resources relative to this class.
         * Templates use HTML comment placeholders like `<!--PLACEHOLDER-->` which are
         * replaced by the servlet when serving pages.
         */
        private fun loadTemplate(resourceName: String): String {
            return try {
                val stream = LoginServlet::class.java.getResourceAsStream(resourceName)
                    ?: run {
                        log.error("Template resource not found: {}", resourceName)
                        throw IllegalStateException("Template resource not found: $resourceName")
                    }
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: IllegalStateException) {
                throw e
            } catch (e: Exception) {
                log.error("Failed to load template resource: {}", resourceName, e)
                throw IllegalStateException("Failed to load template resource: $resourceName", e)
            }
        }

        private fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;")
        }
        /**
         * HTML snippet that loads the site-wide ThemeManager module.
        * See `/modules/theme.js` for documentation.
        *
        * The ThemeManager:
        * - Persists selection in localStorage under 'cognotik-theme'
        * - Is overridable via URL parameter ?theme=light|dark|auto
        * - URL param value (if present and valid) is persisted to localStorage
        * - Applies theme by setting data-theme attribute on <html>
         */
        private const val THEME_SCRIPT_TAG = """<script src="/modules/theme.js"></script>"""
        /**
         * HTML snippet that initializes the ThemeManager and (if present) binds it
         * to a theme selector element with id="theme-selector".
        *
        * Initialization is wrapped in DOMContentLoaded to ensure the selector
        * element (if any) is available when bindSelector is called.
         */
        private const val THEME_INIT_SCRIPT = """<script>
(function() {
    function initTheme() {
        try {
            if (typeof ThemeManager !== 'undefined') {
                ThemeManager.init();
                var selector = document.getElementById('theme-selector');
                if (selector) {
                    ThemeManager.bindSelector(selector);
                }
            } else {
                console.warn('ThemeManager is not available; theme will not be applied.');
            }
        } catch (e) {
            console.error('Failed to initialize ThemeManager', e);
        }
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initTheme);
    } else {
        initTheme();
    }
})();
</script>"""
        /**
         * Injects the ThemeManager script (`/modules/theme.js`) and initialization
         * code into the rendered HTML. The script tag is inserted just before
         * `</head>` and the initialization is inserted just before `</body>`.
         * If the corresponding tags are not present, the snippets are appended.
         *
         * This ensures every page served by [LoginServlet] participates in the
         * site-wide theme management, with theme selection persisted in localStorage
         * under 'cognotik-theme' and overridable via the `?theme=` URL parameter.
         */
        internal fun injectThemeManager(html: String): String {
            return try {
                var result = html
                // Avoid double-injecting if the template already references theme.js
                val alreadyHasScript = result.contains("/modules/theme.js")
                if (!alreadyHasScript) {
                    result = if (result.contains("</head>", ignoreCase = true)) {
                        result.replaceFirst(
                            Regex("</head>", RegexOption.IGNORE_CASE),
                            "$THEME_SCRIPT_TAG</head>"
                        )
                    } else {
                        // No <head> tag: prepend the script so it loads before body content
                        THEME_SCRIPT_TAG + result
                    }
                }
                val alreadyHasInit = result.contains("ThemeManager.init(")
                if (!alreadyHasInit) {
                    result = if (result.contains("</body>", ignoreCase = true)) {
                        result.replaceFirst(
                            Regex("</body>", RegexOption.IGNORE_CASE),
                            "$THEME_INIT_SCRIPT</body>"
                        )
                    } else {
                        result + THEME_INIT_SCRIPT
                    }
                }
                result
            } catch (e: Exception) {
                log.error("Failed to inject ThemeManager into HTML; serving page without theme support", e)
                html
            }
        }
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val action = req.getParameter("action") ?: req.getParameter("formAction")
            log.debug("doGet action='{}' from remote='{}'", action, req.remoteAddr)
            // If the user is already authenticated, redirect them away from the login flow
            // regardless of which action was requested (login, register, or default).
            if (action != "logout" && isAlreadyAuthenticated(req)) {
                val redirectUrl = resolveRedirectTarget(req.getParameter("target"))
                log.debug("User already authenticated, redirecting to '{}'", redirectUrl)
                resp.sendRedirect(redirectUrl)
                return
            }
            when (action) {
                "register" -> {
                    if (!IS_LOCAL_AUTH_ENABLED) {
                        log.warn("Registration attempt while local auth is disabled from remote: {}", req.remoteAddr)
                        serveLoginPage(req, resp, error = "Local authentication is disabled.")
                        return
                    }
                    serveRegistrationPage(req, resp)
                }

                "logout" -> handleLogout(req, resp)
                 "login" -> dispatchLogin(req, resp)
                else -> serveLoginPage(req, resp)
            }
        } catch (e: Exception) {
            log.error("Unhandled error in doGet from remote: {}", req.remoteAddr, e)
            try {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                serveLoginPage(req, resp, error = "An internal error occurred.")
            } catch (inner: Exception) {
                log.error("Failed to serve fallback error page", inner)
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val action = req.getParameter("action") ?: req.getParameter("formAction")
            log.debug("doPost action='{}' from remote='{}'", action, req.remoteAddr)
            // If the user is already authenticated, redirect them away from login/register flows.
            if (action != "logout" && isAlreadyAuthenticated(req)) {
                val redirectUrl = resolveRedirectTarget(req.getParameter("target"))
                log.debug("User already authenticated on POST, redirecting to '{}'", redirectUrl)
                resp.sendRedirect(redirectUrl)
                return
            }
            when (action) {
                "register" -> {
                    if (!IS_LOCAL_AUTH_ENABLED) {
                        log.warn(
                            "Registration POST attempt while local auth is disabled from remote: {}",
                            req.remoteAddr
                        )
                        serveLoginPage(req, resp, error = "Local authentication is disabled.")
                        return
                    }
                    handleRegistration(req, resp)
                }

                "login" -> dispatchLogin(req, resp)
                "logout" -> handleLogout(req, resp)
                else -> {
                    log.debug("Unknown POST action '{}', redirecting to /login/", action)
                    resp.sendRedirect("/login/")
                }
            }
        } catch (e: Exception) {
            log.error("Unhandled error in doPost from remote: {}", req.remoteAddr, e)
            try {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                serveLoginPage(req, resp, error = "An internal error occurred.")
            } catch (inner: Exception) {
                log.error("Failed to serve fallback error page", inner)
            }
        }
    }

    /**
     * Dispatches the POST to the selected [LoginMethod] (if any). If the method's
     * `handleLogin` returns true, the response is considered fully handled.
     * Otherwise, fall back to the legacy username/password flow.
     */
    private fun dispatchLogin(req: HttpServletRequest, resp: HttpServletResponse) {
        val methodName = req.getParameter("loginMethod")
        log.debug("dispatchLogin methodName='{}' from remote='{}'", methodName, req.remoteAddr)
        if (!methodName.isNullOrBlank()) {
            val method = try {
                LoginMethod.valueOf(methodName)
            } catch (e: IllegalArgumentException) {
                log.warn("Unknown login method requested: '{}' from remote: {}", methodName, req.remoteAddr)
                null
            } catch (e: Exception) {
                log.error("Error resolving login method: '{}'", methodName, e)
                null
            }
            if (method != null) {
                // If local auth is disabled, block the built-in username/password method
                if (!IS_LOCAL_AUTH_ENABLED && method == UsernamePasswordLoginMethod) {
                    log.warn(
                        "Username/password login attempt while local auth is disabled from remote: {}",
                        req.remoteAddr
                    )
                    serveLoginPage(
                        req,
                        resp,
                        error = "Local authentication is disabled.",
                        target = req.getParameter("target")
                    )
                    return
                }
                try {
                    log.debug("Dispatching to login method '{}'", method.name)
                    val handled = method.handleLogin(req, resp)
                    if (handled) {
                        log.debug("Login method '{}' handled the request", method.name)
                        return
                    } else {
                        log.debug("Login method '{}' did not handle the request, falling back", method.name)
                    }
                } catch (e: Exception) {
                    log.error("Error in login method '{}' from remote: {}", methodName, req.remoteAddr, e)
                    serveLoginPage(
                        req,
                        resp,
                        error = "An error occurred during login.",
                        target = req.getParameter("target")
                    )
                    return
                }
            }
        }
        // Fall back to legacy username/password handling (only if local auth is enabled).
        if (!IS_LOCAL_AUTH_ENABLED) {
            log.warn("Legacy login fallback blocked because local auth is disabled from remote: {}", req.remoteAddr)
            serveLoginPage(
                req,
                resp,
                error = "Local authentication is disabled.",
                target = req.getParameter("target")
            )
            return
        }
        handleLogin(req, resp)
    }


    private fun handleLogin(req: HttpServletRequest, resp: HttpServletResponse) {
        if (!IS_LOCAL_AUTH_ENABLED) {
            log.warn("handleLogin invoked while local auth is disabled from remote: {}", req.remoteAddr)
            serveLoginPage(
                req,
                resp,
                error = "Local authentication is disabled.",
                target = req.getParameter("target")
            )
            return
        }
        val username = req.getParameter("username")?.trim()
        val password = req.getParameter("password")
        val target = req.getParameter("target")
        log.debug("handleLogin username='{}' from remote='{}'", username, req.remoteAddr)

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            log.warn(
                "Login attempt with missing credentials from remote: {} (username blank: {}, password blank: {})",
                req.remoteAddr, username.isNullOrBlank(), password.isNullOrBlank()
            )
            serveLoginPage(req, resp, error = "Username and password are required.", target = target)
            return
        }

        try {
            val user = User(
                email = username,
                name = username,
            )
            val fileServices = try {
                ApplicationServices.fileApplicationServices()
            } catch (e: Exception) {
                log.error("Failed to get fileApplicationServices for login: {}", username, e)
                serveLoginPage(req, resp, error = "An internal error occurred.", target = target)
                return
            }
            val settings = try {
                fileServices.userSettingsManager.getUserSettings(user)
            } catch (e: Exception) {
                log.error("Failed to load user settings for login: {}", username, e)
                serveLoginPage(req, resp, error = "An internal error occurred.", target = target)
                return
            }

            if (settings.passwordHash == null) {
                log.warn("Login attempt for user without password set: {} from remote: {}", username, req.remoteAddr)
                serveLoginPage(req, resp, error = "User not found. Please register first.", target = target)
                return
            }

            val inputHash = hashPassword(password)
            if (inputHash != settings.passwordHash) {
                log.warn("Failed login attempt for user: {} from remote: {}", username, req.remoteAddr)
                serveLoginPage(req, resp, error = "Username password is not set.", target = target)
                return
            }

            val accessToken = createSessionToken(username, inputHash)
            try {
                ApplicationServices.authenticationManager.putUser(accessToken, user)
            } catch (e: Exception) {
                log.error("Failed to register user with authentication manager: {}", username, e)
                serveLoginPage(req, resp, error = "An internal error occurred.", target = target)
                return
            }

            val cookie = Cookie(AuthenticationInterface.AUTH_COOKIE, accessToken)
            cookie.path = "/"
            cookie.maxAge = 60 * 60 * 24 * 7 // 7 days
            cookie.isHttpOnly = true
            resp.addCookie(cookie)

            log.info("User logged in successfully: {} from remote: {}", username, req.remoteAddr)
            try {
                initializeSystem(user)
            } catch (e: Exception) {
                log.error("Error during post-login system initialization for user: {}", username, e)
                // Continue with the redirect even if initialization fails
            }
            val redirectUrl = if (!target.isNullOrBlank()) {
                try {
                    URLDecoder.decode(target, "UTF-8")
                } catch (e: Exception) {
                    log.warn("Failed to decode redirect target '{}', defaulting to /", target, e)
                    "/"
                }
            } else "/"
            log.debug("Redirecting user '{}' to '{}'", username, redirectUrl)
            resp.sendRedirect(redirectUrl)
        } catch (e: Exception) {
            log.error("Error during login for user: {} from remote: {}", username, req.remoteAddr, e)
            serveLoginPage(req, resp, error = "An error occurred during login.", target = target)
        }
    }

    private fun initializeSystem(user: User) {
    }

    /**
     * Checks whether the request carries a valid auth cookie that resolves to a known user.
     */
    private fun isAlreadyAuthenticated(req: HttpServletRequest): Boolean {
        return try {
            val authCookie = req.cookies?.firstOrNull { it.name == AuthenticationInterface.AUTH_COOKIE }
            val token = authCookie?.value
            if (token.isNullOrBlank()) return false
            val user = try {
                ApplicationServices.authenticationManager.getUser(token)
            } catch (e: Exception) {
                log.debug("Error checking existing authentication", e)
                null
            }
            user != null
        } catch (e: Exception) {
            log.debug("Error inspecting auth cookie", e)
            false
        }
    }

    /**
     * Resolves the redirect target. If [target] is non-blank, attempt to URL-decode it.
     * Falls back to "/" (the homepage) on any failure or if [target] is blank/null.
     */
    private fun resolveRedirectTarget(target: String?): String {
        if (target.isNullOrBlank()) return "/"
        return try {
            URLDecoder.decode(target, "UTF-8")
        } catch (e: Exception) {
            log.warn("Failed to decode redirect target '{}', defaulting to /", target, e)
            "/"
        }
    }

    /**
     * Handles logout by clearing the auth cookie, removing the session token from the
     * authentication manager, and redirecting to the login page (or an optional target).
     */
    private fun handleLogout(req: HttpServletRequest, resp: HttpServletResponse) {
        val target = req.getParameter("target")
        log.debug("handleLogout target='{}' from remote='{}'", target, req.remoteAddr)
        try {
            // Find the auth cookie (if any) and remove the corresponding user mapping
            val authCookie = req.cookies?.firstOrNull { it.name == AuthenticationInterface.AUTH_COOKIE }
            val token = authCookie?.value
            if (!token.isNullOrBlank()) {
                try {
                    val user = ApplicationServices.authenticationManager.getUser(token)
                    if (user == null) {
                        log.warn("Logout requested for token with no associated user from remote: {}", req.remoteAddr)
                    } else {
                        try {
                            ApplicationServices.authenticationManager.logoutIfMatching(token, user)
                            log.info("User logged out: {} from remote: {}", user.email, req.remoteAddr)
                        } catch (e: Exception) {
                            log.error("Error invoking authenticationManager.logout for user: {}", user.email, e)
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Error removing user from authentication manager during logout", e)
                }
            } else {
                log.debug("Logout requested but no auth cookie present from remote: {}", req.remoteAddr)
            }
            // Clear the auth cookie on the client by sending a cookie with maxAge=0
            val clearCookie = Cookie(AuthenticationInterface.AUTH_COOKIE, "")
            clearCookie.path = "/"
            clearCookie.maxAge = 0
            clearCookie.isHttpOnly = true
            resp.addCookie(clearCookie)
        } catch (e: Exception) {
            log.error("Error during logout from remote: {}", req.remoteAddr, e)
        }
        val redirectUrl = if (!target.isNullOrBlank()) {
            try {
                val decoded = URLDecoder.decode(target, "UTF-8")
                val encoded = URLEncoder.encode(decoded, "UTF-8")
                "/login/?target=$encoded"
            } catch (e: Exception) {
                log.warn("Failed to decode/encode logout target '{}', defaulting to /login/", target, e)
                "/login/"
            }
        } else "/login/"
        log.debug("Redirecting logout to '{}'", redirectUrl)
        resp.sendRedirect(redirectUrl)
    }


    private fun handleRegistration(req: HttpServletRequest, resp: HttpServletResponse) {
        if (!IS_LOCAL_AUTH_ENABLED) {
            log.warn("handleRegistration invoked while local auth is disabled from remote: {}", req.remoteAddr)
            serveLoginPage(
                req,
                resp,
                error = "Local authentication is disabled.",
                target = req.getParameter("target")
            )
            return
        }
        val username = req.getParameter("username")?.trim()
        val password = req.getParameter("password")
        val confirmPassword = req.getParameter("confirmPassword")
        val target = req.getParameter("target")
        log.debug("handleRegistration username='{}' from remote='{}'", username, req.remoteAddr)

        if (username.isNullOrBlank() || password.isNullOrBlank() || confirmPassword.isNullOrBlank()) {
            log.debug(
                "Registration missing fields (username blank: {}, password blank: {}, confirm blank: {})",
                username.isNullOrBlank(), password.isNullOrBlank(), confirmPassword.isNullOrBlank()
            )
            serveRegistrationPage(req, resp, error = "All fields are required.", target = target)
            return
        }

        if (password != confirmPassword) {
            log.debug("Registration password mismatch for username: {}", username)
            serveRegistrationPage(req, resp, error = "Passwords do not match.", target = target)
            return
        }

        if (password.isEmpty()) {
            log.debug("Registration empty password for username: {}", username)
            serveRegistrationPage(req, resp, error = "Password cannot be empty.", target = target)
            return
        }

        try {
            val user = User(
                email = username,
                name = username,
            )
            val fileServices = try {
                ApplicationServices.fileApplicationServices()
            } catch (e: Exception) {
                log.error("Failed to get fileApplicationServices for registration: {}", username, e)
                serveRegistrationPage(req, resp, error = "An internal error occurred.", target = target)
                return
            }
            // Debounce registration attempts by IP + username
            val throttleKey = "${req.remoteAddr}:$username"
            if (isThrottled(throttleKey)) {
                log.warn("Registration attempt throttled for key: {} from remote: {}", throttleKey, req.remoteAddr)
                serveRegistrationPage(
                    req,
                    resp,
                    error = "Too many registration attempts. Please try again later.",
                    target = target
                )
                return
            }
            // Ask the local operator for confirmation via Swing dialog
            val approved = try {
                confirmRegistrationViaDialog(username, req.remoteAddr)
            } catch (e: Exception) {
                log.error(
                    "Failed to show registration confirmation dialog for user: {} from remote: {}",
                    username, req.remoteAddr, e
                )
                false
            }
            if (!approved) {
                log.info("Registration denied by operator for user: {} from remote: {}", username, req.remoteAddr)
                serveRegistrationPage(
                    req,
                    resp,
                    error = "Registration was not approved. Please contact the administrator.",
                    target = target
                )
                return
            }

            val existingSettings = try {
                fileServices.userSettingsManager.getUserSettings(user)
            } catch (e: Exception) {
                log.error("Failed to load existing settings for registration: {}", username, e)
                serveRegistrationPage(req, resp, error = "An internal error occurred.", target = target)
                return
            }
            if (existingSettings.passwordHash != null) {
                log.info("Registration attempt for existing user: {} from remote: {}", username, req.remoteAddr)
                serveRegistrationPage(req, resp, error = "User already exists. Please login instead.", target = target)
                return
            }
            val newSettings = existingSettings.copy(passwordHash = hashPassword(password))
            try {
                fileServices.userSettingsManager.updateUserSettings(user, newSettings)
            } catch (e: Exception) {
                log.error("Failed to persist user settings during registration: {}", username, e)
                serveRegistrationPage(req, resp, error = "An internal error occurred.", target = target)
                return
            }

            log.info("User registered successfully: {} from remote: {}", username, req.remoteAddr)

            val accessToken = createSessionToken(username, hashPassword(password))
            try {
                ApplicationServices.authenticationManager.putUser(accessToken, user)
            } catch (e: Exception) {
                log.error("Failed to register newly-registered user with authentication manager: {}", username, e)
                serveRegistrationPage(req, resp, error = "An internal error occurred.", target = target)
                return
            }

            val cookie = Cookie(AuthenticationInterface.AUTH_COOKIE, accessToken)
            cookie.path = "/"
            cookie.maxAge = 60 * 60 * 24 * 7 // 7 days
            cookie.isHttpOnly = true
            resp.addCookie(cookie)

            val redirectUrl = if (!target.isNullOrBlank()) {
                try {
                    URLDecoder.decode(target, "UTF-8")
                } catch (e: Exception) {
                    log.warn("Failed to decode registration redirect target '{}', defaulting to /", target, e)
                    "/"
                }
            } else "/"
            log.debug("Redirecting newly-registered user '{}' to '{}'", username, redirectUrl)
            resp.sendRedirect(redirectUrl)
        } catch (e: Exception) {
            log.error("Error during registration for user: {} from remote: {}", username, req.remoteAddr, e)
            serveRegistrationPage(req, resp, error = "An error occurred during registration.", target = target)
        }
    }

    private fun serveLoginPage(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        error: String? = null,
        target: String? = null
    ) {
        try {
            val effectiveTarget = target ?: req.getParameter("target")
            // If the user is already authenticated, skip the login form and redirect them
            // to either the requested target or the homepage. This applies regardless of
            // whether an error message would otherwise be shown.
            try {
                if (isAlreadyAuthenticated(req)) {
                    val redirectUrl = resolveRedirectTarget(effectiveTarget)
                    log.debug(
                        "User already authenticated, redirecting to '{}' instead of showing login form",
                        redirectUrl
                    )
                    resp.sendRedirect(redirectUrl)
                    return
                }
            } catch (e: Exception) {
                log.debug("Error during pre-login authentication check", e)
            }
            val targetParam = if (!effectiveTarget.isNullOrBlank()) effectiveTarget else null
            val encodedTarget = targetParam?.let {
                try {
                    URLEncoder.encode(it, "UTF-8")
                } catch (e: Exception) {
                    log.warn("Failed to URL-encode target '{}'", it, e)
                    null
                }
            }
            val registerLink =
                if (encodedTarget != null) "/login?action=register&target=$encodedTarget" else "/login?action=register"
            val errorBlock = if (error != null) """<div class="error">${escapeHtml(error)}</div>""" else ""
            // Render all registered login methods. If multiple are registered, render them
            // separated by visual dividers.
            val allMethods = LoginMethod.values()
            // If local auth is disabled, exclude the built-in username/password method
            val methods = if (!IS_LOCAL_AUTH_ENABLED) {
                allMethods.filter { it != UsernamePasswordLoginMethod }.toTypedArray()
            } else {
                allMethods.toTypedArray()
            }
            val methodsHtml = if (methods.isEmpty()) {
                if (!IS_LOCAL_AUTH_ENABLED) {
                    """<div class="error">Local authentication is disabled and no other login methods are available.</div>"""
                } else {
                    """<div class="error">No login methods are registered.</div>"""
                }
            } else {
                methods.joinToString(separator = """<div class="divider"><span>or</span></div>""") { method ->
                    try {
                        method.renderForm(req, effectiveTarget)
                    } catch (e: Exception) {
                        log.error("Error rendering login method '{}'", method.name, e)
                        ""
                    }
                }
            }

            val html = loadTemplate(LOGIN_TEMPLATE_RESOURCE)
                .replace("<!--ERROR_BLOCK-->", errorBlock)
                .replace("<!--LOGIN_METHODS-->", methodsHtml)
                .replace("<!--REGISTER_LINK-->", registerLink)
                // Hide the "Create one" link when local auth (registration) is disabled
                .let { rendered ->
                    if (!IS_LOCAL_AUTH_ENABLED) {
                        rendered.replace(
                            Regex("""<div class="links">[\s\S]*?</div>"""),
                            ""
                        )
                    } else rendered
                }

            resp.contentType = "text/html"
            resp.characterEncoding = "UTF-8"
            resp.writer.write(injectThemeManager(html))
        } catch (e: Exception) {
            log.error("Failed to serve login page", e)
            try {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "text/html"
                resp.characterEncoding = "UTF-8"
                resp.writer.write(
                    injectThemeManager(
                        "<html><head></head><body><h1>Internal Server Error</h1><p>Unable to render login page.</p></body></html>"
                    )
                )
            } catch (inner: Exception) {
                log.error("Failed to write fallback error response", inner)
            }
        }
    }

    private fun serveRegistrationPage(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        error: String? = null,
        target: String? = null
    ) {
        try {
            val effectiveTarget = target ?: req.getParameter("target")
            // If the user is already authenticated, redirect rather than showing the registration form.
            try {
                if (isAlreadyAuthenticated(req)) {
                    val redirectUrl = resolveRedirectTarget(effectiveTarget)
                    log.debug(
                        "User already authenticated, redirecting to '{}' instead of showing registration form",
                        redirectUrl
                    )
                    resp.sendRedirect(redirectUrl)
                    return
                }
            } catch (e: Exception) {
                log.debug("Error during pre-registration authentication check", e)
            }
            val targetParam = if (!effectiveTarget.isNullOrBlank()) effectiveTarget else null
            val encodedTarget = targetParam?.let {
                try {
                    URLEncoder.encode(it, "UTF-8")
                } catch (e: Exception) {
                    log.warn("Failed to URL-encode target '{}'", it, e)
                    null
                }
            }
            val targetHiddenField =
                if (encodedTarget != null) """<input type="hidden" name="target" value="$encodedTarget">""" else ""
            val loginLink = if (encodedTarget != null) "/login/?target=$encodedTarget" else "/login/"
            val errorBlock = if (error != null) """<div class="error">${escapeHtml(error)}</div>""" else ""
            val html = loadTemplate(REGISTER_TEMPLATE_RESOURCE)
                .replace("<!--ERROR_BLOCK-->", errorBlock)
                .replace("<!--TARGET_HIDDEN_FIELD-->", targetHiddenField)
                .replace("<!--LOGIN_LINK-->", loginLink)

            resp.contentType = "text/html"
            resp.characterEncoding = "UTF-8"
            resp.writer.write(injectThemeManager(html))
        } catch (e: Exception) {
            log.error("Failed to serve registration page", e)
            try {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "text/html"
                resp.characterEncoding = "UTF-8"
                resp.writer.write(
                    injectThemeManager(
                        "<html><head></head><body><h1>Internal Server Error</h1><p>Unable to render registration page.</p></body></html>"
                    )
                )
            } catch (inner: Exception) {
                log.error("Failed to write fallback error response", inner)
            }
        }
    }
}