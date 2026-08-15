import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = providers.gradleProperty("libraryGroup").get()
version = providers.gradleProperty("libraryVersion").get()

plugins {
  `java-library`
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

  implementation(project("$projectPrefix:text"))
  implementation(project("$projectPrefix:fileserver"))
  implementation(project("$projectPrefix:core"))
  implementation(project("$projectPrefix:lwcore"))
  implementation(project("$projectPrefix:platform-model"))
  implementation(project("$projectPrefix:platform-db"))
  implementation(project("$projectPrefix:docops"))
  compileOnly(project("$projectPrefix:kotlin"))
  testImplementation(project("$projectPrefix:providers"))
  testImplementation(project("$projectPrefix:kotlin"))
  compileOnly(project("$projectPrefix:groovy"))
  implementation(project("$projectPrefix:groovy"))
//    testImplementation(project(":Cognotik:scala"))
  implementation(libs.pty4j)
  implementation(libs.webdrivermanager)
  implementation(libs.pdfbox)
  implementation("com.github.jai-imageio:jai-imageio-core:1.4.0")
  implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
  implementation("org.apache.pdfbox:jbig2-imageio:3.0.4")
  implementation(libs.poi)
  implementation(libs.poi.ooxml)
  implementation(libs.jsoup)
  implementation(libs.zxing.core)
  implementation(libs.zxing.javase)
  implementation(libs.jetty.server)
  implementation(libs.jetty.servlet)
  implementation(libs.jetty.annotations)
  implementation(libs.jetty.websocket.server)
  implementation(libs.jetty.websocket.client)
  implementation(libs.jetty.websocket.servlet)
  implementation(libs.jetty.webapp)
  implementation(libs.flexmark.all)
  implementation(libs.flexmark.ext.tables)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.annotations)
  implementation(libs.jackson.kotlin)
  implementation(libs.guava)
  implementation(libs.google.api.client)
  implementation(libs.google.oauth.client.jetty)
  implementation(libs.google.api.services.oauth2)
  implementation(libs.google.http.client.gson)
  implementation(libs.commons.io)
  implementation(libs.commons.codec)
  implementation(libs.slf4j.api)
  implementation(libs.tinkerpop)
  //implementation(libs.hsqldb)
  implementation(libs.h2)
  implementation(libs.httpclient5) {
    exclude(group = "org.slf4j", module = "slf4j-api")
  }
  implementation(libs.selenium.java) {
    exclude(group = "com.intellij.remoterobot", module = "remote-robot")
  }
  // Exposed DSL (jetbrains)
  val exposed_dsl_version =
    "1.3.0" // Check for latest version at https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
  implementation("org.jetbrains.exposed:exposed-core:$exposed_dsl_version")
  implementation("org.jetbrains.exposed:exposed-dao:$exposed_dsl_version")
  implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_dsl_version")
  implementation("org.jetbrains.exposed:exposed-java-time:$exposed_dsl_version")

  compileOnly(libs.eclipse.jdt.core) // Needed for Java parsing? If so, keep.
  compileOnly(libs.graalvm.js)
  compileOnly(libs.graalvm.js.language)
  compileOnly(libs.kotlinx.coroutines)
  compileOnly(libs.aws.sdk)

  compileOnly("org.openapitools:openapi-generator:7.3.0") {
    exclude(group = "org.slf4j")
  }

  compileOnly("org.openapitools:openapi-generator-cli:7.3.0") {
    exclude(group = "org.slf4j")
  }

  runtimeOnly(libs.logback.classic)
  runtimeOnly(libs.logback.core)

  testRuntimeOnly("org.openapitools:openapi-generator-cli:7.3.0")
  testImplementation(libs.kotlinx.coroutines)
  testImplementation(libs.kotlinx.collections.immutable)
  testImplementation(libs.aws.sdk)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  /*org.junit.jupiter.params*/
  testImplementation(libs.junit.jupiter.params)

  /*io.mockk.every*/
  testImplementation(libs.mockk)


  compileOnly(kotlin("stdlib"))
  testImplementation(kotlin("stdlib"))
  testImplementation(kotlin("scripting-jsr223"))
  testImplementation(kotlin("scripting-jvm"))
  testImplementation(kotlin("scripting-jvm-host"))
  testImplementation(kotlin("script-runtime"))
  testImplementation(kotlin("scripting-compiler-embeddable"))
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation(kotlin("script-runtime"))
  testImplementation(kotlin("test"))
}


/* ---------------------------------------------------------------------------
  * Frontend build (pnpm). No gradle-node plugin: just shell out to pnpm.
   * Prefers `pnpm` on PATH (e.g. `corepack enable`), but degrades gracefully:
   * if pnpm cannot be located and the webapp output is already present, the
   * pre-built resources are reused instead of failing the build.
  * Use -PskipWebapp to build the JVM artifacts without touching the frontend.
   * Use -PpnpmPath=/path/to/pnpm (or PNPM_PATH env var) to point at pnpm.
   * Use -PstrictWebapp to fail when pnpm is missing and nothing is pre-built.
  * ------------------------------------------------------------------------ */
val webappDir = file("${project.projectDir}/../webapp-v2")
val isWindows = System.getProperty("os.name").lowercase().contains("windows")

