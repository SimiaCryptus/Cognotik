fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
    id("cognotik.common-conventions")
    `java-library`
    `maven-publish`
    id("signing")
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

    implementation(platform("software.amazon.awssdk:bom:${libs.versions.aws.get()}"))
    implementation(libs.aws.bedrockruntime)
    implementation("software.amazon.awssdk:auth:${libs.versions.aws.get()}")

    implementation("io.swagger:swagger-annotations:1.6.6")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation(libs.httpclient5)
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")
    implementation("jakarta.annotation:jakarta.annotation-api:1.3.5")

    implementation("com.fasterxml.jackson.core:jackson-core:${libs.versions.jackson.get()}")
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation("com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:${libs.versions.jackson.get()}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${libs.versions.jackson.get()}")

    implementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)
    testImplementation(libs.logback.core)

    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.jackson.kotlin)
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(group = "org.openimaj", name = "JTransforms", version = "1.3.10")
    implementation(libs.commons.io)

    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("script-runtime"))

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.1")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = "5.10.1")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "jo-penai"
            from(components["java"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("Jo-Penai")
                description.set("Java OpenAI API Client")
                url.set("https://github.com/SimiaCryptus/Cognotik")
                // Add license, developers, and scm sections similar to core module
            }
        }
    }
    // Add repositories section similar to core module
}
