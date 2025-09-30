package com.simiacryptus.cognotik.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.simiacryptus.cognotik.PluginStartupActivity
import com.simiacryptus.cognotik.PluginStartupActivity.Companion.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

suspend fun Project.showDocument(welcomeFile: String): Boolean {
    val resource = PluginStartupActivity::class.java.classLoader.getResource(welcomeFile)
    if (resource == null) {
        log.error("Welcome page resource not found: $welcomeFile")
        return true
    }
    var virtualFile = resource.let { VirtualFileManager.getInstance().findFileByUrl(it.toString()) }
    if (virtualFile == null) try {
        val path = resource.toURI()?.let { Paths.get(it) }
        virtualFile = path?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
    } catch (e: Exception) {
        log.debug("Error opening welcome page", e)
    }
    if (virtualFile == null) {
        try {
            log.debug("Creating temporary file for welcome page")
            val tempFile =
                withContext(Dispatchers.IO) {
                    File.createTempFile(
                        welcomeFile.substringBefore("."),
                        "." + welcomeFile.substringAfter(".")
                    )
                }
            tempFile.deleteOnExit()
            resource.openStream()?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempFile.toPath())
            log.debug("Welcome page temporary file created: ${tempFile.absolutePath}")
        } catch (e: Exception) {
            log.error("Error opening welcome page", e)
        }
    }
    virtualFile?.let {
        try {
            log.debug("Opening welcome page in editor")
            ApplicationManager.getApplication().invokeLater {
                FileEditorManager.getInstance(this).openFile(it, true).forEach { editor ->
                    try {
                        editor::class.declaredMembers.filter { it.name == "setLayout" }.forEach { member ->
                            member.isAccessible = true
                            member.call(editor, TextEditorWithPreview.Layout.SHOW_PREVIEW)
                            log.debug("Successfully set preview layout for welcome page")
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to set preview layout for welcome page editor", e)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Error opening welcome page", e)
        }
    } ?: log.error("Welcome page not found")
    return false
}