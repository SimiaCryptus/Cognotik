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

    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("script-runtime"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter.api)
    compileOnly(libs.junit.jupiter.engine)

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
