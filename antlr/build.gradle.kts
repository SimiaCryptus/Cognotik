plugins {
    antlr
    `maven-publish`
    signing
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

configurations.all {
    resolutionStrategy {
        force(libs.antlr.runtime)
    }
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

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.cognotik"
            artifactId = "antlr"
            version = project.version.toString()
            pom {
                name.set("Cognotik ANTLR")
                description.set("ANTLR grammars for Cognotik AI framework")
                url.set("https://github.com/SimiaCryptus/Cognotik")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("simiacryptus")
                        name.set("Andrew Charneski")
                        email.set("acharneski@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/SimiaCryptus/Cognotik.git")
                    developerConnection.set("scm:git:ssh://github.com/SimiaCryptus/Cognotik.git")
                    url.set("https://github.com/SimiaCryptus/Cognotik")
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingInMemoryKey")?.toString() ?: System.getenv("SIGNING_KEY")
    val signingPassword = findProperty("signingInMemoryKeyPassword")?.toString() ?: System.getenv("SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
tasks.named<Jar>("sourcesJar") {
    dependsOn("generateGrammarSource")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}


tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}