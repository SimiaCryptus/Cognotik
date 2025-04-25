plugins {
  `maven-publish`
  signing
}
fun properties(key: String) = project.findProperty(key).toString()
//val javadocJar by tasks.registering(Jar::class) {
//    archiveClassifier.set("javadoc")
//    from(tasks.javadoc)
//}
//val sourcesJar by tasks.registering(Jar::class) {
//    archiveClassifier.set("sources")
//    from(sourceSets.main.get().allSource)
//}
publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
//            artifact(sourcesJar.get())
//            artifact(javadocJar.get())
      pom {
        name.set(project.name)
        description.set("Cognotik ${project.name.capitalize()} Module")
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
      url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
      credentials {
        username = System.getenv("OSSRH_USERNAME") ?: System.getProperty("ossrhUsername")
            ?: properties("ossrhUsername")
        password = System.getenv("OSSRH_PASSWORD") ?: System.getProperty("ossrhPassword")
            ?: properties("ossrhPassword")
      }
    }
  }
}
if (System.getenv("GPG_PRIVATE_KEY") != null && System.getenv("GPG_PASSPHRASE") != null) {
  signing {
    useInMemoryPgpKeys(System.getenv("GPG_PRIVATE_KEY"), System.getenv("GPG_PASSPHRASE"))
    sign(publishing.publications["mavenJava"])
  }
}