import com.sass_lang.embedded_protocol.OutputStyle
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
    `java-library`
    id("io.freefair.sass-base") version "8.13"
    id("io.freefair.sass-java") version "8.13"
}

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

val graal_version = "24.1.1"

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-scripting-compiler:${libs.versions.kotlin.get()}")
    compileOnly(libs.eclipse.jdt.core) 
    compileOnly(libs.graalvm.js) 
    compileOnly(libs.graalvm.js.language) 

    testImplementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlinx.collections.immutable) 
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("scripting-jsr223"))
    testImplementation(kotlin("scripting-jvm"))
    testImplementation(kotlin("scripting-jvm-host"))
    testImplementation(kotlin("script-runtime"))
    testImplementation(kotlin("scripting-compiler-embeddable"))
    testImplementation(kotlin("compiler-embeddable"))

    implementation(project(":jo-penai")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }

    implementation(project(":core"))
    implementation(project(":kotlin"))
    implementation(project(":groovy"))
    implementation(libs.pdfbox) 

    implementation(libs.selenium.java) { 
        exclude(group = "com.intellij.remoterobot", module = "remote-robot")
    }
    implementation(libs.webdrivermanager) 
    implementation(libs.jsoup) 

    implementation(libs.zxing.core) 
    implementation(libs.zxing.javase) 

    compileOnly(libs.aws.sdk)
    testImplementation(libs.aws.sdk)

    // compileOnly("org.openapitools:openapi-generator:7.3.0") { // Base generator lib - add to TOML if needed
    //     exclude(group = "org.slf4j")
    // }
    compileOnly(libs.openapi.generator.cli) {  for CLI
        exclude(group = "org.slf4j")
    }
    testRuntimeOnly(libs.openapi.generator.cli)  for CLI

    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
    implementation(libs.jetty.annotations)
    implementation(libs.jetty.websocket.server)
    implementation(libs.jetty.websocket.client)
    implementation("org.eclipse.jetty.websocket:websocket-servlet:${libs.versions.jetty.get()}")
    implementation(libs.jetty.webapp)

    implementation(libs.flexmark.core) 
    implementation(libs.flexmark.ext.tables) 

    compileOnly(libs.kotlinx.coroutines)

    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))

    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    implementation(libs.jackson.core) 
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)
    implementation(libs.guava)

    implementation(libs.google.api.client) 
    implementation(libs.google.oauth.client.jetty) 
    implementation(libs.google.api.services.oauth2) 
    implementation(libs.google.http.client.gson) 

    implementation(libs.commons.io)
    implementation(libs.commons.codec) 

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.logback.core)

    testImplementation(kotlin("script-runtime"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api) // Version from BOM
    testRuntimeOnly(libs.junit.jupiter.engine) // Version from BOM
}

sass {
    omitSourceMapUrl.set(false)
    outputStyle.set(OutputStyle.EXPANDED)
    sourceMapContents.set(false)
    sourceMapEmbed.set(false)
    sourceMapEnabled.set(true)
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
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )
    }
}