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

class MainActivity : AppCompatActivity(), CognotikService.ServerStatusListener {
    
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var webView: WebView
    
    private var cognotikService: CognotikService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as CognotikService.CognotikBinder
            cognotikService = binder.getService()
            cognotikService?.setStatusListener(this@MainActivity)
            isBound = true
            
            // Check if server is already running
            if (cognotikService?.isServerRunning() == true) {
                val port = cognotikService?.getServerPort() ?: 0
                onServerStarted(port)
            } else {
                // Start the server
                cognotikService?.startCognotikServer()
            }
        }
        
        override fun onServiceDisconnected(className: ComponentName) {
            cognotikService = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)
        
        setupWebView()
        
        // Bind to the service
        val intent = Intent(this, CognotikService::class.java)
        startService(intent) // Ensure service is started
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
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
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Handle URL loading within the WebView
                return false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // You could update a progress bar here if needed
            }
        }
    }
    
    override fun onServerStarted(port: Int) {
        runOnUiThread {
            statusText.text = getString(R.string.server_running, port)
            progressBar.visibility = View.GONE
            webView.visibility = View.VISIBLE
            
            // Load the Cognotik web interface
            val url = "http://localhost:$port"
            Log.d(TAG, "Loading URL: $url")
            webView.loadUrl(url)
        }
    }
    
    override fun onServerError(error: String) {
        runOnUiThread {
            statusText.text = getString(R.string.server_error, error)
            progressBar.visibility = View.GONE
            Log.e(TAG, "Server error: $error")
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            cognotikService?.setStatusListener(null)
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}