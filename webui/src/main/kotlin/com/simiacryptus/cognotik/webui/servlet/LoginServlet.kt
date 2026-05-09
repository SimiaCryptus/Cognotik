package com.simiacryptus.cognotik.webui.servlet

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
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
        UsernamePasswordLoginMethod.toString()
    }

    init {
        ensureRegistered()
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
            val envKey = System.getenv("SESSION_ENVELOPE_KEY")
            if (!envKey.isNullOrBlank()) {
                val keyBytes = Base64.getDecoder().decode(envKey)
                require(keyBytes.size == 32) { "SESSION_ENVELOPE_KEY must be 32 bytes (Base64-encoded)" }
                SecretKeySpec(keyBytes, "AES")
            } else {
                val keyBytes: ByteArray = hashData(SESSION_ENVELOPE_KEY_SEED).take(32).toByteArray()
                log.info("Generated ephemeral session envelope key (tokens will not survive restart)")
                SecretKeySpec(keyBytes, "AES")
            }
        }

        fun hashData(data: String) : ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(data.toByteArray(Charsets.UTF_8))
        }

        fun hashPassword(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(hashBytes)
        }

        /**
         * Computes a salted hash-of-hash: SHA-256(salt + SHA-256(passwordHash)).
         * This provides an additional layer so the raw passwordHash is not embedded in the token.
         */
        fun saltedHashOfHash(passwordHash: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val inner = digest.digest(passwordHash.toByteArray(Charsets.UTF_8))
            digest.reset()
            digest.update(salt.toByteArray(Charsets.UTF_8))
            digest.update(inner)
            return Base64.getEncoder().encodeToString(digest.digest())
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
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
        }

        /**
         * Decrypts and parses a session token envelope.
         * Returns the parsed [SessionEnvelope] or null if decryption/parsing fails.
         */
        fun decryptSessionToken(token: String): SessionEnvelope? {
            return try {
                val combined = Base64.getUrlDecoder().decode(token)
                if (combined.size < GCM_IV_LENGTH + 1) return null
                val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
                val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, envelopeKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val plaintext = cipher.doFinal(ciphertext)
                val json = gson.fromJson(String(plaintext, Charsets.UTF_8), JsonObject::class.java)
                SessionEnvelope(
                    username = json.get("username").asString,
                    hohash = json.get("hohash").asString,
                    salt = json.get("salt").asString,
                    created = json.get("created").asLong,
                    nonce = json.get("nonce").asLong
                )
            } catch (e: Exception) {
                log.debug("Failed to decrypt session token: {}", e.message)
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
         * @return the [SessionEnvelope] if valid, or null if verification fails
         */
        fun verifySessionToken(
            token: String, passwordHash: String, maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000
        ): SessionEnvelope? {
            val envelope = decryptSessionToken(token) ?: return null
            // Check expiration
            val age = System.currentTimeMillis() - envelope.created
            if (age < 0 || age > maxAgeMs) {
                log.debug("Session token expired for user: {} (age={}ms)", envelope.username, age)
                return null
            }
            // Re-derive the salted hash-of-hash and compare
            val expectedHohash = saltedHashOfHash(passwordHash, envelope.salt)
            if (expectedHohash != envelope.hohash) {
                log.debug("Session token hohash mismatch for user: {}", envelope.username)
                return null
            }
            return envelope
        }

        /**
         * Data class representing the decrypted contents of a session token envelope.
         */
        data class SessionEnvelope(
            val username: String, val hohash: String, val salt: String, val created: Long, val nonce: Long
        )

        /**
         * Returns true if the registration attempt should be throttled (debounced).
         */
        fun isThrottled(key: String): Boolean {
            val now = System.currentTimeMillis()
            val lastAttempt = registrationAttempts[key]
            if (lastAttempt != null && (now - lastAttempt) < DEBOUNCE_INTERVAL_MS) {
                return true
            }
            registrationAttempts[key] = now
            // Clean up old entries periodically
            if (registrationAttempts.size > 1000) {
                val cutoff = now - DEBOUNCE_INTERVAL_MS
                registrationAttempts.entries.removeIf { it.value < cutoff }
            }
            return false
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
            val stream = LoginServlet::class.java.getResourceAsStream(resourceName)
                ?: throw IllegalStateException("Template resource not found: $resourceName")
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        private fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;")
        }
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val action = req.getParameter("action") ?: req.getParameter("formAction")
        when (action) {
            "register" -> {
                if (!IS_LOCAL_AUTH_ENABLED) {
                    log.warn("Registration attempt while local auth is disabled")
                    serveLoginPage(req, resp, error = "Local authentication is disabled.")
                    return
                }
                serveRegistrationPage(req, resp)
            }
           "logout" -> handleLogout(req, resp)
            else -> serveLoginPage(req, resp)
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val action = req.getParameter("action") ?: req.getParameter("formAction")
        when (action) {
            "register" -> {
                if (!IS_LOCAL_AUTH_ENABLED) {
                    log.warn("Registration POST attempt while local auth is disabled")
                    serveLoginPage(req, resp, error = "Local authentication is disabled.")
                    return
                }
                handleRegistration(req, resp)
            }
            "login" -> dispatchLogin(req, resp)
           "logout" -> handleLogout(req, resp)
            else -> {
                resp.sendRedirect("/login/")
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
        if (!methodName.isNullOrBlank()) {
            val method = try {
                LoginMethod.valueOf(methodName)
            } catch (e: Exception) {
                log.warn("Unknown login method requested: {}", methodName)
                null
            }
            if (method != null) {
                // If local auth is disabled, block the built-in username/password method
                if (!IS_LOCAL_AUTH_ENABLED && method == UsernamePasswordLoginMethod) {
                    log.warn("Username/password login attempt while local auth is disabled")
                    serveLoginPage(
                        req,
                        resp,
                        error = "Local authentication is disabled.",
                        target = req.getParameter("target")
                    )
                    return
                }
                try {
                    val handled = method.handleLogin(req, resp)
                    if (handled) return
                } catch (e: Exception) {
                    log.error("Error in login method '{}'", methodName, e)
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
            log.warn("Legacy login fallback blocked because local auth is disabled")
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
            log.warn("handleLogin invoked while local auth is disabled")
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

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            log.warn("Login attempt with missing credentials")
            serveLoginPage(req, resp, error = "Username and password are required.", target = target)
            return
        }

        try {
            val user = User(
                email = username,
                name = username,
                id = username,
            )
            val fileServices = ApplicationServices.fileApplicationServices()
            val settings = fileServices.userSettingsManager.getUserSettings(user)

            if (settings.passwordHash == null) {
                log.warn("Login attempt for user without password set: {}", username)
                serveLoginPage(req, resp, error = "User not found. Please register first.", target = target)
                return
            }

            val inputHash = hashPassword(password)
            if (inputHash != settings.passwordHash) {
                log.warn("Failed login attempt for user: {}", username)
                serveLoginPage(req, resp, error = "Username password is not set.", target = target)
                return
            }

            val accessToken = createSessionToken(username, inputHash)
            ApplicationServices.authenticationManager.putUser(accessToken, user)

            val cookie = Cookie(AuthenticationInterface.AUTH_COOKIE, accessToken)
            cookie.path = "/"
            cookie.maxAge = 60 * 60 * 24 * 7 // 7 days
            cookie.isHttpOnly = true
            resp.addCookie(cookie)

            log.info("User logged in successfully: {}", username)
            initializeSystem(user)
            val redirectUrl = if (!target.isNullOrBlank()) {
                try {
                    URLDecoder.decode(target, "UTF-8")
                } catch (e: Exception) {
                    "/"
                }
            } else "/"
            resp.sendRedirect(redirectUrl)
        } catch (e: Exception) {
            log.error("Error during login for user: {}", username, e)
            serveLoginPage(req, resp, error = "An error occurred during login.", target = target)
        }
    }

    private fun initializeSystem(user: User) {
    }
   /**
    * Handles logout by clearing the auth cookie, removing the session token from the
    * authentication manager, and redirecting to the login page (or an optional target).
    */
   private fun handleLogout(req: HttpServletRequest, resp: HttpServletResponse) {
       val target = req.getParameter("target")
       try {
           // Find the auth cookie (if any) and remove the corresponding user mapping
           val authCookie = req.cookies?.firstOrNull { it.name == AuthenticationInterface.AUTH_COOKIE }
           val token = authCookie?.value
           if (!token.isNullOrBlank()) {
               try {
                   val user = ApplicationServices.authenticationManager.getUser(token)
                   if (user == null) {
                       throw RuntimeException("No user found for token")
                   }
                   ApplicationServices.authenticationManager.logout(token, user)
                   log.info("User logged out: {}", user?.email ?: "<unknown>")
               } catch (e: Exception) {
                   log.warn("Error removing user from authentication manager during logout", e)
               }
           }
           // Clear the auth cookie on the client by sending a cookie with maxAge=0
           val clearCookie = Cookie(AuthenticationInterface.AUTH_COOKIE, "")
           clearCookie.path = "/"
           clearCookie.maxAge = 0
           clearCookie.isHttpOnly = true
           resp.addCookie(clearCookie)
       } catch (e: Exception) {
           log.error("Error during logout", e)
       }
       val redirectUrl = if (!target.isNullOrBlank()) {
           try {
               val decoded = URLDecoder.decode(target, "UTF-8")
               val encoded = URLEncoder.encode(decoded, "UTF-8")
               "/login/?target=$encoded"
           } catch (e: Exception) {
               "/login/"
           }
       } else "/login/"
       resp.sendRedirect(redirectUrl)
   }


    private fun handleRegistration(req: HttpServletRequest, resp: HttpServletResponse) {
        if (!IS_LOCAL_AUTH_ENABLED) {
            log.warn("handleRegistration invoked while local auth is disabled")
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

        if (username.isNullOrBlank() || password.isNullOrBlank() || confirmPassword.isNullOrBlank()) {
            serveRegistrationPage(req, resp, error = "All fields are required.", target = target)
            return
        }

        if (password != confirmPassword) {
            serveRegistrationPage(req, resp, error = "Passwords do not match.", target = target)
            return
        }

        if (password.isEmpty()) {
            serveRegistrationPage(req, resp, error = "Password cannot be empty.", target = target)
            return
        }

        try {
            val user = User(
                email = username,
                name = username,
                id = username
            )
            val fileServices = ApplicationServices.fileApplicationServices()
            val existingSettings = fileServices.userSettingsManager.getUserSettings(user)

            if (existingSettings.passwordHash != null) {
                serveRegistrationPage(req, resp, error = "User already exists. Please login instead.", target = target)
                return
            }
            // Debounce registration attempts by IP + username
            val throttleKey = "${req.remoteAddr}:$username"
            if (isThrottled(throttleKey)) {
                log.warn("Registration attempt throttled for key: {}", throttleKey)
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
                log.error("Failed to show registration confirmation dialog", e)
                false
            }
            if (!approved) {
                log.info("Registration denied by operator for user: {}", username)
                serveRegistrationPage(
                    req,
                    resp,
                    error = "Registration was not approved. Please contact the administrator.",
                    target = target
                )
                return
            }

            val newSettings = existingSettings.copy(passwordHash = hashPassword(password))
            fileServices.userSettingsManager.updateUserSettings(user, newSettings)

            log.info("User registered successfully: {}", username)

            val accessToken = createSessionToken(username, hashPassword(password))
            ApplicationServices.authenticationManager.putUser(accessToken, user)

            val cookie = Cookie(AuthenticationInterface.AUTH_COOKIE, accessToken)
            cookie.path = "/"
            cookie.maxAge = 60 * 60 * 24 * 7 // 7 days
            cookie.isHttpOnly = true
            resp.addCookie(cookie)

            val redirectUrl = if (!target.isNullOrBlank()) {
                try {
                    URLDecoder.decode(target, "UTF-8")
                } catch (e: Exception) {
                    "/"
                }
            } else "/"
            resp.sendRedirect(redirectUrl)
        } catch (e: Exception) {
            log.error("Error during registration for user: {}", username, e)
            serveRegistrationPage(req, resp, error = "An error occurred during registration.", target = target)
        }
    }

    private fun serveLoginPage(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        error: String? = null,
        target: String? = null
    ) {
        val effectiveTarget = target ?: req.getParameter("target")
        val targetParam = if (!effectiveTarget.isNullOrBlank()) effectiveTarget else null
        val encodedTarget = targetParam?.let { URLEncoder.encode(it, "UTF-8") }
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
        resp.writer.write(html)
    }

    private fun serveRegistrationPage(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        error: String? = null,
        target: String? = null
    ) {
        val effectiveTarget = target ?: req.getParameter("target")
        val targetParam = if (!effectiveTarget.isNullOrBlank()) effectiveTarget else null
        val encodedTarget = targetParam?.let { URLEncoder.encode(it, "UTF-8") }
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
        resp.writer.write(html)
    }
}