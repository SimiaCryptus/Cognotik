package com.simiacryptus.cognotik.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.txt.BlockComment
import com.simiacryptus.cognotik.txt.LineComment
import com.simiacryptus.cognotik.txt.TextBlockFactory
import java.util.*

private fun line(prefix: String) = LineComment.Factory(prefix)
private fun block(start: String, end: String, linePrefix: String = "") = BlockComment.Factory(start, linePrefix, end)
private fun cBlock() = block("/*", "*/")
private fun cDoc() = block("/**", "*/", "*")

enum class ComputerLanguage(configuration: Configuration) {
    Java(
        Configuration().setDocumentationStyle("JavaDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(BlockComment.Factory("/**", " * ", " */")).setFileExtensions("java")
    ),
    Cpp(
        Configuration().setDocumentationStyle("Doxygen").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("cpp", "cc", "cxx", "c++", "hpp", "hh", "hxx")
    ),
    LUA(
        Configuration().setDocumentationStyle("LuaDoc").setLineComments(line("--"))
            .setBlockComments(block("--[[", "]]")).setDocComments(block("---[[", "]]")).setFileExtensions("lua")
    ),
    SVG(
        Configuration().setDocumentationStyle("SVG").setLineComments(block("<!--", "-->"))
            .setBlockComments(block("<!--", "-->")).setDocComments(block("<!--", "-->")).setFileExtensions("svg")
    ),
    OpenSCAD(
        Configuration().setDocumentationStyle("OpenSCAD").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("scad")
    ),
    Bash(Configuration().setLineComments(line("#")).setFileExtensions("sh", "bash")),
    Markdown(
        Configuration().setDocumentationStyle("Markdown").setLineComments(block("<!--", "-->"))
            .setBlockComments(block("<!--", "-->")).setDocComments(block("<!--", "-->"))
            .setFileExtensions("md", "markdown")
    ),
    Text(Configuration().setDocumentationStyle("Text").setLineComments(line("#")).setFileExtensions("txt")),
    XML(
        Configuration().setDocumentationStyle("XML").setLineComments(block("<!--", "-->"))
            .setBlockComments(block("<!--", "-->")).setDocComments(block("<!--", "-->"))
            .setFileExtensions("xml", "xsd", "xsl", "xslt")
    ),
    Ada(Configuration().setLineComments(line("--")).setFileExtensions("ada", "adb", "ads")),
    Assembly(Configuration().setLineComments(line(";")).setFileExtensions("asm", "s", "assembly")),
    Basic(Configuration().setLineComments(line("'")).setFileExtensions("bas", "basic", "bs")),
    C(
        Configuration().setDocumentationStyle("Doxygen").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("c", "h")
    ),
    Clojure(
        Configuration().setDocumentationStyle("ClojureDocs").setLineComments(line(";"))
            .setFileExtensions("clj", "cljs", "cljc", "edn")
    ),
    COBOL(Configuration().setLineComments(line("*>")).setFileExtensions("cob", "cbl", "cobol")),
    CSharp(
        Configuration().setDocumentationStyle("XML").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(line("///")).setFileExtensions("cs", "csx")
    ),
    CSS(
        Configuration().setLineComments(cBlock()).setBlockComments(cBlock()).setDocComments(cDoc())
            .setFileExtensions("css")
    ),
    Dart(
        Configuration().setDocumentationStyle("DartDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(line("///")).setFileExtensions("dart")
    ),
    Delphi(
        Configuration().setLineComments(line("//")).setBlockComments(block("{", "}"))
            .setFileExtensions("delphi", "dpr")
    ),
    Erlang(
        Configuration().setDocumentationStyle("EDoc").setLineComments(line("%")).setDocComments(line("%%"))
            .setFileExtensions("erl", "hrl")
    ),
    Elixir(
        Configuration().setDocumentationStyle("ExDoc").setLineComments(line("#"))
            .setDocComments(block("@doc \"\"\"", "\"\"\"")).setFileExtensions("ex", "exs")
    ),
    FORTRAN(
        Configuration().setLineComments(line("!"))
            .setFileExtensions("f", "for", "ftn", "f77", "f90", "f95", "f03", "f08")
    ),
    FSharp(
        Configuration().setLineComments(line("//")).setBlockComments(block("(*", "*)"))
            .setDocComments(line("///")).setFileExtensions("fs", "fsi", "fsx")
    ),
    Go(
        Configuration().setDocumentationStyle("GoDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(line("//")).setFileExtensions("go")
    ),
    Groovy(
        Configuration().setDocumentationStyle("GroovyDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("groovy", "gradle")
    ),
    Haskell(
        Configuration().setDocumentationStyle("Haddock").setLineComments(line("--"))
            .setBlockComments(block("{-", "-}")).setDocComments(block("{-|", "-}")).setFileExtensions("hs", "lhs")
    ),
    HTML(
        Configuration().setLineComments(block("<!--", "-->")).setBlockComments(block("<!--", "-->"))
            .setDocComments(block("<!--", "-->")).setFileExtensions("html", "htm")
    ),
    Julia(
        Configuration().setLineComments(line("#")).setBlockComments(block("#=", "=#")).setFileExtensions("jl")
    ),
    JavaScript(
        Configuration().setDocumentationStyle("JSDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("js", "mjs", "cjs", "jsx", "javascript")
    ),
    Json(Configuration().setLineComments(line("//")).setFileExtensions("json", "jsonc")),
    Kotlin(
        Configuration().setDocumentationStyle("KDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("kt", "kts")
    ),
    Lisp(
        Configuration().setLineComments(line(";")).setBlockComments(block("#|", "|#"))
            .setFileExtensions("lisp", "lsp", "cl")
    ),
    Logo(Configuration().setLineComments(line(";")).setFileExtensions("logo")),
    MATLAB(
        Configuration().setLineComments(line("%")).setBlockComments(block("%{", "%}"))
            .setFileExtensions("m", "matlab")
    ),
    OCaml(
        Configuration().setDocumentationStyle("OCamlDoc").setLineComments(block("(*", "*)"))
            .setBlockComments(block("(*", "*)")).setDocComments(block("(**", "*)")).setFileExtensions("ml", "mli")
    ),
    Pascal(
        Configuration().setLineComments(line("//")).setBlockComments(block("{", "}"))
            .setFileExtensions("pas", "pp", "pascal")
    ),
    PHP(
        Configuration().setDocumentationStyle("PHPDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("php")
    ),
    Perl(
        Configuration().setDocumentationStyle("POD").setLineComments(line("#"))
            .setBlockComments(block("=pod", "=cut")).setFileExtensions("pl", "pm", "perl")
    ),
    Prolog(Configuration().setLineComments(line("%")).setBlockComments(cBlock()).setFileExtensions("prolog")),
    Python(
        Configuration().setDocumentationStyle("PyDoc").setLineComments(line("#"))
            .setBlockComments(block("\"\"\"", "\"\"\"")).setFileExtensions("py", "pyw", "python")
    ),
    R(Configuration().setLineComments(line("#")).setDocComments(line("#'")).setFileExtensions("r")),
    Ruby(
        Configuration().setDocumentationStyle("RDoc").setLineComments(line("#"))
            .setBlockComments(block("=begin", "=end")).setFileExtensions("rb", "ruby", "rake", "gemspec")
    ),
    Racket(
        Configuration().setLineComments(line(";")).setBlockComments(block("#|", "|#"))
            .setFileExtensions("rkt", "racket")
    ),
    Rust(
        Configuration().setDocumentationStyle("Rustdoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(line("///")).setFileExtensions("rs")
    ),
    Scala(
        Configuration().setDocumentationStyle("ScalaDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("scala", "sc")
    ),
    Scheme(
        Configuration().setLineComments(line(";")).setBlockComments(block("#|", "|#"))
            .setFileExtensions("scm", "ss", "scheme")
    ),
    SCSS(
        Configuration().setDocumentationStyle("SCSS").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(line("///")).setFileExtensions("scss")
    ),
    SQL(Configuration().setLineComments(line("--")).setBlockComments(cBlock()).setFileExtensions("sql")),
    Smalltalk(
        Configuration().setLineComments(block("\"", "\"")).setBlockComments(block("\"", "\""))
            .setFileExtensions("st", "smalltalk")
    ),
    Swift(
        Configuration().setLineComments(line("//")).setBlockComments(cBlock()).setDocComments(line("///"))
            .setFileExtensions("swift")
    ),
    Tcl(Configuration().setLineComments(line("#")).setFileExtensions("tcl")),
    TypeScript(
        Configuration().setDocumentationStyle("TypeDoc").setLineComments(line("//")).setBlockComments(cBlock())
            .setDocComments(cDoc()).setFileExtensions("ts", "tsx", "mts", "cts", "typescript")
    ),
    VisualBasic(
        Configuration().setLineComments(line("'")).setDocComments(line("'''"))
            .setFileExtensions("vb", "vbs", "visualbasic")
    ),
    YAML(Configuration().setLineComments(line("#")).setFileExtensions("yaml", "yml")),
    ZShell(Configuration().setLineComments(line("#")).setFileExtensions("zsh")),
    PowerShell(
        Configuration().setLineComments(line("#")).setBlockComments(block("<#", "#>"))
            .setFileExtensions("ps1", "psm1", "psd1")
    );

    val extensions: List<CharSequence> = listOf(*configuration.fileExtensions)
    val docStyle: String = configuration.documentationStyle
    val lineComment: TextBlockFactory<*> = configuration.lineComments!!
    val blockComment: TextBlockFactory<*> = configuration.getBlockComments()!!
    val docComment: TextBlockFactory<*> = configuration.getDocComments()!!

    internal class Configuration {
        var documentationStyle = ""
            private set
        var fileExtensions = arrayOf<CharSequence>()
            private set
        var lineComments: TextBlockFactory<*>? = null
            private set
        private var blockComments: TextBlockFactory<*>? = null
        private var docComments: TextBlockFactory<*>? = null

        fun setDocumentationStyle(documentationStyle: String) = apply { this.documentationStyle = documentationStyle }

        fun setFileExtensions(vararg extensions: CharSequence) = apply {
            this.fileExtensions = arrayOf(*extensions)
        }

        fun setLineComments(lineComments: TextBlockFactory<*>) = apply { this.lineComments = lineComments }

        fun getBlockComments(): TextBlockFactory<*>? = blockComments ?: lineComments

        fun setBlockComments(blockComments: TextBlockFactory<*>) = apply { this.blockComments = blockComments }

        fun getDocComments(): TextBlockFactory<*>? = docComments ?: getBlockComments()

        fun setDocComments(docComments: TextBlockFactory<*>) = apply { this.docComments = docComments }
    }

    companion object {
        /** Extension (lowercase, no dot) -> language. First declared language wins on conflicts. */
        private val byExtension: Map<String, ComputerLanguage> by lazy {
            val map = LinkedHashMap<String, ComputerLanguage>()
            entries.forEach { lang ->
                lang.extensions.forEach { map.putIfAbsent(it.toString().lowercase(Locale.ROOT), lang) }
            }
            map
        }

        @JvmStatic
        fun findByExtension(extension: CharSequence?): ComputerLanguage? =
            extension?.toString()?.trim()?.removePrefix(".")?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotEmpty() }?.let { byExtension[it] }

        @JvmStatic
        fun forFile(file: VirtualFile?): ComputerLanguage? =
            file?.takeIf { !it.isDirectory }?.extension?.let { findByExtension(it) }

        /** Uses the active editor's file if present, otherwise the selected virtual file. */
        @JvmStatic
        fun getComputerLanguage(e: AnActionEvent?): ComputerLanguage? {
            e ?: return null
            val editorFile = e.getData(CommonDataKeys.EDITOR)?.document
                ?.let { FileDocumentManager.getInstance().getFile(it) }
            return forFile(editorFile ?: e.getData(CommonDataKeys.VIRTUAL_FILE))
        }
    }
}