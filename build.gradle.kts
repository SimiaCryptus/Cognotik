fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

tasks {
  wrapper {
    gradleVersion = properties("gradleVersion")
  }
}

repositories {
  gradlePluginPortal()
  mavenCentral()
}
plugins {
  kotlin("jvm") version "2.0.20"
}
