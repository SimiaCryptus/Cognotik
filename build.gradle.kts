fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

subprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    repositories {
        mavenCentral()
    }
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
            freeCompilerArgs = listOf("-Xjsr305=strict")
            javaParameters = true
        }
    }
    tasks.register("analyzeDependencies") {
        description = "Analyzes project dependencies for potential issues"
        doLast {
            val implementation = configurations.findByName("implementation")
            val api = configurations.findByName("api")
            if (implementation != null && api != null) {
                val implementationDeps = implementation.dependencies.map { "${it.group}:${it.name}" }.toSet()
                val apiDeps = api.dependencies.map { "${it.group}:${it.name}" }.toSet()
                val duplicates = implementationDeps.intersect(apiDeps)
                if (duplicates.isNotEmpty()) {
                    logger.warn("Found dependencies declared in both api and implementation: $duplicates")
                }
            }
        }
    }
}

allprojects {
    apply(plugin = "java")
    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    configurations.all {
        // Apply resolution strategy to all configurations in all projects
        resolutionStrategy {
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:${rootProject.libs.versions.kotlin.get()}",
                "org.jetbrains.kotlin:kotlin-reflect:${rootProject.libs.versions.kotlin.get()}",
                "org.slf4j:slf4j-api:${rootProject.libs.versions.slf4j.get()}"
            )
            preferProjectModules()
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        maxHeapSize = "2g"
        jvmArgs(
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )
        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }

}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
        distributionType = Wrapper.DistributionType.ALL
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    kotlin("jvm") // Version is applied globally via settings.gradle.kts
    id("com.github.ben-manes.versions") // Version is applied globally via settings.gradle.kts
}
// Configure the dependency updates plugin
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    // Configure resolution strategy to only recommend stable releases
    // Other options include: ReleaseCandidate, Milestone, Integration
    resolutionStrategy {
        componentSelection {
            all {
                if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
                    reject("Release candidate")
                }
            }
        }
    }
    // Optional: Output results to a file (e.g., JSON, XML, plain text)
    // outputFormatter = "json"
    // outputDir = "build/dependencyUpdates"
    // reportfileName = "report"
}
// Helper function to check for non-stable versions (adjust keywords as needed)
fun isNonStable(version: String): Boolean {
    val unstableKeywords = listOf("rc", "m", "beta", "alpha", "snapshot", "dev", "eap")
    return unstableKeywords.any { version.contains(it, ignoreCase = true) }
}