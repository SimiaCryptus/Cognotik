import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    `java-library`
    `maven-publish`
    id("signing")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    application
}
application {
    // Replace 'com.simiacryptus.MainKt' with the fully qualified name of your main class
    mainClass.set("com.simiacryptus.MainKt")
}

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

val jackson_version = "2.15.3"
val jupiter_version = "5.10.1"
val logback_version = "1.4.11"
dependencies {
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jackson_version)
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-annotations", version = jackson_version)
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = jackson_version)

    implementation(kotlin("stdlib"))
    implementation(group = "commons-io", name = "commons-io", version = "2.15.0")
    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.9")

    testImplementation(group = "ch.qos.logback", name = "logback-classic", version = logback_version)
    testImplementation(group = "ch.qos.logback", name = "logback-core", version = logback_version)
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = jupiter_version)
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = jupiter_version)
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = jupiter_version)
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
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"
        )
    }
    wrapper {
        gradleVersion = properties("gradleVersion")
    }
}