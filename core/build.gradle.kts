// Keeping findProperty as it might be needed for immediate resolution by other plugins/tasks
// If not, switch to providers.gradleProperty(key).get()
// Use providers for consistency with other modules
group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

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

    implementation(libs.hsqldb)
    implementation(project(":jo-penai")) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(":antlr")) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(libs.antlr.runtime)
    implementation(libs.commons.text)

    implementation(libs.slf4j.api)
    implementation(libs.commons.io)
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.httpclient5)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)

    compileOnly(libs.asm)
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.kotlinx.coroutines)

    testImplementation(kotlin("script-runtime"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)

    compileOnly(platform(libs.aws.bom))
    compileOnly(libs.aws.sdk)
    compileOnly(libs.logback.classic)
    compileOnly(libs.logback.core)

    testImplementation(platform(libs.aws.bom))
    testImplementation(libs.aws.sdk)
    testImplementation(libs.logback.classic)
    testImplementation(libs.logback.core)
    testImplementation(libs.mockito)

}