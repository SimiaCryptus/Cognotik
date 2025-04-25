fun properties(key: String) = providers.gradleProperty(key).get()

plugins {
    id("cognotik.common-conventions")
    `java-library`
    alias(libs.plugins.kotlin)
}

group = "com.simiacryptus"
version = properties("pluginVersion")

repositories {
    mavenCentral()
    maven(url = "https://www.jetbrains.com/intellij-repository/releases")
    maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven(url = "https://packages.jetbrains.team/maven/p/iuia/qa-automation-maven")
}

val remoterobot_version = "0.11.23"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.aws.bedrock)
    implementation(libs.aws.bedrockruntime)
    implementation(libs.httpclient5) {
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

    implementation(libs.logback.classic)
    implementation(libs.logback.core)
    implementation(libs.slf4j.api)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)

    implementation(group = "com.squareup.okhttp3", name = "okhttp", version = "4.12.0")
    implementation(platform("org.junit:junit-bom:5.10.1"))
    implementation("org.junit.jupiter:junit-jupiter")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")

//    intellijPlatform {
//        intellijIdeaCommunity("2024.1")
//    }
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
    }
}

tasks.test {
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
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            javaParameters.set(true)
        }
    }
}

