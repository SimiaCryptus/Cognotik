package com.simiacryptus.cognotik.fileserver.util

  import org.eclipse.jetty.http.MimeTypes

  object MimeTypeResolver {

    /**
     * Files Jetty's extension table cannot classify but which are text in
     * practice. Answering `application/octet-stream` for these makes every
     * client (notably the editor) treat them as binary — see note #1.
     */
    private val TEXT_FILENAMES = setOf(
      ".gitattributes", ".gitignore", ".gitmodules", ".gitkeep", ".dockerignore",
      ".editorconfig", ".env", ".npmrc", ".nvmrc", ".prettierrc", ".eslintrc",
      ".babelrc", ".readonly", ".writeable", ".hidden", ".classpath", ".project",
      "dockerfile", "makefile", "license", "licence", "notice", "readme",
      "changelog", "authors", "contributors", "codeowners", "gradlew", "procfile"
    )

    /** Source/config extensions Jetty does not know about. */
    private val TEXT_EXTENSIONS = setOf(
      "kt", "kts", "java", "gradle", "groovy", "scala", "py", "rb", "go", "rs", "php", "swift",
      "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "cs", "sh", "bash", "zsh", "sql", "lua", "r",
      "pl", "pm", "yml", "yaml", "toml", "ini", "cfg", "conf", "properties", "md", "markdown",
      "csv", "tsv", "diff", "patch", "lock", "tf", "tfvars", "bat", "cmd", "ps1", "mk", "proto",
      "graphql", "gql", "vue", "svelte", "dart", "ex", "exs", "jsx", "tsx", "ts", "mjs", "cjs"
    )

    fun getMimeType(fileName: String): String {
      val lower = fileName.lowercase()
      return when {
        lower.endsWith(".js") -> "application/javascript"
        lower.endsWith(".mjs") -> "application/javascript"
        lower.endsWith(".log") -> "text/plain"
        else -> MimeTypes.getDefaultMimeByExtension(fileName) ?: fallback(lower)
      }
    }

    private fun fallback(lower: String): String {
      val name = lower.substringAfterLast('/').substringAfterLast('\\')
      if (name in TEXT_FILENAMES) return "text/plain"
      /* A dotfile with no further dot ('.gitattributes', '.foorc') has no
         extension at all: substringAfterLast would return the whole name. */
      if (name.startsWith(".") && !name.drop(1).contains('.')) return "text/plain"
      val extension = name.substringAfterLast('.', "")
      if (extension.isNotEmpty() && extension in TEXT_EXTENSIONS) return "text/plain"
      /* No extension whatsoever (LICENSE, Makefile handled above): assume text,
         because a genuine binary is caught by the NUL sniff before editing. */
      if (extension.isEmpty()) return "text/plain"
      return "application/octet-stream"
    }
  }