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
    implementation(project(":core"))

    implementation(libs.groovy.all)

    compileOnly(libs.kotlinx.coroutines)
    compileOnly(kotlin("stdlib"))

    implementation(libs.slf4j.api)
    implementation(libs.commons.io)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api) // Version from BOM
    testRuntimeOnly(libs.junit.jupiter.engine) // Version from BOM

}
