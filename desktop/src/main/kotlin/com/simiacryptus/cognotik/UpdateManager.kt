package com.simiacryptus.cognotik

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.simiacryptus.cognotik.SystemTrayManager.Companion.confirm
import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import org.slf4j.LoggerFactory
import scala.reflect.internal.util.NoPosition.showError
import java.awt.BorderLayout
import java.awt.BorderLayout.*
import java.awt.Desktop
import java.awt.Dimension
import java.awt.MenuItem
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.BorderFactory.createEmptyBorder
import javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import kotlin.system.exitProcess


object UpdateManager {
    private val log = LoggerFactory.getLogger(UpdateManager::class.java)
    private const val REPO_OWNER = "SimiaCryptus"
    private const val REPO_NAME = "Cognotik"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
    private val httpClient = HttpClient.newBuilder().build()
    private val gson = Gson()

    // Cache the latest release to avoid repeated API calls
    private var cachedLatestRelease: Release? = null
    private var lastCheckTime: Long = 0
   private var cachedLatestVersion: Version? = null
   private var lastVersionCheckTime: Long = 0
    private const val CACHE_DURATION_MS = 3600000 // 1 hour

    data class Release(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("name") val name: String,
        @SerializedName("html_url") val htmlUrl: String,
        @SerializedName("assets") val assets: List<Asset>
    )

    data class Asset(
        @SerializedName("name") val name: String,
        @SerializedName("browser_download_url") val downloadUrl: String,
        @SerializedName("content_type") val contentType: String
    )

    fun doUpdate() {
        log.info("Starting update process")
        var progressDialog: JDialog? = null // Keep track of the dialog
        try {
            log.debug("Fetching latest release information")
            val latestRelease = fetchLatestRelease() ?: throw IOException("No releases found")
            log.info("Latest release found: ${latestRelease.name} (${latestRelease.tagName})")

            val currentOs = detectOperatingSystem()
            log.info("Detected operating system: $currentOs")

            val assetToDownload = findAssetForCurrentOs(latestRelease, currentOs)
                ?: throw IOException("No compatible download found for $currentOs")
            log.info("Found compatible asset for download: ${assetToDownload.name}")

            // Create and show progress dialog
            val canceled = AtomicBoolean(false)
            log.debug("Creating progress dialog for download")
            val message = "Downloading ${assetToDownload.name}..."
            val dialogHolder = arrayOfNulls<JDialog>(1)


            val progressBarHolder = arrayOfNulls<JProgressBar>(1)

            // Ensure dialog creation and showing happens on the EDT
            val semaphore = java.util.concurrent.Semaphore(0)
            SwingUtilities.invokeLater {
                val panel = JPanel(BorderLayout(10, 10)).apply {
                    this.border = createEmptyBorder(10, 10, 10, 10)
                }

                val progressBar = JProgressBar(0, 100).apply {
                    this.isIndeterminate = false
                    this.value = 0
                    this.preferredSize = Dimension(300, 20)
                    this.name = "downloadProgressBar" // Keep name for potential testing/lookup
                }
                progressBarHolder[0] = progressBar // Store progressBar reference

                dialogHolder[0] = JDialog().apply {
                    this.title = "Cognotik Update"
                    this.isModal = false // Keep non-modal for cancel button interaction
                    this.defaultCloseOperation = DISPOSE_ON_CLOSE

                    panel.add(JLabel(message), NORTH)
                    panel.add(progressBar, CENTER)
                    panel.add(JPanel().apply {
                        add(JButton("Cancel").apply {
                            addActionListener {
                                log.info("User canceled the download")
                                canceled.set(true)
                                // Optionally disable button or close dialog immediately on cancel
                                // dialogHolder[0]?.dispose() // Example: close dialog on cancel
                            }
                        })
                    }, SOUTH)

                    this.contentPane = panel
                    pack()
                    setLocationRelativeTo(null) // Center on screen
                    log.debug("Progress dialog created and configured, making visible")
                    this.isVisible = true // Show the dialog
                }
                semaphore.release()
            }
            semaphore.acquire()
            progressDialog = dialogHolder[0] // Assign to outer variable
            val progressBar = progressBarHolder[0]
                ?: throw IllegalStateException("Progress bar was not initialized") // Should not happen

            // Download the asset
            log.info("Starting download of asset: ${assetToDownload.name}")
            val tempFile = downloadAsset(assetToDownload, progressBar, canceled)


            log.info("Download completed successfully to: ${tempFile.absolutePath}")
            // Close the progress dialog as download is done
            progressDialog?.dispose()
            progressDialog = null // Nullify to avoid disposing again in finally

            // Launch the installer and exit the current application
            log.info("Launching installer and preparing to exit application")
            launchInstallerAndExit(tempFile, currentOs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt() // Re-interrupt the thread
            log.warn("Update process interrupted", e)
        } catch (e: Exception) {
            log.error("Update failed", e)
            log.debug("Stack trace for update failure", e)
            JOptionPane.showMessageDialog(
                null, "Update failed: ${e.message}", "Update Error", JOptionPane.ERROR_MESSAGE
            )
            throw e
        } finally {
            // Ensure dialog is disposed if an error occurred after it was created
            progressDialog?.dispose()
        }
    }

    data class Version(
        val version: String
    ) {
        override fun toString() = version
        fun greaterThan(other: Version) = greaterThan(other.version)
        fun greaterThan(other: String): Boolean {
            log.debug("Comparing versions: $version > $other")
            val thisParts = version.split('.').map { it.toIntOrNull() ?: 0 }
            val otherParts = other.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(thisParts.size, otherParts.size)) {
                val thisPart = if (i < thisParts.size) thisParts[i] else 0
                val otherPart = if (i < otherParts.size) otherParts[i] else 0
                if (thisPart > otherPart) return true
                if (thisPart < otherPart) return false
            }
            return false
        }
    }

