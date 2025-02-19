import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
  java
  `java-library`
  id("org.jetbrains.kotlin.jvm") version "2.0.20"
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

kotlin {
  jvmToolchain(11)
}

val junit_version = "5.10.1"
val logback_version = "1.5.13"
val jackson_version = "2.17.2"
val hsqldb_version = "2.7.2"

dependencies {

  implementation(group = "com.simiacryptus", name = "jo-penai", version = "1.1.13")
  implementation(group = "org.hsqldb", name = "hsqldb", version = hsqldb_version)
  implementation(project(":antlr"))
  implementation("org.antlr:antlr4-runtime:4.13.2")
//  antlr("org.antlr:antlr4:4.13.2")

  implementation("org.apache.commons:commons-text:1.11.0")

  implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.16")
  implementation(group = "commons-io", name = "commons-io", version = "2.15.0")
  implementation(group = "com.google.guava", name = "guava", version = "32.1.3-jre")
  implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")
  implementation(group = "org.apache.httpcomponents.client5", name = "httpclient5", version = "5.2.3")
  implementation("org.eclipse.jetty.toolchain:jetty-jakarta-servlet-api:5.0.2")

  implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jackson_version)
  implementation(group = "com.fasterxml.jackson.core", name = "jackson-annotations", version = jackson_version)
  implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = jackson_version)
  
  
  
  compileOnly("org.ow2.asm:asm:9.6")
  compileOnly(kotlin("stdlib"))
  compileOnly(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.8.0-RC")

  testImplementation(kotlin("stdlib"))
  testImplementation(kotlin("script-runtime"))

  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = junit_version)
  testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = junit_version)
  compileOnly(group = "org.junit.jupiter", name = "junit-jupiter-api", version = junit_version)
  compileOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = junit_version)

  compileOnly(platform("software.amazon.awssdk:bom:2.21.29"))
  compileOnly(group = "software.amazon.awssdk", name = "aws-sdk-java", version = "2.27.23")
  testImplementation(platform("software.amazon.awssdk:bom:2.21.29"))
  testImplementation(group = "software.amazon.awssdk", name = "aws-sdk-java", version = "2.27.23")

  compileOnly(group = "ch.qos.logback", name = "logback-classic", version = logback_version)
  compileOnly(group = "ch.qos.logback", name = "logback-core", version = logback_version)
  testImplementation(group = "ch.qos.logback", name = "logback-classic", version = logback_version)
  testImplementation(group = "ch.qos.logback", name = "logback-core", version = logback_version)

  testImplementation(group = "org.mockito", name = "mockito-core", version = "5.7.0")

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
        name.set("SkyeNet Core Components")
        description.set("A very helpful puppy")
        url.set("https://github.com/SimiaCryptus/SkyeNet")
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
          connection.set("scm:git:git://git@github.com/SimiaCryptus/SkyeNet.git")
          developerConnection.set("scm:git:ssh://git@github.com/SimiaCryptus/SkyeNet.git")
          url.set("https://github.com/SimiaCryptus/SkyeNet")
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