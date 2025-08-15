plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
}

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

android {
    namespace = "com.simiacryptus.cognotik.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.simiacryptus.cognotik.android"
        minSdk = 26
        targetSdk = 34
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
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":webui"))
    implementation(project(":jo-penai"))
    implementation(project(":kotlin"))
    implementation(project(":groovy"))
    
    // Android dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.9.0")
    
    // Core dependencies - exclude desktop-specific ones
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)
    implementation(libs.guava)
    implementation(libs.jetty.server)
    implementation(libs.jetty.webapp)
    implementation(libs.jetty.websocket.server)
    implementation(libs.httpclient5.fluent)
    implementation(libs.gson)
    implementation(libs.h2)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.commons.io)
    implementation(libs.flexmark.all)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    
    implementation(kotlin("stdlib"))
    implementation(kotlin("scripting-jsr223"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("scripting-compiler-embeddable"))
    implementation(kotlin("compiler-embeddable"))
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}