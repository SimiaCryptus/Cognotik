# Android Build Setup Guide

To enable the Android module build in an environment with Android SDK:

## 1. Prerequisites

- Android SDK 26+ installed
- Android Build Tools
- Google Play Services (for repositories)

## 2. Enable Android Module

### Step 1: Uncomment Android module in settings.gradle.kts
```kotlin
include(":android")  // Uncomment this line
```

### Step 2: Add Android plugins to settings.gradle.kts
```kotlin
plugins {
    // ... existing plugins ...
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
```

### Step 3: Ensure Google repository is available
The pluginManagement block should include:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()  // Required for Android plugins
        mavenCentral()
    }
}
```

## 3. Build Commands

### Debug Build
```bash
./gradlew :android:assembleDebug
```

### Release Build  
```bash
./gradlew :android:assembleRelease
```

### Install on Device
```bash
./gradlew :android:installDebug
```

## 4. Android SDK Requirements

- **compileSdk**: 34
- **minSdk**: 26 (Android 8.0+)
- **targetSdk**: 34

## 5. Generated Artifacts

The build produces:
- `android/build/outputs/apk/debug/android-debug.apk`
- `android/build/outputs/apk/release/android-release.apk`

## 6. Testing

1. Install APK on Android device or emulator
2. Launch the Cognotik app
3. Wait for server startup (loading screen)
4. Access full Cognotik functionality through WebView interface

## 7. Troubleshooting

### Build Issues
- Ensure Android SDK is properly installed
- Verify ANDROID_HOME environment variable
- Check Google repository access
- Update Android Gradle Plugin if needed

### Runtime Issues
- Check device API level (minimum Android 8.0)
- Verify internet permission for AI API calls
- Monitor memory usage on lower-end devices
- Check WebView compatibility