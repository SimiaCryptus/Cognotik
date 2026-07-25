group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

plugins {
  `java-library`
  kotlin("jvm")
  `maven-publish`
  signing
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

  implementation(libs.hsqldb)
  implementation(project("$projectPrefix:antlr")) {
    exclude(group = "org.jetbrains.kotlin")
  }
  implementation(libs.antlr.runtime)
  implementation(libs.commons.text)

  implementation(libs.slf4j.api)
  implementation(libs.commons.io)
  implementation(libs.guava)
  implementation(libs.gson)
  implementation(libs.httpclient5)
  implementation(libs.jsoup)
  implementation(libs.pdfbox)
  implementation(libs.poi)
  implementation(libs.poi.ooxml)
  implementation(libs.poiscratchpad)
  implementation(libs.commons.csv)
  implementation(libs.odfdom.java)
  implementation(libs.jtransforms)
  implementation("jakarta.mail:jakarta.mail-api:2.1.2")
  implementation("org.eclipse.angus:angus-mail:2.0.2")

  implementation(libs.jackson.databind)
  implementation(libs.jackson.annotations)
  implementation(libs.jackson.kotlin)
  implementation(libs.jackson.jaxrs.json)
  implementation(libs.jackson.datatype.jsr310)
  implementation(libs.jackson.datatype.jdk8)
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.19.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.19.0")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-properties:2.19.0")

  compileOnly(libs.asm)
  implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.libs.versions.kotlin.get()}")
  implementation(libs.kotlinx.coroutines)

  testImplementation(libs.kotlin.script.runtime)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.jupiter.engine)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.kotlin.test.junit5)

  // Optional Android dependency
  compileOnly(libs.android)
  implementation(libs.groovy.all)

  compileOnly(platform(libs.aws.bom))
  compileOnly(libs.aws.sdk)
  compileOnly(libs.logback.classic) {
    exclude(group = "org.slf4j", module = "slf4j-api")
  }
  compileOnly(libs.logback.core) {
    exclude(group = "org.slf4j", module = "slf4j-api")
  }

  implementation(platform(libs.aws.bom)) // Use BOM alias
  implementation(libs.aws.bedrockruntime)
  implementation(libs.aws.bedrock)
  implementation(libs.aws.auth)
  implementation(libs.aws.sso)

  implementation(libs.swagger.annotations)
  implementation(libs.jsr305)
  implementation(libs.httpclient5)
  implementation(libs.jackson.databind.nullable)
  implementation(libs.jakarta.annotations.api)

  implementation(libs.slf4j.api)
  testImplementation(libs.logback.classic)
  testImplementation(libs.logback.core)

  implementation(libs.httpclient5) {
    exclude(group = "org.slf4j", module = "slf4j-api")
  }
  implementation(libs.jackson.kotlin)
  implementation(libs.guava)
  implementation(libs.gson)
  implementation(libs.commons.io)

  implementation("com.google.genai:google-genai:1.32.0")

  testImplementation(platform(libs.aws.bom))
  testImplementation(libs.aws.sdk)
  testImplementation(libs.logback.classic) {
    exclude(group = "org.slf4j", module = "slf4j-api")
  }
  testImplementation(libs.logback.core) {
    exclude(group = "org.slf4j", module = "slf4j-api")
  }
  testImplementation(libs.mockito)
// Ensure Kotlin compilation happens before Java compilation
  tasks.named("compileJava") {
    dependsOn(tasks.named("compileKotlin"))
  }
  tasks.named("compileTestJava") {
    dependsOn(tasks.named("compileTestKotlin"))
  }
}

configurations.all {
  resolutionStrategy {
    force(libs.antlr.runtime)
  }
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
      artifactId = "core"
      version = project.version.toString()

      pom {
        name.set("Cognotik Core")
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
            name.set("Andrew Charneski")
            email.set("acharneski@gmail.com")
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