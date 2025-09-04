package com.simiacryptus.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object LoggerFactory {

    fun getLogger(javaClass: Class<*>) : Logger {
        val tag = javaClass.name
        return getLogger(tag)
    }

    fun getLogger(tag: String): Logger {
        return if (isAndroid()) {
            LoggerFactory.getLogger("CognotikService")
        } else {
            LoggerFactory.getLogger(tag)
        }
    }

    fun isAndroid(): Boolean {
        return try {
            Class.forName("android.os.Build")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

}