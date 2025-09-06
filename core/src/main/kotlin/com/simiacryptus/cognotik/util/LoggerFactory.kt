package com.simiacryptus.cognotik.util

import android.util.Log
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker

object LoggerFactory {

    var ANDROID_TAG: String = "CognotikService"
    fun getLogger(javaClass: Class<*>) : Logger {
        val tag = javaClass.name
        return getLogger(tag)
    }

    fun getLogger(tag: String): Logger {
        return if (isAndroid()) {
            AndroidLogger(tag)
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
    private class AndroidLogger(private val tag: String) : Logger {
        private fun formatMessage(format: String?, vararg args: Any?): String {
            return if (format == null) {
                ""
            } else if (args.isEmpty()) {
                format
            } else {
                try {
                    String.format(format, *args)
                } catch (e: Exception) {
                    "$format ${args.joinToString(" ")}"
                }
            }
        }

        private fun logToAndroid(level: String, message: String?, throwable: Throwable? = null) {
            try {
                when (level) {
                    "TRACE", "DEBUG" -> {
                        if (throwable != null) {
                            Log.d(tag, message ?: "", throwable)
                        } else {
                            Log.d(tag, message ?: "")
                        }
                    }
                    "INFO" -> {
                        if (throwable != null) {
                            Log.i(tag, message ?: "", throwable)
                        } else {
                            Log.i(tag, message ?: "")
                        }
                    }
                    "WARN" -> {
                        if (throwable != null) {
                            Log.w(tag, message ?: "", throwable)
                        } else {
                            Log.w(tag, message ?: "")
                        }
                    }
                    "ERROR" -> {
                        if (throwable != null) {
                            Log.e(tag, message ?: "", throwable)
                        } else {
                            Log.e(tag, message ?: "")
                        }
                    }
                    else -> {
                        if (throwable != null) {
                            Log.v(tag, message ?: "", throwable)
                        } else {
                            Log.v(tag, message ?: "")
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to System.out if reflection fails
                println("[$ANDROID_TAG] $level: $message")
                throwable?.printStackTrace()
            }
        }

        override fun getName() = tag
        // TRACE level methods
        override fun isTraceEnabled(): Boolean = true
        override fun isTraceEnabled(marker: Marker?): Boolean = true
        override fun trace(msg: String?) = logToAndroid("TRACE", msg)
        override fun trace(format: String?, arg: Any?) = logToAndroid("TRACE", formatMessage(format, arg))
        override fun trace(format: String?, arg1: Any?, arg2: Any?) = logToAndroid("TRACE", formatMessage(format, arg1, arg2))
        override fun trace(format: String?, vararg arguments: Any?) = logToAndroid("TRACE", formatMessage(format, *arguments))
        override fun trace(msg: String?, t: Throwable?) = logToAndroid("TRACE", msg, t)
        override fun trace(marker: Marker?, msg: String?) = trace(msg)
        override fun trace(marker: Marker?, format: String?, arg: Any?) = trace(format, arg)
        override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = trace(format, arg1, arg2)
        override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) = trace(format, *argArray)
        override fun trace(marker: Marker?, msg: String?, t: Throwable?) = trace(msg, t)
        // DEBUG level methods
        override fun isDebugEnabled(): Boolean = true
        override fun isDebugEnabled(marker: Marker?): Boolean = true
        override fun debug(msg: String?) = logToAndroid("DEBUG", msg)
        override fun debug(format: String?, arg: Any?) = logToAndroid("DEBUG", formatMessage(format, arg))
        override fun debug(format: String?, arg1: Any?, arg2: Any?) = logToAndroid("DEBUG", formatMessage(format, arg1, arg2))
        override fun debug(format: String?, vararg arguments: Any?) = logToAndroid("DEBUG", formatMessage(format, *arguments))
        override fun debug(msg: String?, t: Throwable?) = logToAndroid("DEBUG", msg, t)
        override fun debug(marker: Marker?, msg: String?) = debug(msg)
        override fun debug(marker: Marker?, format: String?, arg: Any?) = debug(format, arg)
        override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = debug(format, arg1, arg2)
        override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) = debug(format, *arguments)
        override fun debug(marker: Marker?, msg: String?, t: Throwable?) = debug(msg, t)
        // INFO level methods
        override fun isInfoEnabled(): Boolean = true
        override fun isInfoEnabled(marker: Marker?): Boolean = true
        override fun info(msg: String?) = logToAndroid("INFO", msg)
        override fun info(format: String?, arg: Any?) = logToAndroid("INFO", formatMessage(format, arg))
        override fun info(format: String?, arg1: Any?, arg2: Any?) = logToAndroid("INFO", formatMessage(format, arg1, arg2))
        override fun info(format: String?, vararg arguments: Any?) = logToAndroid("INFO", formatMessage(format, *arguments))
        override fun info(msg: String?, t: Throwable?) = logToAndroid("INFO", msg, t)
        override fun info(marker: Marker?, msg: String?) = info(msg)
        override fun info(marker: Marker?, format: String?, arg: Any?) = info(format, arg)
        override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = info(format, arg1, arg2)
        override fun info(marker: Marker?, format: String?, vararg arguments: Any?) = info(format, *arguments)
        override fun info(marker: Marker?, msg: String?, t: Throwable?) = info(msg, t)
        // WARN level methods
        override fun isWarnEnabled(): Boolean = true
        override fun isWarnEnabled(marker: Marker?): Boolean = true
        override fun warn(msg: String?) = logToAndroid("WARN", msg)
        override fun warn(format: String?, arg: Any?) = logToAndroid("WARN", formatMessage(format, arg))
        override fun warn(format: String?, arg1: Any?, arg2: Any?) = logToAndroid("WARN", formatMessage(format, arg1, arg2))
        override fun warn(format: String?, vararg arguments: Any?) = logToAndroid("WARN", formatMessage(format, *arguments))
        override fun warn(msg: String?, t: Throwable?) = logToAndroid("WARN", msg, t)
        override fun warn(marker: Marker?, msg: String?) = warn(msg)
        override fun warn(marker: Marker?, format: String?, arg: Any?) = warn(format, arg)
        override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = warn(format, arg1, arg2)
        override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) = warn(format, *arguments)
        override fun warn(marker: Marker?, msg: String?, t: Throwable?) = warn(msg, t)
        // ERROR level methods
        override fun isErrorEnabled(): Boolean = true
        override fun isErrorEnabled(marker: Marker?): Boolean = true
        override fun error(msg: String?) = logToAndroid("ERROR", msg)
        override fun error(format: String?, arg: Any?) = logToAndroid("ERROR", formatMessage(format, arg))
        override fun error(format: String?, arg1: Any?, arg2: Any?) = logToAndroid("ERROR", formatMessage(format, arg1, arg2))
        override fun error(format: String?, vararg arguments: Any?) = logToAndroid("ERROR", formatMessage(format, *arguments))
        override fun error(msg: String?, t: Throwable?) = logToAndroid("ERROR", msg, t)
        override fun error(marker: Marker?, msg: String?) = error(msg)
        override fun error(marker: Marker?, format: String?, arg: Any?) = error(format, arg)
        override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) = error(format, arg1, arg2)
        override fun error(marker: Marker?, format: String?, vararg arguments: Any?) = error(format, *arguments)
        override fun error(marker: Marker?, msg: String?, t: Throwable?) = error(msg, t)
    }

}