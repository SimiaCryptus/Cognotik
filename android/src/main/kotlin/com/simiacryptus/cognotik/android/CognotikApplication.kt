package com.simiacryptus.cognotik.android

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat

class CognotikApplication : Application() {
    companion object {
        private const val TAG = "CognotikApplication"
        private val isEmojiCompatInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
        
        @JvmStatic
        fun initializeEmojiCompatStatic(application: Context) {
            // Use atomic boolean to prevent multiple initialization attempts
            if (isEmojiCompatInitialized.get() || EmojiCompat.isConfigured()) {
                Log.d(TAG, "EmojiCompat already configured")
                return
            }
            // Double-checked locking pattern for thread safety
            synchronized(this) {
                if (isEmojiCompatInitialized.get() || EmojiCompat.isConfigured()) {
                    Log.d(TAG, "EmojiCompat already configured (double-check)")
                    return
                }
                try {
                    EmojiCompat.init(
                        BundledEmojiCompatConfig(application)
                            .setReplaceAll(true)
                            .setUseEmojiAsDefaultStyle(true)
                            .setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
                            .setEmojiSpanIndicatorEnabled(false)
                    )
                    isEmojiCompatInitialized.set(true)
                    Log.d(TAG, "EmojiCompat initialized successfully")
                    // Manually load the metadata since we're using LOAD_STRATEGY_MANUAL
                    EmojiCompat.get().load()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize EmojiCompat: ${e.message}", e)
                }
            }
        }
        @JvmStatic
        fun safeGetEmojiCompat(): EmojiCompat? {
            try {
                return if (EmojiCompat.isConfigured()) EmojiCompat.get() else null
            } catch (e: Exception) {
                Log.w(TAG, "EmojiCompat not available: ${e.message}")
                return null
            }
        }
    }
    
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CognotikApplication onCreate started")
        
        // Initialize EmojiCompat now that context is available
        initializeEmojiCompat()
        
        Log.i(TAG, "CognotikApplication onCreate completed")
    }
    
    private fun initializeEmojiCompat() {
        initializeEmojiCompatStatic(this)
    }
}