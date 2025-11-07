group = providers.gradleProperty("cognotikGroup").get()
version = providers.gradleProperty("cognotikVersion").get()

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