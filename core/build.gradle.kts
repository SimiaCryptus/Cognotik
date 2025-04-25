import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
    id("cognotik.common-conventions")
    `java-library`
    `maven-publish`
    id("signing")
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

    implementation(libs.hsqldb)
    implementation(project(":jo-penai")) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(":antlr")) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(libs.antlr.runtime)
    implementation(libs.commons.text)

    implementation(libs.slf4j.api)
    implementation(libs.commons.io)
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.httpclient5)
    implementation("org.eclipse.jetty.toolchain:jetty-jakarta-servlet-api:5.0.2")

    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)

    compileOnly(libs.asm)
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.kotlinx.coroutines)

    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("script-runtime"))

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    compileOnly(platform("org.junit:junit-bom:5.10.2"))
    compileOnly("org.junit.jupiter:junit-jupiter-api")
    compileOnly("org.junit.jupiter:junit-jupiter-engine")

    compileOnly(platform("software.amazon.awssdk:bom:2.21.29"))
    compileOnly(libs.aws.sdk)
    compileOnly(libs.logback.classic)
    compileOnly(libs.logback.core)

    testImplementation(platform("software.amazon.awssdk:bom:2.21.29"))
    testImplementation(libs.aws.sdk)
    testImplementation(libs.logback.classic)
    testImplementation(libs.logback.core)
    testImplementation(libs.mockito)

}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.javadoc)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {

    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "core"
            from(components["java"])
            artifact(sourcesJar.get())
            artifact(javadocJar.get())
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("Cognotik Core")
                description.set("Cognotik Agentic Toolkit")
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