import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    `java-library`
}

group = "com.simiacryptus"
version = providers.gradleProperty("libraryVersion").get()

repositories {
    mavenCentral()
    maven(url = "https://www.jetbrains.com/intellij-repository/releases")
    maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven(url = "https://packages.jetbrains.team/maven/p/iuia/qa-automation-maven")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.aws.bedrock)
    implementation(libs.aws.bedrockruntime)
    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.jsoup)

    implementation(project(":jo-penai"))
    implementation(project(":core"))

    implementation(libs.monte.media.screenrecorder)
    implementation(libs.monte.media)
    implementation(libs.monte.media.swing)

    implementation(libs.selenium.java) {
        exclude(group = "com.intellij.remoterobot", module = "remote-robot")
    }
    implementation(libs.webdrivermanager)

    implementation(libs.remoterobot.fixtures)
    implementation(libs.remoterobot.robot)

    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.slf4j.api)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)

    implementation(libs.okhttp)
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.jupiter.api)
    runtimeOnly(libs.junit.jupiter.engine)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
    }
}

tasks.test {
    enabled = false
    useJUnitPlatform()
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks {
    compileKotlin {
        destinationDirectory.set(compileJava.get().destinationDirectory)
        doLast {
            val servicesDir = File(destinationDirectory.get().asFile, "META-INF/services")
            servicesDir.mkdirs()
            File(servicesDir, "org.monte.media.av.MovieWriterSpi").apply {
                writeText(
                    listOf(
                        "org.monte.media.avi.AVIWriterSpi",
                        "org.monte.media.quicktime.QuickTimeWriterSpi"
                    ).joinToString("") { "$it\n" })
            }
            File(servicesDir, "org.monte.media.av.CodecSpi").apply {
                writeText(
                    listOf(
                        "org.monte.media.avi.codec.audio.AVIPCMAudioCodecSpi",
                        "org.monte.media.av.codec.video.TechSmithCodecSpi"
                    ).joinToString("") { "$it\n" })
            }
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(sourceSets.main.get().output)
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            javaParameters.set(true)
        }
    }
}