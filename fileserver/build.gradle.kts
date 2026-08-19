import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.github.node-gradle.node") version "7.1.0"
}

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

val projectPrefix = if (rootProject.name == "Cognotik") "" else ":Cognotik"
dependencies {

    implementation(project("$projectPrefix:text"))
    implementation(project("$projectPrefix:lwcore"))
    implementation(project("$projectPrefix:platform-model"))
    implementation(project("$projectPrefix:docops"))
    compileOnly(project("$projectPrefix:kotlin"))
    testImplementation(project("$projectPrefix:providers"))
    testImplementation(project("$projectPrefix:kotlin"))
    compileOnly(project("$projectPrefix:groovy"))
    implementation(project("$projectPrefix:groovy"))
//    testImplementation(project(":Cognotik:scala"))
    implementation(libs.pty4j)
    implementation(libs.webdrivermanager)
    implementation(libs.pdfbox)
    implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
    implementation("org.apache.pdfbox:jbig2-imageio:3.0.4")
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.jsoup)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)
    implementation(libs.jetty.annotations)
    implementation(libs.jetty.websocket.server)
    implementation(libs.jetty.websocket.client)
    implementation(libs.jetty.websocket.servlet)
    implementation(libs.jetty.webapp)
    implementation(libs.flexmark.all)
    implementation(libs.flexmark.ext.tables)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)
    implementation(libs.guava)
    implementation(libs.google.api.client)
    implementation(libs.google.oauth.client.jetty)
    implementation(libs.google.api.services.oauth2)
    implementation(libs.google.http.client.gson)
    implementation(libs.commons.io)
    implementation(libs.commons.codec)
    implementation(libs.slf4j.api)
    implementation(libs.tinkerpop)
    //implementation(libs.hsqldb)
    implementation(libs.h2)
    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.selenium.java) {
        exclude(group = "com.intellij.remoterobot", module = "remote-robot")
    }
    // Exposed DSL (jetbrains)
    val exposed_dsl_version = "1.3.0" // Check for latest version at https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    implementation("org.jetbrains.exposed:exposed-core:$exposed_dsl_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_dsl_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_dsl_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_dsl_version")

    compileOnly(libs.eclipse.jdt.core) // Needed for Java parsing? If so, keep.
    compileOnly(libs.graalvm.js)
    compileOnly(libs.graalvm.js.language)
    compileOnly(libs.kotlinx.coroutines)
    compileOnly(libs.aws.sdk)
    /*
    implementation("org.eclipse.jetty:jetty-server:11.0.20")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.20")
    */
    implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)

    compileOnly("org.openapitools:openapi-generator:7.3.0") {
        exclude(group = "org.slf4j")
    }

    compileOnly("org.openapitools:openapi-generator-cli:7.3.0") {
        exclude(group = "org.slf4j")
    }

    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.logback.core)

    testRuntimeOnly("org.openapitools:openapi-generator-cli:7.3.0")
    testImplementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.aws.sdk)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    /*org.junit.jupiter.params*/
    testImplementation(libs.junit.jupiter.params)

    /*io.mockk.every*/
    testImplementation(libs.mockk)

    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("scripting-jsr223"))
    testImplementation(kotlin("scripting-jvm"))
    testImplementation(kotlin("scripting-jvm-host"))
    testImplementation(kotlin("script-runtime"))
    testImplementation(kotlin("scripting-compiler-embeddable"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(kotlin("script-runtime"))
    testImplementation(kotlin("test"))
}

node {
    version.set("20.19.5")
    npmVersion.set("11.6.0")
    download.set(true)
    nodeProjectDir.set(file("${project.projectDir}/../webapp"))
}


java {
    withJavadocJar()
    withSourcesJar()
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "com.cognotik"
            artifactId = "webui"
            version = project.version.toString()

            pom {
                name.set("Cognotik Webapp")
                description.set("Core library for Cognotik AI framework")
                url.set("https://github.com/SimiaCryptus/Cognotik")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("simiacryptus")
                        name.set("SimiaCryptus")
                        email.set("simiacryptus@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/SimiaCryptus/Cognotik.git")
                    developerConnection.set("scm:git:ssh://github.com/SimiaCryptus/Cognotik.git")
                    url.set("https://github.com/SimiaCryptus/Cognotik")
                }
            }
        }
    }
}

signing {
    val signingKey = findProperty("signingInMemoryKey")?.toString() ?: System.getenv("SIGNING_KEY")
    val signingPassword = findProperty("signingInMemoryKeyPassword")?.toString() ?: System.getenv("SIGNING_PASSWORD")

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}