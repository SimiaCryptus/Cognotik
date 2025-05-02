plugins {
    antlr
}

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.antlr.runtime)
    antlr(libs.antlr.tool)
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