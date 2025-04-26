package com.simiacryptus.cognotik

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

object OutputInterceptor {
    private val originalOut: PrintStream = System.out
    private val originalErr: PrintStream = System.err
    private val isSetup = AtomicBoolean(false)
    private val globalStreamLock = Any()

    fun setupInterceptor() {
        if (isSetup.getAndSet(true)) return
        System.setOut(PrintStream(OutputStreamRouter(originalOut)))
        System.setErr(PrintStream(OutputStreamRouter(originalErr)))
    }

    private val globalStream = ByteArrayOutputStream()

    private val threadLocalBuffer = WeakHashMap<Thread, ByteArrayOutputStream>()

    private fun getThreadOutputStream(): ByteArrayOutputStream {
        val currentThread = Thread.currentThread()
        synchronized(threadLocalBuffer) {
            return threadLocalBuffer.getOrPut(currentThread) { ByteArrayOutputStream() }
        }
    }

    fun getThreadOutput(): String {
        val outputStream = getThreadOutputStream()
        try {
            outputStream.flush()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return outputStream.toString()
    }

    fun clearThreadOutput() {
        getThreadOutputStream().reset()
    }

    fun getGlobalOutput(): String {
        synchronized(globalStreamLock) {
            return globalStream.toString()
        }
    }

    fun clearGlobalOutput() {
        synchronized(globalStreamLock) {
            globalStream.reset()
        }
    }

    private class OutputStreamRouter(private val originalStream: PrintStream) : ByteArrayOutputStream() {
        private val maxGlobalBuffer = 8 * 1024 * 1024
        private val maxThreadBuffer = 1024 * 1024

        override fun write(b: Int) {
            originalStream.write(b)
            synchronized(globalStreamLock) {
                if (globalStream.size() > maxGlobalBuffer) {
                    globalStream.reset()
                }
                globalStream.write(b)
            }
            val threadOutputStream = getThreadOutputStream()
            if (threadOutputStream.size() > maxThreadBuffer) {
                threadOutputStream.reset()
            }
            threadOutputStream.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            originalStream.write(b, off, len)
            synchronized(globalStreamLock) {
                if (globalStream.size() > maxGlobalBuffer) {
                    globalStream.reset()
                }
                globalStream.write(b, off, len)
            }
            val threadOutputStream = getThreadOutputStream()
            if (threadOutputStream.size() > maxThreadBuffer) {
                threadOutputStream.reset()
            }
            threadOutputStream.write(b, off, len)
        }
    }
}