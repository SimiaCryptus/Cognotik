import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = providers.gradleProperty(key).getOrElse("")

plugins {
  id("java")
//  kotlin("jvm") version "2.1.20"
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = "com.simiacryptus"
version = properties("pluginVersion")

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

val jetty_version = "11.0.24"
val slf4j_version = "2.0.16"
val remoterobot_version = "0.11.23"
val jackson_version = "2.17.2"
val aws_sdk_version = "2.25.60"
val httpclient5_version = "5.3.1"
val logback_version = "1.5.16"
val commons_text_version = "1.11.0"
val commons_lang3_version = "3.15.0"

dependencies {
  
  
  // Pin all AWS SDK dependencies to the same version to avoid forced upgrades and reduce duplicates
  implementation("software.amazon.awssdk:bedrockruntime:$aws_sdk_version")
  implementation("software.amazon.awssdk:s3:$aws_sdk_version")
  implementation("software.amazon.awssdk:kms:$aws_sdk_version")
  
  implementation("org.apache.commons:commons-text:$commons_text_version")
  implementation("org.apache.commons:commons-lang3:$commons_lang3_version")
  implementation("com.vladsch.flexmark:flexmark:0.64.8")
  implementation("com.googlecode.java-diff-utils:diffutils:1.3.0")
  implementation("org.apache.httpcomponents.client5:httpclient5:$httpclient5_version")

    implementation(project(":jo-penai")) {
    exclude(group = "org.jetbrains.kotlin")
  }
    implementation(project(":core")) {
    exclude(group = "org.jetbrains.kotlin")
  }
    implementation(project(":webui")) {
    exclude(group = "org.jetbrains.kotlin")
  }
  
  implementation("com.fasterxml.jackson.core:jackson-databind:$jackson_version")
  implementation("com.fasterxml.jackson.core:jackson-annotations:$jackson_version")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson_version")
  
  implementation("org.eclipse.jetty:jetty-server:$jetty_version")
  implementation("org.eclipse.jetty:jetty-servlet:$jetty_version")
  implementation("org.eclipse.jetty:jetty-annotations:$jetty_version")
  implementation("org.eclipse.jetty.websocket:websocket-jetty-server:$jetty_version")
  implementation("org.eclipse.jetty.websocket:websocket-jetty-client:$jetty_version")
  implementation("org.eclipse.jetty.websocket:websocket-servlet:$jetty_version")
  
  implementation("org.slf4j:slf4j-api:$slf4j_version")
  implementation("ch.qos.logback:logback-classic:$logback_version")

  testImplementation(platform("org.junit:junit-bom:5.11.2"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.junit.vintage:junit-vintage-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  testImplementation(group = "com.intellij.remoterobot", name = "remote-robot", version = remoterobot_version)
  testImplementation(group = "com.intellij.remoterobot", name = "remote-fixtures", version = remoterobot_version)
  testImplementation(
    group = "com.intellij.remoterobot",
    name = "robot-server-plugin",
    version = remoterobot_version,
    ext = "zip"
  )


// IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        //testFramework(TestFrameworkType.Platform)
    }

}

kotlin {
  jvmToolchain(17)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }

  test {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
    }
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    systemProperty("idea.force.use.core.classloader", "true")
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
    // Include JUnit 3/4 tests
    include("**/*Test.class")
  }
  withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      javaParameters.set(true)
    }
  }


    /*
    runIde {
      maxHeapSize = "8g"
    }
  */


}

// Configure IntelliJ Plugin
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Set the JVM compatibility versions
tasks {
  patchPluginXml {
    sinceBuild.set(properties("pluginSinceBuild"))
    untilBuild.set(properties("pluginUntilBuild"))

      // Extract from README.md file
    pluginDescription.set(providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
      val start = "<!-- Plugin description -->"
      val end = "<!-- Plugin description end -->"
      
      with(it.lines()) {
        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
      }
    })
    
    val changelog = project.changelog
    changeNotes.set(providers.gradleProperty("pluginVersion").map { pluginVersion ->
      with(changelog) {
        renderItem(
          (getOrNull(pluginVersion) ?: getUnreleased())
            .withHeader(false)
            .withEmptySections(false),
          Changelog.OutputType.HTML,
        )
      }
    })
  }
  
  runIde {
    maxHeapSize = "8g"
  }
  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }
  publishPlugin {
    dependsOn("patchChangelog")
    token.set(System.getenv("PUBLISH_TOKEN"))
    channels.set(properties("pluginVersion").split('-').drop(1).take(1).map { it.substringBefore('.').ifEmpty { "default" } })
  }

}