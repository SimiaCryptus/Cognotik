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

    implementation(project(":core"))

    implementation(platform(libs.aws.bom)) // Use BOM alias
    implementation(libs.aws.bedrockruntime)
    implementation(libs.aws.bedrock)
    implementation(libs.aws.auth)
    implementation(libs.aws.sso)

    implementation(libs.swagger.annotations)
    implementation(libs.jsr305)
    implementation(libs.httpclient5)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotations.api)

    implementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)
    testImplementation(libs.logback.core)

    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.jackson.kotlin)
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.commons.io)

    implementation("com.google.genai:google-genai:1.24.0")

    compileOnly(libs.android)

    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
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
            artifactId = "jo-penai"
            version = project.version.toString()

            pom {
                name.set("Cognotik Core")
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
        println("Signing with in-memory PGP keys")
        println("Signing Key (first 300 chars): ${signingKey.take(300)}...")
        println("Signing Password Length: ${signingPassword.length} characters")
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }

}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}