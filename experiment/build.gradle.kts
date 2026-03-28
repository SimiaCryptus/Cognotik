import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    alias(libs.plugins.shadow)
    application
}


// Use providers for consistency with other modules
group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}


java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":core"))
    implementation(project(":webui"))
    implementation(project(":tasklib"))
    testImplementation(project(":providers"))
    testImplementation(project(":stdtools"))

    implementation(libs.batik.transcoder)
    implementation(libs.batik.codec)
    implementation(libs.commons.text)
    implementation(libs.aws.s3)
    implementation(libs.aws.kms)
    implementation(libs.jsoup)
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

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api) // Version from BOM
    testImplementation(libs.junit.jupiter.params) // Version from BOM
    testRuntimeOnly(libs.junit.jupiter.engine) // Version from BOM
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("surefire.useManifestOnlyJar", "false")
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        jvmArgs(
            "--add-opens",
            "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.util=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.lang=ALL-UNNAMED",
            "--add-opens",
            "java.base/sun.nio.ch=ALL-UNNAMED"
        )

        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    }
}

