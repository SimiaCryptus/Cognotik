import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

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
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.1")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.1")
    testImplementation(group = "org.scala-lang.modules", name = "scala-java8-compat_2.13", version = "0.9.1")

}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("surefire.useManifestOnlyJar", "false")
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        jvmArgs(
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )
    }
}
