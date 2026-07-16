import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
    alias(libs.plugins.kover)
}

group = "com.cognotik"
version = providers.gradleProperty("libraryVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")

    implementation(project(":Cognotik:core")) {
        exclude(group = "com.fasterxml.jackson.core")
    }
    implementation(project(":Cognotik:groovy")) {
        exclude(group = "com.fasterxml.jackson.core")
    }
    implementation(project(":Cognotik:webui")) {
        exclude(group = "org.seleniumhq.selenium")
        exclude(group = "io.github.bonigarcia")
        exclude(group = "com.google.api-client")
        exclude(group = "com.google.oauth-client")
    }

    implementation(project(":Cognotik:providers")) {
        exclude(group = "org.seleniumhq.selenium")
        exclude(group = "io.github.bonigarcia")
        exclude(group = "com.google.api-client")
        exclude(group = "com.google.oauth-client")
    }
    implementation(project(":Cognotik:tasklib")) {
        exclude(group = "org.seleniumhq.selenium")
        exclude(group = "io.github.bonigarcia")
        exclude(group = "com.google.api-client")
        exclude(group = "com.google.oauth-client")
    }
    implementation(project(":Cognotik:stdtools")) {
        exclude(group = "org.seleniumhq.selenium")
        exclude(group = "io.github.bonigarcia")
        exclude(group = "com.google.api-client")
        exclude(group = "com.google.oauth-client")
    }



    implementation(libs.aws.bedrockruntime)
    implementation(libs.aws.s3)
    implementation(libs.aws.kms)
    implementation(libs.commons.text)
    implementation(libs.commons.lang3)
    implementation(libs.flexmark.core)
    implementation(libs.diffutils)
    implementation(libs.httpclient5)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)
    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
    implementation(libs.jetty.annotations)
    implementation(libs.jetty.websocket.servlet)
    implementation(libs.jetty.websocket.server)
    implementation(libs.jetty.websocket.client)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.tinkerpop)

    implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
    implementation("org.apache.pdfbox:jbig2-imageio:3.0.4")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlin.test.junit5)
    // Add JUnit 4 explicitly as it seems required by the IntelliJ test framework runtime for some tests/runners
    testRuntimeOnly(libs.junit.junit)

    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
    }

}

kotlin {
    jvmToolchain(21)
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
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

        include("**/*Test.class")
    }
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            javaParameters.set(true)
        }
    }

    runIde {
        maxHeapSize = "8g"
    }

}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("libraryVersion")

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

        val changelog = project.changelog


        changeNotes = providers.gradleProperty("libraryVersion").map { libraryVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(libraryVersion) ?: getUnreleased())
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
        channels = providers.gradleProperty("libraryVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            // Use specific IDE versions that are known to be available
            ide(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
            // Optionally add a few more specific versions for broader compatibility testing
            // ide("IC", "2024.3")
            // ide("IC", "2024.2")
        }
    }
}