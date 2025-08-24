package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File

class SessionFileServlet(val dataStorage: StorageInterface) : FileServlet() {

    private fun getDirs(req: HttpServletRequest): List<File> {
        val pathSegments = parsePath(req.pathInfo ?: "/")
        val session = Session(pathSegments.first())
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val sessionDir = dataStorage.getSessionDir(user, session)
        val dataDir = dataStorage.getDataDir(user, session)
        return if (sessionDir.absolutePath != dataDir.absolutePath) {
            listOf(sessionDir, dataDir)
        } else {
            listOf(sessionDir)
        }
    }

    override fun getDir(req: HttpServletRequest): File {
        // Return the first directory as default for compatibility
        return getDirs(req).first()
    }

    override fun getFile(dir: File, pathSegments: List<String>, req: HttpServletRequest): File {
        val relativePath = pathSegments.drop(1).joinToString("/")
        // Try to find the file in any of the directories
        for (directory in getDirs(req)) {
            val file = File(directory, relativePath)
            if (file.exists()) {
                return file
            }
        }
        // If not found, return from the first directory (default behavior)
        return File(dir, relativePath)
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received GET request for path: ${req.pathInfo ?: req.servletPath}")
        val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
        val dirs = getDirs(req)
        log.info("Serving directories: ${dirs.map { it.absolutePath }}")

        // For file operations, use the parent class logic with our custom getFile
        val file = getFile(dirs.first(), pathSegments, req)
        log.info("Resolved file path: ${file.absolutePath}")

        when {
            !file.exists() -> {
                // Check if it's a directory that exists in any of the dirs
                val relativePath = pathSegments.drop(1).joinToString("/")
                val dirExists = dirs.any { File(it, relativePath).exists() && File(it, relativePath).isDirectory }

                if (dirExists && !req.pathInfo?.endsWith("/").let { it == true }) {
                    log.info("Redirecting to directory path: ${req.requestURI + "/"}")
                    resp.sendRedirect(req.requestURI + "/")
                } else if (dirExists) {
                    // List combined directory contents
                    listCombinedDirectory(req, resp, dirs, pathSegments)
                } else {
                    log.warn("File not found: ${file.absolutePath}")
                    resp.status = HttpServletResponse.SC_NOT_FOUND
                    resp.writer.write("File not found")
                }
            }

            file.isFile -> {
                // Use parent class file serving logic
                super.doGet(req, resp)
            }

            req.pathInfo?.endsWith("/") == false -> {
                log.info("Redirecting to directory path: ${req.requestURI + "/"}")
                resp.sendRedirect(req.requestURI + "/")
            }

            else -> {
                // List combined directory contents
                listCombinedDirectory(req, resp, dirs, pathSegments)
            }
        }
    }

