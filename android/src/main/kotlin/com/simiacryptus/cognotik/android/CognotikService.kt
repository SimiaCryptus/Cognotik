package com.simiacryptus.cognotik.android

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.simiacryptus.cognotik.android.CognotikApplication.Companion.initializeEmojiCompatStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CognotikService : Service() {

    private val binder = CognotikBinder()
    private var cognotikApps: AndroidCognotikApps? = null
    private var serverJob: Job? = null
    private var serverPort: Int = 12891
    private var startTime: Long = 0

    interface ServerStatusListener {
        fun onServerStarted(port: Int)
        fun onServerError(error: String)
    }

    private var statusListener: ServerStatusListener? = null

    inner class CognotikBinder : Binder() {
        fun getService(): CognotikService = this@CognotikService
    }

    override fun onCreate() {
        super.onCreate()
        try {
            initializeEmojiCompatStatic(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "EmojiCompat initialization failed in service: ${e.message}")
            // Continue without emoji support rather than crashing
        }
        Log.i(TAG, "CognotikService created")
        Log.d(TAG, "Service process ID: ${android.os.Process.myPid()}")
        Log.d(TAG, "Service thread: ${Thread.currentThread().name}")
    }


    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound by client")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service unbound by client")
        return super.onUnbind(intent)
    }

    fun setStatusListener(listener: ServerStatusListener?) {
        Log.d(TAG, "Status listener ${if (listener != null) "set" else "cleared"}")
        this.statusListener = listener
    }

    fun startCognotikServer() {
        if (cognotikApps != null) {
            Log.i(TAG, "Server already running on port $serverPort")
            statusListener?.onServerStarted(serverPort)
            return
        }
        Log.i(TAG, "Starting Cognotik server...")
        startTime = System.currentTimeMillis()
        // Log system information
        logSystemInfo()


        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Server startup coroutine started on thread: ${Thread.currentThread().name}")
                Log.d(TAG, "Application context: ${applicationContext.javaClass.simpleName}")

                val filesDir = applicationContext.filesDir
                Log.i(TAG, "Files directory: ${filesDir.absolutePath}")
                Log.d(TAG, "Files directory exists: ${filesDir.exists()}")
                Log.d(TAG, "Files directory writable: ${filesDir.canWrite()}")
                Log.d(TAG, "Available space: ${filesDir.freeSpace / 1024 / 1024} MB")

                Log.d(TAG, "Creating AndroidCognotikApps instance...")
                cognotikApps = AndroidCognotikApps.create(applicationContext)
                Log.d(TAG, "Starting server...")
                serverPort = cognotikApps?.startServer() ?: 0

                if (serverPort > 0) {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    Log.i(TAG, "Server started successfully on port $serverPort in ${elapsedTime}ms")
                    Log.i(TAG, "Server URL: http://localhost:$serverPort")
                    statusListener?.onServerStarted(serverPort)
                } else {
                    Log.e(TAG, "Server startup failed - invalid port returned: $serverPort")
                    throw Exception("Failed to start server - invalid port")
                }
            } catch (e: Exception) {
                val elapsedTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "Error starting server after ${elapsedTime}ms", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")

                // Log additional context
                Log.e(TAG, "Current thread: ${Thread.currentThread().name}")
                Log.e(TAG, "Available memory: ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB")

                e.printStackTrace()
                statusListener?.onServerError(e.message ?: "Unknown error")
                cognotikApps = null
                serverPort = 0
            }
        }
        Log.d(TAG, "Server startup coroutine launched")
    }

    private fun logSystemInfo() {
        Log.d(TAG, "=== System Information ===")
        Log.d(TAG, "Android version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        Log.d(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.d(TAG, "Architecture: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        val runtime = Runtime.getRuntime()
        Log.d(TAG, "Available processors: ${runtime.availableProcessors()}")
        Log.d(TAG, "Max memory: ${runtime.maxMemory() / 1024 / 1024} MB")
        Log.d(TAG, "Total memory: ${runtime.totalMemory() / 1024 / 1024} MB")
        Log.d(TAG, "Free memory: ${runtime.freeMemory() / 1024 / 1024} MB")
        // Log storage info
        val filesDir = applicationContext.filesDir
        Log.d(
            TAG,
            "Internal storage: ${filesDir.totalSpace / 1024 / 1024} MB total, ${filesDir.freeSpace / 1024 / 1024} MB free"
        )
        Log.d(TAG, "=========================")
    }

    fun getServerPort(): Int {
        return serverPort
    }

    fun isServerRunning(): Boolean {
        val running = cognotikApps != null && serverPort > 0
        Log.d(TAG, "Server running status: $running (port: $serverPort)")
        return running
    }

    fun stopCognotikServer() {
        Log.i(TAG, "Stopping Cognotik server...")
        if (serverJob?.isActive == true) {
            Log.d(TAG, "Cancelling server job...")
            serverJob?.cancel()
        }
        if (cognotikApps != null) {
            Log.d(TAG, "Cleaning up server instance...")
            //cognotikApps?.stopServer()
            cognotikApps = null
        }
        if (serverPort > 0) {
            Log.d(TAG, "Server was running on port: $serverPort")
        }

        serverJob?.cancel()
        cognotikApps = null
        serverPort = 0
        startTime = 0

        Log.i(TAG, "Server stopped successfully")
    }

    override fun onDestroy() {
        Log.i(TAG, "CognotikService being destroyed")
        super.onDestroy()
        stopCognotikServer()
        Log.d(TAG, "CognotikService destroyed")
    }

    companion object {
        private const val TAG = "CognotikService"
    }
}