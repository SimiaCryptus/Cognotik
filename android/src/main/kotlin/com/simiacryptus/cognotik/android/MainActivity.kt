package com.simiacryptus.cognotik.android

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.emoji2.text.EmojiCompat

class MainActivity : AppCompatActivity(), CognotikService.ServerStatusListener {
    
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var webView: WebView
    
    private var cognotikService: CognotikService? = null
    private var isBound = false
    private var activityStartTime: Long = 0
    private var serverStartTime: Long = 0
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i(TAG, "Service connected: ${className.className}")
            val binder = service as CognotikService.CognotikBinder
            cognotikService = binder.getService()
            cognotikService?.setStatusListener(this@MainActivity)
            isBound = true
            Log.d(TAG, "Service binding completed")
            
            // Check if server is already running
            val isRunning = cognotikService?.isServerRunning() == true
            Log.d(TAG, "Server already running: $isRunning")
            
            if (isRunning) {
                val port = cognotikService?.getServerPort() ?: 0
                Log.i(TAG, "Using existing server on port: $port")
                onServerStarted(port)
            } else {
                // Start the server
                Log.i(TAG, "Starting new server instance")
                serverStartTime = System.currentTimeMillis()
                cognotikService?.startCognotikServer()
            }
        }
        
        override fun onServiceDisconnected(className: ComponentName) {
            Log.w(TAG, "Service disconnected: ${className.className}")
            cognotikService = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityStartTime = System.currentTimeMillis()
        Log.i(TAG, "MainActivity onCreate started")
        
        // Check and ensure EmojiCompat is initialized
        ensureEmojiCompatInitialized()

        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)
        Log.d(TAG, "Views initialized")
        
        
        setupWebView()
        Log.d(TAG, "WebView setup completed")
        
        
        // Bind to the service
        val intent = Intent(this, CognotikService::class.java)
        Log.d(TAG, "Starting CognotikService...")
        startService(intent) // Ensure service is started
        Log.d(TAG, "Binding to CognotikService...")
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        val elapsedTime = System.currentTimeMillis() - activityStartTime
        Log.i(TAG, "MainActivity onCreate completed in ${elapsedTime}ms")
    }
    
    private fun ensureEmojiCompatInitialized() {
        try {
            if (!EmojiCompat.isConfigured()) {
                Log.w(TAG, "EmojiCompat not configured, initializing in MainActivity")
                // Use the static initializer from CognotikApplication
                CognotikApplication.initializeEmojiCompatStatic(application)
                Log.d(TAG, "EmojiCompat initialized from MainActivity using static method")
            } else {
                val emojiCompat = EmojiCompat.get()
                val loadState = emojiCompat.loadState
                Log.d(TAG, "EmojiCompat already configured, load state: $loadState")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure EmojiCompat initialization: ${e.message}", e)
        }
    }
    
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        Log.d(TAG, "Setting up WebView configuration...")
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        Log.d(TAG, "WebView settings configured")
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                Log.d(TAG, "WebView URL loading: $url")
                // Handle URL loading within the WebView
                return false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "WebView page loaded successfully: $url")
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "WebView page loading started: $url")
            }
            
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView error: $errorCode - $description for URL: $failingUrl")
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                Log.v(TAG, "WebView loading progress: $newProgress%")
            }
            
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let { msg ->
                    val level = when (msg.messageLevel()) {
                        android.webkit.ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                        android.webkit.ConsoleMessage.MessageLevel.WARNING -> "WARN"
                        android.webkit.ConsoleMessage.MessageLevel.DEBUG -> "DEBUG"
                        else -> "INFO"
                    }
                    Log.d(TAG, "WebView Console [$level]: ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                }
                return true
            }
        }
        Log.d(TAG, "WebView clients configured")
    }
    
    override fun onServerStarted(port: Int) {
        val totalElapsed = System.currentTimeMillis() - activityStartTime
        val serverElapsed = if (serverStartTime > 0) System.currentTimeMillis() - serverStartTime else 0
        Log.i(TAG, "Server started successfully on port $port")
        Log.i(TAG, "Total time from activity start: ${totalElapsed}ms")
        if (serverElapsed > 0) {
            Log.i(TAG, "Server startup time: ${serverElapsed}ms")
        }
        
        runOnUiThread {
            Log.d(TAG, "Updating UI for server start")
            statusText.text = getString(R.string.server_running, port)
            progressBar.visibility = View.GONE
            webView.visibility = View.VISIBLE
            
            // Load the Cognotik web interface
            val url = "http://localhost:$port"
            Log.i(TAG, "Loading Cognotik interface: $url")
            webView.loadUrl(url)
        }
    }
    
    override fun onServerError(error: String) {
        Log.e(TAG, "Server startup failed: $error")
        val totalElapsed = System.currentTimeMillis() - activityStartTime
        Log.e(TAG, "Error occurred after ${totalElapsed}ms from activity start")
        
        runOnUiThread {
            Log.d(TAG, "Updating UI for server error")
            statusText.text = getString(R.string.server_error, error)
            progressBar.visibility = View.GONE
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            Log.d(TAG, "WebView going back")
            webView.goBack()
        } else {
            Log.d(TAG, "Finishing activity (back pressed)")
            super.onBackPressed()
        }
    }
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "MainActivity paused")
    }
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity resumed")
    }
    
    
    override fun onDestroy() {
        Log.i(TAG, "MainActivity being destroyed")
        super.onDestroy()
        if (isBound) {
            Log.d(TAG, "Unbinding from service")
            cognotikService?.setStatusListener(null)
            unbindService(serviceConnection)
            isBound = false
        }
        Log.d(TAG, "MainActivity destroyed")
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}