fun locatePnpm(): String? {
  val explicit = (providers.gradleProperty("pnpmPath").orNull
    ?: System.getenv("PNPM_PATH"))?.takeIf { it.isNotBlank() }
  if (explicit != null) {
    val f = File(explicit)
    if (f.isFile && f.canExecute()) return f.absolutePath
    logger.warn("Configured pnpm path '$explicit' is not an executable file; falling back to discovery.")
  }
  val candidateNames = if (isWindows) listOf("pnpm.cmd", "pnpm.exe", "pnpm.bat", "pnpm") else listOf("pnpm")
  val home = System.getProperty("user.home") ?: ""
  val searchDirs = buildList {
    (System.getenv("PATH") ?: "").split(File.pathSeparator)
      .filter { it.isNotBlank() }
      .forEach { add(File(it)) }
    System.getenv("PNPM_HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
    if (home.isNotBlank()) {
      add(File(home, ".local/share/pnpm"))
      add(File(home, ".local/share/pnpm/bin"))
      add(File(home, ".local/bin"))
      add(File(home, "AppData/Local/pnpm"))
      add(File(home, "AppData/Roaming/npm"))
    }
    add(File("/usr/local/bin"))
    add(File("/usr/bin"))
    add(File("/opt/homebrew/bin"))
  }
  for (dir in searchDirs) {
    for (name in candidateNames) {
      val f = File(dir, name)
      if (f.isFile && f.canExecute()) return f.absolutePath
    }
  }
  return null
}

val pnpmExecutable: String? = locatePnpm()

/* Fallback command so Exec tasks stay configurable even when pnpm is absent;
  * such tasks are disabled via onlyIf and never actually run. */
val pnpm: String = pnpmExecutable ?: if (isWindows) "pnpm.cmd" else "pnpm"
val skipWebapp = providers.gradleProperty("skipWebapp").isPresent
val strictWebapp = providers.gradleProperty("strictWebapp").isPresent
val sassVersion = "1.83.0"

fun File.hasContent(): Boolean = isDirectory && (listFiles()?.isNotEmpty() ?: false)

val prebuiltWebappPresent =
  File(webappDir, "build").hasContent() ||
      File(projectDir, "src/main/resources/application").hasContent()

/* Decide once, at configuration time, whether the frontend tasks can run. */
val webappBuildEnabled: Boolean = when {
  skipWebapp -> false
  pnpmExecutable != null -> true
  prebuiltWebappPresent -> {
    logger.lifecycle(
      "pnpm executable not found; reusing pre-built webapp resources " +
          "(pass -PpnpmPath=/path/to/pnpm to rebuild the frontend)."
    )
    false
  }

  strictWebapp -> throw GradleException(
    "pnpm executable not found and no pre-built webapp resources are available. " +
        "Install pnpm (corepack enable), set -PpnpmPath=/path/to/pnpm, or build with -PskipWebapp."
  )

  else -> {
    logger.warn(
      "pnpm executable not found and no pre-built webapp resources were detected; " +
          "skipping frontend build. The resulting artifact may not contain the web UI."
    )
    false
  }
}

tasks.register<Exec>("pnpmInstall") {
  group = "webapp"
  description = "Installs webapp-v2 dependencies via pnpm"
  onlyIf { webappBuildEnabled }
  workingDir = webappDir
  commandLine(pnpm, "install")
  inputs.files(File(webappDir, "package.json"), File(webappDir, "pnpm-lock.yaml"))
  outputs.dir(File(webappDir, "node_modules"))
}

tasks.register<Exec>("buildWebapp") {
  group = "webapp"
  description = "Builds webapp-v2 via pnpm"
  onlyIf { webappBuildEnabled }
  dependsOn("pnpmInstall")
  workingDir = webappDir
  commandLine(pnpm, "run", "build")
  inputs.dir(File(webappDir, "src"))
  inputs.files(File(webappDir, "package.json"), File(webappDir, "pnpm-lock.yaml"))
  outputs.dir(File(webappDir, "build"))
}

// Copy webapp build output to resources
tasks.register<Copy>("copyWebappBuild") {
  dependsOn("buildWebapp")
  /* Nothing to copy when the frontend build was skipped and no output exists. */
  onlyIf { !skipWebapp && File(webappDir, "build").hasContent() }
  from("../webapp-v2/build")
  into("src/main/resources/application")
}

tasks.register<Copy>("copyWebappStatic") {
  dependsOn("buildWebapp")
  onlyIf { !skipWebapp && File(webappDir, "build/static").hasContent() }
  from("../webapp-v2/build/static")
  into("src/main/resources/web/static")
}

// Clean webapp build artifacts
tasks.register<Delete>("cleanWebapp") {
  /* Never wipe pre-built output we cannot regenerate without pnpm. */
  onlyIf { pnpmExecutable != null }
  delete("../webapp-v2/build")
  delete("src/main/resources/application/static")
  delete("src/main/resources/web/static")
}
tasks.clean {
  dependsOn("cleanWebapp")
}



tasks.register<Exec>("compileSass") {
  group = "webapp"
  description = "Compiles shared SCSS to build/resources/main/css"
  onlyIf { webappBuildEnabled && File(projectDir, "src/main/resources/shared").hasContent() }
  workingDir = projectDir
  commandLine(
    pnpm, "dlx", "sass@$sassVersion",
    "src/main/resources/shared:build/resources/main/css",
    "--style=expanded",
    "--source-map"
  )
  inputs.dir("src/main/resources/shared")
  outputs.dir(layout.buildDirectory.dir("resources/main/css"))
}

tasks.named("processResources") {
  dependsOn("compileSass", "copyWebappBuild", "copyWebappStatic")
}
java {
  withJavadocJar()
  withSourcesJar()
}
tasks.named("sourcesJar") {
  dependsOn("copyWebappBuild", "copyWebappStatic")
}



publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])

      groupId = "com.cognotik"
      artifactId = "webui"
      version = project.version.toString()

      pom {
        name.set("Cognotik Webapp")
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
    sign(publishing.publications["maven"])
  }

}



tasks.javadoc {
  if (JavaVersion.current().isJava9Compatible) {
    (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
  }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
  freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}