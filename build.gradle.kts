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
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        withSourcesJar()
        withJavadocJar()
    }
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events = setOf(
                org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
            )
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        jvmArgs(
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
        failFast = true
    }
    tasks.withType<Javadoc> {
        options {
            (this as StandardJavadocDocletOptions).apply {
                addStringOption("Xdoclint:none", "-quiet")
                addBooleanOption("html5", true)
                links("https://docs.oracle.com/en/java/javase/17/docs/api/")
            }
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
    /*
    */
}


allprojects {
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    configurations.all {
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
    /*
    */
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
    kotlin("jvm") version "2.0.21"
    id("com.github.ben-manes.versions") version "0.50.0"
}