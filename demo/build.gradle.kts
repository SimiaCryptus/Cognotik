fun properties(key: String) = providers.gradleProperty(key).get()

plugins {
    id("java") // Java support
    kotlin("jvm") version "2.1.20"
}

group = "com.simiacryptus"
version = properties("pluginVersion")

repositories {
    mavenCentral()
    maven(url = "https://www.jetbrains.com/intellij-repository/releases")
    maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven(url = "https://packages.jetbrains.team/maven/p/iuia/qa-automation-maven")
}

val slf4j_version = "2.0.16"
val remoterobot_version = "0.11.23"
val jackson_version = "2.17.2"
val logback_version = "1.5.13"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("software.amazon.awssdk:bedrock:2.25.7")
    implementation("software.amazon.awssdk:bedrockruntime:2.25.7")
    implementation(group = "org.apache.httpcomponents.client5", name = "httpclient5", version = "5.3.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.jsoup:jsoup:1.19.1")


    implementation(project(":jo-penai"))
    implementation(project(":core"))

    implementation("ch.randelshofer:org.monte.media.screenrecorder:17.1")
    implementation("ch.randelshofer:org.monte.media:17.1")
    implementation("ch.randelshofer:org.monte.media.swing:17.1")

    implementation("org.seleniumhq.selenium:selenium-java:4.27.0") {
        exclude(group = "com.intellij.remoterobot", module = "remote-robot")
    }
    implementation("io.github.bonigarcia:webdrivermanager:5.9.2")

    implementation(group = "com.intellij.remoterobot", name = "remote-fixtures", version = remoterobot_version)
    implementation(group = "com.intellij.remoterobot", name = "remote-robot", version = remoterobot_version)

    implementation(group = "ch.qos.logback", name = "logback-classic", version = logback_version)
    implementation(group = "ch.qos.logback", name = "logback-core", version = logback_version)
    implementation(group = "org.slf4j", name = "slf4j-api", version = slf4j_version)

    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jackson_version)
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-annotations", version = jackson_version)
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = jackson_version)

    implementation(group = "com.squareup.okhttp3", name = "okhttp", version = "4.12.0")
    implementation(platform("org.junit:junit-bom:5.10.1"))
    implementation("org.junit.jupiter:junit-jupiter")
    implementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjsr305=strict")
        //jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}

java {
    sourceSets {
        main {
            java {
                srcDirs("src/main/java")
            }
        }
        test {
            java {
                srcDirs("src/test/java")
            }
        }

    }
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

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        classpath = sourceSets.test.get().runtimeClasspath

    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            javaParameters.set(true)
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

}