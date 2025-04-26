plugins {
    `maven-publish`
    signing
}
fun properties(key: String) = project.findProperty(key).toString()

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            // Sources and javadoc jars are now created by the java plugin configuration
            pom {
                name.set(project.name)
                description.set("Cognotik ${project.name.replaceFirstChar { it.uppercase() }} Module")
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
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                // Use providers for lazy evaluation and better error handling
                username = providers.environmentVariable("OSSRH_USERNAME")
                    .orElse(providers.systemProperty("ossrhUsername"))
                    .orElse(properties("ossrhUsername"))
                    .get()
                password = providers.environmentVariable("OSSRH_PASSWORD")
                    .orElse(providers.systemProperty("ossrhPassword"))
                    .orElse(properties("ossrhPassword"))
                    .get()
            }
        }
    }
}