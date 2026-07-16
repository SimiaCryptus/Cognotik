group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

plugins {
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

dependencies {
    implementation(project(":Cognotik:core"))

    implementation(libs.groovy.all)

    compileOnly(libs.kotlinx.coroutines)
    compileOnly(kotlin("stdlib"))

    implementation(libs.slf4j.api)
    implementation(libs.commons.io)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlin.test.junit5)

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
            artifactId = "groovy"
            version = project.version.toString()

            pom {
                name.set("Cognotik Groovy Runtime")
                description.set("Core library for Cognotik AI framework")
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
                        name.set("SimiaCryptus")
                        email.set("simiacryptus@gmail.com")
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

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}