    private fun listCombinedDirectory(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        dirs: List<File>,
        pathSegments: List<String>
    ) {
        log.info("Listing combined directory contents")
        resp.contentType = "text/html"
        resp.characterEncoding = "UTF-8"
        resp.status = HttpServletResponse.SC_OK

        val currentPathString = pathSegments.drop(1).joinToString("/")
        val servletPathBase = req.contextPath + req.servletPath.removeSuffix("/*").removeSuffix("/") +
                "/" + req.pathInfo.split("/").firstOrNull { it.isNotBlank() }

        // Combine files and folders from all directories
        val allFiles = mutableSetOf<String>()
        val allFolders = mutableSetOf<String>()

        val relativePath = pathSegments.drop(1).joinToString("/")
        dirs.forEach { baseDir ->
            val targetDir = if (relativePath.isBlank()) baseDir else File(baseDir, relativePath)
            if (targetDir.exists() && targetDir.isDirectory) {
                targetDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        allFiles.add(file.name)
                    } else {
                        allFolders.add(file.name)
                    }
                }
            }
        }

        val files = allFiles.sorted().joinToString("") {
            """<li><a class="item-link" href="$it"><span class="icon">📄</span>$it</a></li>"""
        }

        val folders = allFolders.sorted().joinToString("") {
            """<li><a class="item-link" href="$it/"><span class="icon">📁</span>$it</a></li>"""
        }

        resp.writer.write(
            directoryHTML(
                currentPathString,
                servletPathBase,
                getZipLink(req, currentPathString),
                folders,
                files
            )
        )
    }

    private fun directoryHTML(
        currentPath: String,
        servletBaseHref: String,
        zipLink: String,
        folders: String,
        files: String
    ): String {
        // Use reflection or duplicate the parent class's directoryHTML method
        // Since it's private in the parent, we need to duplicate it
        return """
            |<!DOCTYPE html>
            |<html lang="en">
            |<head>
            |    <meta charset="UTF-8">
            |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
            |    <title>Directory Listing: /$currentPath</title>
            |    <style>
            |        body {
            |            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
            |            background-color: #f0f2f5;
            |            color: #1c1e21;
            |            margin: 0;
            |            padding: 0;
            |            line-height: 1.5;
            |        }
            |        .navbar {
            |            background-color: #ffffff;
            |            padding: 1rem 1.5rem;
            |            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
            |            margin-bottom: 1.5rem;
            |            display: flex;
            |            align-items: center;
            |            justify-content: space-between;
            |            flex-wrap: wrap;
            |        }
            |        .navbar-title {
            |            font-size: 1.4rem;
            |            font-weight: 600;
            |            color: #343a40;
            |            margin-right: 1rem;
            |        }
            |        .zip-link {
            |            display: inline-block;
            |            padding: 0.5rem 1rem;
            |            font-size: 0.9rem;
            |            font-weight: 500;
            |            color: #fff;
            |            background-color: #0d6efd;
            |            border: none;
            |            border-radius: 0.25rem;
            |            text-decoration: none;
            |            transition: background-color 0.15s ease-in-out;
            |            white-space: nowrap;
            |        }
            |        .zip-link:hover {
            |            background-color: #0b5ed7;
            |        }
            |        .container {
            |            max-width: 960px;
            |            margin: 0 auto;
            |            padding: 0 1rem 1.5rem 1rem;
            |        }
            |        .breadcrumb-nav {
            |            margin-bottom: 1.5rem;
            |            padding: 0.75rem 1rem;
            |            background-color: #ffffff;
            |            border-radius: 0.25rem;
            |            box-shadow: 0 1px 2px rgba(0,0,0,0.04);
            |        }
            |        .breadcrumb {
            |            padding: 0; margin:0; list-style:none; display:flex; flex-wrap:wrap;
            |        }
            |        .section {
            |            background-color: #ffffff;
            |            border: 1px solid #dee2e6;
            |            border-radius: 0.375rem;
            |            margin-bottom: 1.5rem;
            |            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
            |        }
            |        .section-header {
            |            padding: 0.75rem 1.25rem;
            |            margin-bottom: 0;
            |            background-color: #f8f9fa;
            |            border-bottom: 1px solid #dee2e6;
            |            border-top-left-radius: calc(0.375rem - 1px);
            |            border-top-right-radius: calc(0.375rem - 1px);
            |        }
            |        .section-title {
            |            font-size: 1.2rem;
            |            font-weight: 500;
            |            color: #343a40;
            |            margin: 0;
            |        }
            |        .section-content {
            |            padding: 1.25rem;
            |        }
            |        .item-list {
            |            list-style: none;
            |            padding: 0;
            |            margin: 0;
            |        }
            |        .item-list li {
            |            margin-bottom: 0.25rem;
            |        }
            |        .item-list li:last-child { margin-bottom: 0; }
            |        .item-link {
            |            color: #0d6efd;
            |            text-decoration: none;
            |            display: flex;
            |            align-items: center;
            |            padding: 0.45rem 0.75rem;
            |            border-radius: 0.25rem;
            |            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
            |        }
            |        .item-link:hover {
            |            background-color: #e9ecef;
            |            color: #0a58ca;
            |        }
            |        .item-link .icon {
            |            margin-right: 0.7em;
            |            width: 1.2em; 
            |            text-align: center;
            |            color: #495057;
            |        }
            |        .item-link:hover .icon { color: #0a58ca; }
            |        .empty-state {
            |            color: #6c757d;
            |            padding: 0.5rem 0.75rem;
            |            font-style: italic;
            |        }
            |    </style>
            |</head>
            |<body>
            |    <div class="navbar">
            |        <span class="navbar-title"> File Browser</span>
            |        ${if (zipLink.isNotBlank()) """<a href="$zipLink" class="zip-link">Download Current Directory as ZIP</a>""" else ""}
            |    </div>
            |    <div class="container">
            |        <nav class="breadcrumb-nav" aria-label="breadcrumb">
            |           <ol class="breadcrumb">
            |               ${generateBreadcrumbs(currentPath, servletBaseHref)}
            |           </ol>
            |        </nav>
            |
            |        <div class="section">
            |            <div class="section-header"><h2 class="section-title">Folders</h2></div>
            |            <div class="section-content">
            |                ${if (folders.isBlank()) "<p class=\"empty-state\">No sub-folders found.</p>" else "<ul class=\"item-list\">$folders</ul>"}
            |            </div>
            |        </div>
            |
            |        <div class="section">
            |            <div class="section-header"><h2 class="section-title">Files</h2></div>
            |            <div class="section-content">
            |                ${if (files.isBlank()) "<p class=\"empty-state\">No files found.</p>" else "<ul class=\"item-list\">$files</ul>"}
            |            </div>
            |        </div>
            |    </div>
            |</body>
            |</html>
        """.trimMargin()
    }

    private fun generateBreadcrumbs(currentPath: String, servletBaseHref: String): String {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        val breadcrumbs = StringBuilder()
        val rootLink = if (servletBaseHref.endsWith("/")) servletBaseHref else "$servletBaseHref/"

        if (parts.isEmpty()) {
            breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">Root</li>""")
        } else {
            breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink" style="color: #0d6efd; text-decoration:none;">Root</a></li>""")
        }

        var accumulatedPath = ""
        for ((index, part) in parts.withIndex()) {
            accumulatedPath += "$part/"
            if (index >= 0) {
                breadcrumbs.append("""<li style="padding-right: .5rem; color: #6c757d;">/</li>""")
            }

            if (index < parts.size - 1) {
                breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink$accumulatedPath" style="color: #0d6efd; text-decoration:none;">$part</a></li>""")
            } else {
                breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">$part</li>""")
            }
        }
        return breadcrumbs.toString()
    }

    override fun getZipLink(req: HttpServletRequest, filePath: String): String {
        val pathSegments = parsePath(req.pathInfo ?: "/")
        val session = Session(pathSegments.first())
        return "${req.contextPath}/fileZip?session=$session&path=$filePath"
    }
}