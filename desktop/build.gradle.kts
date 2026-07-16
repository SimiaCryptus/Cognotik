import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

plugins {
    `java-library`
    application
    war
    alias(libs.plugins.shadow)
    `maven-publish`
     signing
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
application {
    mainClass.set("com.simiacryptus.cognotik.desktop.CognotikApps")
}
tasks.register<JavaExec>("runDaemonClient") {
    group = "application"
    description = "Runs the DaemonClient main class"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.simiacryptus.cognotik.desktop.DaemonClient")
}
tasks.register<JavaExec>("runCognotikApps") {
    group = "application"
    description = "Runs the CognotikApps main class"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.simiacryptus.cognotik.desktop.CognotikApps")
}
tasks.register<JavaExec>("runCognotikAppsFromJar") {
     group = "application"
     description = "Runs the CognotikApps main class from the shadow (fat) JAR, to test packaged resource handling"
     dependsOn("shadowJar")
     classpath = files(tasks.named<ShadowJar>("shadowJar").get().archiveFile)
     mainClass.set("com.simiacryptus.cognotik.desktop.CognotikApps")
}


java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

val projectPrefix = if (rootProject.name == "Cognotik") "" else ":Cognotik"
dependencies {
    implementation(project("$projectPrefix:core"))
    implementation(project("$projectPrefix:groovy"))
    implementation(project("$projectPrefix:kotlin"))
    implementation(project("$projectPrefix:webui"))
    implementation(project("$projectPrefix:providers"))
    implementation(project("$projectPrefix:tasklib"))
    implementation(project("$projectPrefix:stdtools"))

    implementation(libs.batik.transcoder)
    implementation(libs.batik.codec)
    implementation(libs.commons.text)
    implementation(libs.aws.s3)
    implementation(libs.aws.kms)
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
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.libs.versions.kotlin.get()}")
    implementation(kotlin("scripting-jsr223"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("scripting-compiler-embeddable"))
    implementation(kotlin("compiler-embeddable"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api) // Version from BOM
    testImplementation(libs.junit.jupiter.params) // Version from BOM
    testRuntimeOnly(libs.junit.jupiter.engine) // Version from BOM
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
}

tasks.war {
    archiveClassifier.set("")
    from(sourceSets.main.get().output)
    from(project.configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
    manifest {
        attributes(
            "Main-Class" to "com.simiacryptus.cognotik.desktop.DaemonClient"
        )
    }
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("all")
    mergeServiceFiles()
    isZip64 = true
    exclude("org/slf4j/impl/**")
    manifest {
        attributes(
            "Main-Class" to "com.simiacryptus.cognotik.desktop.DaemonClient"
        )
    }
}

abstract class JPackageTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations
}

tasks.register("packageDmg", JPackageTask::class) {
    group = "distribution"
    description = "Creates a .dmg package for macOS"
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    dependsOn("shadowJar")
    doLast {
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        val inputDir = layout.buildDirectory.dir("jpackage/input").get().asFile
        if (!inputDir.exists()) {
            inputDir.mkdirs()
        }
        copy {
            from(shadowJarFile)
            into(inputDir)
        }
        // Prepare resources directory for icon
        val resourceDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourceDir.exists()) {
            resourceDir.mkdirs()
        }
        // Convert PNG to ICNS format for macOS
        val iconFile = File(resourceDir, "Cognotik.icns")
        if (!iconFile.exists()) {
            // Create a script to convert PNG to ICNS using macOS tools
            val iconsetDir = File(resourceDir, "Cognotik.iconset")
            iconsetDir.mkdirs()

            // Copy the source PNG icon
            val sourceIcon = layout.projectDirectory.file("src/main/resources/icon-512x512.png").asFile

            // Create different sizes for the iconset
            val sizes = listOf(16, 32, 64, 128, 256, 512, 1024)
            sizes.forEach { size ->
                execOperations.exec {
                    commandLine(
                        "sips",
                        "-z", "$size", "$size",
                        sourceIcon.absolutePath,
                        "--out", "${iconsetDir.absolutePath}/icon_${size}x${size}.png"
                    )
                }
                // Also create @2x versions for Retina displays
                if (size <= 512) {
                    execOperations.exec {
                        commandLine(
                            "sips",
                            "-z", "${size * 2}", "${size * 2}",
                            sourceIcon.absolutePath,
                            "--out", "${iconsetDir.absolutePath}/icon_${size}x${size}@2x.png"
                        )
                    }
                }
            }

            // Convert the iconset to ICNS
            execOperations.exec {
                commandLine(
                    "iconutil",
                    "-c", "icns",
                    iconsetDir.absolutePath,
                    "-o", iconFile.absolutePath
                )
            }

            println("Created ICNS icon at: ${iconFile.absolutePath}")
        }

        execOperations.exec {
            commandLine(
                "jpackage", "--verbose",
                "--type", "dmg",
                "--input", inputDir.path,
                "--main-jar", shadowJarName,
                "--icon", iconFile.path,
                "--main-class", "com.simiacryptus.cognotik.desktop.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", "${project.version}",
                "--copyright", "Copyright © 2026 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
                "--resource-dir", resourceDir.path,
                "--mac-package-name", "Cognotik",
                "--mac-package-identifier", "com.simiacryptus.cognotik",
                "--file-associations", layout.projectDirectory.file("src/packaging/macos/file-associations.properties").asFile.path
            )
        }
    }
}

tasks.register("packageMsi", JPackageTask::class) {
    group = "distribution"
    description = "Creates a .msi package for Windows"
    onlyIf { System.getProperty("os.name").lowercase().contains("windows") }
    dependsOn("shadowJar")
    doLast {
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        val inputDir = layout.buildDirectory.dir("jpackage/input").get().asFile
        if (!inputDir.exists()) {
            inputDir.mkdirs()
        }
        copy {
            from(shadowJarFile)
            into(inputDir)
        }
        val resourceDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourceDir.exists()) {
            resourceDir.mkdirs()
        }
        copy {
            from(layout.projectDirectory.file("src/main/resources/toolbarIcon_128x128.ico"))
            into(resourceDir)
        }
        copy {
            from(layout.projectDirectory.file("src/main/resources/icon-512x512.png"))
            into(resourceDir)
        }

        // Create a directory for additional resources that need to be included in the app directory
        val appResourcesDir = layout.buildDirectory.dir("jpackage/app-resources").get().asFile
        if (!appResourcesDir.exists()) {
            appResourcesDir.mkdirs()
        }

        execOperations.exec {
            commandLine(
                "jpackage",
                "--type", "msi",
                "--input", inputDir.path,
                "--main-jar", shadowJarName,
                "--main-class", "com.simiacryptus.cognotik.desktop.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", project.version.toString().replace("-", "."),
                "--copyright", "Copyright © 2026 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
                "--win-dir-chooser",
                "--win-menu",
                "--win-shortcut",
                "--icon", File(resourceDir, "toolbarIcon_128x128.ico").path,
                "--resource-dir", resourceDir.path,
                "--temp", layout.buildDirectory.dir("jpackage/temp").get().asFile.path,
                "--app-content", appResourcesDir.path,
                "--win-shortcut-prompt",
                "--win-help-url", "https://github.com/SimiaCryptus/Cognotik",
                "--win-update-url", "https://github.com/SimiaCryptus/Cognotik/releases",
                "--install-dir", "Cognotik",
                "--vendor", "SimiaCryptus",
                "--win-shortcut",
                "--win-menu",
                "--win-menu-group", "Cognotik",
                "--win-shortcut-prompt",
            )
        }
    }
}

