group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

plugins {
    `java-library`
    `scala`
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
    implementation(libs.scala.library)
    implementation(libs.scala.compiler)
    implementation(libs.scala.reflect)
    implementation(libs.slf4j.api)

    testImplementation(group = "org.slf4j", name = "slf4j-simple", version = libs.versions.slf4j.get())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api) // Version from BOM
    testRuntimeOnly(libs.junit.jupiter.engine) // Version from BOM
    testImplementation(libs.scala.java8.compat)

}
