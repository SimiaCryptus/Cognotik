plugins {
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
    antlr("org.antlr:antlr4:${rootProject.libs.versions.antlr.get()}")
}

tasks {
    generateGrammarSource {
        maxHeapSize = "64m"
        arguments = arguments + listOf("-visitor", "-long-messages")
        outputDirectory = file("build/generated-src/antlr/main")

        outputs.cacheIf { true }
    }

    sourceSets {
        main {
            java {
                srcDir("build/generated-src/antlr/main")
            }
        }
    }

    register("cleanGeneratedSources") {
        group = "build"
        description = "Cleans the generated ANTLR sources"
        doLast {
            delete("build/generated-src/antlr")
        }
    }

    clean {
        dependsOn("cleanGeneratedSources")
    }
}