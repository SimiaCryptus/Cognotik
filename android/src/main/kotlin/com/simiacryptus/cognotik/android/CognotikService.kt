package com.simiacryptus.cognotik.android

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CognotikService : Service() {
    
    private val binder = CognotikBinder()
    private var cognotikApps: AndroidCognotikApps? = null
    private var serverJob: Job? = null
    private var serverPort: Int = 0
    
    interface ServerStatusListener {
        fun onServerStarted(port: Int)
        fun onServerError(error: String)
    }
    
    private var statusListener: ServerStatusListener? = null
    
    inner class CognotikBinder : Binder() {
        fun getService(): CognotikService = this@CognotikService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    fun setStatusListener(listener: ServerStatusListener?) {
        this.statusListener = listener
    }
    
    fun startCognotikServer() {
        if (cognotikApps != null) {
            Log.d(TAG, "Server already running on port $serverPort")
            statusListener?.onServerStarted(serverPort)
            return
        }
        
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting Cognotik server...")
                cognotikApps = AndroidCognotikApps.create(applicationContext)
                serverPort = cognotikApps?.startServer() ?: 0
                
                if (serverPort > 0) {
                    Log.d(TAG, "Server started successfully on port $serverPort")
                    statusListener?.onServerStarted(serverPort)
                } else {
                    throw Exception("Failed to start server - invalid port")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server", e)
                statusListener?.onServerError(e.message ?: "Unknown error")
                cognotikApps = null
                serverPort = 0
            }
        }
    }
    
    fun getServerPort(): Int = serverPort
    
    fun isServerRunning(): Boolean = cognotikApps != null && serverPort > 0
    
    fun stopCognotikServer() {
        serverJob?.cancel()
        cognotikApps?.stopServer()
        cognotikApps = null
        serverPort = 0
        Log.d(TAG, "Server stopped")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopCognotikServer()
    }
    
    companion object {
        private const val TAG = "CognotikService"
    }
}