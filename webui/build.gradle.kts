import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.github.node-gradle.node") version "7.0.1"
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
    compileOnly(project(":kotlin"))
    testImplementation(project(":kotlin"))
    compileOnly(project(":groovy"))
    implementation(project(":groovy"))
//    testImplementation(project(":scala"))
    implementation(project(":jo-penai")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation(libs.pty4j)
    implementation(libs.webdrivermanager)
    implementation(libs.pdfbox)
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
    implementation(libs.hsqldb)
    implementation(libs.httpclient5) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(libs.selenium.java) {
        exclude(group = "com.intellij.remoterobot", module = "remote-robot")
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

    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("scripting-jsr223"))
    testImplementation(kotlin("scripting-jvm"))
    testImplementation(kotlin("scripting-jvm-host"))
    testImplementation(kotlin("script-runtime"))
    testImplementation(kotlin("scripting-compiler-embeddable"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(kotlin("script-runtime"))
}

node {
    version.set("20.19.5")
    npmVersion.set("11.6.0")
    download.set(true)
    nodeProjectDir.set(file("${project.projectDir}/../webapp"))
}

// Add webapp build tasks
tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildWebapp") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "build"))
    inputs.dir("../webapp/src")
    inputs.files("../webapp/package.json", "../webapp/package-lock.json")
    outputs.dir("../webapp/build")
}

// Copy webapp build output to resources
tasks.register<Copy>("copyWebappBuild") {
    dependsOn("buildWebapp")
    from("../webapp/build")
    into("src/main/resources/application")
}

tasks.register<Copy>("copyWebappStatic") {
    dependsOn("buildWebapp")
    from("../webapp/build/static")
    into("src/main/resources/welcome/static")
}

// Clean webapp build artifacts
tasks.register<Delete>("cleanWebapp") {
    delete("../webapp/build")
    delete("src/main/resources/application/static")
    delete("src/main/resources/welcome/static")
}
tasks.clean {
    dependsOn("cleanWebapp")
}


tasks.register<com.github.gradle.node.npm.task.NpmTask>("installSass") {
    args.set(listOf("install", "sass", "--save-dev"))
}

tasks.register<com.github.gradle.node.npm.task.NpxTask>("compileSass") {
    dependsOn("installSass")
    command.set("sass")
    workingDir.set(file("${project.projectDir}"))
    args.set(
        listOf(
            "src/main/resources/shared:build/resources/main/css",
            "--style=expanded",
            "--source-map"
        )
    )
}

tasks.named("processResources") {
    dependsOn("compileSass", "copyWebappBuild", "copyWebappStatic")
}
java {
    withJavadocJar()
    withSourcesJar()
}
tasks.named("sourcesJar") {
    dependsOn("copyWebappBuild", "copyWebappStatic")
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