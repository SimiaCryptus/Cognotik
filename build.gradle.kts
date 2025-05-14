fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

subprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "jacoco")
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
    // Configure JaCoCo for code coverage
    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
        // Ensure source directories are properly set
        sourceDirectories.setFrom(files(sourceSets.main.get().allSource.srcDirs))
        // Exclude common non-testable classes
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/generated/**",
                    "**/*Test*.*",
                    "**/test/**",
                    "**/*Exception*.*",
                    "**/META-INF/**"
                )
            }
        }))
    }
    // Configure JaCoCo agent
    tasks.withType<Test> {
        extensions.configure<JacocoTaskExtension> {
            // Ensure we have consistent output location
            setDestinationFile(layout.buildDirectory.file("jacoco/${name}.exec").get().asFile)
            // Include Kotlin inline functions
            isIncludeNoLocationClasses = true
            // Include classes from the same module
            excludes = listOf("jdk.internal.*")
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
// Add a task to generate an aggregated report for the entire project
tasks.register<JacocoReport>("jacocoRootReport") {
    description = "Generates an aggregate report from all subprojects"
    group = "Verification"
    dependsOn(subprojects.map { it.tasks.withType<Test>() })
    executionData.setFrom(fileTree(project.rootDir) {
        include("**/build/jacoco/*.exec")
        exclude("**/build/jacoco/jacocoRootReport.exec")
    })
    subprojects.forEach { subproject ->
        subproject.plugins.withType<JavaPlugin>().configureEach {
            sourceDirectories.from(subproject.sourceSets.main.get().allSource.srcDirs)
            classDirectories.from(subproject.sourceSets.main.get().output.asFileTree.matching {
                // Exclude common patterns
                exclude(
                    "**/generated/**",
                    "**/*Test*.*",
                    "**/test/**",
                    "**/*Exception*.*",
                    "**/META-INF/**"
                )
            })
        }
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoRootReport"))
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
    jacoco
}