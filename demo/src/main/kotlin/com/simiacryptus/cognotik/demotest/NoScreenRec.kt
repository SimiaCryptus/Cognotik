package com.simiacryptus.cognotik.demotest

open class NoScreenRec {
    open val recordingConfig: RecordingConfig = RecordingConfig()
    open fun startScreenRecording() {}
    open fun stopScreenRecording() {}
    open fun sleepForSplash() {}
}