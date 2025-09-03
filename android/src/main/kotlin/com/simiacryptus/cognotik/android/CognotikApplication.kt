package com.simiacryptus.cognotik.android

import android.app.Application
import android.util.Log
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat

class CognotikApplication : Application() {
    companion object {
        private const val TAG = "CognotikApplication"
        @JvmStatic
        fun initializeEmojiCompatStatic(application: Application) {
            if (EmojiCompat.isConfigured()) {
                Log.d(TAG, "EmojiCompat already configured")
                return
            }
            try {
                val config = BundledEmojiCompatConfig(application)
                    .setReplaceAll(true) // Replace all emojis for consistency
                    .setUseEmojiAsDefaultStyle(true) // Use emoji as default
                    .setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_DEFAULT) // Use default strategy for immediate loading
                EmojiCompat.init(config)
                Log.d(TAG, "EmojiCompat initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EmojiCompat: ${e.message}", e)
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