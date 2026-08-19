package com.simiacryptus.cognotik.webui.servlet

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * The default username/password login implementation.
 *
 * Renders the standard login form (username + password fields) and lets the
 * existing LoginServlet handle credential validation. handleLogin returns
 * false here so the legacy code path in LoginServlet continues to execute.
 */
object UsernamePasswordLoginMethod : LoginMethod(
    name = "userpass",
    displayName = "Username & Password",
    iconUrl = null,
) {

    override fun renderForm(request: HttpServletRequest, redirect: String?): String {
        val targetField = if (redirect.isNullOrBlank()) ""
        else """<input type="hidden" name="target" value="${escapeHtml(redirect)}">"""
        return """
    <form id="loginForm" name="loginForm" method="POST" action="/login/" autocomplete="on">
            <input type="hidden" name="formAction" value="login">
            <input type="hidden" name="loginMethod" value="$name">
            $targetField
            <div class="form-group">
                <label for="username">Username</label>
                <input type="text" id="username" name="username"
                       autocomplete="username webauthn"
                       placeholder="Enter your username" required autofocus>
            </div>
            <div class="form-group">
                <label for="password">Password</label>
                <input type="password" id="password" name="password"
                       autocomplete="current-password webauthn"
                       placeholder="Enter your password" required>
            </div>
            <button type="submit" name="login" value="Sign In">Sign In</button>
        </form>
    """.trimIndent()
    }

    override fun handleLogin(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        // The legacy LoginServlet still owns the actual authentication logic for
        // username/password. Returning false signals that it should continue.
        return false
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    init {
        // Register on first reference.
        register(this)
    }
}