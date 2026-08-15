package com.simiacryptus.cognotik.auth

/**
 * Represents a single authorization step that must succeed before proceeding.
 * Steps are composable via [AuthorizationChain].
  *
  * Steps can operate in two modes:
  * 1. Programmatic (headless): using [authorize] with callbacks
  * 2. Web-interactive: using [renderHtml] to present UI with callbacks
  *
  * For non-interactive steps, [authorize] must invoke either [onSuccess] or [onFailure]
  * synchronously (before returning) when used in a web flow context.
 */
fun interface AuthorizationStep {
    /**
     * Perform this authorization step.
     * @param onSuccess Called when this step succeeds; the step should invoke this to continue the chain.
     * @param onFailure Called when this step fails, with a human-readable reason.
     */
    fun authorize(onSuccess: () -> Unit, onFailure: (reason: String) -> Unit)

     /**
      * Whether this step requires web-based user interaction.
      * If true, the authorization chain will render HTML and wait for a callback.
      */
     fun requiresWebInteraction(): Boolean = false

     /**
      * Render HTML content for this authorization step.
      * The HTML should include forms/buttons that POST back to the callback URL.
      *
      * @param callbackUrl The URL to POST/GET callback responses to
      * @param sessionId The session ID for this authorization flow
      * @return HTML string to display to the user
      */
     fun renderHtml(sessionId: String, callbackUrl: ((AuthorizationChain.AuthorizationSession)->String)->String): String {
         val escapedSessionId = sessionId.replace("&", "&amp;").replace("\"", "&quot;")
             .replace("<", "&lt;").replace(">", "&gt;")
         return """<p>Authorization step: ${javaClass.simpleName}</p>
            <form method="POST" action="${callbackUrl{"OK!"}}">
                <input type="hidden" name="action" value="authCallback"/>
                <input type="hidden" name="sessionId" value="$escapedSessionId"/>
                <button type="submit" name="approve" value="true" class="btn-success">Approve</button>
                <button type="submit" name="deny" value="true" class="btn-danger">Deny</button>
            </form>"""
     }

}