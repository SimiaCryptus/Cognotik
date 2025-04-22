import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
    java
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version "2.0.20"
    id("signing")
    id("jacoco")
}

repositories {
    mavenCentral()
}

tasks {

    compileKotlin {
        compilerOptions {
            javaParameters.set(true)
        }
    }
    compileTestKotlin {
        compilerOptions {
            javaParameters.set(true)
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    test {
        useJUnitPlatform()
        systemProperty("surefire.useManifestOnlyJar", "false")
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(11)
}

val logback_version = "1.5.13"
val jupiter_version = "5.10.1"
val jackson_version = "2.17.2"

val swagger_annotations_version = "1.6.6"
val jackson_databind_version = jackson_version
val jackson_databind_nullable_version = "0.2.6"
val jakarta_annotation_version = "1.3.5"
val httpclient_version = "5.1.3"
val jodatime_version = "2.9.9"
val aws_version = "2.25.60"

dependencies {

//    compileOnly("software.amazon.awssdk:bedrock:2.25.7")
//    compileOnly("software.amazon.awssdk:bedrockagent:2.25.7")
//    compileOnly("software.amazon.awssdk:bedrockagentruntime:2.25.7")
    implementation("software.amazon.awssdk:bedrockruntime:$aws_version")
    implementation("software.amazon.awssdk:auth:$aws_version")

    implementation("io.swagger:swagger-annotations:$swagger_annotations_version")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.apache.httpcomponents.client5:httpclient5:$httpclient_version")
    implementation("org.openapitools:jackson-databind-nullable:$jackson_databind_nullable_version")
    implementation("jakarta.annotation:jakarta.annotation-api:$jakarta_annotation_version")

    implementation("com.fasterxml.jackson.core:jackson-core:$jackson_version")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jackson_version")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jackson_databind_version")
    implementation("com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:$jackson_version")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson_version")


    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.16")
    testImplementation(group = "ch.qos.logback", name = "logback-classic", version = logback_version)
    testImplementation(group = "ch.qos.logback", name = "logback-core", version = logback_version)

    implementation(group = "org.apache.httpcomponents.client5", name = "httpclient5", version = "5.3.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jackson_version)
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-annotations", version = jackson_version)
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = jackson_version)
    implementation(group = "com.google.guava", name = "guava", version = "32.1.3-jre")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")
    implementation(group = "org.openimaj", name = "JTransforms", version = "1.3.10")
    implementation(group = "commons-io", name = "commons-io", version = "2.15.0")

    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("script-runtime"))

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = jupiter_version)
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = jupiter_version)
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = jupiter_version)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "jo-penai"
            from(components["java"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("Jo Penai")
                description.set("A Java client for OpenAI's API")
                url.set("https://github.com/SimiaCryptus/JoPenai")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("acharneski")
                        name.set("Andrew Charneski")
                        email.set("acharneski@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://git@github.com/SimiaCryptus/JoPenai.git")
                    developerConnection.set("scm:git:ssh://git@github.com/SimiaCryptus/JoPenai.git")
                    url.set("https://github.com/SimiaCryptus/JoPenai")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://oss.sonatype.org/mask/repositories/snapshots"
            url = URI(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: System.getProperty("ossrhUsername")
                        ?: properties("ossrhUsername")
                password = System.getenv("OSSRH_PASSWORD") ?: System.getProperty("ossrhPassword")
                        ?: properties("ossrhPassword")
            }
        }
    }
    if (System.getenv("GPG_PRIVATE_KEY") != null && System.getenv("GPG_PASSPHRASE") != null) afterEvaluate {
        signing {
            sign(publications["mavenJava"])
        }
    }
}

if (System.getenv("GPG_PRIVATE_KEY") != null && System.getenv("GPG_PASSPHRASE") != null) {
    apply<SigningPlugin>()

    configure<SigningExtension> {
        useInMemoryPgpKeys(System.getenv("GPG_PRIVATE_KEY"), System.getenv("GPG_PASSPHRASE"))
        sign(configurations.archives.get())
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8)
}