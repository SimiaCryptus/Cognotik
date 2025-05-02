fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
    `java-library`
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
    implementation(libs.aws.auth)

    implementation(libs.swagger.annotations)
    implementation(libs.jsr305)
    implementation(libs.httpclient5)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotations.api)

    implementation(libs.jackson.core) 
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jaxrs.json)
    implementation(libs.jackson.datatype.jsr310) 

    implementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)
    testImplementation(libs.logback.core)

    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.jackson.kotlin)
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.jtransforms) 
    implementation(libs.commons.io)

    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("script-runtime"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api) // Version from BOM
    testImplementation(libs.junit.jupiter.params) // Version from BOM
    testRuntimeOnly(libs.junit.jupiter.engine) // Version from BOM
    testImplementation(libs.kotlin.test.junit5) 
}