tasks.register("createAppImage", JPackageTask::class) {
    group = "distribution"
    description = "Creates a self-contained application image for Linux"
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    dependsOn("shadowJar")
    // Define outputs for incremental build
    outputs.dir(layout.buildDirectory.dir("jpackage/linux-image"))
    doLast {
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        val inputDir = layout.buildDirectory.dir("jpackage/input").get().asFile
        if (!inputDir.exists()) {
            inputDir.mkdirs()
        }
        copy {
            from(shadowJarFile)
            into(inputDir)
        }
        execOperations.exec {
            commandLine(
                "jpackage",
                "--type", "app-image",
                "--input", inputDir.path,
                "--main-jar", shadowJarName,
                "--main-class", "com.simiacryptus.cognotik.desktop.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage/linux-image").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", "${project.version}",
                "--icon", layout.projectDirectory.file("src/main/resources/icon-512x512.png").asFile.path,
            )
        }
    }
}

tasks.register("prepareLinuxDesktopFile") {
    group = "build"
    description = "Copies desktop files and icons to the jpackage resource directory for Linux"
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    doLast {

        val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs()
        }

        // Use the package name for the main desktop file.
        val mainDesktopFile = File(resourcesDir, "cognotik.desktop")
        val mainDesktopTemplateFile = layout.projectDirectory.file("src/packaging/linux/main.desktop.template").asFile
        mainDesktopFile.writeText(mainDesktopTemplateFile.readText()) // Copy template content directly

        val installScript = File(resourcesDir, "postinst")

        val postinstTemplateFile = layout.projectDirectory.file("src/packaging/linux/postinst.template").asFile
        // Copy the template directly.
        installScript.writeText(postinstTemplateFile.readText())
        installScript.setExecutable(true)

        val uninstallScript = File(resourcesDir, "prerm")
        val prermTemplateFile = layout.projectDirectory.file("src/packaging/linux/prerm.template").asFile
        uninstallScript.writeText(prermTemplateFile.readText())
        uninstallScript.setExecutable(true)

        println("Created desktop files in jpackage resources directory:")
        println("- ${mainDesktopFile.absolutePath}")
        println("- ${installScript.absolutePath}")
        println("- ${uninstallScript.absolutePath}")
    }
}

