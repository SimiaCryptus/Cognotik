fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

allprojects {
    repositories {
        mavenCentral()
    }
    // Apply common configurations to all projects
    apply {
        plugin("idea")
    }
    // Set consistent encoding for all Java compilation
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
// Configure the root project
//tasks.register("clean") {
//    delete(rootProject.buildDir)
//}


tasks {
  wrapper {
    gradleVersion = properties("gradleVersion")
      distributionType = Wrapper.DistributionType.ALL
  }
}

// Configure parallel execution for all test tasks
allprojects {
    tasks.withType<Test>().configureEach {
        maxParallelForks = (Runtime.getRuntime().availableProcessors() - 1).takeIf { it > 0 } ?: 1

        // Add standard JVM args for all tests
        jvmArgs(
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )
        // Improve test output
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
// Add dependency analysis
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
// Add dependency analysis and optimization
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
// Add a task to check for dependency updates across all modules
tasks.register("checkAllDependencyUpdates") {
    group = "verification"
    description = "Checks for dependency updates in all modules"
    dependsOn(subprojects.map { "${it.path}:dependencyUpdates" })
}