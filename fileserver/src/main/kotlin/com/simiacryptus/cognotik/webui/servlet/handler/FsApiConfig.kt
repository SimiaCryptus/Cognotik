package com.simiacryptus.cognotik.webui.servlet.handler

/**
 * Capability/limit configuration surfaced through GET /.fsapi/v1/meta
 * (nodejs.md §5.8). The client shim configures itself from this, so a
 * disabled capability produces a clean ENOSYS rather than a silent no-op.
 */
data class FsApiConfig(
  /** Server-wide read-only mode (`--read-only`): all mutations answer EROFS. */
  val readOnly: Boolean = false,
  /** cmd -> allowed first-arg (sub-command) set; empty set = any sub-command. */
  val execAllowlist: Map<String, Set<String>> = mapOf(
  ),
  /**
   * Permissive mode for trusted (typically loopback) deployments: any bare
   * command name may be spawned, regardless of [execAllowlist]. Still never
   * routed through a shell.
   */
  val execAllowAny: Boolean = false,
  /**
   * When true, argument-injection hardening (`-c`, `--upload-pack`, ...) is
   * applied to /exec. Disable only for a trusted local server, where the
   * terminal already grants arbitrary execution anyway.
   */
  val execRestrictArguments: Boolean = true,
  /** "sse", "poll" or "none". */
  val watchMode: String = "sse",
  val utimesEnabled: Boolean = true,
  val snapshotEnabled: Boolean = true,
  val resolveEnabled: Boolean = true,
  val maxFileSize: Long = 50L * 1024 * 1024,
  val maxRequestSize: Long = 100L * 1024 * 1024,
  val maxBatchOps: Int = 256,
  val maxDirEntries: Int = 50_000,
  val maxDepth: Int = 32,
  val maxSnapshotBytes: Long = 32L * 1024 * 1024,
  val execTimeoutMs: Long = 30_000,
  /** CSRF mitigation: mutating requests must carry `X-Fs-Api`. */
  val requireApiHeader: Boolean = true,
  val cwd: String = "/",
  val tmpdir: String = "/.tmp",
  val caseSensitive: Boolean = defaultCaseSensitive(),
  val crossOriginIsolated: Boolean = false,
  /** Advertised sync strategy for the client bridge: "sab" | "xhr" | "snapshot". */
  val syncStrategy: String = "xhr",
  val terminalEnabled: Boolean = true,
  val maxTerminals: Int = 8,
  /** Empty = auto-detect an interactive login-less shell ($SHELL, bash, sh, cmd). */
  val terminalShell: List<String> = emptyList(),
) {


  companion object {
    fun defaultCaseSensitive(): Boolean {
      val os = System.getProperty("os.name", "").lowercase()
      return !(os.contains("win") || os.contains("mac") || os.contains("darwin"))
    }

    fun platform(): String {
      val os = System.getProperty("os.name", "").lowercase()
      return when {
        os.contains("win") -> "win32"
        os.contains("mac") || os.contains("darwin") -> "darwin"
        os.contains("sunos") || os.contains("solaris") -> "sunos"
        os.contains("aix") -> "aix"
        else -> "linux"
      }
    }
  }
}