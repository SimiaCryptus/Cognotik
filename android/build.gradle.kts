import java.util.*

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
    ?: System.getenv("ANDROID_SDK_HOME")
    ?: (System.getProperty("user.home") + "/Android/Sdk")  // Default macOS location

if (sdkDir != null && File(sdkDir).exists()) {
    println("Using Android SDK at: $sdkDir")
    System.setProperty("android.home", sdkDir)
} else {
    println("Android SDK not found at: $sdkDir")
    if (System.getenv("CI") != null) {
        println("Running in CI environment - Android SDK not required for non-Android tasks")
    }
}

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

android {
    namespace = "com.simiacryptus.cognotik.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simiacryptus.cognotik.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = version.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
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
        jvmTarget = "21"
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
            excludes += "META-INF/FastDoubleParser-LICENSE"
            excludes += "META-INF/FastDoubleParser-NOTICE"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/native-image/**"
            excludes += "META-INF/versions/**"
            excludes += "META-INF/services/**"
            // Exclude duplicate Groovy extension module files
            excludes += "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule"
            // Exclude duplicate Kotlin builtin files
            excludes += "kotlin/kotlin.kotlin_builtins"
            excludes += "kotlin/**/*.kotlin_builtins"
            excludes += "kotlin/annotation/annotation.kotlin_builtins"
            excludes += "kotlin/collections/collections.kotlin_builtins"
            excludes += "kotlin/coroutines/coroutines.kotlin_builtins"
            excludes += "kotlin/internal/internal.kotlin_builtins"
            excludes += "kotlin/ranges/ranges.kotlin_builtins"
            excludes += "kotlin/reflect/reflect.kotlin_builtins"
            // Exclude duplicate Groovy release info files
            excludes += "META-INF/groovy-release-info.properties"
            // Exclude duplicate license files
            excludes += "license/**"
        }
    }
    // Disable Jetifier to avoid Java 21 bytecode compatibility issues
    androidComponents {
        beforeVariants { variantBuilder ->
            variantBuilder.enableAndroidTest = false
        }
    }
}

dependencies {
    implementation(project(":core")) {
        exclude(group = "org.apache.pdfbox")
        exclude(group = "com.vladsch.flexmark", module = "flexmark-pdf-converter")
        exclude(group = "de.rototor.pdfbox", module = "graphics2d")
    }
    implementation(project(":kotlin")) {
        exclude(group = "org.apache.pdfbox")
        exclude(group = "com.vladsch.flexmark", module = "flexmark-pdf-converter")
        exclude(group = "de.rototor.pdfbox", module = "graphics2d")
    }
    implementation(project(":groovy")) {
        exclude(group = "org.apache.pdfbox")
        exclude(group = "com.vladsch.flexmark", module = "flexmark-pdf-converter")
        exclude(group = "de.rototor.pdfbox", module = "graphics2d")
    }
    implementation(project(":webui")) {
        exclude(group = "org.apache.pdfbox")
        exclude(group = "org.eclipse.jetty")
        exclude(group = "com.vladsch.flexmark", module = "flexmark-pdf-converter")
        exclude(group = "de.rototor.pdfbox", module = "graphics2d")
    }
    implementation(project(":desktop")) {
        exclude(group = "org.apache.pdfbox")
        exclude(group = "com.vladsch.flexmark", module = "flexmark-pdf-converter")
        exclude(group = "de.rototor.pdfbox", module = "graphics2d")
    }

    // Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.bundled)
    implementation(libs.androidx.swiperefreshlayout)

    // Essential dependencies only - avoid server-side libraries
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.commons.io)
    implementation(libs.slf4jandroid)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.libs.versions.kotlin.get()}")

    // Jetty server dependencies - use only core server, not webapp
    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
    implementation(libs.jetty.websocket.server)
    implementation(libs.jetty.websocket.servlet)

    // Exclude to avoid conflicts
    configurations.all {
        exclude(group = "org.slf4j", module = "slf4j-simple")
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
        exclude(group = "commons-logging", module = "commons-logging")
    }

    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)

}