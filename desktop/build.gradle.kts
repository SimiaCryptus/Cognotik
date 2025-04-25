import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("cognotik.common-conventions")
    `java-library`
    `maven-publish`
    id("signing")
    alias(libs.plugins.shadow)
    war
    alias(libs.plugins.beryx.runtime)
    application
}



application {
    mainClass.set("com.simiacryptus.cognotik.DaemonClient")
}
// Create a task to run the server directly (for development)
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run the AppServer directly (not as daemon)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.simiacryptus.cognotik.AppServer")
    args = listOf("server")
    // Set JVM arguments for better performance
    jvmArgs = listOf("-Xmx2g", "-XX:+UseG1GC")
}
// Create a task to stop the server
tasks.register<JavaExec>("stopServer") {
    group = "application"
    description = "Stop the running server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.simiacryptus.cognotik.DaemonClient")
    args = listOf("--stop")
}


// Set jpackage type to 'deb' on Linux to avoid 'rpm' errors
runtime {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    modules.set(
        listOf(
            "java.base",
            "java.desktop",
            "java.logging",
            "java.naming",
            "java.net.http",
            "java.sql",
            "jdk.crypto.ec",
            "jdk.jfr",
            "jdk.management",
            "java.base",
            "java.desktop",
            "java.logging",
            "java.naming",
            "java.net.http",
            "java.sql",
            "jdk.crypto.ec",
            "jdk.jfr",
            "jdk.management"
        )
    )
    // Set jpackage type to 'deb' on Linux to avoid 'rpm' errors
    jpackage {
        // Set application metadata
        appVersion = project.version.toString()
//        vendor = "SimiaCryptus"
//        copyright = "Copyright © 2024 SimiaCryptus"
        // Set application metadata
        appVersion = project.version.toString()

        val os = System.getProperty("os.name").lowercase()
        // Only set one type per OS, and avoid setting both --type app-image and --type deb/rpm
        if (os.contains("linux")) {
            imageOptions.clear()
            imageOptions.addAll(listOf("--type", "deb"))
            // Add Linux-specific options
            installerOptions.addAll(
                listOf(
                    "--linux-menu-group", "Development",
                    "--linux-shortcut"
                )
            )
            // Add Linux-specific options
            installerOptions.addAll(
                listOf(
                    "--linux-menu-group", "Development",
                    "--linux-shortcut"
                )
            )
        }
        if (os.contains("mac")) {
            imageOptions.clear()
            imageOptions.addAll(listOf("--type", "dmg"))
            // Add macOS-specific options
            installerOptions.addAll(
                listOf(
                    "--mac-package-identifier", "com.simiacryptus.cognotik",
                    "--mac-package-name", "Cognotik"
                )
            )
            // Add macOS-specific options
            installerOptions.addAll(
                listOf(
                    "--mac-package-identifier", "com.simiacryptus.cognotik",
                    "--mac-package-name", "Cognotik"
                )
            )
        }
        if (os.contains("windows")) {
            imageOptions.clear()
            imageOptions.addAll(listOf("--type", "msi"))
            // Add Windows-specific options
            installerOptions.addAll(
                listOf(
                    "--win-dir-chooser",
                    "--win-menu",
                    "--win-shortcut",
                    "--win-per-user-install"
                )
            )
            // Add Windows-specific options
            installerOptions.addAll(
                listOf(
                    "--win-dir-chooser",
                    "--win-menu",
                    "--win-shortcut",
                    "--win-per-user-install"
                )
            )
        }
    }
}


fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.apache.xmlgraphics:batik-transcoder:1.14")
    implementation("org.apache.xmlgraphics:batik-codec:1.14")

    implementation("org.openjfx:javafx-swing:17")
    implementation("org.openjfx:javafx-graphics:17")
    implementation("org.openjfx:javafx-base:17")

    implementation(libs.commons.text)

    implementation(project(":jo-penai"))
    implementation(project(":core"))
    implementation(project(":groovy"))
    implementation(project(":kotlin"))
    implementation(project(":webui"))

    implementation(libs.aws.sdk)
    implementation("org.jsoup:jsoup:1.19.1")

    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.kotlin)

    implementation(libs.guava)
    implementation(libs.jetty.server)
    implementation(libs.jetty.webapp)
    implementation(libs.jetty.websocket.server)
    implementation(group = "org.apache.httpcomponents.client5", name = "httpclient5-fluent", version = "5.2.3")
    implementation(libs.gson)
    implementation(group = "com.h2database", name = "h2", version = "2.2.224")

    implementation(libs.kotlinx.coroutines)
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version = "0.3.8")
    implementation(kotlin("stdlib"))
    implementation(kotlin("scripting-jsr223"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-jvm-host"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("scripting-compiler-embeddable"))
    implementation(kotlin("compiler-embeddable"))

    implementation(group = "org.scala-lang", name = "scala-library", version = "2.13.9")
    implementation(group = "org.scala-lang", name = "scala-compiler", version = "2.13.9")
    implementation(group = "org.scala-lang", name = "scala-reflect", version = "2.13.9")

    implementation(libs.commons.io)
    implementation(group = "com.vladsch.flexmark", name = "flexmark-all", version = "0.64.8")
    implementation(platform("software.amazon.awssdk:bom:2.27.23"))
    implementation(libs.aws.sdk)
    implementation(group = "software.amazon.awssdk", name = "sso", version = "2.21.29")

    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.logback.core)

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.1")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = "5.10.1")
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.1")
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
    }
}

