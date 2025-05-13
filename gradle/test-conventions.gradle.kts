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
                    "**/*Exception*.*"
                )
            }
        })
    )
}
// Add a task to verify minimum code coverage
tasks.register<JacocoCoverageVerification>("verifyCoverage") {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal() // Start with 50% and increase over time
            }
        }
    }
    classDirectories.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().classDirectories)
    sourceDirectories.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().sourceDirectories)
    executionData.setFrom(tasks.named<JacocoReport>("jacocoTestReport").get().executionData)
}