rootProject.name = "Cognotik"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
    id("com.github.ben-manes.versions") version "0.50.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0" apply false
}

include(":jo-penai")
include(":antlr")
include(":core")
include(":groovy")
include(":scala")
include(":kotlin")
include(":webui")
include(":desktop")
include(":intellij")
include(":demo")