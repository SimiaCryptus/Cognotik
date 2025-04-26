import com.sass_lang.embedded_protocol.OutputStyle
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
    id("cognotik.common-conventions")
    `java-library`
    `maven-publish`
    id("signing")
    id("io.freefair.sass-base") version "8.13"
    id("io.freefair.sass-java") version "8.13"
}

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}


val graal_version = "24.1.1"

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-scripting-compiler:${libs.versions.kotlin.get()}")
    compileOnly("org.eclipse.jdt:org.eclipse.jdt.core:3.36.0")
    compileOnly("org.graalvm.js:js:$graal_version")
    compileOnly("org.graalvm.js:js-language:$graal_version")

    testImplementation(libs.kotlinx.coroutines)
    testImplementation(group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version = "0.3.8")
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("scripting-jsr223"))
    testImplementation(kotlin("scripting-jvm"))
    testImplementation(kotlin("scripting-jvm-host"))
    testImplementation(kotlin("script-runtime"))
    testImplementation(kotlin("scripting-compiler-embeddable"))
    testImplementation(kotlin("compiler-embeddable"))

    implementation(project(":jo-penai")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }

    implementation(project(":core"))
    implementation(project(":kotlin"))
    implementation(project(":groovy"))
    implementation("org.apache.pdfbox:pdfbox:3.0.3")

    implementation("org.seleniumhq.selenium:selenium-java:4.27.0") {
        exclude(group = "com.intellij.remoterobot", module = "remote-robot")
    }
    implementation("io.github.bonigarcia:webdrivermanager:5.9.2")
    implementation("org.jsoup:jsoup:1.19.1")

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    compileOnly(libs.aws.sdk)
    testImplementation(libs.aws.sdk)

    compileOnly("org.openapitools:openapi-generator:7.3.0") {
        exclude(group = "org.slf4j")
    }
    compileOnly("org.openapitools:openapi-generator-cli:7.3.0") {
        exclude(group = "org.slf4j")
    }
    testRuntimeOnly("org.openapitools:openapi-generator-cli:7.3.0")

    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
    implementation(libs.jetty.annotations)
    implementation(libs.jetty.websocket.server)
    implementation(libs.jetty.websocket.client)
    implementation("org.eclipse.jetty.websocket:websocket-servlet:${libs.versions.jetty.get()}")
    implementation(libs.jetty.webapp)

    implementation(group = "com.vladsch.flexmark", name = "flexmark", version = "0.64.8")
    implementation(group = "com.vladsch.flexmark", name = "flexmark-ext-tables", version = "0.64.8")

    compileOnly(libs.kotlinx.coroutines)

    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))

    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    implementation("com.fasterxml.jackson.core:jackson-core:${libs.versions.jackson.get()}")
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)


    implementation(libs.guava)

    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-oauth2:v2-rev20200213-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")



    implementation(libs.commons.io)
    implementation(group = "commons-codec", name = "commons-codec", version = "1.16.0")

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.logback.core)

    testImplementation(kotlin("script-runtime"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

sass {
    omitSourceMapUrl.set(false)
    outputStyle.set(OutputStyle.EXPANDED)
    sourceMapContents.set(false)
    sourceMapEmbed.set(false)
    sourceMapEnabled.set(true)
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

publishing {

    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "webui"
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
                name.set("Cognotik Web Interface")
                description.set("A very helpful puppy")
                url.set("https://github.com/SimiaCryptus/Cognotik")
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
                    connection.set("scm:git:git://git@github.com/SimiaCryptus/Cognotik.git")
                    developerConnection.set("scm:git:ssh://git@github.com/SimiaCryptus/Cognotik.git")
                    url.set("https://github.com/SimiaCryptus/Cognotik")
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