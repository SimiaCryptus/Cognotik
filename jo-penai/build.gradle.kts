val libraryGroup: String by project
val libraryVersion: String by project
group = libraryGroup
version = libraryVersion

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

    implementation(platform(libs.aws.bom)) // Use BOM alias
    implementation(libs.aws.bedrockruntime)
    implementation(libs.aws.auth)

    implementation(libs.swagger.annotations)
    implementation(libs.jsr305)
    implementation(libs.httpclient5)
    implementation(libs.jackson.databind.nullable)
    implementation(libs.jakarta.annotations.api)

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
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("script-runtime"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlin.test.junit5)
}