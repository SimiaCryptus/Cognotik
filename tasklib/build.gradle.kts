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

dependencies {
    implementation(project(":core"))
    compileOnly(project(":webui"))
    implementation(project(":webui"))
    testImplementation(project(":providers"))
    testImplementation(project(":stdtools"))

    implementation(libs.pty4j)
    implementation(libs.pdfbox)
    implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
    implementation("org.apache.pdfbox:jbig2-imageio:3.0.4")
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.jsoup)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
     compileOnly("org.seleniumhq.selenium:selenium-java:4.27.0")
     compileOnly("io.github.bonigarcia:webdrivermanager:5.9.2")

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
    implementation(libs.hsqldb)
    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    compileOnly(libs.eclipse.jdt.core) // Needed for Java parsing? If so, keep.
    compileOnly(libs.graalvm.js)
    compileOnly(libs.graalvm.js.language)
    compileOnly(libs.kotlinx.coroutines)
    compileOnly(libs.aws.sdk)

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

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "com.cognotik"
            artifactId = "tasklib"
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