    val currentVersion: Version
        get() {
            val version = System.getProperty("jpackage.app-version", "0.0.0").removePrefix("v")
            log.info("Current application version: $version")
            return Version(version)
        }

    val latestVersion: Version
        get() {
            log.debug("Retrieving latest version information")
           val now = System.currentTimeMillis()
           if (cachedLatestVersion != null && now - lastVersionCheckTime < CACHE_DURATION_MS) {
               log.debug("Using cached version information (age: ${(now - lastVersionCheckTime) / 1000} seconds)")
               return cachedLatestVersion!!
           }
           
            try {
                val release = fetchLatestRelease()
                return if (release != null) {
                    val version = release.tagName.removePrefix("v")
                    log.info("Latest available version: $version")
                   val versionObj = Version(version)
                   cachedLatestVersion = versionObj
                   lastVersionCheckTime = now
                   versionObj
                } else {
                    log.warn("Could not determine latest version, using current version as fallback")
                   val currentVer = currentVersion
                   cachedLatestVersion = currentVer
                   lastVersionCheckTime = now
                   currentVer
                }
            } catch (e: Exception) {
                log.error("Failed to fetch latest version", e)
                log.debug("Stack trace for version fetch failure", e)
               val currentVer = currentVersion
               cachedLatestVersion = currentVer
               lastVersionCheckTime = now
               return currentVer
            }
        }