tasks.war {
    archiveClassifier.set("")
    from(sourceSets.main.get().output)
    from(project.configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
    /*JDK 17*/

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
    // Exclude duplicate SLF4J bindings
    exclude("org/slf4j/impl/**")


    manifest {
        attributes(
            "Main-Class" to "com.simiacryptus.cognotik.DaemonClient"
        )
    }
}

// Platform-specific packaging using jpackage: Only use supported types for each OS
// Helper to install context menu actions for folders
fun installContextMenuAction(os: String) {
    val appName = "Cognotik"
    val appDisplayName = "Open with Cognotik"
    val scriptPath = layout.buildDirectory.dir("jpackage/scripts").get().asFile
    scriptPath.mkdirs()
    when {
        os.contains("windows") -> {
            // Write a .reg file to add context menu for folders
            val regFile = scriptPath.resolve("add_skyenetapps_context_menu.reg")
            regFile.writeText(
                """
                Windows Registry Editor Version 5.00
                
                [HKEY_CLASSES_ROOT\Directory\shell\${appDisplayName}]
                @="${appDisplayName}"
                "Icon"="\"%ProgramFiles%\\$appName\\$appName.exe\""
                
                [HKEY_CLASSES_ROOT\Directory\shell\${appDisplayName}\command]
                @="\"%ProgramFiles%\\$appName\\$appName.exe\" \"%1\""
                [HKEY_CLASSES_ROOT\Directory\shell\Stop Cognotik Server]
                @="Stop Cognotik Server"
                "Icon"="\"%ProgramFiles%\\$appName\\$appName.exe\""
                [HKEY_CLASSES_ROOT\Directory\shell\Stop Cognotik Server\command]
                @="\"%ProgramFiles%\\$appName\\$appName.exe\" \"--stop\""
            """.trimIndent())
            println("Wrote context menu .reg file to: $regFile")
        }

        os.contains("mac") -> {
            // Write a .plist file for a Finder Quick Action (Service)
            val plistFile = scriptPath.resolve("Cognotik.workflow/Contents/info.plist")
            plistFile.parentFile.mkdirs()
            plistFile.writeText(
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>CFBundleIdentifier</key>
              <string>com.simiacryptus.cognotik.workflow</string>
              <key>CFBundleName</key>
              <string>$appDisplayName</string>
              <key>NSServices</key>
              <array>
                <dict>
                  <key>NSMenuItem</key>
                  <dict>
                    <key>default</key>
                    <string>$appDisplayName</string>
                  </dict>
                  <key>NSMessage</key>
                  <string>runWorkflowAsService</string>
                  <key>NSSendFileTypes</key>
                  <array>
                    <string>public.folder</string>
                  </array>
                </dict>
              </array>
            </dict>
            </plist>
        """.trimIndent()
            )
            // Write a shell script to launch the app with the folder path
            val script = scriptPath.resolve("Cognotik.workflow/Contents/document.wflow")
            script.writeText(
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>actions</key>
              <array>
                <dict>
                  <key>action</key>
                  <string>com.apple.cocoa.application.run.shellscript</string>
                  <key>parameters</key>
                  <dict>
                    <key>inputMethod</key>
                    <string>arguments</string>
                    <key>script</key>
                    <string>open -a "$appName" "$1"</string>
                  </dict>
                </dict>
              </array>
              <key>workflowTypeIdentifier</key>
              <string>com.apple.Automator.services</string>
            </dict>
            </plist>
        """.trimIndent()
            )
            // Add a separate Quick Action for stopping the server
            val stopScriptDir = scriptPath.resolve("StopSkyenetApps.workflow/Contents")
            stopScriptDir.mkdirs()
            val stopPlistFile = stopScriptDir.resolve("info.plist")
            stopPlistFile.writeText(
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>CFBundleIdentifier</key>
              <string>com.simiacryptus.cognotik.stop.workflow</string>
              <key>CFBundleName</key>
              <string>Stop Cognotik Server</string>
              <key>NSServices</key>
              <array>
                <dict>
                  <key>NSMenuItem</key>
                  <dict>
                    <key>default</key>
                    <string>Stop Cognotik Server</string>
                  </dict>
                  <key>NSMessage</key>
                  <string>runWorkflowAsService</string>
                </dict>
              </array>
            </dict>
            </plist>
        """.trimIndent()
            )
            val stopScript = stopScriptDir.resolve("document.wflow")
            stopScript.writeText(
                """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>actions</key>
              <array>
                <dict>
                  <key>action</key>
                  <string>com.apple.cocoa.application.run.shellscript</string>
                  <key>parameters</key>
                  <dict>
                    <key>inputMethod</key>
                    <string>arguments</string>
                    <key>script</key>
                    <string>open -a "$appName" --args --stop</string>
                  </dict>
                </dict>
              </array>
              <key>workflowTypeIdentifier</key>
              <string>com.apple.Automator.services</string>
            </dict>
            </plist>
        """.trimIndent()
            )
            println("Wrote stop server Quick Action to: ${stopScript.parentFile}")
            println("Wrote context menu Quick Action to: ${script.parentFile}")
        }


        os.contains("linux") -> {
            // Write a .desktop file for Nautilus/Thunar context menu
            val desktopFile = scriptPath.resolve("cognotik-folder-action.desktop")
            desktopFile.writeText(
                """
          [Desktop Entry]
          Type=Application
          Name=$appDisplayName
          Comment=Open folders with Cognotik
      Icon=/opt/cognotik/lib/icon.png
          Exec=/opt/cognotik/bin/Cognotik "%f"
          MimeType=inode/directory;
          Categories=Development;Utility;TextEditor;
          Terminal=false
          StartupNotify=true
          Actions=OpenFolder;
          [Desktop Action OpenFolder]
          Name=Open Folder with Cognotik
          Exec=/opt/cognotik/bin/Cognotik "%f"
        """.trimIndent()
            )
            // Also create a main application desktop file
            val mainDesktopFile =
                layout.buildDirectory.dir("jpackage/resources").get().asFile.resolve("cognotik.desktop")
            mainDesktopFile.parentFile.mkdirs()
            mainDesktopFile.writeText(
                """
          [Desktop Entry]
          Type=Application
          Name=Cognotik
          Comment=AI-powered application suite
          Icon=/opt/cognotik/lib/icon.png
          Exec=/opt/cognotik/bin/Cognotik %f
          Categories=Development;Utility;TextEditor;
          MimeType=inode/directory;text/plain;
          Terminal=false
          StartupNotify=true
        """.trimIndent()
            )
            println("Created main application .desktop file to: $mainDesktopFile")
            println("Created context menu .desktop file to: $desktopFile")
        }
    }
}

// Use ExecOperations for exec, to avoid deprecation
abstract class JPackageTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: org.gradle.process.ExecOperations
}

tasks.register("packageDmg", JPackageTask::class) {
    group = "distribution"
    description = "Creates a .dmg package for macOS"
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    doLast {
        execOperations.exec {
            commandLine(
                "jpackage",
                "--type", "dmg",
                "--input", layout.buildDirectory.dir("libs").get().asFile.path,
                "--main-jar", "${project.name}-${project.version}-all.jar",
                "--main-class", "com.simiacryptus.cognotik.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", "${project.version}",
                "--copyright", "Copyright © 2024 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
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
        // Get the actual shadow jar file name
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        // Create a temporary directory for the input files
        val inputDir = layout.buildDirectory.dir("jpackage/input").get().asFile
        if (!inputDir.exists()) {
            inputDir.mkdirs()
        }
        // Copy the shadow jar to the input directory
        copy {
            from(shadowJarFile)
            into(inputDir)
        }
        // Create a resource directory for the icon
        val resourceDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourceDir.exists()) {
            resourceDir.mkdirs()
        }
        // Copy the icon to the resource directory
        copy {
            from(layout.projectDirectory.file("src/main/resources/toolbarIcon_128x128.ico"))
            into(resourceDir)
        }
        execOperations.exec {
            commandLine(
                "jpackage",
                "--type", "msi",
                "--input", inputDir.path,
                "--main-jar", shadowJarName,
                "--main-class", "com.simiacryptus.cognotik.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", project.version.toString().replace("-", "."),
                "--copyright", "Copyright © 2024 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
                "--win-dir-chooser",
                "--win-menu",
                "--win-shortcut",
                "--icon", File(resourceDir, "toolbarIcon_128x128.ico").path,
                "--resource-dir", resourceDir.path,
                "--vendor", "SimiaCryptus"
            )
        }
        installContextMenuAction("windows")
    }
}

tasks.register("packageDeb", JPackageTask::class) {
    group = "distribution"
    description = "Creates a .deb package for Linux"
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    dependsOn("shadowJar", "prepareLinuxDesktopFile")
    doLast {
        // Ensure resources directory exists
        val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs()
        }
        // Get the actual shadow jar file name
        val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
        val shadowJarName = shadowJarFile.name
        // Create a post-install script to install the desktop files
        val postinstFile = File(resourcesDir, "postinst")
        postinstFile.writeText(
            """
      #!/bin/sh
      set -e
      # Create desktop files directory if it doesn't exist
      mkdir -p /usr/share/applications
      
      # Copy desktop files from resources to applications directory
      cp "${resourcesDir.absolutePath}/cognotik.desktop" /usr/share/applications/cognotik.desktop
      cp "${resourcesDir.absolutePath}/cognotik-folder-action.desktop" /usr/share/applications/cognotik-folder-action.desktop
      
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
        )
        postinstFile.setExecutable(true)

        // Create a pre-remove script to stop the app and clean up desktop files
        val prermFile = File(resourcesDir, "prerm")
        prermFile.writeText(
            """
      #!/bin/sh
      set -e
      # Stop the running Cognotik server if any
      if [ -x "/opt/cognotik/bin/Cognotik" ]; then
        "/opt/cognotik/bin/Cognotik" --stop || true
      fi
      # Remove desktop files
      rm -f /usr/share/applications/cognotik.desktop
      rm -f /usr/share/applications/cognotik-folder-action.desktop
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
        )
        prermFile.setExecutable(true)


        execOperations.exec {
            commandLine(
                "jpackage",
                "--type", "deb",
                "--input", layout.buildDirectory.dir("libs").get().asFile.path,
                "--main-jar", shadowJarName,
                "--main-class", "com.simiacryptus.cognotik.DaemonClient",
                "--dest", layout.buildDirectory.dir("jpackage").get().asFile.path,
                "--name", "Cognotik",
                "--app-version", "${project.version}",
                "--copyright", "Copyright © 2024 SimiaCryptus",
                "--description", "Cognotik Agentic Toolkit",
                "--linux-menu-group", "Utilities",
                "--resource-dir", layout.buildDirectory.dir("jpackage/resources").get().asFile.path,
                "--linux-deb-maintainer", "support@simiacryptus.com",
                "--linux-shortcut",
                "--linux-app-category", "Development;Utility",
                "--icon", layout.projectDirectory.file("src/main/resources/icon.png").asFile.path,
                "--linux-package-name", "cognotik"
            )
        }
        installContextMenuAction("linux")
    }
}
tasks.register("prepareLinuxDesktopFile") {
    group = "build"
    description = "Copies the .desktop file to the jpackage input directory for Linux"
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    doLast {
        // Create resources directory if it doesn't exist
        val resourcesDir = layout.buildDirectory.dir("jpackage/resources").get().asFile
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs()
        }


        // Create the context menu desktop file
        val contextMenuDesktopFile = File(resourcesDir, "cognotik-folder-action.desktop")
        contextMenuDesktopFile.writeText(
            """
      [Desktop Entry]
      Type=Application
      Name=Open with Cognotik
      Comment=Open folders with Cognotik
      Icon=/opt/cognotik/lib/icon.png
      Exec=/opt/cognotik/bin/Cognotik %f
      MimeType=inode/directory;text/plain;
      Categories=Development;Utility;TextEditor;
      Terminal=false
      StartupNotify=true
      Actions=OpenFolder;
      [Desktop Action OpenFolder]
      Name=Open Folder with Cognotik
      Exec=/opt/cognotik/bin/Cognotik %f
      """.trimIndent()
        )

        // Create the main application desktop file
        val mainDesktopFile = File(resourcesDir, "cognotik.desktop")
        mainDesktopFile.writeText(
            """
      [Desktop Entry]
      Type=Application
      Name=Cognotik
      Comment=AI-powered application suite
      Icon=/opt/cognotik/lib/icon.png
      Exec=/opt/cognotik/bin/Cognotik %f
      Categories=Development;Utility;TextEditor;
      MimeType=inode/directory;text/plain;
      Terminal=false
      StartupNotify=true
      Actions=StopServer;
      [Desktop Action StopServer]
      Name=Stop Cognotik Server
      Exec=/opt/cognotik/bin/Cognotik --stop
      Icon=/opt/cognotik/lib/icon.png
      """.trimIndent()
        )

        // Create a shell script to copy the desktop files to the correct location
        val installScript = File(resourcesDir, "postinst")
        installScript.writeText(
            """
      #!/bin/sh
      set -e
      # Create desktop files directory if it doesn't exist
      mkdir -p /usr/share/applications
      
      # Copy desktop files from resources
      cp "${resourcesDir.absolutePath}/cognotik.desktop" /usr/share/applications/cognotik.desktop
      cp "${resourcesDir.absolutePath}/cognotik-folder-action.desktop" /usr/share/applications/cognotik-folder-action.desktop
      
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
        )
        installScript.setExecutable(true)

        // Create a shell script to remove the desktop files and stop the app
        val uninstallScript = File(resourcesDir, "prerm")
        uninstallScript.writeText(
            """
      #!/bin/sh
      set -e
      # Stop the running Cognotik server if any
      if [ -x "/opt/cognotik/bin/Cognotik" ]; then
        "/opt/cognotik/bin/Cognotik" --stop || true
      fi
      # Remove desktop files
      rm -f /usr/share/applications/cognotik.desktop
      rm -f /usr/share/applications/cognotik-folder-action.desktop
      # Update desktop database
      update-desktop-database /usr/share/applications || true
      exit 0
      """.trimIndent()
        )
        uninstallScript.setExecutable(true)

        println("Created desktop files in jpackage resources directory:")
        println("- ${mainDesktopFile.absolutePath}")
        println("- ${contextMenuDesktopFile.absolutePath}")
        println("- ${installScript.absolutePath}")
        println("- ${uninstallScript.absolutePath}")
    }
}

tasks.register("package") {
    description = "Creates a platform-specific package"
    val os = System.getProperty("os.name").lowercase()
    when {
        os.contains("linux") -> dependsOn("prepareLinuxDesktopFile", "shadowJar", "packageDeb")
        os.contains("mac") -> dependsOn("packageDmg")
        os.contains("windows") -> dependsOn("packageMsi")
    }
}

// Add a task to run the Linux packaging flow
tasks.register("packageLinux") {
    description = "Creates a Linux package using the custom flow"
    dependsOn("clean", "shadowJar", "packageDeb")
}

// Only depend on "package" if not running the beryx jpackage task, to avoid double packaging
tasks.named("build") {
    dependsOn(tasks.war)
    dependsOn(tasks.shadowJar)
    // Remove dependsOn("package") to avoid running both beryx jpackage and custom package tasks
    // dependsOn(tasks.named("package"))
}
// Add a task to update version from environment variable if present
tasks.register("updateVersionFromEnv") {
    val envVersion = System.getenv("SKYENET_VERSION")
    if (envVersion != null && envVersion.isNotEmpty()) {
        println("Updating version from environment variable: $envVersion")
        project.version = envVersion
    }
}
// Make sure the version is updated before packaging
tasks.named("jpackage") {
    dependsOn("jpackageImage")
    dependsOn("updateVersionFromEnv")
}
// Add a task to clean up jpackage output
tasks.register("cleanJpackage") {
    group = "build"
    description = "Cleans jpackage output directories"
    doLast {
        delete(layout.buildDirectory.dir("jpackage"))
    }
}
// Make the clean task depend on cleanJpackage
tasks.named("clean") {
    dependsOn("cleanJpackage")
}
// Add a task to verify the runtime environment
tasks.register("verifyRuntimeEnvironment") {
    group = "verification"
    description = "Verifies the runtime environment for packaging"
    doLast {
        val javaHome = System.getProperty("java.home")
        val javaVersion = System.getProperty("java.version")
        println("Java Home: $javaHome")
        println("Java Version: $javaVersion")
        // Check if jpackage is available
        try {
            @Suppress("DEPRECATION")
            exec {
                commandLine("jpackage", "--version")
                standardOutput = System.out
            }
        } catch (e: Exception) {
            logger.warn("jpackage command not found. Make sure you're using JDK 14+ with jpackage.")
        }
    }
}
// Force jpackageImage to run when needed
tasks.named("jpackageImage") {
    outputs.upToDateWhen {
        val appImageDir = layout.buildDirectory.dir("jpackage/Cognotik").get().asFile
        appImageDir.exists()
    }
}

// Workaround: Disable the beryx jpackage task if running on Linux and not building RPMs
tasks.named("jpackage") {
    dependsOn("jpackageImage")
    doFirst {
        // Check if the app-image exists for any OS, not just Linux
        val appImageDir = layout.buildDirectory.dir("jpackage/Cognotik").get().asFile
        if (!appImageDir.exists()) {
            // Just log a warning, but don't try to modify the task - it will be run by the dependsOn above
            logger.warn("App image directory does not exist: $appImageDir")
            throw GradleException("App image directory not found. Please run the jpackageImage task first.")
        }
    }
}
// Configure jpackageImage task to ensure it creates the app image
tasks.named("jpackageImage") {
    outputs.upToDateWhen { 
        val appImageDir = layout.buildDirectory.dir("jpackage/Cognotik").get().asFile
        appImageDir.exists()
    }
}