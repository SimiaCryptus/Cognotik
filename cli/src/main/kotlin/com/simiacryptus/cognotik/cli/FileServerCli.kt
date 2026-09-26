package com.simiacryptus.cognotik.cli

/**
 * Minimal foreground file server, and the reference example of a **permissive
 * local mount**: interactive terminals and unrestricted `child_process` are on
 * by default, because the process already runs with the invoking user's rights
 * and binds to loopback. Pass the lockdown flags to harden it.
 *
 * On top of the plain mount it also wires in the two agentic CLIs of this module
 * as FS API operations (see [ServerTaskActions]):
 *
 * ```
 * POST {mount}/.fsapi/v1/docops?command=plan
 * POST {mount}/.fsapi/v1/docops?command=run&path=docs/api.md
 * POST {mount}/.fsapi/v1/autofix?cmd=./gradlew%20build
 * POST {mount}/.fsapi/v1/modify?path=src/Foo.kt
 * GET  {mount}/.fsapi/v1/tasks[?id=t1]
 * ```
 *
 * The classic directory listing grows matching affordances: per-document
 * *Plan* / *Run* links for markdown files, a per-file *Modify* link (the port of
 * the IDE's `ModifyFilesAction`), an *AutoFix…* toolbar button, and a live output
 * panel that polls the task endpoint.
 *
 * Usage:
 *   FileServerCli [options] [directory]
 *
 * Options:
 *   -p, --port <n>     Port to listen on (default 8081, 0 = random free port)
 *   -h, --host <addr>  Interface to bind (default 127.0.0.1, use 0.0.0.0 for all)
 *       --no-git       Disable the Git UI/API features
 *       --read-only    Disable POST/PUT/DELETE (uploads, edits, deletes)
 *       --no-terminal  Disable /.fsapi/v1/terminal sessions
 *       --no-exec      Restrict /.fsapi/v1/exec to read-mostly git sub-commands
 *       --secure       --read-only --no-terminal --no-exec --no-tasks
 *       --shell <cmd>  Shell used for new terminals (default: auto-detect)
 *       --help         Print this help
 *
 * Runs until interrupted (Ctrl-C).
 */
object FileServerCli : FileServer() {

  /** Mount point of [docProcessorServlet]. */
  const val DOCOPS_PREFIX = "/docops"

  /** Mount point of the resource-based homepage ([StaticResourceServlet]). */
  const val HOME_PREFIX = "/home"

  const val LIB_PREFIX = "/lib"

  /** @see LIB_PREFIX */
  const val APP_PREFIX = "/app"

  const val PROXY_PREFIX = "/proxy"

  @JvmStatic
  fun main(args: Array<String>) {
    super._main(args)
  }
}