    private fun fetchLatestRelease(): Release? {
        log.debug("Fetching latest release from GitHub API")
        val now = System.currentTimeMillis()
        if (cachedLatestRelease != null && now - lastCheckTime < CACHE_DURATION_MS) {
            log.debug("Using cached release information (age: ${(now - lastCheckTime) / 1000} seconds)")
            return cachedLatestRelease
        }

        try {
            log.debug("Building HTTP request to GitHub API")
            val request = HttpRequest.newBuilder().uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/vnd.github.v3+json").GET().build()
            log.debug("Sending request to GitHub API")

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.error("GitHub API returned error status code: ${response.statusCode()}")
                log.debug("Response body: ${response.body()}")
                return null
            }
            log.debug("Parsing GitHub API response")

            val releases = gson.fromJson(response.body(), Array<Release>::class.java)
            if (releases.isEmpty()) {
                log.warn("No releases found in GitHub API response")
                return null
            }
            log.info("Found ${releases.size} releases, using latest: ${releases[0].name} (${releases[0].tagName})")

            cachedLatestRelease = releases[0]
            lastCheckTime = now
            return cachedLatestRelease
        } catch (e: Exception) {
            log.error("Error fetching releases from GitHub", e)
            log.debug("Stack trace for GitHub API error", e)
            return null
        }
    }

    private fun detectOperatingSystem(): String {
        log.debug("Detecting operating system")
        val os = System.getProperty("os.name").lowercase()
        val detectedOs = when {
            os.contains("win") -> "windows"
            os.contains("mac") -> "mac"
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> "linux"
            else -> "unknown"
        }
        log.debug("Detected OS: $detectedOs (from: ${System.getProperty("os.name")})")
        return detectedOs
    }

    private fun findAssetForCurrentOs(release: Release, os: String): Asset? {
        log.debug("Finding compatible asset for OS: $os")
        log.debug("Available assets: ${release.assets.joinToString { it.name }}")

        val asset = release.assets.find { asset ->
            when (os) {
                "windows" -> asset.name.endsWith(".msi") || asset.name.endsWith(".exe")
                "mac" -> asset.name.endsWith(".dmg") || asset.name.endsWith(".pkg")
                "linux" -> asset.name.endsWith(".deb") || asset.name.endsWith(".rpm") || asset.name.endsWith(".AppImage")
                else -> false
            }
        }
        if (asset != null) {
            log.debug("Found compatible asset: ${asset.name}")
        } else {
            log.warn("No compatible asset found for OS: $os")
        }
        return asset
    }

    private fun downloadAsset(asset: Asset, progressBar: JProgressBar, canceled: AtomicBoolean): File {
        log.debug("Creating temporary file for download")
        val tempFile = File("cognotik-update-${UUID.randomUUID().toString().split("-").first()}-${asset.name}")
        log.info("Downloading update from ${asset.downloadUrl} to ${tempFile.absolutePath}")

        // Create a connection to get file size
        log.debug("Opening connection to download URL")
        val connection = URL(asset.downloadUrl).openConnection()
        val fileSize = connection.contentLengthLong
        log.debug("File size to download: $fileSize bytes")

        connection.getInputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            var lastLoggedProgress = 0

            // Use output stream to write to file
            log.debug("Starting file download loop")
            tempFile.outputStream().use { output ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Check for cancellation before writing
                    if (canceled.get()) {
                        log.warn("Download canceled by user.")
                        throw IOException("Download canceled by user") // Throw exception to stop
                    }

                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Update progress in UI thread
                    if (fileSize > 0) {
                        val progress = (totalBytesRead * 100 / fileSize).toInt()
                        // Log progress at 10% intervals
                        if (progress / 10 > lastLoggedProgress / 10) {
                            log.debug("Download progress: $progress% ($totalBytesRead/$fileSize bytes)")
                            lastLoggedProgress = progress
                        }

                        SwingUtilities.invokeLater {
                            progressBar.let { bar ->
                                if (bar.isDisplayable) {
                                    bar.value = progress
                                }
                            }
                        }
                    }
                }
            }
        } // Input stream is closed automatically by 'use'
        log.info("Download completed successfully: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
        return tempFile
    }

    private fun loadScriptTemplate(resourcePath: String): String {
        log.debug("Loading script template from resource: {}", resourcePath)
        val inputStream = UpdateManager::class.java.getResourceAsStream(resourcePath)
            ?: throw IOException("Cannot find resource: $resourcePath")
        return inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }


    private fun launchInstallerAndExit(installerFile: File, os: String) {
        log.info("Preparing to launch installer for OS: $os")
        try {
            when (os) {
                "windows" -> {
                    log.debug("Windows update process starting")
                    // For Windows, first uninstall the current version, then install the new one
                    val appName = "Cognotik" // Ensure this matches the installer's application name
                    // Get the product code using wmic
                    log.debug("Getting product code for $appName")
                    // Show confirmation dialog
                    log.debug("Showing confirmation dialog to user")
                    val confirm = JOptionPane.showConfirmDialog(
                        null,
                        "The application will now close and update to the latest version.\n" + "1. The current version will be uninstalled\n" + "2. The new version will be installed\n\n" + "Do you want to continue?",
                        "Update Confirmation",
                        JOptionPane.YES_NO_OPTION
                    )
                    if (confirm != JOptionPane.YES_OPTION) {
                        log.info("User canceled the update process")
                        return
                    }
                    // Create a PowerShell script file to execute the uninstall and install commands
                    log.debug("Creating PowerShell script for update process")
                    val scriptFile = File.createTempFile("cognotik-update-", ".ps1")
                    val installerPath = installerFile.absolutePath
                    // Construct the PowerShell script content carefully
                    val template = loadScriptTemplate("/scripts/update/windows_update.ps1.template")
                    // Substitute the Kotlin variables into the PowerShell script template
                    val finalSrc = template
                        .replace("@@APP_NAME@@", appName)
                        .replace("@@INSTALLER_PATH@@", installerPath)


                    log.debug("Writing to PowerShell script file: ${scriptFile.absolutePath}: \n${finalSrc.indent("  ")}")
                    scriptFile.writeText(finalSrc)

                    // Execute the PowerShell script and exit
                    // Using -NoProfile for faster startup and -ExecutionPolicy Bypass to avoid issues
                    // Start-Process can be used within PowerShell itself for elevation, but we'll launch directly first.
                    // We need to ensure PowerShell can find the script path correctly, especially if it contains spaces.
                    val process = ProcessBuilder(
                        "cmd",
                        "/c", // Run command and terminate
                        "start", // Start a separate window/process
                        "\"Cognotik Update\"", // Title for the new window (optional but good practice)
                        "powershell.exe",
                        "-WindowStyle", "Normal",
                        "-NoExit",
                        "-File", scriptFile.absolutePath
                    ).redirectErrorStream(true).start()
                    log.info("Update PowerShell process started with PID: ${process.pid()}")

                    // Schedule application exit after a short delay
                    CompletableFuture.runAsync {
                        log.info("Scheduling application exit in 1 second")
                        TimeUnit.SECONDS.sleep(1)
                        log.info("Exiting application for update")
                        exitProcess(0)
                    }
                }

                "mac" -> {
                    log.debug("macOS update process starting")

                    // For Mac, we need to uninstall the current version first
                    val appName = "Cognotik"

                    // Create a script to handle the update
                    log.debug("Creating update script for macOS")
                    val scriptFile = File.createTempFile("cognotik-update-", ".sh")
                    scriptFile.setExecutable(true)
                    
                    // Write the update script
                    val installerPath = installerFile.absolutePath
                    val scriptPath = scriptFile.absolutePath
                    
                    val template = loadScriptTemplate("/scripts/update/mac_update.sh.template")
                    val finalSrc = template
                        .replace("@@INSTALLER_PATH@@", installerPath)
                        .replace("@@SCRIPT_PATH@@", scriptPath)
                        .replace("@@APP_NAME@@", appName)
                    
                    log.debug("Writing to macOS update script file: ${scriptFile.absolutePath}: \n${finalSrc.indent("  ")}")
                    scriptFile.writeText(finalSrc)
                    
                    // Show confirmation dialog
                    log.debug("Showing update confirmation to user")
                    JOptionPane.showMessageDialog(
                        null,
                        "The application will now close and update to the latest version.\n" +
                        "You may need to enter your password for the installation process.",
                        "Update Confirmation",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    
                    log.info("Executing update script: ${scriptFile.absolutePath}")
                    // Execute the update script in a new terminal
                    ProcessBuilder("open", "-a", "Terminal", scriptFile.absolutePath).start()

                    // Schedule application exit
                    CompletableFuture.runAsync {
                        log.info("Scheduling application exit in 2 seconds")
                        TimeUnit.SECONDS.sleep(2)
                        log.info("Exiting application for update")
                        exitProcess(0)
                    }
                }

                "linux" -> {
                    log.debug("Linux update process starting")

                    // For Linux, create a script to handle the update
                    val scriptFile = File.createTempFile("cognotik-update-", ".sh")

                    // Make the script executable
                    log.debug("Setting execute permissions on update script")
                    scriptFile.setExecutable(true)

                    // Write the update script
                    log.debug("Creating update script for installer: ${installerFile.name}")
                    val scriptTemplatePath: String
                    val installerPath = installerFile.absolutePath
                    val scriptPath = scriptFile.absolutePath

                    if (installerFile.name.endsWith(".deb")) {
                        scriptTemplatePath = "/scripts/update/linux_update_deb.sh.template"
                    } else if (installerFile.name.endsWith(".AppImage")) {
                        scriptTemplatePath = "/scripts/update/linux_update_appimage.sh.template"
                    } else {
                        log.error("Unsupported Linux installer type: ${installerFile.name}")
                        throw IOException("Unsupported Linux installer type: ${installerFile.name}")
                    }
                    val template = loadScriptTemplate(scriptTemplatePath)
                    val finalSrc = template
                        .replace("@@INSTALLER_PATH@@", installerPath)
                        .replace("@@SCRIPT_PATH@@", scriptPath)
                    scriptFile.writeText(finalSrc)

                    // Show confirmation dialog
                    log.debug("Showing update confirmation to user")
                    JOptionPane.showMessageDialog(
                        null,
                        "The application will now close and update to the latest version.\n" + "You may need to enter your password for the uninstallation and installation process.",
                        "Update Confirmation",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    log.info("Executing update script: ${scriptFile.absolutePath}")
                    // Execute the update script in a new terminal
                    ProcessBuilder("x-terminal-emulator", "-e", scriptFile.absolutePath).start()

                    // Schedule application exit
                    CompletableFuture.runAsync {
                        log.info("Scheduling application exit in 2 seconds")
                        TimeUnit.SECONDS.sleep(2)
                        log.info("Exiting application for update")
                        exitProcess(0)
                    }
                }

                else -> {
                    log.warn("Unknown OS: $os - attempting to open installer directly")
                    // For unknown OS, just open the file and hope for the best
                    Desktop.getDesktop().open(installerFile)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to launch installer", e)
            log.debug("Stack trace for installer launch failure", e)
            JOptionPane.showMessageDialog(
                null,
                "Failed to launch installer: ${e.message}\n" + "The installer has been downloaded to: ${installerFile.absolutePath}",
                "Installation Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    fun checkUpdate() {
        if(latestVersion.greaterThan(currentVersion)) {
            confirm("Update to ${latestVersion.version}?") {
                Thread {
                    try {
                        doUpdate()
                    } catch (e: Exception) {
                        log.error("Failed to update: ${e.message}", e)
                        showError("Failed to update")
                    }
                }.start()
            }
        }
    }
}