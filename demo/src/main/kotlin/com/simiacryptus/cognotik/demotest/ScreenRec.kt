package com.simiacryptus.cognotik.demotest

import org.monte.media.av.codec.audio.AudioFormatKeys.MIME_AVI
import org.monte.media.av.codec.video.VideoFormatKeys
import org.monte.media.math.Rational
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File

data class RecordingConfig(
    val enableAudio: Boolean = true,
    val outputFolder: File = File("test-recordings"),
    val captureSize: Rectangle = defaultResolution(),
    val frameRate: Rational = Rational(30, 1),
    val videoQuality: Float = 0.8f,
    val videoDepth: Int = 24,
    val keyFrameInterval: Int = 5 * 60,
    val sampleRate: Double = -1.0,
    val sampleSize: Int = 16,
    val audioChannels: Int = 1,
    val preferredSoundInput: String = "Primary Sound Driver",
    val splashScreenDuration: Long = 5000,
    val splashScreenDelay: Long = 10000,
    val fileFormat: String = MIME_AVI,
    val videoEncoding: String = VideoFormatKeys.ENCODING_AVI_TECHSMITH_SCREEN_CAPTURE,
    val mousePointerColor: String = "black",
    val outputFileNamePattern: String = "%s.%s.avi",
    val dateFormat: String = "yyyyMMddHHmmss",
    val waitForFileTimeout: Long = 10000,
    val waitForFileInterval: Long = 100,
    val splashNarration: String = "",
)

fun defaultResolution() = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    .bounds.let { bounds -> Rectangle(/* width = */ bounds.width, /* height = */ bounds.height) }

