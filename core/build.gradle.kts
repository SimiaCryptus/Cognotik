group = providers.gradleProperty("cognotikGroup").get()
 version = providers.gradleProperty("cognotikVersion").get()

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

dependencies {

    implementation(libs.hsqldb)
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
    testImplementation(project(":jo-penai"))


    compileOnly(libs.asm)
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.coroutines)

    testImplementation(libs.kotlin.script.runtime)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.kotlin.test.junit5)

    // Optional Android dependency
    compileOnly(libs.android)

    compileOnly(platform(libs.aws.bom))
    compileOnly(libs.aws.sdk)
    compileOnly(libs.logback.classic) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    compileOnly(libs.logback.core) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

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


 java {
  withJavadocJar()
  withSourcesJar()
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])

      groupId = project.group.toString()
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
  }

  sign(publishing.publications["maven"])
}

tasks.javadoc {
  if (JavaVersion.current().isJava9Compatible) {
    (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
  }
}