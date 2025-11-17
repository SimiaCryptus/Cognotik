import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
 import org.gradle.api.tasks.testing.logging.TestLogEvent
 import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  alias(libs.plugins.shadow)
  application
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot") version "3.5.6"
  id("io.spring.dependency-management") version "1.1.7"
 //  id("com.github.node-gradle.node") version "7.0.1"
}


// Use providers for consistency with other modules
group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

repositories {
  mavenCentral {
    metadataSources {
      mavenPom()
      artifact()
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

//node {
//  version.set("20.19.5")
//  npmVersion.set("11.6.0")
//  download.set(true)
//  nodeProjectDir.set(file("${project.projectDir}/ui"))
//}

dependencies {
  // Exclude conflicting logging implementations
  configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
    exclude(group = "commons-logging", module = "commons-logging")
  }

  // Force compatible Jackson versions for Spring Boot 3.2.2
  implementation(platform("com.fasterxml.jackson:jackson-bom:2.15.3"))
  // Cognotik dependencies
  implementation(project(":core"))
  implementation(project(":jo-penai"))


  // Spring Boot
 implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-web")
implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  // Kotlin Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

  // Spring Boot dependencies
  //  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

  implementation(libs.batik.transcoder) {
    exclude(group = "org.apache.commons", module = "commons-compress")
  }
  implementation(libs.batik.codec) {
    exclude(group = "org.apache.commons", module = "commons-compress")
  }
  implementation(libs.commons.text)
  implementation(libs.jsoup)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.annotations)
  implementation(libs.jackson.kotlin)
  implementation(libs.guava)
  implementation(libs.jetty.server)
  implementation(libs.jetty.webapp)
  implementation(libs.jetty.websocket.server)
  implementation(libs.httpclient5.fluent)
  implementation(libs.gson)
  implementation(libs.h2)
  implementation(libs.kotlinx.coroutines)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.commons.io)
  implementation(libs.flexmark.all)

  implementation(kotlin("stdlib"))

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api) // Version from BOM
  testImplementation(libs.junit.jupiter.params) // Version from BOM
  testRuntimeOnly(libs.junit.jupiter.engine) // Version from BOM
  //  testImplementation("org.springframework.boot:spring-boot-starter-test")
  //  testImplementation("io.projectreactor:reactor-test")
  //  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

application {
  mainClass.set("com.example.news.NewsApplicationKt")
//// Add UI build tasks
//  tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildUI") {
//    dependsOn(tasks.npmInstall)
//    args.set(listOf("run", "build"))
//    inputs.dir("ui/src")
//    inputs.files("ui/package.json", "ui/package-lock.json")
//    outputs.dir("ui/build")
//  }
//// Copy UI build output to resources
//  tasks.register<Copy>("copyUIBuild") {
//    dependsOn("buildUI")
//    from("ui/build")
//    into("src/main/resources/static")
//  }
//// Clean UI build artifacts
//  tasks.register<Delete>("cleanUI") {
//    delete("ui/build")
//    delete("ui/node_modules")
//    delete("src/main/resources/static")
//  }
//  tasks.clean {
//    dependsOn("cleanUI")
//  }
//// Ensure UI is built before processing resources
//  tasks.named("processResources") {
//    dependsOn("copyUIBuild")
//  }
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
      "--add-opens",
      "java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens",
      "java.base/java.util=ALL-UNNAMED",
      "--add-opens",
      "java.base/java.lang=ALL-UNNAMED",
      "--add-opens",
      "java.base/sun.nio.ch=ALL-UNNAMED"
    )

    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
  }
  jar {
    enabled = true
    archiveClassifier.set("")
  }
}


tasks.withType<ShadowJar> {
  archiveClassifier.set("all")
  mergeServiceFiles()
  isZip64 = true
  exclude("org/slf4j/impl/**")
  manifest {
    attributes(
      "Main-Class" to "com.example.news.NewsApplicationKt"
    )
  }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
  freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}