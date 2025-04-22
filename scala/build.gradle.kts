import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
  java
  `java-library`
  `scala`
  `maven-publish`
  id("org.jetbrains.kotlin.jvm") version "2.0.20"
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

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

val scala_version = "2.13.12"
dependencies {

  implementation(project(":core"))
  implementation(group = "org.scala-lang", name = "scala-library", version = scala_version)
  implementation(group = "org.scala-lang", name = "scala-compiler", version = scala_version)
  implementation(group = "org.scala-lang", name = "scala-reflect", version = scala_version)
  implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.16")

  testImplementation(group = "org.slf4j", name = "slf4j-simple", version = "2.0.16")
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.8.1")
  testImplementation(group = "org.scala-lang.modules", name = "scala-java8-compat_2.13", version = "0.9.1")

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
      artifactId = "scala"
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
        name.set("Cognotik Scala Interpreter")
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
