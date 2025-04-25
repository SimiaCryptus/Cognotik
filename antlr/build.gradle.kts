plugins {
    java
    `java-library`
    antlr
}

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

repositories {
    mavenCentral()
}


dependencies {
    implementation(libs.antlr.runtime)
    antlr("org.antlr:antlr4:${libs.versions.antlr.get()}")
}

tasks {
    generateGrammarSource {
        maxHeapSize = "64m"
        arguments = arguments + listOf("-visitor", "-long-messages")
        outputDirectory = file("build/generated-src/antlr/main")
        // Add caching to improve build performance
        outputs.cacheIf { true }
    }
    // Add source sets to include generated sources
    sourceSets {
        main {
            java {
                srcDir("build/generated-src/antlr/main")
            }
        }
    }
    withType<JavaCompile> {
        options.release.set(17)
        // Enable incremental compilation
        //options.incremental = true
    }
    // Add a clean task for generated sources
    register("cleanGeneratedSources") {
        group = "build"
        description = "Cleans the generated ANTLR sources"
        doLast {
            delete("build/generated-src/antlr")
        }
    }
    // Make the clean task depend on cleanGeneratedSources
    clean {
        dependsOn("cleanGeneratedSources")
    }
}