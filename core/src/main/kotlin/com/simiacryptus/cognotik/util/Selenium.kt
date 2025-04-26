package com.simiacryptus.cognotik.util

import java.net.URL

interface Selenium : AutoCloseable {
    fun navigate(url: String)
    fun getPageSource(): String
    fun getCurrentUrl(): String
    fun executeScript(script: String): Any?
    fun quit()

    fun save(
        url: URL,
        currentFilename: String?,
        saveRoot: String
    )

    fun setScriptTimeout(timeout: Long)
    fun getBrowserInfo(): String
    fun forceQuit()
    fun isAlive(): Boolean
    fun getLogs(): String

}