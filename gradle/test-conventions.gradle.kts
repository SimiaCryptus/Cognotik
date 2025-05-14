// This file can be applied to projects that need test configuration
// Apply with: apply(from = rootProject.file("gradle/test-conventions.gradle.kts"))
plugins {
    jacoco
}


dependencies {
    val testImplementation by configurations
    val testRuntimeOnly by configurations
    
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Configure parallel test execution with a property to disable it if needed
    maxParallelForks = if (project.hasProperty("disableParallelTests")) {
        1
    } else {
        (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    }

    testLogging {
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
        )
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    jvmArgs(
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "-Xmx1g",
        "-XX:+HeapDumpOnOutOfMemoryError"
    )
    // Enable JaCoCo agent for code coverage
    finalizedBy(tasks.jacocoTestReport)
}
// Configure JaCoCo test report
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    // Exclude classes that don't need coverage
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/generated/**",
                    "**/*Test*.*",
                    "**/test/**",
                    "**/*Exception*.*",
                    "**/META-INF/**"
                )
            }
        })
    )
    // Include source sets for accurate reporting
    sourceDirectories.setFrom(files(sourceSets.main.get().allSource.srcDirs))
}
// Add a task to verify minimum code coverage
tasks.register<JacocoCoverageVerification>("verifyCoverage") {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.30".toBigDecimal() // Start with a more achievable target
            }
        }
        // Add more specific rules for critical packages
        rule {
            element = "PACKAGE"
            includes = listOf("com.simiacryptus.cognotik.core.*")
            limit {
                minimum = "0.40".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().classDirectories)
    sourceDirectories.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().sourceDirectories)
    executionData.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().executionData)
}
// Add a task to generate an aggregated report
tasks.register<JacocoReport>("jacocoFullReport") {
    description = "Generates an aggregate report from all subprojects"
    group = "Verification"
    // Only execute this task when called directly
    onlyIf { gradle.taskGraph.hasTask(this) }
    // Gather execution data from all subprojects
    executionData.setFrom(fileTree(project.rootDir) {
        include("**/build/jacoco/*.exec")
    })
    // Add all source sets from subprojects
    subprojects.forEach { subproject ->
        subproject.plugins.withType<JavaPlugin>().configureEach {
            sourceDirectories.from(subproject.sourceSets.main.get().allSource.srcDirs)
            classDirectories.from(subproject.sourceSets.main.get().output)
        }
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}