package com.simiacryptus.cognotik.util

/**
 * Simple utility for generating LaTeX documents from text content.
 * This provides basic LaTeX document generation functionality for output validation.
 */
object LatexGenerator {

    /**
     * Generates a basic LaTeX document from the given content.
     * 
     * @param title Document title
     * @param content Main document content
     * @param author Document author (optional)
     * @return Complete LaTeX document as string
     */
    fun generateDocument(
        title: String,
        content: String,
        author: String? = null
    ): String {
        val authorSection = if (author != null) "\\author{${escapeLatex(author)}}" else ""
        
        return """
\documentclass{article}
\usepackage[utf8]{inputenc}
\usepackage{amsmath}
\usepackage{amsfonts}
\usepackage{amssymb}
\usepackage{listings}
\usepackage{xcolor}

\title{${escapeLatex(title)}}
$authorSection
\date{\today}

\lstset{
    basicstyle=\ttfamily\small,
    breaklines=true,
    frame=single,
    backgroundcolor=\color{gray!10}
}

\begin{document}

\maketitle

${processContent(content)}

\end{document}
        """.trimIndent()
    }

    /**
     * Generates a simple LaTeX report from structured content.
     * 
     * @param title Report title
     * @param sections List of section titles and content pairs
     * @return Complete LaTeX document as string
     */
    fun generateReport(
        title: String,
        sections: List<Pair<String, String>>
    ): String {
        val sectionsContent = sections.joinToString("\n\n") { (sectionTitle, content) ->
            "\\section{${escapeLatex(sectionTitle)}}\n${processContent(content)}"
        }

        return """
\documentclass{article}
\usepackage[utf8]{inputenc}
\usepackage{amsmath}
\usepackage{amsfonts}
\usepackage{amssymb}
\usepackage{listings}
\usepackage{xcolor}

\title{${escapeLatex(title)}}
\date{\today}

\lstset{
    basicstyle=\ttfamily\small,
    breaklines=true,
    frame=single,
    backgroundcolor=\color{gray!10}
}

\begin{document}

\maketitle

$sectionsContent

\end{document}
        """.trimIndent()
    }

    /**
     * Processes content to handle code blocks and basic formatting.
     */
    private fun processContent(content: String): String {
        var processed = escapeLatex(content)
        
        // Handle code blocks
        processed = processed.replace(Regex("```(\\w+)?\\n([\\s\\S]*?)\\n```")) { matchResult ->
            val rawLanguage = matchResult.groupValues[1].takeIf { it.isNotEmpty() } ?: "text"
            val language = mapToSupportedLanguage(rawLanguage)
            val code = matchResult.groupValues[2]
            "\\begin{lstlisting}[language=$language]\n$code\n\\end{lstlisting}"
        }
        
        // Handle inline code
        processed = processed.replace(Regex("`([^`]+)`")) { matchResult ->
            "\\texttt{${matchResult.groupValues[1]}}"
        }
        
        return processed
    }

    /**
     * Maps programming language names to languages supported by the listings package.
     */
    private fun mapToSupportedLanguage(language: String): String {
        return when (language.lowercase()) {
            "kotlin", "kt" -> "java"  // Kotlin syntax is similar to Java
            "javascript", "js" -> "java"  // Use Java for JavaScript
            "typescript", "ts" -> "java"  // Use Java for TypeScript  
            "python", "py" -> "python"
            "java" -> "java"
            "c", "cpp", "c++" -> "c"
            "html" -> "html"
            "xml" -> "xml"
            "sql" -> "sql"
            "bash", "sh", "shell" -> "bash"
            "json" -> "python"  // Use Python for JSON as it's close enough
            else -> "text"  // Fallback to plain text
        }
    }

    /**
     * Escapes special LaTeX characters in text.
     */
    private fun escapeLatex(text: String): String {
        return text
            .replace("\\", "\\textbackslash{}")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("$", "\\$")
            .replace("&", "\\&")
            .replace("%", "\\%")
            .replace("#", "\\#")
            .replace("^", "\\textasciicircum{}")
            .replace("_", "\\_")
            .replace("~", "\\textasciitilde{}")
    }
}