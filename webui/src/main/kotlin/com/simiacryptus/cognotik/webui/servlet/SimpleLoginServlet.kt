package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SimpleLoginServlet : HttpServlet() {

  companion object {
    private val log = LoggerFactory.getLogger(SimpleLoginServlet::class.java)
    private const val ACCESS_TOKEN_COOKIE = "access_token"
     private const val DEBOUNCE_INTERVAL_MS = 30_000L // 30 seconds between registration attempts per IP/username
     private val registrationAttempts = ConcurrentHashMap<String, Long>()
     private val dialogActive = AtomicBoolean(false)

    fun hashPassword(password: String): String {
      val digest = MessageDigest.getInstance("SHA-256")
      val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
      return Base64.getEncoder().encodeToString(hashBytes)
    }
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
       // Prevent multiple dialogs from stacking up
       if (!dialogActive.compareAndSet(false, true)) {
         log.warn("Registration confirmation dialog already active, rejecting request for user: {}", username)
         return false
       }
       try {
         val latch = CountDownLatch(1)
         val approved = AtomicBoolean(false)
         SwingUtilities.invokeLater {
           try {
             val result = JOptionPane.showConfirmDialog(
               null,
               "A new user registration has been requested:\n\n" +
                 "Username: $username\n" +
                 "Remote IP: $remoteAddr\n\n" +
                 "Do you want to allow this registration?",
               "Registration Confirmation",
               JOptionPane.YES_NO_OPTION,
               JOptionPane.QUESTION_MESSAGE
             )
             approved.set(result == JOptionPane.YES_OPTION)
           } catch (e: Exception) {
             log.error("Error showing registration confirmation dialog", e)
             approved.set(false)
           } finally {
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
  }

  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    val action = req.getParameter("action")
    when (action) {
      "register" -> serveRegistrationPage(req, resp)
      else -> serveLoginPage(req, resp)
    }
  }

  override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    val action = req.getParameter("action")
    when (action) {
      "register" -> handleRegistration(req, resp)
      "login" -> handleLogin(req, resp)
      else -> {
        resp.sendRedirect("/login/")
      }
    }
  }

  private fun handleLogin(req: HttpServletRequest, resp: HttpServletResponse) {
    val username = req.getParameter("username")?.trim()
    val password = req.getParameter("password")

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
      log.warn("Login attempt with missing credentials")
      serveLoginPage(req, resp, error = "Username and password are required.")
      return
    }

    try {
      val user = User(email = username, name = username, id = username, picture = null)
      val fileServices = ApplicationServices.fileApplicationServices()
      val settings = fileServices.userSettingsManager.getUserSettings(user)

      if (settings.passwordHash == null) {
        log.warn("Login attempt for user without password set: {}", username)
        serveLoginPage(req, resp, error = "User not found. Please register first.")
        return
      }

      val inputHash = hashPassword(password)
      if (inputHash != settings.passwordHash) {
        log.warn("Failed login attempt for user: {}", username)
        serveLoginPage(req, resp, error = "Invalid username or password.")
        return
      }

      val accessToken = UUID.randomUUID().toString()
      ApplicationServices.authenticationManager.putUser(accessToken, user)

      val cookie = jakarta.servlet.http.Cookie(AuthenticationInterface.AUTH_COOKIE, accessToken)
      cookie.path = "/"
      cookie.maxAge = 60 * 60 * 24 * 7 // 7 days
      cookie.isHttpOnly = true
      resp.addCookie(cookie)

      log.info("User logged in successfully: {}", username)
      resp.sendRedirect("/")
    } catch (e: Exception) {
      log.error("Error during login for user: {}", username, e)
      serveLoginPage(req, resp, error = "An error occurred during login.")
    }
  }

  private fun handleRegistration(req: HttpServletRequest, resp: HttpServletResponse) {
    val username = req.getParameter("username")?.trim()
    val password = req.getParameter("password")
    val confirmPassword = req.getParameter("confirmPassword")

    if (username.isNullOrBlank() || password.isNullOrBlank() || confirmPassword.isNullOrBlank()) {
      serveRegistrationPage(req, resp, error = "All fields are required.")
      return
    }

    if (password != confirmPassword) {
      serveRegistrationPage(req, resp, error = "Passwords do not match.")
      return
    }

    if (password.length < 6) {
      serveRegistrationPage(req, resp, error = "Password must be at least 6 characters long.")
      return
    }

    try {
      val user = User(email = username, name = username, id = username, picture = null)
      val fileServices = ApplicationServices.fileApplicationServices()
      val existingSettings = fileServices.userSettingsManager.getUserSettings(user)

      if (existingSettings.passwordHash != null) {
        serveRegistrationPage(req, resp, error = "User already exists. Please login instead.")
        return
      }
       // Debounce registration attempts by IP + username
       val throttleKey = "${req.remoteAddr}:$username"
       if (isThrottled(throttleKey)) {
         log.warn("Registration attempt throttled for key: {}", throttleKey)
         serveRegistrationPage(req, resp, error = "Too many registration attempts. Please try again later.")
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
         serveRegistrationPage(req, resp, error = "Registration was not approved. Please contact the administrator.")
         return
       }


      val passwordHash = hashPassword(password)
      val newSettings = existingSettings.copy(passwordHash = passwordHash)
      fileServices.userSettingsManager.updateUserSettings(user, newSettings)

      log.info("User registered successfully: {}", username)

      val accessToken = UUID.randomUUID().toString()
      ApplicationServices.authenticationManager.putUser(accessToken, user)

      val cookie = jakarta.servlet.http.Cookie(ACCESS_TOKEN_COOKIE, accessToken)
      cookie.path = "/"
      cookie.maxAge = 60 * 60 * 24 * 7 // 7 days
      cookie.isHttpOnly = true
      resp.addCookie(cookie)

      resp.sendRedirect("/")
    } catch (e: Exception) {
      log.error("Error during registration for user: {}", username, e)
      serveRegistrationPage(req, resp, error = "An error occurred during registration.")
    }
  }

  private fun serveLoginPage(req: HttpServletRequest, resp: HttpServletResponse, error: String? = null) {
    resp.contentType = "text/html"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(
      """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Login</title>
                <style>
                    body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; background-color: #f5f5f5; }
                    .login-container { background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); width: 100%; max-width: 400px; }
                    h2 { text-align: center; color: #333; margin-bottom: 1.5rem; }
                    .form-group { margin-bottom: 1rem; }
                    label { display: block; margin-bottom: 0.5rem; color: #555; font-weight: bold; }
                    input[type="text"], input[type="password"] { width: 100%; padding: 0.75rem; border: 1px solid #ddd; border-radius: 4px; font-size: 1rem; box-sizing: border-box; }
                    input[type="text"]:focus, input[type="password"]:focus { outline: none; border-color: #4a90d9; box-shadow: 0 0 3px rgba(74,144,217,0.3); }
                    button { width: 100%; padding: 0.75rem; background-color: #4a90d9; color: white; border: none; border-radius: 4px; font-size: 1rem; cursor: pointer; }
                    button:hover { background-color: #357abd; }
                    .error { color: #d9534f; background-color: #fdf2f2; padding: 0.75rem; border-radius: 4px; margin-bottom: 1rem; text-align: center; }
                    .links { text-align: center; margin-top: 1rem; }
                    .links a { color: #4a90d9; text-decoration: none; }
                    .links a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
                <div class="login-container">
                    <h2>Login</h2>
                    ${if (error != null) """<div class="error">$error</div>""" else ""}
                    <form method="POST" action="/login/">
                        <input type="hidden" name="action" value="login">
                        <div class="form-group">
                            <label for="username">Username</label>
                            <input type="text" id="username" name="username" required autofocus>
                        </div>
                        <div class="form-group">
                            <label for="password">Password</label>
                            <input type="password" id="password" name="password" required>
                        </div>
                        <button type="submit">Login</button>
                    </form>
                    <div class="links">
                        <a href="/login?action=register">Don't have an account? Register</a>
                    </div>
                </div>
            </body>
            </html>
            """.trimIndent()
    )
  }

  private fun serveRegistrationPage(req: HttpServletRequest, resp: HttpServletResponse, error: String? = null) {
    resp.contentType = "text/html"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(
      """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Register</title>
                <style>
                    body { font-family: Arial, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; background-color: #f5f5f5; }
                    .login-container { background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); width: 100%; max-width: 400px; }
                    h2 { text-align: center; color: #333; margin-bottom: 1.5rem; }
                    .form-group { margin-bottom: 1rem; }
                    label { display: block; margin-bottom: 0.5rem; color: #555; font-weight: bold; }
                    input[type="text"], input[type="password"] { width: 100%; padding: 0.75rem; border: 1px solid #ddd; border-radius: 4px; font-size: 1rem; box-sizing: border-box; }
                    input[type="text"]:focus, input[type="password"]:focus { outline: none; border-color: #4a90d9; box-shadow: 0 0 3px rgba(74,144,217,0.3); }
                    button { width: 100%; padding: 0.75rem; background-color: #4a90d9; color: white; border: none; border-radius: 4px; font-size: 1rem; cursor: pointer; }
                    button:hover { background-color: #357abd; }
                    .error { color: #d9534f; background-color: #fdf2f2; padding: 0.75rem; border-radius: 4px; margin-bottom: 1rem; text-align: center; }
                    .links { text-align: center; margin-top: 1rem; }
                    .links a { color: #4a90d9; text-decoration: none; }
                    .links a:hover { text-decoration: underline; }
                </style>
            </head>
            <body>
                <div class="login-container">
                    <h2>Register</h2>
                    ${if (error != null) """<div class="error">$error</div>""" else ""}
                    <form method="POST" action="/login/">
                        <input type="hidden" name="action" value="register">
                        <div class="form-group">
                            <label for="username">Username</label>
                            <input type="text" id="username" name="username" required autofocus>
                        </div>
                        <div class="form-group">
                            <label for="password">Password</label>
                            <input type="password" id="password" name="password" required>
                        </div>
                        <div class="form-group">
                            <label for="confirmPassword">Confirm Password</label>
                            <input type="password" id="confirmPassword" name="confirmPassword" required>
                        </div>
                        <button type="submit">Register</button>
                    </form>
                    <div class="links">
                        <a href="/login/">Already have an account? Login</a>
                    </div>
                </div>
            </body>
            </html>
            """.trimIndent()
    )
  }
}