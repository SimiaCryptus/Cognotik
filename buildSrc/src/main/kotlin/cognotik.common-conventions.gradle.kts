plugins {
    java
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjsr305=strict")
        javaParameters = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    jvmArgs(
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
    // Improve test performance
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    failFast = true
}
// Add common configurations for all projects
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    // Enable incremental compilation for faster builds
//  options.incremental = true
}
// Add common configurations for Javadoc
tasks.withType<Javadoc> {
    options {
        (this as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
            addBooleanOption("html5", true)
            // Add links to JDK and common library Javadocs
            links("https://docs.oracle.com/en/java/javase/17/docs/api/")
        }
    }
}
// Add dependency analysis task
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