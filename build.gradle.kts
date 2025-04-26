fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

allprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    configurations.all {
        resolutionStrategy {
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
                "org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}",
                "org.slf4j:slf4j-api:${libs.versions.slf4j.get()}"
            )
            preferProjectModules()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
        distributionType = Wrapper.DistributionType.ALL
    }
}

allprojects {
    tasks.withType<Test>().configureEach {
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

repositories {
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    kotlin("jvm") version "2.1.20"
    id("com.github.ben-manes.versions") version "0.50.0"
}

tasks.register("analyzeModuleDependencies") {
    group = "verification"
    description = "Analyzes dependencies between modules"
    doLast {
        val projectDependencies = mutableMapOf<Project, Set<Project>>()
        subprojects.forEach { project ->
            val dependencies = project.configurations
                .filter { it.name == "implementation" || it.name == "api" }
                .flatMap { it.dependencies }
                .filterIsInstance<org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal>()
                .map {
                    @Suppress("DEPRECATION")
                    it.dependencyProject
                }
                .toSet()
            projectDependencies[project] = dependencies
        }
        println("Module Dependencies:")
        projectDependencies.forEach { (project, dependencies) ->
            println("${project.name} depends on: ${dependencies.joinToString { it.name }}")
        }
    }
}

tasks.register("optimizeDependencies") {
    group = "verification"
    description = "Analyzes and optimizes dependencies"
    doLast {
        val allDependencies = mutableMapOf<String, MutableSet<String>>()
        subprojects.forEach { project ->
            project.configurations
                .filter { it.isCanBeResolved }
                .forEach { config ->
                    try {
                        config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                            val id = artifact.moduleVersion.id
                            val key = "${id.group}:${id.name}"
                            val version = id.version
                            allDependencies.getOrPut(key) { mutableSetOf() }.add(version)
                        }
                    } catch (e: Exception) {
                        // Ignore configurations that can't be resolved
                    }
                }
        }
        println("Dependency Version Conflicts:")
        allDependencies.filter { it.value.size > 1 }.forEach { (dep, versions) ->
            println("$dep has multiple versions: ${versions.joinToString()}")
        }
    }
}

tasks.register("checkAllDependencyUpdates") {
    group = "verification"
    description = "Checks for dependency updates in all modules"
    dependsOn(subprojects.map { "${it.path}:dependencyUpdates" })
}