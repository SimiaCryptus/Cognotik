plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")

    implementation(libs.mockito)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)
}