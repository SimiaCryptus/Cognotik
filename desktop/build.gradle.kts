import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

plugins {
    `java-library`
    alias(libs.plugins.shadow)
    war
    application
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
    mainClass.set("com.simiacryptus.cognotik.DaemonClient")
}


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":jo-penai"))
    implementation(project(":core"))
    implementation(project(":groovy"))
    implementation(project(":kotlin"))
    implementation(project(":webui"))

    implementation(libs.batik.transcoder)
    implementation(libs.batik.codec)
    implementation(libs.openjfx.swing)
    implementation(libs.openjfx.graphics)
    implementation(libs.openjfx.base)
    implementation(libs.commons.text)
    // implementation(libs.aws.sdk) // Provided by BOM below
    implementation(libs.jsoup)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)
    implementation(libs.guava)
    implementation(libs.jetty.server)
    implementation(libs.jetty.webapp)
    implementation(libs.jetty.websocket.server) // Already in TOML
    implementation(libs.httpclient5.fluent)
    implementation(libs.gson)
    implementation(libs.h2)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.scala.library) // Ensure this is needed if :scala project isn't used directly
    implementation(libs.scala.compiler)
    implementation(libs.scala.reflect)
    implementation(libs.commons.io)
    implementation(libs.flexmark.all)
    implementation(platform("software.amazon.awssdk:bom:2.27.23"))
    implementation(libs.aws.sdk)
    implementation(libs.aws.sso)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)

    implementation(kotlin("stdlib"))
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
            "Main-Class" to "com.simiacryptus.cognotik.DaemonClient"
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
            "Main-Class" to "com.simiacryptus.cognotik.DaemonClient"
        )
    }
}

fun installContextMenuAction(os: String) {
    val appName = "Cognotik"
    val appDisplayName = "Cognotik"
    val scriptPath = layout.buildDirectory.dir("jpackage/scripts").get().asFile
    scriptPath.mkdirs()
    when {
        os.contains("windows") -> {

            val regFile = scriptPath.resolve("add_skyenetapps_context_menu.reg")
            val templateFile = layout.projectDirectory.file("src/packaging/windows/context_menu.reg.template").asFile
            val templateContent = templateFile.readText()
            val regContent = templateContent
                .replace("{{appDisplayName}}", appDisplayName)
                .replace("{{appName}}", appName)
            regFile.writeText(regContent)
            println("Wrote context menu .reg file to: $regFile")

            val batchFile = scriptPath.resolve("install_context_menu.bat")
            batchFile.writeText(
                """
                @echo off
                echo Installing context menu entries...
                regedit /s "%~dp0add_skyenetapps_context_menu.reg"
                echo Context menu entries installed.
                exit /b 0
            """.trimIndent()
            )
            println("Wrote batch file to apply registry entries: $batchFile")
        }

        os.contains("mac") -> {

            val plistFile = scriptPath.resolve("Cognotik.workflow/Contents/info.plist")
            plistFile.parentFile.mkdirs()
            val plistTemplateFile = layout.projectDirectory.file("src/packaging/macos/info.plist.template").asFile
            val plistContent = plistTemplateFile.readText()
                .replace("{{appDisplayName}}", appDisplayName)
            plistFile.writeText(plistContent)

            val script = scriptPath.resolve("Cognotik.workflow/Contents/document.wflow")
            val wflowTemplateFile = layout.projectDirectory.file("src/packaging/macos/document.wflow.template").asFile
            val wflowContent = wflowTemplateFile.readText()
                .replace("{{appName}}", appName)
            script.writeText(wflowContent)

            println("Wrote context menu Quick Action to: ${script.parentFile}")
        }
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
                "--main-class", "com.simiacryptus.cognotik.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", "${project.version}",
                "--copyright", "Copyright © 2024 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
                "--resource-dir", resourceDir.path,
                "--mac-package-name", "Cognotik",
                "--mac-package-identifier", "com.simiacryptus.cognotik",
                "--file-associations", layout.projectDirectory.file("src/packaging/macos/file-associations.properties").asFile.path
            )
        }
        installContextMenuAction("mac")
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

        installContextMenuAction("windows")

        copy {
            from(layout.buildDirectory.dir("jpackage/scripts").get().asFile) {
                include("add_skyenetapps_context_menu.reg")
                include("install_context_menu.bat")
            }
            into(resourceDir)
        }

        val userInstallerScript = File(resourceDir, "Setup_Context_Menu.bat")
        userInstallerScript.writeText(layout.projectDirectory.file("src/packaging/windows/Setup_Context_Menu.bat.template").asFile.readText())
        val uninstallerScript = File(resourceDir, "Uninstall_Context_Menu.bat")
        uninstallerScript.writeText(layout.projectDirectory.file("src/packaging/windows/Uninstall_Context_Menu.bat.template").asFile.readText())
        val removeRegFile = File(resourceDir, "remove_context_menu.reg")
        val removeRegTemplateFile =
            layout.projectDirectory.file("src/packaging/windows/remove_context_menu.reg.template").asFile
        val removeRegContent = removeRegTemplateFile.readText().replace("{{appDisplayName}}", "Cognotik")
        removeRegFile.writeText(removeRegContent)

        // Create a directory for additional resources that need to be included in the app directory
        val appResourcesDir = layout.buildDirectory.dir("jpackage/app-resources").get().asFile
        if (!appResourcesDir.exists()) {
            appResourcesDir.mkdirs()
        }
        // Copy the registry file and batch script to the app resources directory
        copy {
            from(resourceDir) {
                include("add_skyenetapps_context_menu.reg")
                include("Setup_Context_Menu.bat")
                include("remove_context_menu.reg")
                include("Uninstall_Context_Menu.bat")
            }
            into(appResourcesDir)
        }


        execOperations.exec {
            commandLine(
                "jpackage",
                "--type",
                "msi",
                "--input",
                inputDir.path,
                "--main-jar",
                shadowJarName,
                "--main-class",
                "com.simiacryptus.cognotik.DaemonClient",
                "--dest",
                layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name",
                "Cognotik",
                "--app-version",
                project.version.toString().replace("-", "."),
                "--copyright",
                "Copyright © 2025 SimiaCryptus",
                "--description",
                "Cognotik Agentic Toolkit",
                "--win-dir-chooser",
                "--win-menu",
                "--win-shortcut",
                "--icon",
                File(resourceDir, "toolbarIcon_128x128.ico").path,
                "--resource-dir",
                resourceDir.path,
                "--temp",
                layout.buildDirectory.dir("jpackage/temp").get().asFile.path,
                "--app-content",
                appResourcesDir.path,
                "--win-shortcut-prompt",
                "--win-help-url",
                "https://github.com/SimiaCryptus/Cognotik",
                "--win-update-url",
                "https://github.com/SimiaCryptus/Cognotik/releases",
                "--file-associations",
                layout.projectDirectory.file("src/packaging/windows/file-associations.properties").asFile.path,
                "--install-dir",
                "Cognotik",
                "--vendor",
                "SimiaCryptus",
                "--win-shortcut",
                "--win-menu",
                "--win-menu-group",
                "Cognotik",
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
                "--main-class", "com.simiacryptus.cognotik.DaemonClient",
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
            // Ensure executables keep their permissions
            eachFile(Action<FileCopyDetails> {
                if (Files.isExecutable(file.toPath())) {
                    mode = mode or 0b001_001_001 // Add execute permissions ugo+x
                }
            })
            // Remove the auto-generated .desktop file to avoid duplication
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
             AI-powered application suite for various tasks.
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