// Remove the old packageDeb task that used jpackage --type deb
tasks.findByName("packageDeb")?.enabled = false

tasks.register("buildDebManually", JPackageTask::class) {
    group = "distribution"
    description = "Builds a .deb package manually from the app image"
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    dependsOn("createAppImage", "prepareLinuxDesktopFile")

    doLast {
        val appImageDir = layout.buildDirectory.dir("jpackage/linux-image/Cognotik").get().asFile
        val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        val stagingDir = layout.buildDirectory.dir("deb-staging").get().asFile
        val debOutputDir = layout.buildDirectory.dir("jpackage").get().asFile
        val packageName = "cognotik"
        val version = project.version.toString()
        // Assume amd64, make configurable if needed
        val arch = "amd64"
        val debFileName = "${packageName}_${version}_${arch}.deb"
        val iconSourcePath = layout.projectDirectory.file("src/main/resources/icon-512x512.png")

        // --- 1. Clean and Setup Staging Directory ---
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
        stagingDir.mkdirs()

        val debianDir = File(stagingDir, "DEBIAN").apply { mkdirs() }
        val optDir = File(stagingDir, "opt").apply { mkdirs() }
        val appInstallDir = File(optDir, packageName).apply { mkdirs() }
        val usrDir = File(stagingDir, "usr").apply { mkdirs() }
        val shareDir = File(usrDir, "share").apply { mkdirs() }
        val applicationsDir = File(shareDir, "applications").apply { mkdirs() }
        val iconsDir = File(shareDir, "icons/hicolor/512x512/apps").apply { mkdirs() }

        // --- 2. Copy Application Files ---
        copy {
            from(appImageDir)
            into(appInstallDir)
            exclude("lib/Cognotik.desktop")
        }

        // --- 3. Copy Desktop Files ---
        copy {
            from(resourcesDir) { include("*.desktop") }
            into(applicationsDir)
        }

        // --- 4. Copy Icon ---
        copy {
            from(iconSourcePath)
            into(iconsDir)
            rename { "cognotik.png" } // Ensure consistent naming
        }

        // --- 5. Copy Control Scripts (postinst, prerm) ---
        listOf("postinst", "prerm").forEach { scriptName ->
            val scriptFile = File(resourcesDir, scriptName)
            val destFile = File(debianDir, scriptName)
            copy {
                from(scriptFile)
                into(debianDir)
            }
            // Make scripts executable
            Files.setPosixFilePermissions(
                destFile.toPath(), setOf(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
                )
            )
        }

        // --- 6. Create DEBIAN/control file ---
        val controlFile = File(debianDir, "control")
        // Calculate installed size (approximation)
        val installedSizeKb = Files.walk(stagingDir.toPath())
            .filter { Files.isRegularFile(it) }
            .mapToLong { Files.size(it) }
            .sum() / 1024

        controlFile.writeText(
"""
Package: $packageName
Version: $version
Architecture: $arch
Maintainer: support@simiacryptus.com
Installed-Size: $installedSizeKb
Section: utils
Priority: optional
Description: Cognotik Agentic Toolkit
""".trimIndent() + "\n"
        )

        // --- 7. Build the .deb package ---
        if (!debOutputDir.exists()) debOutputDir.mkdirs()
        execOperations.exec {
            commandLine(
                "dpkg-deb", "--build", stagingDir.absolutePath, File(debOutputDir, debFileName).absolutePath
            )
        }
        println("Successfully built DEB package: ${File(debOutputDir, debFileName).absolutePath}")
    }
}

