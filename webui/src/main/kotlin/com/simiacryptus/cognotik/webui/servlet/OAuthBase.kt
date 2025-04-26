package com.simiacryptus.cognotik.webui.servlet

import org.eclipse.jetty.webapp.WebAppContext

abstract class OAuthBase(val redirectUri: String) {
    abstract fun configure(context: WebAppContext, addFilter: Boolean = true): WebAppContext
}