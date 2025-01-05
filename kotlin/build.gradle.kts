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

dependencies {

  implementation(project(":core"))

  //compileOnly(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.8.0-RC")// https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")


  implementation(kotlin("stdlib"))
  implementation(kotlin("scripting-jsr223"))
  implementation(kotlin("scripting-jvm"))
  implementation(kotlin("scripting-jvm-host"))
  implementation(kotlin("script-runtime"))
  implementation(kotlin("scripting-compiler-embeddable"))
  implementation(kotlin("compiler-embeddable"))

  implementation(group = "commons-io", name = "commons-io", version = "2.15.0")

  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.1")
  testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.1")

  implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.16")
  testImplementation(group = "ch.qos.logback", name = "logback-classic", version = "1.5.13")
  testImplementation(group = "ch.qos.logback", name = "logback-core", version = "1.5.13")
  testImplementation("org.ow2.asm:asm:9.6")


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
      artifactId = "kotlin"
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
        name.set("SkyeNet Kotlin Interpreter")
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
