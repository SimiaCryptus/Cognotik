rootProject.name = "Cognotik"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.jetbrains.kotlin.plugin.spring") version "2.3.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
    id("com.github.ben-manes.versions") version "0.53.0" apply false
    id("com.android.application") version "8.9.3" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0" apply false
}

include(":antlr")
include(":core")
include(":providers")
include(":groovy")
include(":kotlin")
include(":webui")
include(":tasklib")
include(":stdtools")
include(":desktop")
include(":experiment")
include(":tool")

if (System.getenv("CI") == null || System.getenv("ANDROID_HOME") != null) {
//    include(":android")
}
include(":intellij")



//include(":demo")
// Include modules from sibling ../Cognotik-Plugins directory
// (Windows doesn't reliably support symlinks, so we map projectDir explicitly)
val cognotikPluginsDir = file("../Cognotik-Plugins")
if (cognotikPluginsDir.isDirectory) {
    val pluginModules = listOf(
        "shared",
        "jobs",
        "office",
        "games",
        "home",
        "omni",
        "controller",
        "worker",
        "proxy-providers",
        "proxy"
    )
     // Create the parent container project and point it at the external directory
     // so Gradle doesn't try to use the non-existent <root>/Cognotik-Plugins path.
     include(":Cognotik-Plugins")
     project(":Cognotik-Plugins").projectDir = cognotikPluginsDir
    for (module in pluginModules) {
        val moduleDir = cognotikPluginsDir.resolve(module)
        if (moduleDir.isDirectory) {
            val path = ":Cognotik-Plugins:$module"
            include(path)
            project(path).projectDir = moduleDir
        } else {
            logger.warn("Skipping $module: directory not found at ${moduleDir.absolutePath}")
        }
    }
}