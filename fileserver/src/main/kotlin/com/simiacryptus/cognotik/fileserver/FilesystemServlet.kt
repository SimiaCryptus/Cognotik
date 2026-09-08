package com.simiacryptus.cognotik.fileserver

import com.simiacryptus.cognotik.fileserver.handler.*
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File

/**
 * FileServlet v2: everything the v1 servlet does (HTML browser, `_files.json`,
 * Monaco editor, markdown rendering, upload/PUT/DELETE, git UI) **plus** the
 * remote-filesystem HTTP API described in `docs/nodejs.md` — "FS API v1".
 *
 * The API is mounted at `{mount}/[<session>/].fsapi/v1/<op>` and is intercepted
 * in [service] *before* normal path parsing, so it never collides with real
 * files (the `.fsapi` segment is reserved by `PathUtils`/`FsPath`).
 *
 * Design invariants (nodejs.md §4 A1–A4):
 *  - **A1** the shim is not a security boundary: all access control stays here,
 *    in `FileAccessControl` + `FsPath`, and is applied identically to every op.
 *  - **A2** additive & versioned: the v1 surface is untouched.
 *  - **A3** one canonical root per mount: `/` in Node-space == [getFsApiRoot].
 *  - **A4** no magic: the FS API never performs `.md -> .html/.pdf` substitution,
 *    so `stat('foo.html')` is ENOENT unless `foo.html` really exists.
 *
 * Operations are registered `FsAction`/`GitAction` DynamicEnum constants, so a
 * downstream module can add or replace an operation without subclassing; the
 * live set is always discoverable via `GET {mount}/.fsapi/v1/actions`.
 *
 * Subclasses normally only need to implement [getDir]; override
 * [getFsApiConfig] to advertise/limit capabilities (read-only mode, exec
 * allowlist, watch mode, quotas).
 */
@MultipartConfig(
  fileSizeThreshold = 1024 * 1024 * 2,
  maxFileSize = 1024 * 1024 * 50,
  maxRequestSize = 1024 * 1024 * 100
)
abstract class FilesystemServlet : FileServlet() {

  /**
   * Optional git API provider. When non-null, requests whose path contains
   * `/.git/api/` are routed to [GitProvider.handleGitApiGet] /
   * [GitProvider.handleGitApiPost] from [service], *before* FS API / classic
   * routing is considered. Subclasses that expose the git UI/API should
   * override this instead of intercepting `.git/api/` paths themselves.
   */
  open val git: GitProvider? = null

  /**
   * The directory that FS API paths are resolved against — the Node-visible
   * filesystem root. Defaults to the same directory the HTML browser serves.
   */
  open fun getFsApiRoot(req: HttpServletRequest, resp: HttpServletResponse): File? = getDir(req, resp)

  /** Capability/limit advertisement returned by `GET /.fsapi/v1/meta`. */
  open fun getFsApiConfig(req: HttpServletRequest): FsApiConfig = FsApiConfig(
    execAllowlist = if (isGitEnabled(req)) mapOf("git" to GIT_SUBCOMMANDS) else emptyMap()
  )


  /** Set to false to serve the classic v1 surface only. */
  open fun isFsApiEnabled(req: HttpServletRequest): Boolean = true

  override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
    val pathInfo = req.pathInfo ?: req.servletPath ?: "/"
    val gitProvider = git
    if (gitProvider != null && pathInfo.contains("/.git/api/")) {
      when ((req.method ?: "GET").uppercase()) {
        "GET" -> gitProvider.handleGitApiGet(req, resp, pathInfo)
        "POST" -> gitProvider.handleGitApiPost(req, resp, pathInfo)
        else -> super.service(req, resp)
      }
      return
    }
    val route = FsApiRoute.parse(req.pathInfo ?: req.servletPath)
    if (route == null) {
      super.service(req, resp)
      return
    }
    val root = try {
      this@FilesystemServlet.getFsApiRoot(req, resp)
    } catch (e: Exception) {
      log.warn("Failed to resolve FS API root", e)
      null
    }
    val config = getFsApiConfig(req)
    fsService(req, resp, route, root, config, this.isFsApiEnabled(req))
  }

  companion object {
    private val log = LoggerFactory.getLogger(FilesystemServlet::class.java)

    /** Read-mostly git sub-commands considered safe to expose through /exec. */
    val GIT_SUBCOMMANDS: Set<String> = setOf(
      "init", "status", "log", "diff", "show", "branch", "checkout", "switch", "restore",
      "add", "commit", "reset", "clean", "rev-parse", "ls-files", "ls-tree", "describe",
      "merge-base", "stash", "tag", "blame", "shortlog", "config",
      /* export / packaging operations */
      "archive", "bundle", "clone",
      /* sequencer / integration operations */
      "revert", "cherry-pick", "merge", "rebase",
      /* submodule support (network fetches are still bounded by GIT_TERMINAL_PROMPT=0) */
      "submodule", "remote"
    )
    private fun fsService(
      req: HttpServletRequest, resp: HttpServletResponse,
      route: FsApiRoute,
      root: File?,
      config: FsApiConfig = FsApiConfig(),
      enableFsAPI: Boolean = true
    ) {
      val config = config.let {
        if (isWriteAllowed(getUser(req, resp), req)) it else it.readOnlyView()
      }
      if (!enableFsAPI) {
        FsErrors.write(resp, FsException(FsErrorCode.ENOSYS, "fsapi", null, "FS API is disabled for this mount"))
        return
      }
      if (route.version != FsApiHandler.API_VERSION) {
        FsErrors.write(
          resp,
          FsException(
            FsErrorCode.ENOSYS, "fsapi", null,
            "unsupported FS API version '${route.version}' (server speaks v${FsApiHandler.API_VERSION})"
          )
        )
        return
      }
      val method = (req.method ?: "GET").uppercase()
      val user = getUser(req, resp)
      val op = route.op
      val writeAllowed = isWriteAllowed(user, req)
      if (resp.isCommitted) return
      log.debug("FS API {} /{} (prefix='{}', user='{}')", method, op, route.prefix, user?.email ?: "anonymous")
      FsApiHandler.handle(
        method, op, req, resp, root,
        config, user, writeAllowed
      )
    }
  }
}