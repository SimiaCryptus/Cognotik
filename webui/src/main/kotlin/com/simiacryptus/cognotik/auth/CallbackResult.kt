package com.simiacryptus.cognotik.auth

/**
  * Result of handling a web callback for an authorization step.
  */
sealed class CallbackResult {
     /** Step succeeded, proceed to next step */
     object Success : CallbackResult() {
         override fun toString(): String = "CallbackResult.Success"
     }

     /** Step failed with a reason */
     data class Failure(val reason: String) : CallbackResult()

     /**
      * Step needs to redirect the user (e.g., OAuth flow).
      * @param url The URL to redirect to
      */
     data class Redirect(val url: String) : CallbackResult()

     /**
      * Step needs to render additional HTML (e.g., multi-page form).
      * @param html The HTML to render
      */
     data class RenderHtml(val html: String) : CallbackResult()
}