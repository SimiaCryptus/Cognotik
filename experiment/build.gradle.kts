import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
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

val projectPrefix = if (rootProject.name == "Cognotik") "" else ":Cognotik"
dependencies {
    compileOnly(project("$projectPrefix:core"))
    compileOnly(project("$projectPrefix:webui"))
    compileOnly(project("$projectPrefix:tasklib"))
    testImplementation(project("$projectPrefix:core"))
    implementation(project("$projectPrefix:text"))
    testImplementation(project("$projectPrefix:webui"))
    testImplementation(project("$projectPrefix:tasklib"))
    testImplementation(project("$projectPrefix:providers"))
    testImplementation(project("$projectPrefix:stdtools"))
    testImplementation(project("$projectPrefix:desktop"))

    implementation(libs.hsqldb)
    implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
    implementation("org.apache.pdfbox:jbig2-imageio:3.0.4")
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.jsoup)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    implementation(libs.tinkerpop)
    implementation(libs.webdrivermanager)
    implementation(libs.selenium.java) {
        exclude(group = "com.intellij.remoterobot", module = "remote-robot")
    }
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

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.libs.versions.kotlin.get()}")
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


