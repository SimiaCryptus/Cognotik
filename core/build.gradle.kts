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
    implementation(libs.jsoup)
    implementation(libs.pdfbox)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.poiscratchpad)
    implementation(libs.commons.csv)
    implementation(libs.odfdom.java)
    implementation("jakarta.mail:jakarta.mail-api:2.1.2")
    implementation("org.eclipse.angus:angus-mail:2.0.2")

    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jaxrs.json)
    implementation(libs.jackson.datatype.jsr310)


    compileOnly(libs.asm)
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.kotlinx.coroutines)

    testImplementation(kotlin("script-runtime"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotlin.test.junit5)

    // Optional Android dependency
    compileOnly(libs.android)

    compileOnly(platform(libs.aws.bom))
    compileOnly(libs.aws.sdk)
    compileOnly(libs.logback.classic) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    compileOnly(libs.logback.core) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation(platform(libs.aws.bom))
    testImplementation(libs.aws.sdk)
    testImplementation(libs.logback.classic) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(libs.logback.core) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(libs.mockito)

}