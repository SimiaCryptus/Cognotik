import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Ensure Android SDK is found
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// Try to find SDK location from various sources
val sdkDir = localProperties.getProperty("sdk.dir")
    ?: findProperty("android.sdk.home") as String?
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: System.getProperty("user.home") + "/Android/Sdk"  // Default Linux location
    ?: System.getProperty("user.home") + "/Library/Android/sdk"  // Default macOS location

if (sdkDir != null) {
    println("Using Android SDK at: $sdkDir")
    // Set the SDK directory for the build
    System.setProperty("android.home", sdkDir)
} else {
    throw GradleException(
        "Android SDK location not found. Define location with:\n" +
        "1. sdk.dir in android/local.properties file, or\n" +
        "2. ANDROID_HOME environment variable, or\n" +
        "3. ANDROID_SDK_ROOT environment variable"
    )
}

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

android {
    namespace = "com.simiacryptus.cognotik.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simiacryptus.cognotik.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = version.toString()
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/native-image/**"
            excludes += "META-INF/versions/**"
            excludes += "META-INF/services/**"
        }
    }
}

dependencies {
    implementation(project(":core"))
    // Temporarily exclude problematic project dependencies
    // implementation(project(":webui"))
    // implementation(project(":jo-penai"))
    // implementation(project(":kotlin"))
    // implementation(project(":groovy"))
    
    // Android dependencies
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.12.1")
    
    // Essential dependencies only - avoid server-side libraries
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.commons.io)
    // Use Android-compatible logging - slf4j-android includes slf4j-api
    implementation("org.slf4j:slf4j-android:1.7.36") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    
    implementation(kotlin("stdlib"))
    // Remove heavy Kotlin scripting dependencies for Android
    // implementation(kotlin("scripting-jsr223"))
    // implementation(kotlin("scripting-jvm"))
    // implementation(kotlin("scripting-jvm-host"))
    // implementation(kotlin("script-runtime"))
    // implementation(kotlin("scripting-compiler-embeddable"))
    // implementation(kotlin("compiler-embeddable"))
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}