tasks.register("package") {
    description = "Creates a platform-specific package"
    val os = System.getProperty("os.name").lowercase()
    when {
        os.contains("linux") -> dependsOn("buildDebManually") // Depend on the new manual task
        os.contains("mac") -> dependsOn("packageDmg")
        os.contains("windows") -> dependsOn("packageMsi")
    }
}

tasks.register("packageLinux") {
    description = "Creates a Linux package using the custom flow"
    dependsOn("clean", "buildDebManually") // Depend on the new manual task
}

tasks.named("build") {
    dependsOn(tasks.war)
    dependsOn(tasks.shadowJar)
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
            artifactId = "desktop"
            version = project.version.toString()

            pom {
                name.set("Cognotik Desktop Application")
                description.set("Desktop application module for Cognotik AI framework, providing a GUI and agent management interface")
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

tasks.register("updateVersionFromEnv") {
    val envVersion = System.getenv("COGNOTIK_VERSION")
    if (envVersion != null && envVersion.isNotEmpty()) {
        println("Updating version from environment variable: $envVersion")
        project.version = envVersion
    }
}

tasks.register("verifyRuntimeEnvironment", JPackageTask::class) { // Inherit from JPackageTask to get execOperations
    group = "verification"
    description = "Verifies the runtime environment for packaging"
    doLast {
        val javaHome = System.getProperty("java.home")
        val javaVersion = System.getProperty("java.version")
        println("Java Home: $javaHome")
        println("Java Version: $javaVersion")

        try {
            execOperations.exec { // Use injected execOperations
                commandLine("jpackage", "--version")
                standardOutput = System.out
            }
        } catch (e: Exception) {
            logger.warn("jpackage command not found. Make sure you're using JDK 14+ with jpackage.")
        }
        // Verify dpkg-deb exists for Linux manual build
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            try {
                execOperations.exec { commandLine("dpkg-deb", "--version") } // Use injected execOperations
            } catch (e: Exception) {
                logger.error("dpkg-deb command not found. It is required for building .deb packages manually.")
                throw e
            }
        }
    }
}

tasks.register("debugPackagingEnvironment", JPackageTask::class) {
    group = "verification"
    description = "Prints debug information about the packaging environment"
    doLast {
        println("=== Java Version Information ===")
        execOperations.exec {
            commandLine("java", "-version")
            standardOutput = System.out
            errorOutput = System.out // Java -version outputs to stderr
        }
        println("\n=== JPackage Help Information ===")
        try {
            execOperations.exec {
                commandLine("jpackage", "--help")
                standardOutput = System.out
                errorOutput = System.out
            }
        } catch (e: Exception) {
            println("Error executing jpackage command: ${e.message}")
            println("Make sure you're using JDK 14+ with jpackage available.")
        }
    }
}

// Make packaging tasks depend on the debug task
tasks.named("packageDmg").configure {
    dependsOn("debugPackagingEnvironment")
}
tasks.named("packageMsi").configure {
    dependsOn("debugPackagingEnvironment")
}
tasks.named("buildDebManually").configure {
    dependsOn("debugPackagingEnvironment")
}